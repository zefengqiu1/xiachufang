package com.webcrawler.recipe.app.model.recipe;

public class ScoredRecipeChunk {

    private final RecipeChunk chunk;
    private final double score;

    public ScoredRecipeChunk(RecipeChunk chunk, double score) {
        this.chunk = chunk;
        this.score = score;
    }

    public RecipeChunk getChunk() {
        return chunk;
    }

    public double getScore() {
        return score;
    }
}
