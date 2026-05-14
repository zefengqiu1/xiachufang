package com.webcrawler.recipe.app.model.recipe;

import java.util.List;

public class RecipeChunk {

    private String text;
    private RecipeChunkMetadata metadata;

    public RecipeChunk() {
        this.metadata = new RecipeChunkMetadata();
    }

    public RecipeChunk(String id, String dishId, String type, int order, String text) {
        this();
        this.metadata.setChunkId(id);
        this.metadata.setDishId(dishId);
        this.metadata.setView(type);
        this.metadata.setChunkOrder(order);
        this.text = text;
    }

    public String text() {
        return text;
    }

    public String type() {
        return getView();
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDishId() {
        return metadata == null ? null : metadata.getDishId();
    }

    public void setDishId(String dishId) {
        ensureMetadata().setDishId(dishId);
    }

    public String getDishName() {
        return metadata == null ? null : metadata.getDishName();
    }

    public void setDishName(String dishName) {
        ensureMetadata().setDishName(dishName);
    }

    public String getView() {
        return metadata == null ? null : metadata.getView();
    }

    public void setView(String view) {
        ensureMetadata().setView(view);
    }

    public String getCategory() {
        return metadata == null ? null : metadata.getCategory();
    }

    public void setCategory(String category) {
        ensureMetadata().setCategory(category);
    }

    public Integer getDifficulty() {
        return metadata == null ? null : metadata.getDifficulty();
    }

    public void setDifficulty(Integer difficulty) {
        ensureMetadata().setDifficulty(difficulty);
    }

    public List<String> getTags() {
        return metadata == null ? null : metadata.getTags();
    }

    public void setTags(List<String> tags) {
        ensureMetadata().setTags(tags);
    }

    public String getStepRange() {
        return metadata == null ? null : metadata.getStepRange();
    }

    public void setStepRange(String stepRange) {
        ensureMetadata().setStepRange(stepRange);
    }

    public RecipeChunkMetadata getMetadata() {
        return ensureMetadata();
    }

    public void setMetadata(RecipeChunkMetadata metadata) {
        this.metadata = metadata == null ? new RecipeChunkMetadata() : metadata;
    }

    private RecipeChunkMetadata ensureMetadata() {
        if (metadata == null) {
            metadata = new RecipeChunkMetadata();
        }
        return metadata;
    }
}
