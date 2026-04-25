package com.webcrawler.recipe.app.service;

import com.webcrawler.recipe.app.model.recipe.RecipeIndexedDocument;
import com.webcrawler.recipe.app.repository.FileRecipeRepository;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InMemoryRecipeEmbeddingIndex {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRecipeEmbeddingIndex.class);

    private final Optional<EmbeddingModel> embeddingModel;
    private final FileRecipeRepository fileRecipeRepository;
    private final Map<String, float[]> embeddings = new HashMap<>();
    private volatile boolean available = true;

    public InMemoryRecipeEmbeddingIndex(
            Optional<EmbeddingModel> embeddingModel,
            FileRecipeRepository fileRecipeRepository) {
        this.embeddingModel = embeddingModel;
        this.fileRecipeRepository = fileRecipeRepository;
    }

    @PostConstruct
    void initialize() {
        if (embeddingModel.isEmpty()) {
            return;
        }

        try {
            EmbeddingModel model = embeddingModel.get();
            for (RecipeIndexedDocument recipe : fileRecipeRepository.findAll()) {
                String text = recipe.normalizedSearchText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                float[] vector = model.embed(text).content().vector();
                embeddings.put(recipe.document().id(), vector);
            }
        } catch (Exception e) {
            available = false;
            embeddings.clear();
            log.warn("Embedding index disabled because initialization failed: {}", e.getMessage());
        }
    }

    public boolean enabled() {
        return available && embeddingModel.isPresent() && !embeddings.isEmpty();
    }

    public double similarity(String query, String recipeId) {
        if (!enabled()) {
            return 0.0D;
        }
        float[] recipeVector = embeddings.get(recipeId);
        if (recipeVector == null) {
            return 0.0D;
        }
        try {
            float[] queryVector = embeddingModel.get().embed(query).content().vector();
            return cosine(queryVector, recipeVector);
        } catch (Exception e) {
            available = false;
            embeddings.clear();
            log.warn("Embedding similarity disabled because query embedding failed: {}", e.getMessage());
            return 0.0D;
        }
    }

    private double cosine(float[] left, float[] right) {
        if (left.length != right.length) {
            return 0.0D;
        }
        double dot = 0.0D;
        double leftNorm = 0.0D;
        double rightNorm = 0.0D;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0D || rightNorm == 0.0D) {
            return 0.0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
