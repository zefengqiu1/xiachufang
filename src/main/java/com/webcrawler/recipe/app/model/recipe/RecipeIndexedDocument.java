package com.webcrawler.recipe.app.model.recipe;

import java.util.List;

public record RecipeIndexedDocument(
        RecipeDocument document,
        String canonicalName,
        String normalizedName,
        String normalizedSearchText,
        List<String> searchableTerms,
        List<RecipeChunk> chunks,
        float[] embedding
) {
}
