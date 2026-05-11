package com.webcrawler.recipe.app.util;

import com.webcrawler.recipe.app.model.recipe.RecipeChunk;
import com.webcrawler.recipe.app.model.recipe.RecipeDocument;
import com.webcrawler.recipe.app.model.recipe.RecipeIngredient;
import com.webcrawler.recipe.app.model.recipe.RecipeStep;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class RecipeDocumentChunker {

    private RecipeDocumentChunker() {
    }

    public static List<RecipeChunk> chunk(RecipeDocument recipe) {
        List<RecipeChunk> chunks = new ArrayList<>();
        chunks.add(buildOverview(recipe));
        chunks.add(buildIngredients(recipe));
        chunks.addAll(buildStepsAtomic(recipe));
        chunks.add(buildTechnique(recipe));
        return chunks;
    }

    private static RecipeChunk buildOverview(RecipeDocument recipe) {
        RecipeChunk chunk = baseChunk(recipe, "overview");
        chunk.setText(
                "菜名：" + recipe.name() + "\n" +
                        "内容类型：概览\n" +
                        "分类：" + recipe.category() + "\n" +
                        "难度：" + recipe.difficulty() + "\n\n" +
                        "简介：\n" + recipe.description()
        );
        return chunk;
    }

    private static RecipeChunk buildIngredients(RecipeDocument recipe) {
        RecipeChunk chunk = baseChunk(recipe, "ingredients");
        List<String> ingredients = normalizeIngredients(recipe.ingredients());
        chunk.setText(
                "菜名：" + recipe.name() + "\n" +
                        "内容类型：食材\n" +
                        "分类：" + recipe.category() + "\n\n" +
                        "食材清单：\n- " + String.join("\n- ", ingredients)
        );
        return chunk;
    }

    private static List<RecipeChunk> buildStepsAtomic(RecipeDocument recipe) {
        List<String> atomicSteps = extractAtomicSteps(recipe.steps());
        List<RecipeChunk> chunks = new ArrayList<>();
        int batchSize = 2;

        for (int i = 0; i < atomicSteps.size(); i += batchSize) {
            int end = Math.min(i + batchSize, atomicSteps.size());
            List<String> sub = atomicSteps.subList(i, end);

            RecipeChunk chunk = baseChunk(recipe, "steps_atomic");
            chunk.setStepRange((i + 1) + "-" + end);
            chunk.setText(
                    "菜名：" + recipe.name() + "\n" +
                            "内容类型：步骤\n" +
                            "步骤范围：" + (i + 1) + "-" + end + "\n\n" +
                            String.join("\n", sub)
            );
            chunks.add(chunk);
        }

        return chunks;
    }

    private static RecipeChunk buildTechnique(RecipeDocument recipe) {
        RecipeChunk chunk = baseChunk(recipe, "technique");
        Set<String> techniques = new LinkedHashSet<>();

        if (recipe.steps() != null) {
            for (RecipeStep step : recipe.steps()) {
                if (step.description() == null) {
                    continue;
                }

                String text = step.description();
                if (text.contains("煎")) techniques.add("煎制技巧");
                if (text.contains("炸")) techniques.add("油炸技巧");
                if (text.contains("焖")) techniques.add("焖煮技巧");
                if (text.contains("收汁")) techniques.add("收汁技巧");
                if (text.contains("翻炒")) techniques.add("火候控制");
                if (text.contains("蒸")) techniques.add("蒸制技巧");
                if (text.contains("腌")) techniques.add("腌制技巧");
            }
        }

        chunk.setText(
                "菜名：" + recipe.name() + "\n" +
                        "内容类型：技巧\n" +
                        "分类：" + recipe.category() + "\n" +
                        "难度：" + recipe.difficulty() + "\n\n" +
                        (
                                techniques.isEmpty()
                                        ? "烹饪技巧：暂无显式技巧"
                                        : "烹饪技巧：\n- " + String.join("\n- ", techniques)
                        )
        );
        return chunk;
    }

    private static RecipeChunk baseChunk(RecipeDocument recipe, String view) {
        RecipeChunk chunk = new RecipeChunk();
        chunk.setDishId(recipe.id());
        chunk.setDishName(recipe.name());
        chunk.setView(view);
        chunk.setCategory(recipe.category());
        chunk.setDifficulty(recipe.difficulty());
        chunk.setTags(recipe.tags());
        return chunk;
    }

    private static List<String> normalizeIngredients(List<RecipeIngredient> ingredients) {
        if (ingredients == null) {
            return List.of();
        }

        Set<String> result = new LinkedHashSet<>();
        for (RecipeIngredient ingredient : ingredients) {
            if (ingredient == null || ingredient.name() == null) {
                continue;
            }

            String name = ingredient.name()
                    .replaceAll("（.*?）", "")
                    .replaceAll("（.*", "");

            String[] parts = name.split("、|，|,| ");
            for (String part : parts) {
                String token = part.trim();
                if (!token.isEmpty()) {
                    result.add(token);
                }
            }
        }

        return new ArrayList<>(result);
    }

    private static List<String> extractAtomicSteps(List<RecipeStep> steps) {
        if (steps == null) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        Pattern mdLink = Pattern.compile("\\[.*?\\]\\(.*?\\)");

        for (RecipeStep step : steps) {
            if (step == null || step.description() == null) {
                continue;
            }

            String text = mdLink.matcher(step.description()).replaceAll("");
            text = text.replace("然后", "，")
                    .replace("再", "，")
                    .replace("并", "，")
                    .replace("；", "，")
                    .replace(";", "，");

            String[] parts = text.split("，|,|\\.");
            for (String part : parts) {
                String token = part.trim();
                if (!token.isEmpty()) {
                    result.add(token);
                }
            }
        }

        return result;
    }
}
