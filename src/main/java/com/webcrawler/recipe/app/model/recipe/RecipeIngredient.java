package com.webcrawler.recipe.app.model.recipe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecipeIngredient(
        String name,
        Double quantity,
        String unit,
        @JsonProperty("text_quantity") String textQuantity,
        String notes
) {
}
