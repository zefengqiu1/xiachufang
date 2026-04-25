package com.webcrawler.recipe.app.model.chat;

import com.webcrawler.recipe.app.model.search.RecipeMatch;
import com.webcrawler.recipe.app.model.search.RecipeSource;
import java.util.List;

public record RecipeAskResponse(
        String sessionId,
        String answerMode,
        String normalizedQuery,
        String reply,
        RecipeMatch matchedRecipe,
        List<RecipeSource> sources
) {
}
