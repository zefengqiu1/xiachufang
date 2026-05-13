package com.webcrawler.recipe.app.retriever;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.cohere.CohereScoringModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CohereRecipeReranker {

    private static final Logger log = LoggerFactory.getLogger(CohereRecipeReranker.class);

    private final ObjectMapper objectMapper;
    private final CohereScoringModel scoringModel;
    private final int topN;
    private final boolean enabled;
    private final boolean debugRag;

    public CohereRecipeReranker(
            ObjectMapper objectMapper,
            CohereScoringModel scoringModel,
            int topN,
            boolean enabled,
            boolean debugRag) {
        this.objectMapper = objectMapper;
        this.scoringModel = scoringModel;
        this.topN = topN;
        this.enabled = enabled;
        this.debugRag = debugRag;
    }

    public boolean isEnabled() {
        return enabled && scoringModel != null;
    }

    public List<Content> rerank(String query, List<Content> candidates) {
        if (!enabled) {
            debugSkip(query, "disabled", candidates);
            return candidates == null ? List.of() : candidates;
        }
        if (scoringModel == null) {
            debugSkip(query, "no_scoring_model", candidates);
            return candidates == null ? List.of() : candidates;
        }
        if (candidates == null || candidates.isEmpty()) {
            debugSkip(query, "empty_candidates", candidates);
            return candidates == null ? List.of() : candidates;
        }

        try {
            List<TextSegment> segments = new ArrayList<>(candidates.size());
            for (Content candidate : candidates) {
                segments.add(TextSegment.from(toStructuredDocument(candidate), candidate.textSegment().metadata()));
            }

            Response<List<Double>> response = scoringModel.scoreAll(segments, query);
            List<Double> scores = response.content();
            if (scores == null || scores.isEmpty()) {
                return candidates;
            }

            debugScores(query, candidates, scores);

            List<IndexedContent> reranked = new ArrayList<>(candidates.size());
            for (int i = 0; i < Math.min(candidates.size(), scores.size()); i++) {
                reranked.add(new IndexedContent(i, candidates.get(i), scores.get(i)));
            }

            reranked.sort(Comparator.comparingDouble(IndexedContent::score).reversed());
            debugRankingChanges(query, reranked);

            List<Content> ordered = reranked.stream()
                    .limit(Math.min(topN, reranked.size()))
                    .map(IndexedContent::content)
                    .toList();

            if (ordered.size() == candidates.size()) {
                return ordered;
            }

            List<Content> completed = new ArrayList<>(ordered);
            for (Content candidate : candidates) {
                if (!completed.contains(candidate)) {
                    completed.add(candidate);
                }
            }
            return completed;
        } catch (Exception e) {
            log.warn("Cohere rerank request failed, falling back to hybrid order: {}", e.getMessage());
            return candidates;
        }
    }

    private String toStructuredDocument(Content content) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "dishId", metadata(content, "dishId"),
                "dishName", metadata(content, "dishName"),
                "view", metadata(content, "view"),
                "category", metadata(content, "category"),
                "difficulty", metadata(content, "difficulty"),
                "stepRange", metadata(content, "stepRange"),
                "text", content.textSegment().text() == null ? "" : content.textSegment().text()
        ));
    }

    private String metadata(Content content, String key) {
        String value = content.textSegment().metadata().getString(key);
        return value == null ? "" : value;
    }

    private void debugScores(String query, List<Content> candidates, List<Double> scores) {
        if (!debugRag) {
            return;
        }
        log.info("[RERANK-SCORE] query='{}', candidateCount={}", query, candidates.size());
        for (int i = 0; i < Math.min(Math.min(candidates.size(), scores.size()), 10); i++) {
            Content content = candidates.get(i);
            log.info(
                    "[RERANK-SCORE] preRank={}, score={}, dishId={}, dishName={}, view={}, stepRange={}, preview={}",
                    i + 1,
                    round(scores.get(i)),
                    metadata(content, "dishId"),
                    metadata(content, "dishName"),
                    metadata(content, "view"),
                    metadata(content, "stepRange"),
                    preview(content.textSegment().text(), 120)
            );
        }
    }

    private void debugRankingChanges(String query, List<IndexedContent> reranked) {
        if (!debugRag) {
            return;
        }
        for (int i = 0; i < Math.min(reranked.size(), 10); i++) {
            IndexedContent content = reranked.get(i);
            log.info(
                    "[RERANK-ORDER] query='{}', postRank={}, preRank={}, score={}, dishId={}, dishName={}, view={}, stepRange={}, preview={}",
                    query,
                    i + 1,
                    content.originalIndex() + 1,
                    round(content.score()),
                    metadata(content.content(), "dishId"),
                    metadata(content.content(), "dishName"),
                    metadata(content.content(), "view"),
                    metadata(content.content(), "stepRange"),
                    preview(content.content().textSegment().text(), 120)
            );
        }
    }

    private double round(double value) {
        return Math.round(value * 10000.0D) / 10000.0D;
    }

    private void debugSkip(String query, String reason, List<Content> candidates) {
        if (!debugRag) {
            return;
        }
        log.info(
                "[RERANK-SKIP] query='{}', reason={}, enabled={}, scoringModelPresent={}, candidateCount={}",
                query,
                reason,
                enabled,
                scoringModel != null,
                candidates == null ? 0 : candidates.size()
        );
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

    private record IndexedContent(int originalIndex, Content content, double score) {
    }
}
