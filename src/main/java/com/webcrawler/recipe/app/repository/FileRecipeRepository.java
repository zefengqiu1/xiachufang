package com.webcrawler.recipe.app.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.recipe.app.model.recipe.RecipeDocument;
import com.webcrawler.recipe.app.model.recipe.RecipeIndexedDocument;
import com.webcrawler.recipe.app.service.RecipeChunkBuilder;
import com.webcrawler.recipe.app.service.RecipeNormalizer;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class FileRecipeRepository {

    private final ObjectMapper objectMapper;
    private final RecipeNormalizer recipeNormalizer;
    private final RecipeChunkBuilder recipeChunkBuilder;
    private final String dataPath;
    private List<RecipeIndexedDocument> indexedRecipes = List.of();

    public FileRecipeRepository(
            ObjectMapper objectMapper,
            RecipeNormalizer recipeNormalizer,
            RecipeChunkBuilder recipeChunkBuilder,
            @Value("${recipe.data.path:caipu.txt}") String dataPath) {
        this.objectMapper = objectMapper;
        this.recipeNormalizer = recipeNormalizer;
        this.recipeChunkBuilder = recipeChunkBuilder;
        this.dataPath = dataPath;
    }

    @PostConstruct
    void load() throws IOException {
        Path path = Path.of(dataPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IOException("Recipe data file not found: " + path);
        }

        List<RecipeDocument> recipes = objectMapper.readValue(
                Files.readString(path),
                new TypeReference<List<RecipeDocument>>() {
                }
        );

        List<RecipeIndexedDocument> prepared = new ArrayList<>();
        for (RecipeDocument recipe : recipes) {
            String canonicalName = recipeNormalizer.toCanonicalName(recipe.name());
            prepared.add(new RecipeIndexedDocument(
                    recipe,
                    canonicalName,
                    recipeNormalizer.normalize(canonicalName),
                    recipeNormalizer.buildSearchText(recipe, canonicalName),
                    recipeNormalizer.buildSearchTerms(recipe, canonicalName),
                    recipeChunkBuilder.build(recipe, canonicalName),
                    null
            ));
        }

        indexedRecipes = Collections.unmodifiableList(prepared);
    }

    public List<RecipeIndexedDocument> findAll() {
        return indexedRecipes;
    }

    public Optional<RecipeIndexedDocument> findById(String id) {
        return indexedRecipes.stream()
                .filter(recipe -> recipe.document().id().equals(id))
                .findFirst();
    }
}
