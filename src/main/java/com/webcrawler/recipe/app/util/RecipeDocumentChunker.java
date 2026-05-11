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


    // =========================
    // ENTRY POINT
    // =========================
    public static List<RecipeChunk> chunk(RecipeDocument d) {

        List<RecipeChunk> chunks = new ArrayList<>();

        chunks.add(buildOverview(d));
        chunks.add(buildIngredients(d));
        chunks.addAll(buildStepsAtomic(d));
        chunks.add(buildTechnique(d));
        chunks.add(buildFlavor(d));

        return chunks;
    }

    // =========================================================
    // 1. OVERVIEW CHUNK
    // =========================================================
    private static RecipeChunk buildOverview(RecipeDocument d) {

        RecipeChunk c = new RecipeChunk();

        c.setDishId(d.id());
        c.setDishName(d.name());
        c.setView("overview");
        c.setCategory(d.category());
        c.setDifficulty(d.difficulty());
        c.setTags(d.tags());

        c.setText(
                "菜名：" + d.name() + "\n" +
                        "分类：" + d.category() + "\n" +
                        "难度：" + d.difficulty() + "\n\n" +
                        "简介：\n" + d.description()
        );

        return c;
    }

    // =========================================================
    // 2. INGREDIENTS (clean + deduplicate)
    // =========================================================
    private static RecipeChunk buildIngredients(RecipeDocument d) {

        RecipeChunk c = new RecipeChunk();

        c.setDishId(d.id());
        c.setDishName(d.name());
        c.setView("ingredients");
        c.setCategory(d.category());
        c.setDifficulty(d.difficulty());
        c.setTags(d.tags());

        List<String> ingredients = normalizeIngredients(d.ingredients());

        c.setText("食材清单：\n- " + String.join("\n- ", ingredients));

        return c;
    }

    private static List<String> normalizeIngredients(List<RecipeIngredient> ingredients) {

        if (ingredients == null) return List.of();

        Set<String> result = new LinkedHashSet<>();

        for (RecipeIngredient i : ingredients) {

            if (i == null || i.name() == null) continue;

            String name = i.name();

            // 去括号说明
            name = name.replaceAll("（.*?）", "");

            // 去尾部解释
            name = name.replaceAll("（.*", "");

            // 多食材拆分
            String[] parts = name.split("、|，|,| ");

            for (String p : parts) {
                p = p.trim();
                if (!p.isEmpty()) {
                    result.add(p);
                }
            }
        }

        return new ArrayList<>(result);
    }

    // =========================================================
    // 3. STEPS (atomic + chunked)
    // =========================================================
    private static List<RecipeChunk> buildStepsAtomic(RecipeDocument d) {

        List<String> atomicSteps = extractAtomicSteps(d.steps());

        List<RecipeChunk> chunks = new ArrayList<>();

        int batchSize = 2;

        for (int i = 0; i < atomicSteps.size(); i += batchSize) {

            int end = Math.min(i + batchSize, atomicSteps.size());

            List<String> sub = atomicSteps.subList(i, end);

            RecipeChunk c = new RecipeChunk();

            c.setDishId(d.id());
            c.setDishName(d.name());
            c.setView("steps_atomic");
            c.setCategory(d.category());
            c.setDifficulty(d.difficulty());
            c.setTags(d.tags());

            c.setStepRange((i + 1) + "-" + end);

            c.setText(String.join("\n", sub));

            chunks.add(c);
        }

        return chunks;
    }

    private static List<String> extractAtomicSteps(List<RecipeStep> steps) {

        if (steps == null) return List.of();

        List<String> result = new ArrayList<>();

        Pattern mdLink = Pattern.compile("\\[.*?\\]\\(.*?\\)");

        for (RecipeStep s : steps) {

            if (s == null || s.description() == null) continue;

            String text = s.description();

            // 清理 markdown link
            text = mdLink.matcher(text).replaceAll("");

            // 标准化分割
            text = text.replace("然后", "，")
                    .replace("再", "，")
                    .replace("并", "，")
                    .replace("；", "，")
                    .replace(";", "，");

            String[] parts = text.split("，|,|\\.");

            for (String p : parts) {
                p = p.trim();
                if (!p.isEmpty()) {
                    result.add(p);
                }
            }
        }

        return result;
    }

    // =========================================================
    // 4. TECHNIQUE CHUNK
    // =========================================================
    private static RecipeChunk buildTechnique(RecipeDocument d) {

        RecipeChunk c = new RecipeChunk();

        c.setDishId(d.id());
        c.setDishName(d.name());
        c.setView("technique");
        c.setCategory(d.category());
        c.setDifficulty(d.difficulty());
        c.setTags(d.tags());

        Set<String> t = new LinkedHashSet<>();

        if (d.steps() != null) {
            for (RecipeStep s : d.steps()) {

                if (s.description() == null) continue;

                String x = s.description();

                if (x.contains("煎")) t.add("煎制技巧");
                if (x.contains("炸")) t.add("油炸技巧");
                if (x.contains("焖")) t.add("焖煮技巧");
                if (x.contains("收汁")) t.add("收汁技巧");
                if (x.contains("翻炒")) t.add("火候控制");
                if (x.contains("蒸")) t.add("蒸制技巧");
                if (x.contains("腌")) t.add("腌制技巧");
            }
        }

        c.setText(
                t.isEmpty()
                        ? "烹饪技巧：暂无显式技巧"
                        : "烹饪技巧：\n- " + String.join("\n- ", t)
        );

        return c;
    }

    // =========================================================
    // 5. FLAVOR CHUNK
    // =========================================================
    private static RecipeChunk buildFlavor(RecipeDocument d) {

        RecipeChunk c = new RecipeChunk();

        c.setDishId(d.id());
        c.setDishName(d.name());
        c.setView("flavor");
        c.setCategory(d.category());
        c.setDifficulty(d.difficulty());
        c.setTags(d.tags());

        c.setText(
                "风味分析：\n" +
                        "该菜属于 " + d.category() + " 类型。\n" +
                        "整体风格：鲜香浓郁 / 调味层次丰富 / 家常与餐厅结合。\n" +
                        "适合人群：喜欢重口味、海鲜或家常菜用户。"
        );

        return c;
    }
}
