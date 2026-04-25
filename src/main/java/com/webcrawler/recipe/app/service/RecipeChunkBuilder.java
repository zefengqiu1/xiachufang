package com.webcrawler.recipe.app.service;

import com.webcrawler.recipe.app.model.recipe.RecipeChunk;
import com.webcrawler.recipe.app.model.recipe.RecipeDocument;
import com.webcrawler.recipe.app.model.recipe.RecipeIngredient;
import com.webcrawler.recipe.app.model.recipe.RecipeStep;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecipeChunkBuilder {

    public List<RecipeChunk> build(RecipeDocument recipe, String canonicalName) {
        List<RecipeChunk> chunks = new ArrayList<>();
        int order = 0;

        chunks.add(new RecipeChunk(
                recipe.id() + "-summary",
                recipe.id(),
                "summary",
                order++,
                canonicalName + "\n" + plain(recipe.description())
        ));

        if (recipe.ingredients() != null && !recipe.ingredients().isEmpty()) {
            String ingredientsText = recipe.ingredients().stream()
                    .map(this::ingredientLine)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            chunks.add(new RecipeChunk(recipe.id() + "-ingredients", recipe.id(), "ingredients", order++, ingredientsText));
        }

        if (recipe.steps() != null && !recipe.steps().isEmpty()) {
            String stepsText = recipe.steps().stream()
                    .map(this::stepLine)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            chunks.add(new RecipeChunk(recipe.id() + "-steps", recipe.id(), "steps", order++, stepsText));
        }

        if (recipe.additionalNotes() != null && !recipe.additionalNotes().isEmpty()) {
            String notesText = String.join("\n", recipe.additionalNotes());
            chunks.add(new RecipeChunk(recipe.id() + "-notes", recipe.id(), "notes", order, notesText));
        }

        return List.copyOf(chunks);
    }

    private String ingredientLine(RecipeIngredient ingredient) {
        if (ingredient.textQuantity() != null && !ingredient.textQuantity().isBlank()) {
            return ingredient.textQuantity();
        }
        return ingredient.name() == null ? "" : ingredient.name();
    }

    private String stepLine(RecipeStep step) {
        int index = step.step() == null ? 0 : step.step();
        return index + ". " + plain(step.description());
    }

    private String plain(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("(?m)^#+\\s*", "")
                .replaceAll("\\[(.+?)]\\((.+?)\\)", "$1")
                .replace("\r", "")
                .trim();
    }
}
