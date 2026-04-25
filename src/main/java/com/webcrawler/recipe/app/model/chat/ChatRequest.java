package com.webcrawler.recipe.app.model.chat;

public record ChatRequest(
        String sessionId,
        String message
) {
}
