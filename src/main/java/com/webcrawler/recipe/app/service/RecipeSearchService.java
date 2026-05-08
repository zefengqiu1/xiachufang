package com.webcrawler.recipe.app.service;

import com.webcrawler.recipe.app.model.recipe.RecipeChunk;
import com.webcrawler.recipe.app.model.recipe.RecipeIndexedDocument;
import com.webcrawler.recipe.app.model.search.RecipeSearchHit;
import com.webcrawler.recipe.app.repository.FileRecipeRepository;
import java.util.Comparator;
import java.util.List;

import com.webcrawler.recipe.app.util.RecipeNormalizer;
import org.springframework.stereotype.Service;


/**
 * searchBest函数：计算query 的vector score 和菜谱的id 的相似读，返回score 最高的。
 *
 */
@Service
public class RecipeSearchService {

    private final FileRecipeRepository fileRecipeRepository;
    private final RecipeNormalizer recipeNormalizer;
    private final InMemoryRecipeEmbeddingIndex embeddingIndex;

    public RecipeSearchService(
            FileRecipeRepository fileRecipeRepository,
            RecipeNormalizer recipeNormalizer,
            InMemoryRecipeEmbeddingIndex embeddingIndex) {
        this.fileRecipeRepository = fileRecipeRepository;
        this.recipeNormalizer = recipeNormalizer;
        this.embeddingIndex = embeddingIndex;
    }

    public RecipeSearchHit searchBest(String rawQuery) {
        String normalizedQuery = recipeNormalizer.normalizeUserQuery(rawQuery);
        if (normalizedQuery.isBlank()) {
            return null;
        }

        return fileRecipeRepository.findAll().stream()
                .map(recipe -> scoreRecipe(recipe, normalizedQuery))
                .sorted(Comparator.comparingDouble(RecipeSearchHit::totalScore).reversed())
                .findFirst()
                .orElse(null);
    }

    private RecipeSearchHit scoreRecipe(RecipeIndexedDocument recipe, String normalizedQuery) {
        double nameScore = computeNameScore(recipe, normalizedQuery);
        double keywordScore = computeKeywordScore(recipe, normalizedQuery);
        double vectorScore = embeddingIndex.enabled() ? embeddingIndex.similarity(normalizedQuery, recipe.document().id()) : 0.0D;
        double totalScore = (nameScore * 0.55D) + (keywordScore * 0.30D) + (vectorScore * 0.15D);
        double confidence = Math.max(nameScore, keywordScore * 0.9D + vectorScore * 0.1D);
        return new RecipeSearchHit(
                recipe,
                totalScore,
                nameScore,
                keywordScore,
                vectorScore,
                confidence,
                selectContext(recipe, normalizedQuery)
        );
    }

    private double computeNameScore(RecipeIndexedDocument recipe, String normalizedQuery) {
        String normalizedName = recipe.normalizedName();
        if (normalizedName.equals(normalizedQuery)) {
            return 1.0D;
        }
        if (normalizedName.contains(normalizedQuery)) {
            return containmentScore(normalizedQuery, normalizedName, 0.82D);
        }
        if (normalizedQuery.contains(normalizedName)) {
            return containmentScore(normalizedName, normalizedQuery, 0.52D);
        }
        return overlapScore(normalizedName, normalizedQuery);
    }

    private double computeKeywordScore(RecipeIndexedDocument recipe, String normalizedQuery) {
        double maxScore = 0.0D;
        for (String term : recipe.searchableTerms()) {
            if (term == null || term.isBlank()) {
                continue;
            }
            String normalizedTerm = recipeNormalizer.normalize(term);
            if (normalizedTerm.equals(normalizedQuery)) {
                maxScore = Math.max(maxScore, 1.0D);
            } else if (normalizedTerm.contains(normalizedQuery)) {
                maxScore = Math.max(maxScore, containmentScore(normalizedQuery, normalizedTerm, 0.72D));
            } else if (normalizedQuery.contains(normalizedTerm)) {
                maxScore = Math.max(maxScore, containmentScore(normalizedTerm, normalizedQuery, 0.42D));
            } else {
                maxScore = Math.max(maxScore, overlapScore(normalizedTerm, normalizedQuery));
            }
        }
        if (recipe.normalizedSearchText().contains(normalizedQuery)) {
            maxScore = Math.max(maxScore, 0.72D);
        }
        return maxScore;
    }

    private List<RecipeChunk> selectContext(RecipeIndexedDocument recipe, String normalizedQuery) {
        return recipe.chunks().stream()
                .sorted(Comparator.comparingDouble((RecipeChunk chunk) -> chunkScore(chunk, normalizedQuery)).reversed())
                .limit(3)
                .toList();
    }

    private double chunkScore(RecipeChunk chunk, String normalizedQuery) {
        String normalized = recipeNormalizer.normalize(chunk.text());
        if (normalized.contains(normalizedQuery)) {
            return 1.0D;
        }
        return overlapScore(normalized, normalizedQuery);
    }

    private double overlapScore(String left, String right) {
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            return 0.0D;
        }

        int common = 0;
        for (int i = 0; i < right.length(); i++) {
            if (left.indexOf(right.charAt(i)) >= 0) {
                common++;
            }
        }
        return (double) common / (double) Math.max(left.length(), right.length());
    }

    private double containmentScore(String shorter, String longer, double maxBase) {
        if (shorter == null || shorter.isBlank() || longer == null || longer.isBlank()) {
            return 0.0D;
        }
        double ratio = (double) shorter.length() / (double) longer.length();
        double score = maxBase * ratio;
        if (longer.startsWith(shorter) || longer.endsWith(shorter)) {
            score += 0.05D;
        }
        return Math.min(score, maxBase);
    }
}
