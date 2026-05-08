package com.webcrawler.recipe.app.model.recipe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 用来读取caipu.txt的对象
 * @param id
 * @param name
 * @param description
 * @param sourcePath
 * @param imagePath
 * @param images
 * @param category
 * @param difficulty
 * @param tags
 * @param servings
 * @param ingredients
 * @param steps
 * @param prepTimeMinutes
 * @param cookTimeMinutes
 * @param totalTimeMinutes
 * @param additionalNotes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecipeDocument(
        String id,
        String name,
        String description,
        @JsonProperty("source_path") String sourcePath,
        @JsonProperty("image_path") String imagePath,
        List<String> images,
        String category,
        Integer difficulty,
        List<String> tags,
        Integer servings,
        List<RecipeIngredient> ingredients,
        List<RecipeStep> steps,
        @JsonProperty("prep_time_minutes") Integer prepTimeMinutes,
        @JsonProperty("cook_time_minutes") Integer cookTimeMinutes,
        @JsonProperty("total_time_minutes") Integer totalTimeMinutes,
        @JsonProperty("additional_notes") List<String> additionalNotes
) {
}
