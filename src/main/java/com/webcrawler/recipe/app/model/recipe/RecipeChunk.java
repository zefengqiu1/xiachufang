package com.webcrawler.recipe.app.model.recipe;

public record RecipeChunk(
        String id,
        String recipeId,
        String type,
        int order,
        String text
) {
}
