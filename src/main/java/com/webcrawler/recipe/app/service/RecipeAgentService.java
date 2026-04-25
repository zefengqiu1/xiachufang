package com.webcrawler.recipe.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.recipe.app.model.chat.ChatRequest;
import com.webcrawler.recipe.app.model.chat.ChatResponse;
import com.webcrawler.recipe.app.model.chat.RecipeAskRequest;
import com.webcrawler.recipe.app.model.chat.RecipeAskResponse;
import com.webcrawler.recipe.app.model.search.RecipeMatch;
import com.webcrawler.recipe.app.model.search.RecipeSearchHit;
import com.webcrawler.recipe.app.model.search.RecipeSource;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.model.chat.ChatLanguageModel;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RecipeAgentService {

    private static final Logger log = LoggerFactory.getLogger(RecipeAgentService.class);

    private final RecipeNormalizer recipeNormalizer;
    private final RecipeSearchService recipeSearchService;
    private final RecipePromptBuilder recipePromptBuilder;
    private final RecipeRenderService recipeRenderService;
    private final Optional<ChatLanguageModel> chatLanguageModel;
    private final WebRecipeFallbackService webRecipeFallbackService;
    private final ObjectMapper objectMapper;
    private final Optional<RecipeAssistant> recipeAssistant;

    public RecipeAgentService(
            RecipeNormalizer recipeNormalizer,
            RecipeSearchService recipeSearchService,
            RecipePromptBuilder recipePromptBuilder,
            RecipeRenderService recipeRenderService,
            Optional<ChatLanguageModel> chatLanguageModel,
            WebRecipeFallbackService webRecipeFallbackService,
            ObjectMapper objectMapper,
            RecipeTools recipeTools) {
        this.recipeNormalizer = recipeNormalizer;
        this.recipeSearchService = recipeSearchService;
        this.recipePromptBuilder = recipePromptBuilder;
        this.recipeRenderService = recipeRenderService;
        this.chatLanguageModel = chatLanguageModel;
        this.webRecipeFallbackService = webRecipeFallbackService;
        this.objectMapper = objectMapper;
        this.recipeAssistant = chatLanguageModel.map(model -> AiServices.builder(RecipeAssistant.class)
                .chatLanguageModel(model)
                .tools(recipeTools)
                .build());
    }

    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.sessionId();
        String message = request.message() == null ? "" : request.message().trim();

        if (recipeAssistant.isPresent() && !message.isBlank()) {
            try {
                String reply = recipeAssistant.get().chat(message);
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("answerMode", "ai_tools");
                return new ChatResponse(
                        sessionId,
                        reply,
                        List.of("searchLocalRecipe", "searchWebRecipePages", "fetchRecipePage"),
                        metadata
                );
            } catch (Exception e) {
                log.warn("AI tools chat failed, falling back to deterministic flow: {}", e.getMessage());
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
        if (chatLanguageModel.isEmpty()) {
            return recipeRenderService.render(hit, query);
        }
        try {
            String prompt = recipePromptBuilder.build(query, normalizedQuery, hit);
            log.info("Recipe chat prompt for query '{}':\n{}", normalizedQuery, prompt);
            return chatLanguageModel.get().generate(prompt);
        } catch (Exception e) {
            log.warn("Chat model failed for query '{}', falling back to local renderer: {}", normalizedQuery, e.getMessage());
            return recipeRenderService.render(hit, query);
        }
    }

    public List<String> splitForStreaming(String reply) {
        if (reply == null || reply.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        String[] lines = reply.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String chunk = i < lines.length - 1 ? line + "\n" : line;
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    public String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize stream payload", e);
        }
    }
}
