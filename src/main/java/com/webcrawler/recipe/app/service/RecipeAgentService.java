package com.webcrawler.recipe.app.service;

import com.webcrawler.recipe.app.model.chat.ChatRequest;
import com.webcrawler.recipe.app.model.chat.ChatResponse;
import com.webcrawler.recipe.app.model.chat.RecipeAskRequest;
import com.webcrawler.recipe.app.model.chat.RecipeAskResponse;
import com.webcrawler.recipe.app.model.search.RecipeMatch;
import com.webcrawler.recipe.app.model.search.RecipeSearchHit;
import com.webcrawler.recipe.app.model.search.RecipeSource;
import com.webcrawler.recipe.app.service.websearch.WebRecipeFallbackService;
import com.webcrawler.recipe.app.tool.RecipeTools;
import com.webcrawler.recipe.app.util.RecipeAssistant;
import com.webcrawler.recipe.app.util.RecipeNormalizer;
import com.webcrawler.recipe.app.util.RecipePromptBuilder;
import com.webcrawler.recipe.app.util.RecipeStreamingAssistant;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.TokenStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.lang.Nullable;

@Service
public class RecipeAgentService {

    private static final Logger log = LoggerFactory.getLogger(RecipeAgentService.class);

    private final RecipeNormalizer recipeNormalizer;
    private final RecipeSearchService recipeSearchService;
    private final RecipePromptBuilder recipePromptBuilder;
    private final RecipeRenderService recipeRenderService;
    private final ChatLanguageModel chatLanguageModel;
    private final WebRecipeFallbackService webRecipeFallbackService;
    private final RecipeAssistant recipeAssistant;
    private final RecipeStreamingAssistant recipeStreamingAssistant;

    public RecipeAgentService(
            RecipeNormalizer recipeNormalizer,
            RecipeSearchService recipeSearchService, // 去内存找向量近似最高的
            RecipePromptBuilder recipePromptBuilder, // 给AI
            RecipeRenderService recipeRenderService, // reply 本地
            @Nullable ChatLanguageModel chatLanguageModel, //模型
            @Nullable StreamingChatLanguageModel streamingChatLanguageModel,
            WebRecipeFallbackService webRecipeFallbackService, // 如果本地找不到就联网找
            RecipeTools recipeTools) {
        this.recipeNormalizer = recipeNormalizer;
        this.recipeSearchService = recipeSearchService;
        this.recipePromptBuilder = recipePromptBuilder;
        this.recipeRenderService = recipeRenderService;
        this.chatLanguageModel = chatLanguageModel;
        this.webRecipeFallbackService = webRecipeFallbackService;
        this.recipeAssistant = chatLanguageModel == null ? null : AiServices.builder(RecipeAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(recipeTools)
                .build();
        this.recipeStreamingAssistant = streamingChatLanguageModel == null ? null : AiServices.builder(RecipeStreamingAssistant.class)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(recipeTools)
                .build();
    }

    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.sessionId();
        String message = request.message() == null ? "" : request.message().trim();

        if (recipeAssistant != null && !message.isBlank()) {
            try {
                String reply = recipeAssistant.chat(message);
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("answerMode", "ai_tools");
                return new ChatResponse(
                        sessionId,
                        reply,
                        List.of("searchLocalRecipe", "searchWebRecipePages", "fetchRecipePage"),
                        metadata
                );
            } catch (Exception e) {
                log.warn("AI tools chat failed: sessionId={}, error={}, falling back to deterministic flow", sessionId, e.getMessage(), e);
            }
        }

        RecipeAskResponse response = ask(new RecipeAskRequest(request.sessionId(), request.message()));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("answerMode", response.answerMode());
        metadata.put("normalizedQuery", response.normalizedQuery());
        metadata.put("matchedRecipe", response.matchedRecipe());
        metadata.put("sources", response.sources());
        return new ChatResponse(
                response.sessionId(),
                response.reply(),
                List.of("recipe_search"),
                metadata
        );
    }

    public RecipeAskResponse ask(RecipeAskRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.sessionId();
        String query = request.query() == null ? "" : request.query().trim();
        String normalizedQuery = recipeNormalizer.normalizeUserQuery(query);

        if (normalizedQuery.isBlank()) {
            return new RecipeAskResponse(
                    sessionId,
                    "empty",
                    normalizedQuery,
                    "请输入菜名或做法问题，例如“鱼香肉丝怎么做”。",
                    null,
                    List.of()
            );
        }

        RecipeSearchHit hit = recipeSearchService.searchBest(normalizedQuery);
        if (hit == null || hit.confidence() < 0.45D) {
            return webRecipeFallbackService.fallback(sessionId, query, normalizedQuery);
        }

        String reply = renderReply(query, normalizedQuery, hit);

        RecipeMatch match = new RecipeMatch(
                hit.recipe().document().id(),
                hit.recipe().document().name(),
                hit.recipe().canonicalName(),
                round(hit.totalScore()),
                round(hit.confidence()),
                hit.recipe().document().category()
        );
        RecipeSource source = new RecipeSource(
                "local",
                hit.recipe().document().name(),
                hit.recipe().document().sourcePath(),
                round(hit.totalScore())
        );

        return new RecipeAskResponse(
                sessionId,
                "local_rag",
                normalizedQuery,
                reply,
                match,
                List.of(source)
        );
    }

    private double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }

    private String renderReply(String query, String normalizedQuery, RecipeSearchHit hit) {
        if (chatLanguageModel == null) {
            return recipeRenderService.render(hit, query);
        }
        try {
            String prompt = recipePromptBuilder.build(query, normalizedQuery, hit);
            log.info("Recipe chat prompt for query '{}':\n{}", normalizedQuery, prompt);
            return chatLanguageModel.generate(prompt);
        } catch (Exception e) {
            log.warn("Chat model failed for query '{}', falling back to local renderer: {}", normalizedQuery, e.getMessage());
            return recipeRenderService.render(hit, query);
        }
    }

    @Nullable
    public TokenStream streamChat(String message) {
        if (recipeStreamingAssistant == null || message == null || message.isBlank()) {
            return null;
        }
        return recipeStreamingAssistant.chat(message.trim());
    }
}
