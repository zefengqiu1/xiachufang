package com.webcrawler.recipe.app.model.chat;

public record RecipeAskRequest(
        String sessionId,
        String query
) {
}
