package com.webcrawler.recipe.app.service.websearch;

import com.webcrawler.recipe.app.model.web.WebPageExtract;
import com.webcrawler.recipe.app.model.web.WebSearchResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WebRecipePromptBuilder {

    public String build(String rawQuery, String normalizedQuery, List<WebSearchResult> results, List<WebPageExtract> pages) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                [C] Context
                用户问题：""").append(rawQuery).append("\n");
        builder.append("标准化问题：").append(normalizedQuery).append("\n\n");
        builder.append("""
                当前信息来自网页检索与网页抽取。

                检索结果：
                """);
        for (WebSearchResult result : results) {
            builder.append("- 标题：").append(result.title()).append("\n");
            builder.append("  链接：").append(result.url()).append("\n");
            builder.append("  摘要：").append(result.snippet()).append("\n");
        }
        builder.append("""

                网页提取内容：
                """);
        for (WebPageExtract page : pages) {
            builder.append("标题：").append(page.title()).append("\n");
            builder.append("链接：").append(page.url()).append("\n");
            builder.append("摘要：").append(page.summary()).append("\n");
            if (!page.ingredients().isEmpty()) {
                builder.append("食材：").append(String.join(" | ", page.ingredients())).append("\n");
            }
            if (!page.steps().isEmpty()) {
                builder.append("步骤：").append(String.join(" | ", page.steps())).append("\n");
            }
            if (!page.tips().isEmpty()) {
                builder.append("技巧：").append(String.join(" | ", page.tips())).append("\n");
            }
            builder.append("\n");
        }
        builder.append("""
                [R] Role
                你是谨慎的中文菜谱整理助手，只基于已提取网页内容归纳回答。

                [I] Instruction
                整理出对用户最有帮助的回答。
                多来源一致时给共识做法；冲突时说明“不同来源做法略有不同”并给更稳妥的做法；信息不足时明确说不确定。

                [S] Steps
                1. 判断用户问的是整道菜还是局部问题。
                2. 先汇总共识信息。
                3. 只补充影响结果的差异。
                4. 冲突时优先采用更具体、更完整的做法。

                [P] Parameters
                - 使用简体中文
                - 回答务实、克制
                - 不要大段照搬网页原文
                - 不要伪造精确配比、火候、时长
                - 可以在结尾简短提示不同做法会有差异
                - 如果来源不足，明确说明不确定
                - 不输出 JSON、XML 或固定字段模板
                """);
        return builder.toString();
    }
}
