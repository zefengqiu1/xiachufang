package com.webcrawler.recipe.app.model.search;

import com.webcrawler.recipe.app.model.recipe.RecipeChunk;
import com.webcrawler.recipe.app.model.recipe.RecipeDocument;
import java.util.List;

/**
 *
 * @param recipe
 * @param totalScore
 * @param nameScore
 * @param keywordScore
 * @param vectorScore
 * @param confidence
 * @param contextChunks
 *
 * 用来返回本地搜索的返回
 */
public record RecipeSearchHit(
        RecipeDocument recipe,
        String canonicalName,
        double totalScore,
        double nameScore,
        double keywordScore,
        double vectorScore,
        double confidence,
        List<RecipeChunk> contextChunks
) {
}
