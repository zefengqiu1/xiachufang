package com.webcrawler.recipe.app.model.web;

public record WebSearchResult(
        String title,
        String url,
        String snippet,
        double score
) {
}
