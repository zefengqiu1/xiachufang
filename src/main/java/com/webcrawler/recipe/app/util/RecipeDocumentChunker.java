package com.webcrawler.recipe.app.util;

import com.webcrawler.recipe.app.model.recipe.RecipeChunk;
import com.webcrawler.recipe.app.model.recipe.RecipeChunkMetadata;
import com.webcrawler.recipe.app.model.recipe.RecipeDocument;
import com.webcrawler.recipe.app.model.recipe.RecipeIngredient;
import com.webcrawler.recipe.app.model.recipe.RecipeStep;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class RecipeDocumentChunker {

    private static final int STEP_WINDOW_SIZE = 3;
    private static final int STEP_WINDOW_OVERLAP = 1;

    private RecipeDocumentChunker() {
    }

    public static List<RecipeChunk> chunk(RecipeDocument recipe) {
        List<RecipeChunk> chunks = new ArrayList<>();
        RecipeChunk parent = buildRecipeParentLight(recipe);
        chunks.add(buildSummary(recipe));
        chunks.add(buildIngredientsFull(recipe));
        chunks.add(buildIngredientsTerms(recipe));
        chunks.addAll(buildStepsWindow(recipe, parent.getMetadata().getChunkId()));
        chunks.add(buildNotesTechnique(recipe, parent.getMetadata().getChunkId()));
        chunks.add(parent);
        finalizeMetadata(chunks);
        return chunks;
    }

    private static RecipeChunk buildSummary(RecipeDocument recipe) {
        RecipeChunk chunk = baseChunk(recipe, "summary");
        chunk.setText(
                "菜名：" + recipe.name() + "\n" +
                        "内容类型：summary\n" +
                        "分类：" + recipe.category() + "\n" +
                        "难度：" + recipe.difficulty() + "\n\n" +
                        "份量：" + nullSafeNumber(recipe.servings(), "未提供") + "\n" +
                        "准备时间：" + minutesText(recipe.prepTimeMinutes()) + "\n" +
                        "烹饪时间：" + minutesText(recipe.cookTimeMinutes()) + "\n" +
                        "总时长：" + minutesText(recipe.totalTimeMinutes()) + "\n" +
                        "标签：" + joinTags(recipe.tags()) + "\n\n" +
                        "简介：\n" + recipe.description()
        );
        return chunk;
    }

    private static RecipeChunk buildIngredientsFull(RecipeDocument recipe) {
        RecipeChunk chunk = baseChunk(recipe, "ingredients_full");
        List<String> ingredients = ingredientLines(recipe.ingredients());
        chunk.setText(
                "菜名：" + recipe.name() + "\n" +
                        "内容类型：ingredients_full\n" +
                        "分类：" + recipe.category() + "\n\n" +
                        "食材清单：\n- " + String.join("\n- ", ingredients)
        );
        return chunk;
    }

    private static RecipeChunk buildIngredientsTerms(RecipeDocument recipe) {
        RecipeChunk chunk = baseChunk(recipe, "ingredients_terms");
        List<String> terms = normalizeIngredientTerms(recipe.ingredients());
        chunk.setText(
                "菜名：" + recipe.name() + "\n" +
                        "内容类型：ingredients_terms\n\n" +
                        "食材词项：\n" + String.join("\n", terms)
        );
        return chunk;
    }

    private static List<RecipeChunk> buildStepsWindow(RecipeDocument recipe, String parentChunkId) {
        List<RecipeStep> steps = normalizeSteps(recipe.steps());
        List<RecipeChunk> chunks = new ArrayList<>();
        if (steps.isEmpty()) {
            return chunks;
        }

        int stride = Math.max(1, STEP_WINDOW_SIZE - STEP_WINDOW_OVERLAP);
        for (int start = 0; start < steps.size(); start += stride) {
            int endExclusive = Math.min(start + STEP_WINDOW_SIZE, steps.size());
            List<RecipeStep> window = steps.subList(start, endExclusive);
            RecipeChunk chunk = baseChunk(recipe, "steps_window");
            chunk.getMetadata().setParentChunkId(parentChunkId);
            int stepStart = safeStepNumber(window.get(0), start + 1);
            int stepEnd = safeStepNumber(window.get(window.size() - 1), endExclusive);
            chunk.getMetadata().setStepStart(stepStart);
            chunk.getMetadata().setStepEnd(stepEnd);
            chunk.setStepRange(stepStart + "-" + stepEnd);
            chunk.setText(
                    "菜名：" + recipe.name() + "\n" +
                            "内容类型：steps_window\n" +
                            "步骤范围：" + stepStart + "-" + stepEnd + "\n\n" +
                            formatSteps(window)
            );
            chunks.add(chunk);
            if (endExclusive >= steps.size()) {
                break;
            }
        }

        return chunks;
    }

    private static RecipeChunk buildNotesTechnique(RecipeDocument recipe, String parentChunkId) {
        RecipeChunk chunk = baseChunk(recipe, "notes_technique");
        chunk.getMetadata().setParentChunkId(parentChunkId);
        List<String> notes = collectNotes(recipe);

        chunk.setText(
                "菜名：" + recipe.name() + "\n" +
                        "内容类型：notes_technique\n" +
                        "分类：" + recipe.category() + "\n" +
                        "难度：" + recipe.difficulty() + "\n\n" +
                        (
                                notes.isEmpty()
                                        ? "注意事项：暂无单独备注"
                                        : "注意事项：\n- " + String.join("\n- ", notes)
                        )
        );
        return chunk;
    }

    private static RecipeChunk buildRecipeParentLight(RecipeDocument recipe) {
        RecipeChunk chunk = baseChunk(recipe, "recipe_parent_light");
        chunk.setText(
                "菜名：" + recipe.name() + "\n" +
                        "内容类型：recipe_parent_light\n" +
                        "分类：" + recipe.category() + "\n" +
                        "难度：" + recipe.difficulty() + "\n\n" +
                        "简介：\n" + safe(recipe.description()) + "\n\n" +
                        "食材摘要：\n- " + String.join("\n- ", ingredientLines(recipe.ingredients())) + "\n\n" +
                        "步骤摘要：\n" + summarizeSteps(recipe.steps()) + "\n\n" +
                        "注意事项：\n- " + String.join("\n- ", defaultIfEmpty(collectNotes(recipe), "暂无单独备注"))
        );
        return chunk;
    }

    private static RecipeChunk baseChunk(RecipeDocument recipe, String view) {
        RecipeChunk chunk = new RecipeChunk();
        RecipeChunkMetadata metadata = chunk.getMetadata();
        metadata.setChunkId(recipe.id() + ":" + view);
        metadata.setDishId(recipe.id());
        metadata.setDishName(recipe.name());
        metadata.setView(view);
        metadata.setCategory(recipe.category());
        metadata.setDifficulty(recipe.difficulty());
        metadata.setTags(recipe.tags());
        metadata.setServings(recipe.servings());
        metadata.setPrepTimeMinutes(recipe.prepTimeMinutes());
        metadata.setCookTimeMinutes(recipe.cookTimeMinutes());
        metadata.setTotalTimeMinutes(recipe.totalTimeMinutes());
        return chunk;
    }

    private static void finalizeMetadata(List<RecipeChunk> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            RecipeChunk chunk = chunks.get(i);
            RecipeChunkMetadata metadata = chunk.getMetadata();
            metadata.setChunkOrder(i + 1);
            metadata.setChunkId(buildChunkId(chunk, i + 1));
        }
    }

    private static String buildChunkId(RecipeChunk chunk, int order) {
        StringBuilder builder = new StringBuilder();
        builder.append(safe(chunk.getDishId()))
                .append(":")
                .append(safe(chunk.getView()));
        if (chunk.getStepRange() != null && !chunk.getStepRange().isBlank()) {
            builder.append(":").append(chunk.getStepRange());
        }
        if (builder.length() == 1 || builder.charAt(builder.length() - 1) == ':') {
            builder.append(order);
        }
        return builder.toString();
    }

    private static List<String> ingredientLines(List<RecipeIngredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of("原始菜谱未提供食材明细");
        }
        List<String> result = new ArrayList<>();
        for (RecipeIngredient ingredient : ingredients) {
            if (ingredient == null || ingredient.name() == null || ingredient.name().isBlank()) {
                continue;
            }
            result.add(formatIngredient(ingredient));
        }
        return result.isEmpty() ? List.of("原始菜谱未提供食材明细") : result;
    }

    private static List<String> normalizeIngredientTerms(List<RecipeIngredient> ingredients) {
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

    private static List<RecipeStep> normalizeSteps(List<RecipeStep> steps) {
        if (steps == null) {
            return List.of();
        }
        List<RecipeStep> result = new ArrayList<>();
        Pattern mdLink = Pattern.compile("\\[.*?\\]\\(.*?\\)");
        int fallbackStep = 1;
        for (RecipeStep step : steps) {
            if (step == null || step.description() == null) {
                continue;
            }
            String text = mdLink.matcher(step.description()).replaceAll("");
            text = text.replace("\r", "").trim();
            if (!text.isEmpty()) {
                result.add(new RecipeStep(step.step() == null ? fallbackStep : step.step(), text));
                fallbackStep++;
            }
        }
        return result;
    }

    private static String formatSteps(List<RecipeStep> steps) {
        List<String> lines = new ArrayList<>();
        for (RecipeStep step : steps) {
            lines.add("步骤" + safeStepNumber(step, 0) + "：" + safe(step.description()));
        }
        return String.join("\n", lines);
    }

    private static String summarizeSteps(List<RecipeStep> steps) {
        List<RecipeStep> normalized = normalizeSteps(steps);
        if (normalized.isEmpty()) {
            return "原始菜谱未提供步骤";
        }
        List<String> lines = new ArrayList<>();
        for (RecipeStep step : normalized) {
            lines.add(step.step() + ". " + safe(step.description()));
        }
        return String.join("\n", lines);
    }

    private static List<String> collectNotes(RecipeDocument recipe) {
        Set<String> notes = new LinkedHashSet<>();
        if (recipe.additionalNotes() != null) {
            for (String note : recipe.additionalNotes()) {
                String normalized = safe(note);
                if (!normalized.isBlank()) {
                    notes.add(normalized);
                }
            }
        }
        if (recipe.steps() != null) {
            for (RecipeStep step : recipe.steps()) {
                String text = step == null ? "" : safe(step.description());
                if (text.isBlank()) {
                    continue;
                }
                if (containsAny(text, "注意", "小火", "中火", "大火", "收汁", "腌", "焯水", "不要", "避免", "火候")) {
                    notes.add(text);
                }
            }
        }
        return new ArrayList<>(notes);
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String formatIngredient(RecipeIngredient ingredient) {
        if (ingredient.textQuantity() != null && !ingredient.textQuantity().isBlank()) {
            return ingredient.textQuantity().replaceFirst("^-\\s*", "").trim();
        }
        StringBuilder builder = new StringBuilder(safe(ingredient.name()));
        if (ingredient.quantity() != null) {
            builder.append(" ").append(ingredient.quantity());
        }
        if (ingredient.unit() != null && !ingredient.unit().isBlank()) {
            builder.append(ingredient.unit().trim());
        }
        if (ingredient.notes() != null && !ingredient.notes().isBlank()) {
            builder.append("（").append(ingredient.notes().trim()).append("）");
        }
        return builder.toString().trim();
    }

    private static String joinTags(List<String> tags) {
        return tags == null || tags.isEmpty() ? "无" : String.join("、", tags);
    }

    private static String minutesText(Integer minutes) {
        return minutes == null ? "未提供" : minutes + "分钟";
    }

    private static String nullSafeNumber(Integer value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static int safeStepNumber(RecipeStep step, int fallback) {
        return step.step() == null ? fallback : step.step();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> defaultIfEmpty(List<String> values, String fallback) {
        return values == null || values.isEmpty() ? List.of(fallback) : values;
    }
}
