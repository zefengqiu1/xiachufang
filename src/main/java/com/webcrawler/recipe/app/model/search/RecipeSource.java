package com.webcrawler.recipe.app.model.search;

public record RecipeSource(
        String type,
        String title,
        String reference,
        double score
) {
}
