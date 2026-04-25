package com.webcrawler.recipe.app.model.recipe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecipeStep(
        Integer step,
        String description
) {
}
