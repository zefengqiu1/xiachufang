package com.webcrawler.recipe.app.util;

import com.webcrawler.recipe.app.model.recipe.RecipeChunk;
import com.webcrawler.recipe.app.model.search.RecipeSearchHit;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RecipePromptBuilder {

    public String build(String rawQuery, String normalizedQuery, RecipeSearchHit hit) {
        String context = hit.contextChunks().stream()
                .map(RecipeChunk::text)
                .collect(Collectors.joining("\n\n"));

        return """
                你是一个专业的中文菜谱助手。请严格依据给定菜谱上下文回答。
                要求：
                1. 优先使用上下文，不要臆造上下文中不存在的原料克数。
                2. 如果上下文中没有精确分量，要明确说明“原始菜谱未给出精确分量”。
                3. 输出结构固定为：菜名、简介、食材、步骤、技巧、来源。
                4. 回复必须使用中文，简洁、实用。

                用户问题：
                %s

                归一化菜名：
                %s

                命中的菜谱：
                %s

                菜谱上下文：
                %s
                """.formatted(rawQuery, normalizedQuery, hit.recipe().canonicalName(), context);
    }
}
