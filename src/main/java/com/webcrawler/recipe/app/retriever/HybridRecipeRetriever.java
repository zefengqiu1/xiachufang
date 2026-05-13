package com.webcrawler.recipe.app.retriever;

import com.webcrawler.recipe.app.model.recipe.RecipeChunk;
import com.webcrawler.recipe.app.model.recipe.ScoredRecipeChunk;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HybridRecipeRetriever implements ContentRetriever {

    private static final int TOP_K = 10;
    private static final int RRF_K = 60;
    private static final Logger log = LoggerFactory.getLogger(HybridRecipeRetriever.class);

    private final BM25RecipeRetriever bm25Retriever;
    private final VectorRecipeRetriever vectorRetriever;
    private final CohereRecipeReranker reranker;
    private final boolean debugRag;

    public HybridRecipeRetriever(
            BM25RecipeRetriever bm25Retriever,
            VectorRecipeRetriever vectorRetriever,
            CohereRecipeReranker reranker,
            boolean debugRag
    ) {
        this.bm25Retriever = bm25Retriever;
        this.vectorRetriever = vectorRetriever;
        this.reranker = reranker;
        this.debugRag = debugRag;
    }

    @Override
    public List<Content> retrieve(Query query) {

        List<ScoredRecipeChunk> bm25 = bm25Retriever.search(query.text(), TOP_K);

        List<VectorRecipeRetriever.ScoredContent> vector = vectorRetriever.search(query.text(), TOP_K);

        debugInputRankings(query.text(), bm25, vector);

        Map<String, FusedContent> fused = new LinkedHashMap<>();

        for (int i = 0; i < bm25.size(); i++) {
            ScoredRecipeChunk scored = bm25.get(i);
            RecipeChunk chunk = scored.getChunk();
            Content content = toContent(chunk);
            mergeRrfScore(fused, buildKey(chunk), content, i);
        }

        for (int i = 0; i < vector.size(); i++) {
            VectorRecipeRetriever.ScoredContent scored = vector.get(i);
            Content content = scored.content();
            mergeRrfScore(fused, buildKey(content), content, i);
        }

        List<Content> candidates = fused.values().stream()
                .sorted(Comparator.comparingDouble(FusedContent::score).reversed())
                .map(FusedContent::content)
                .toList();
        debugFusedRanking(query.text(), fused);
        return reranker.rerank(query.text(), candidates);
    }

    private String buildKey(RecipeChunk chunk) {
        return chunk.getDishId()
                + "_"
                + chunk.getView()
                + "_"
                + chunk.getStepRange();
    }

    private String buildKey(Content content) {
        return metadata(content, "dishId")
                + "_"
                + metadata(content, "view")
                + "_"
                + metadata(content, "stepRange");
    }

    private Content toContent(RecipeChunk chunk) {
        Metadata metadata = Metadata.from(Map.of(
                "dishId", chunk.getDishId() == null ? "" : chunk.getDishId(),
                "dishName", chunk.getDishName() == null ? "" : chunk.getDishName(),
                "view", chunk.getView() == null ? "" : chunk.getView(),
                "category", chunk.getCategory() == null ? "" : chunk.getCategory(),
                "difficulty", chunk.getDifficulty() == null ? "" : String.valueOf(chunk.getDifficulty()),
                "stepRange", chunk.getStepRange() == null ? "" : chunk.getStepRange()
        ));
        TextSegment segment = TextSegment.from(chunk.getText(), metadata);
        return Content.from(segment);
    }

    private void mergeRrfScore(Map<String, FusedContent> fused, String key, Content content, int rankIndex) {
        double rrfScore = 1.0D / (RRF_K + rankIndex + 1.0D);
        fused.compute(key, (ignored, current) -> {
            if (current == null) {
                return new FusedContent(content, rrfScore);
            }
            return new FusedContent(current.content(), current.score() + rrfScore);
        });
    }

    private String metadata(Content content, String key) {
        String value = content.textSegment().metadata().getString(key);
        return value == null ? "" : value;
    }

    private void debugInputRankings(
            String query,
            List<ScoredRecipeChunk> bm25,
            List<VectorRecipeRetriever.ScoredContent> vector) {
        if (!debugRag) {
            return;
        }

        log.info("[RRF-INPUT] query='{}', bm25Count={}, vectorCount={}", query, bm25.size(), vector.size());

        for (int i = 0; i < Math.min(bm25.size(), 10); i++) {
            ScoredRecipeChunk scored = bm25.get(i);
            RecipeChunk chunk = scored.getChunk();
            log.info(
                    "[RRF-BM25] rank={}, score={}, dishId={}, dishName={}, view={}, stepRange={}, preview={}",
                    i + 1,
                    round(scored.getScore()),
                    safe(chunk.getDishId()),
                    safe(chunk.getDishName()),
                    safe(chunk.getView()),
                    safe(chunk.getStepRange()),
                    preview(chunk.getText(), 120)
            );
        }

        for (int i = 0; i < Math.min(vector.size(), 10); i++) {
            VectorRecipeRetriever.ScoredContent scored = vector.get(i);
            Content content = scored.content();
            log.info(
                    "[RRF-VECTOR] rank={}, score={}, dishId={}, dishName={}, view={}, stepRange={}, preview={}",
                    i + 1,
                    round(scored.score()),
                    metadata(content, "dishId"),
                    metadata(content, "dishName"),
                    metadata(content, "view"),
                    metadata(content, "stepRange"),
                    preview(content.textSegment().text(), 120)
            );
        }
    }

    private void debugFusedRanking(String query, Map<String, FusedContent> fused) {
        if (!debugRag) {
            return;
        }

        fused.values().stream()
                .sorted(Comparator.comparingDouble(FusedContent::score).reversed())
                .limit(10)
                .forEachOrdered(new java.util.function.Consumer<>() {
                    private int rank = 1;

                    @Override
                    public void accept(FusedContent fusedContent) {
                        Content content = fusedContent.content();
                        log.info(
                                "[RRF-FUSED] query='{}', rank={}, rrfScore={}, dishId={}, dishName={}, view={}, stepRange={}, preview={}",
                                query,
                                rank++,
                                round(fusedContent.score()),
                                metadata(content, "dishId"),
                                metadata(content, "dishName"),
                                metadata(content, "view"),
                                metadata(content, "stepRange"),
                                preview(content.textSegment().text(), 120)
                        );
                    }
                });
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private double round(double value) {
        return Math.round(value * 10000.0D) / 10000.0D;
    }

    private String preview(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\n", "\\n");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private record FusedContent(Content content, double score) {
    }
}
