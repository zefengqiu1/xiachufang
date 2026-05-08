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
                你是一个专业的中文菜谱助手。
                当前本地菜谱库没有可靠命中，请基于联网抓取到的搜索结果和网页正文，整理出一份尽量可靠的参考做法。
                规则：
                1. 只能基于给定网页信息回答，不要编造来源里没有的具体克数和步骤。
                2. 多个来源不一致时，优先保守表述，并明确说“不同来源略有差异”。
                3. 输出结构固定为：菜名、简介、食材、步骤、技巧、来源、说明。
                4. 说明里必须写明“以下内容来自联网检索整理，建议以下厨前再核对原始来源”。

                用户问题：
                """).append(rawQuery).append("\n\n");
        builder.append("归一化菜名：").append(normalizedQuery).append("\n\n");
        builder.append("搜索结果：\n");
        for (WebSearchResult result : results) {
            builder.append("- 标题：").append(result.title()).append("\n");
            builder.append("  链接：").append(result.url()).append("\n");
            builder.append("  摘要：").append(result.snippet()).append("\n");
        }
        builder.append("\n网页提取：\n");
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
        return builder.toString();
    }
}
