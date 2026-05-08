package com.webcrawler.recipe.app.service.websearch;

import com.webcrawler.recipe.app.model.chat.RecipeAskResponse;
import com.webcrawler.recipe.app.model.search.RecipeSource;
import com.webcrawler.recipe.app.model.web.WebPageExtract;
import com.webcrawler.recipe.app.model.web.WebSearchResult;
import dev.langchain4j.model.chat.ChatLanguageModel;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WebRecipeFallbackService {

    private static final Logger log = LoggerFactory.getLogger(WebRecipeFallbackService.class);

    private final WebSearchService webSearchService;
    private final WebPageExtractorService webPageExtractorService;
    private final WebRecipePromptBuilder webRecipePromptBuilder;
    private final WebRecipeRenderService webRecipeRenderService;
    private final Optional<ChatLanguageModel> chatLanguageModel;

    public WebRecipeFallbackService(
            WebSearchService webSearchService,
            WebPageExtractorService webPageExtractorService,
            WebRecipePromptBuilder webRecipePromptBuilder,
            WebRecipeRenderService webRecipeRenderService,
            Optional<ChatLanguageModel> chatLanguageModel) {
        this.webSearchService = webSearchService;
        this.webPageExtractorService = webPageExtractorService;
        this.webRecipePromptBuilder = webRecipePromptBuilder;
        this.webRecipeRenderService = webRecipeRenderService;
        this.chatLanguageModel = chatLanguageModel;
    }

    public RecipeAskResponse fallback(String sessionId, String rawQuery, String normalizedQuery) {
        if (!webSearchService.enabled()) {
            return unavailable(sessionId, rawQuery, normalizedQuery, "联网 fallback 已关闭。");
        }

        List<WebSearchResult> results = webSearchService.search(normalizedQuery);
        if (results.isEmpty()) {
            return unavailable(sessionId, rawQuery, normalizedQuery, "没有拿到可用的搜索结果。");
        }

        List<WebPageExtract> pages = results.stream()
                .map(result -> webPageExtractorService.extract(result.url()))
                .filter(page -> page != null && useful(page))
                .limit(3)
                .toList();

        if (pages.isEmpty()) {
            return unavailable(sessionId, rawQuery, normalizedQuery, "搜索结果可用，但网页正文抽取失败。");
        }

        String reply = renderReply(rawQuery, normalizedQuery, results, pages);

        return new RecipeAskResponse(
                sessionId,
                "web_fallback",
                normalizedQuery,
                reply,
                null,
                pages.stream()
                        .map(page -> new RecipeSource("web", page.title(), page.url(), 0.6D))
                        .toList()
        );
    }

    private RecipeAskResponse unavailable(String sessionId, String rawQuery, String normalizedQuery, String reason) {
        return new RecipeAskResponse(
                sessionId,
                "web_fallback_unavailable",
                normalizedQuery,
                """
                本地菜谱库里没有可靠命中，已经尝试联网补充，但这次没有拿到可用网页内容。
                原因：%s
                你可以换一个更精确的菜名再试一次。
                原始问题：%s
                """.formatted(reason, rawQuery),
                null,
                List.of(new RecipeSource("web", "unavailable", reason, 0.0D))
        );
    }

    private boolean useful(WebPageExtract page) {
        return (page.summary() != null && !page.summary().isBlank())
                || !page.ingredients().isEmpty()
                || !page.steps().isEmpty()
                || (page.rawText() != null && page.rawText().length() > 200);
    }

    private String renderReply(
            String rawQuery,
            String normalizedQuery,
            List<WebSearchResult> results,
            List<WebPageExtract> pages) {
        if (chatLanguageModel.isEmpty()) {
            return webRecipeRenderService.render(normalizedQuery, pages);
        }
        try {
            String prompt = webRecipePromptBuilder.build(rawQuery, normalizedQuery, results, pages);
            log.info("Web fallback prompt for query '{}':\n{}", normalizedQuery, prompt);
            return chatLanguageModel.get().generate(prompt);
        } catch (Exception e) {
            log.warn("Web fallback model failed for query '{}', using deterministic renderer: {}", normalizedQuery, e.getMessage());
            return webRecipeRenderService.render(normalizedQuery, pages);
        }
    }
}
