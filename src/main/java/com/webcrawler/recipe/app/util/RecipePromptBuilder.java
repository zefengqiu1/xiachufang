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
                [C] Context
                用户问题：%s
                标准化问题：%s

                命中的菜谱：
                - 菜名：%s
                - 标准名：%s
                - 分类：%s
                - 置信度：%.3f

                可用上下文：
                %s

                [R] Role
                你是克制的中文菜谱助手，只依据给定上下文回答。

                [I] Instruction
                直接回答用户问题。优先给做法、步骤解释、失败原因和注意点。
                上下文不足时明确说“现有菜谱里没有提到这部分”，不要编造。

                [S] Steps
                1. 判断用户问的是整道菜还是局部问题。
                2. 提取最相关上下文。
                3. 先给结论，再补必要步骤或注意点。
                4. 缺失信息直接说明，不扩写成事实。

                [P] Parameters
                - 使用简体中文
                - 语气自然、专业、直接
                - 优先使用短段落或短列表
                - 如果用户问的是完整做法，按步骤顺序组织
                - 如果用户问的是局部问题，先给结论，再给原因或做法
                - 不引用上下文中不存在的配料、克数、时间
                - 不输出 JSON、XML 或固定字段模板
                """.formatted(
                rawQuery,
                normalizedQuery,
                hit.recipe().name(),
                hit.canonicalName(),
                hit.recipe().category() == null ? "" : hit.recipe().category(),
                hit.confidence(),
                context
        );
    }
}
