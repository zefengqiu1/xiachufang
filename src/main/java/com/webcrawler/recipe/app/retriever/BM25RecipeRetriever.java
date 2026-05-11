package com.webcrawler.recipe.app.retriever;

import com.webcrawler.recipe.app.model.recipe.RecipeChunk;
import com.webcrawler.recipe.app.model.recipe.ScoredRecipeChunk;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BM25RecipeRetriever {

    private final List<RecipeChunk> index;

    public BM25RecipeRetriever(List<RecipeChunk> index) {
        this.index = index;
    }

    public List<ScoredRecipeChunk> search(String query, int topK) {

        List<ScoredRecipeChunk> scored = new ArrayList<>();

        for (RecipeChunk chunk : index) {
            double score = bm25Like(query, chunk.getText());
            scored.add(new ScoredRecipeChunk(chunk, score));
        }

        return scored.stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .toList();
    }

    private double bm25Like(String query, String doc) {

        List<String> qTokens = tokenize(query);
        String lowerDoc = doc.toLowerCase();

        double score = 0;

        for (String q : qTokens) {
            int freq = count(lowerDoc, q);
            if (freq > 0) {
                score += Math.log(1 + freq);
            }
        }

        return score;
    }

    private int count(String text, String word) {
        int count = 0;
        int idx = 0;

        while ((idx = text.indexOf(word, idx)) != -1) {
            count++;
            idx += word.length();
        }

        return count;
    }

    private List<String> tokenize(String text) {
        String normalized = text == null ? "" : text.toLowerCase().trim();
        if (normalized.isBlank()) {
            return List.of();
        }

        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }

        if (tokens.size() <= 1 && normalized.indexOf(' ') < 0 && normalized.length() > 1) {
            for (int i = 0; i < normalized.length() - 1; i++) {
                tokens.add(normalized.substring(i, i + 2));
            }
            tokens.add(normalized);
        }

        return List.copyOf(tokens);
    }
}
