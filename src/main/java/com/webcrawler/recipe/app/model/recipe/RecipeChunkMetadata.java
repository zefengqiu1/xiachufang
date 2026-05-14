package com.webcrawler.recipe.app.model.recipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecipeChunkMetadata {

    private String chunkId;
    private String parentChunkId;
    private String dishId;
    private String dishName;
    private String view;
    private Integer chunkOrder;
    private String category;
    private Integer difficulty;
    private List<String> tags;
    private Integer servings;
    private Integer prepTimeMinutes;
    private Integer cookTimeMinutes;
    private Integer totalTimeMinutes;
    private Integer stepStart;
    private Integer stepEnd;
    private String stepRange;

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getParentChunkId() {
        return parentChunkId;
    }

    public void setParentChunkId(String parentChunkId) {
        this.parentChunkId = parentChunkId;
    }

    public String getDishId() {
        return dishId;
    }

    public void setDishId(String dishId) {
        this.dishId = dishId;
    }

    public String getDishName() {
        return dishName;
    }

    public void setDishName(String dishName) {
        this.dishName = dishName;
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    public Integer getChunkOrder() {
        return chunkOrder;
    }

    public void setChunkOrder(Integer chunkOrder) {
        this.chunkOrder = chunkOrder;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Integer getServings() {
        return servings;
    }

    public void setServings(Integer servings) {
        this.servings = servings;
    }

    public Integer getPrepTimeMinutes() {
        return prepTimeMinutes;
    }

    public void setPrepTimeMinutes(Integer prepTimeMinutes) {
        this.prepTimeMinutes = prepTimeMinutes;
    }

    public Integer getCookTimeMinutes() {
        return cookTimeMinutes;
    }

    public void setCookTimeMinutes(Integer cookTimeMinutes) {
        this.cookTimeMinutes = cookTimeMinutes;
    }

    public Integer getTotalTimeMinutes() {
        return totalTimeMinutes;
    }

    public void setTotalTimeMinutes(Integer totalTimeMinutes) {
        this.totalTimeMinutes = totalTimeMinutes;
    }

    public Integer getStepStart() {
        return stepStart;
    }

    public void setStepStart(Integer stepStart) {
        this.stepStart = stepStart;
    }

    public Integer getStepEnd() {
        return stepEnd;
    }

    public void setStepEnd(Integer stepEnd) {
        this.stepEnd = stepEnd;
    }

    public String getStepRange() {
        return stepRange;
    }

    public void setStepRange(String stepRange) {
        this.stepRange = stepRange;
    }

    public Map<String, String> asMetadataMap() {
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "chunkId", chunkId);
        put(values, "parentChunkId", parentChunkId);
        put(values, "dishId", dishId);
        put(values, "dishName", dishName);
        put(values, "view", view);
        put(values, "chunkOrder", chunkOrder);
        put(values, "category", category);
        put(values, "difficulty", difficulty);
        put(values, "tags", tags == null || tags.isEmpty() ? "" : String.join("|", tags));
        put(values, "servings", servings);
        put(values, "prepTimeMinutes", prepTimeMinutes);
        put(values, "cookTimeMinutes", cookTimeMinutes);
        put(values, "totalTimeMinutes", totalTimeMinutes);
        put(values, "stepStart", stepStart);
        put(values, "stepEnd", stepEnd);
        put(values, "stepRange", stepRange);
        return values;
    }

    private static void put(Map<String, String> values, String key, Object value) {
        values.put(key, value == null ? "" : String.valueOf(value));
    }
}
