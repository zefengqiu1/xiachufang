package com.webcrawler.recipe.app.model.recipe;

public record RecipeSummary(
        String id,
        String name,
        String canonicalName,
        String category,
        Integer difficulty,
        Integer servings,
        String sourcePath
) {
}
