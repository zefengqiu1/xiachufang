package com.webcrawler.recipe.app.service;

import com.webcrawler.recipe.app.model.recipe.RecipeChunk;
import com.webcrawler.recipe.app.model.recipe.RecipeDocument;
import com.webcrawler.recipe.app.model.recipe.RecipeIngredient;
import com.webcrawler.recipe.app.model.recipe.RecipeStep;
import com.webcrawler.recipe.app.model.search.RecipeSearchHit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RecipeRenderService {

    public String render(RecipeSearchHit hit, String rawQuery) {
        RecipeDocument recipe = hit.recipe().document();
        List<String> lines = new ArrayList<>();
        lines.add("菜名：" + hit.recipe().canonicalName());
        lines.add("");
        lines.add("简介：" + excerpt(recipe.description()));
        lines.add("");
        lines.add("食材：");
        if (recipe.ingredients() == null || recipe.ingredients().isEmpty()) {
            lines.add("- 原始菜谱未提供食材明细");
        } else {
            for (RecipeIngredient ingredient : recipe.ingredients()) {
                lines.add("- " + ingredientText(ingredient));
            }
        }
        lines.add("");
        lines.add("步骤：");
        if (recipe.steps() == null || recipe.steps().isEmpty()) {
            lines.add("- 原始菜谱未提供步骤");
        } else {
            for (RecipeStep step : recipe.steps()) {
                int index = step.step() == null ? 0 : step.step();
                lines.add(index + ". " + safe(step.description()));
            }
        }
        lines.add("");
        lines.add("技巧：");
        List<RecipeChunk> notes = hit.contextChunks().stream()
                .filter(chunk -> "notes".equals(chunk.type()))
                .toList();
        if (notes.isEmpty()) {
            lines.add("- 这份菜谱没有单独的技巧备注，建议按步骤控制火候与调味。");
        } else {
            for (String note : notes.get(0).text().split("\n")) {
                if (!note.isBlank()) {
                    lines.add("- " + note.trim());
                }
            }
        }
        lines.add("");
        lines.add("来源：" + recipe.sourcePath());
        if (rawQuery != null && !rawQuery.isBlank()) {
            lines.add("");
            lines.add("说明：当前回答基于本地菜谱知识库整理。");
        }
        return String.join("\n", lines);
    }

    private String ingredientText(RecipeIngredient ingredient) {
        if (ingredient.textQuantity() != null && !ingredient.textQuantity().isBlank()) {
            return ingredient.textQuantity().replaceFirst("^-\\s*", "");
        }
        return safe(ingredient.name());
    }

    private String excerpt(String description) {
        String plain = safe(description)
                .replaceAll("(?m)^#+\\s*", "")
                .replace("\r", "")
                .trim();
        return plain.isBlank() ? "原始菜谱未提供简介。" : plain;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
