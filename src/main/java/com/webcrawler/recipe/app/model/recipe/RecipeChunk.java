package com.webcrawler.recipe.app.model.recipe;

import java.util.List;

public class RecipeChunk {

    private String text;
    private String dishId;
    private String dishName;
    private String view;
    private String category;
    private Integer difficulty;
    private List<String> tags;
    private String stepRange;

    public RecipeChunk() {
    }

    public RecipeChunk(String id, String dishId, String type, int order, String text) {
        this.dishId = dishId;
        this.view = type;
        this.text = text;
    }

    public String text() {
        return text;
    }

    public String type() {
        return view;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
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

    public String getStepRange() {
        return stepRange;
    }

    public void setStepRange(String stepRange) {
        this.stepRange = stepRange;
    }
}
