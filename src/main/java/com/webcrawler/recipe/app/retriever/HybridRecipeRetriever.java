package com.webcrawler.recipe.app.retriever;

import com.webcrawler.recipe.app.model.recipe.RecipeChunk;
import com.webcrawler.recipe.app.model.recipe.ScoredRecipeChunk;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HybridRecipeRetriever implements ContentRetriever {

    private final BM25RecipeRetriever bm25Retriever;
    private final VectorRecipeRetriever vectorRetriever;

    public HybridRecipeRetriever(
            BM25RecipeRetriever bm25Retriever,
            VectorRecipeRetriever vectorRetriever
    ) {
        this.bm25Retriever = bm25Retriever;
        this.vectorRetriever = vectorRetriever;
    }

    @Override
    public List<Content> retrieve(Query query) {

        List<ScoredRecipeChunk> bm25 = bm25Retriever.search(query.text(), 10);

        List<Content> vector = vectorRetriever.retrieve(query);

        Map<String, Content> merged = new LinkedHashMap<>();

        for (ScoredRecipeChunk s : bm25) {
            RecipeChunk chunk = s.getChunk();
            merged.putIfAbsent(buildKey(chunk), toContent(chunk));
        }

        for (Content content : vector) {
            merged.putIfAbsent(content.textSegment().text(), content);
        }

        return merged.values().stream().toList();
    }

    private String buildKey(RecipeChunk chunk) {
        return chunk.getDishId()
                + "_"
                + chunk.getView()
                + "_"
                + chunk.getStepRange();
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
}
