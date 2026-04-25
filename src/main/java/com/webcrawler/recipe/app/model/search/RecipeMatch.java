package com.webcrawler.recipe.app.model.search;

public record RecipeMatch(
        String id,
        String name,
        String canonicalName,
        double score,
        double confidence,
        String category
) {
}
