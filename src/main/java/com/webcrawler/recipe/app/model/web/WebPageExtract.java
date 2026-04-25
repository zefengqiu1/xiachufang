package com.webcrawler.recipe.app.model.web;

import java.util.List;

public record WebPageExtract(
        String title,
        String url,
        String summary,
        List<String> ingredients,
        List<String> steps,
        List<String> tips,
        String rawText
) {
}
