package com.webcrawler.recipe.app.model.search;

import com.webcrawler.recipe.app.model.recipe.RecipeChunk;
import com.webcrawler.recipe.app.model.recipe.RecipeIndexedDocument;
import java.util.List;

public record RecipeSearchHit(
        RecipeIndexedDocument recipe,
        double totalScore,
        double nameScore,
        double keywordScore,
        double vectorScore,
        double confidence,
        List<RecipeChunk> contextChunks
) {
}
