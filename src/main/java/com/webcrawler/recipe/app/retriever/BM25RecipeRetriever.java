package com.webcrawler.recipe.app.retriever;

import com.webcrawler.recipe.app.model.recipe.RecipeChunk;
import com.webcrawler.recipe.app.model.recipe.ScoredRecipeChunk;
import java.util.ArrayList;
import java.util.List;

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

        String[] qTokens = query.toLowerCase().split("\\s+");
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
}
