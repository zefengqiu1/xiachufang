package com.webcrawler.recipe.app.service;

import com.webcrawler.recipe.app.model.recipe.RecipeIndexedDocument;
import com.webcrawler.recipe.app.model.recipe.RecipeSummary;
import com.webcrawler.recipe.app.repository.FileRecipeRepository;
import java.util.Comparator;
import java.util.List;

import com.webcrawler.recipe.app.util.RecipeNormalizer;
import org.springframework.stereotype.Service;

@Service
public class RecipeCatalogService {

    private final FileRecipeRepository fileRecipeRepository;
    private final RecipeNormalizer recipeNormalizer;

    public RecipeCatalogService(FileRecipeRepository fileRecipeRepository, RecipeNormalizer recipeNormalizer) {
        this.fileRecipeRepository = fileRecipeRepository;
        this.recipeNormalizer = recipeNormalizer;
    }

    public List<RecipeSummary> listRecipes(String query, int limit) {
        String normalized = recipeNormalizer.normalizeUserQuery(query == null ? "" : query);
        return fileRecipeRepository.findAll().stream()
                .filter(recipe -> normalized.isBlank() || recipe.normalizedSearchText().contains(normalized))
                .sorted(Comparator.comparing((RecipeIndexedDocument recipe) -> recipe.canonicalName()))
                .limit(Math.max(1, limit))
                .map(this::toSummary)
                .toList();
    }

    public RecipeSummary getRecipe(String id) {
        return fileRecipeRepository.findById(id)
                .map(this::toSummary)
                .orElse(null);
    }

    private RecipeSummary toSummary(RecipeIndexedDocument recipe) {
        return new RecipeSummary(
                recipe.document().id(),
                recipe.document().name(),
                recipe.canonicalName(),
                recipe.document().category(),
                recipe.document().difficulty(),
                recipe.document().servings(),
                recipe.document().sourcePath()
        );
    }
}
