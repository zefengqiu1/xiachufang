package com.webcrawler.recipe.app.model.chat;

import java.util.List;
import java.util.Map;

public record ChatResponse(
        String sessionId,
        String reply,
        List<String> usedTools,
        Map<String, Object> metadata
) {
}
