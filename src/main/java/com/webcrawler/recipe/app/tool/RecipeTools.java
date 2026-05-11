package com.webcrawler.recipe.app.tool;

import com.webcrawler.recipe.app.model.web.WebPageExtract;
import com.webcrawler.recipe.app.model.web.WebSearchResult;
import com.webcrawler.recipe.app.service.websearch.WebPageExtractorService;
import com.webcrawler.recipe.app.service.websearch.WebSearchService;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RecipeTools {

    private final WebSearchService webSearchService;
    private final WebPageExtractorService webPageExtractorService;

    public RecipeTools(
            WebSearchService webSearchService,
            WebPageExtractorService webPageExtractorService) {
        this.webSearchService = webSearchService;
        this.webPageExtractorService = webPageExtractorService;
    }

    @Tool("在公开网页中搜索菜谱页面。输入菜名或问题，返回最多 5 条结果，每条包含标题、链接和摘要。")
    public String searchWebRecipePages(String query) {
        List<WebSearchResult> results = webSearchService.search(query);
        if (results.isEmpty()) {
            return "没有拿到可用网页搜索结果。";
        }
        StringBuilder builder = new StringBuilder();
        for (WebSearchResult result : results) {
            builder.append("- 标题：").append(result.title()).append('\n');
            builder.append("  链接：").append(result.url()).append('\n');
            builder.append("  摘要：").append(result.snippet()).append("\n\n");
        }
        return builder.toString().trim();
    }

    @Tool("抓取一个菜谱网页，提取标题、摘要、食材、步骤和技巧。输入完整 URL。")
    public String fetchRecipePage(String url) {
        WebPageExtract page = webPageExtractorService.extract(url);
        if (page == null) {
            return "网页抓取失败。";
        }
        return """
                标题：%s
                链接：%s
                摘要：%s
                食材：%s
                步骤：%s
                技巧：%s
                """.formatted(
                page.title(),
                page.url(),
                blankToDefault(page.summary(), "无"),
                page.ingredients().isEmpty() ? "无" : String.join(" | ", page.ingredients()),
                page.steps().isEmpty() ? "无" : String.join(" | ", page.steps()),
                page.tips().isEmpty() ? "无" : String.join(" | ", page.tips())
        ).trim();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
