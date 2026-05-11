package com.webcrawler.recipe.app.service;

import com.webcrawler.recipe.app.model.chat.ChatRequest;
import com.webcrawler.recipe.app.model.chat.ChatResponse;
import com.webcrawler.recipe.app.model.chat.RecipeAskRequest;
import com.webcrawler.recipe.app.model.chat.RecipeAskResponse;
import com.webcrawler.recipe.app.model.recipe.RecipeChunk;
import com.webcrawler.recipe.app.model.recipe.RecipeDocument;
import com.webcrawler.recipe.app.model.search.RecipeMatch;
import com.webcrawler.recipe.app.model.search.RecipeSearchHit;
import com.webcrawler.recipe.app.model.search.RecipeSource;
import com.webcrawler.recipe.app.service.websearch.WebRecipeFallbackService;
import com.webcrawler.recipe.app.tool.RecipeTools;
import com.webcrawler.recipe.app.util.RecipeAssistant;
import com.webcrawler.recipe.app.util.RecipeNormalizer;
import com.webcrawler.recipe.app.util.RecipePromptBuilder;
import com.webcrawler.recipe.app.util.RecipeStreamingAssistant;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class RecipeAgentService {

    private static final Logger log = LoggerFactory.getLogger(RecipeAgentService.class);

    private final RecipeNormalizer recipeNormalizer;
    private final ContentRetriever contentRetriever;
    private final Map<String, RecipeDocument> recipeDocumentsById;
    private final Map<String, List<RecipeChunk>> retrievalChunksByDishId;
    private final RecipePromptBuilder recipePromptBuilder;
    private final RecipeRenderService recipeRenderService;
    private final ChatLanguageModel chatLanguageModel;
    private final WebRecipeFallbackService webRecipeFallbackService;
    private final RecipeAssistant recipeAssistant;
    private final RecipeStreamingAssistant recipeStreamingAssistant;

    public RecipeAgentService(
            RecipeNormalizer recipeNormalizer,
            ContentRetriever contentRetriever,
            Map<String, RecipeDocument> recipeDocumentsById,
            Map<String, List<RecipeChunk>> retrievalChunksByDishId,
            RecipePromptBuilder recipePromptBuilder,
            RecipeRenderService recipeRenderService,
            @Nullable ChatLanguageModel chatLanguageModel,
            @Nullable StreamingChatLanguageModel streamingChatLanguageModel,
            WebRecipeFallbackService webRecipeFallbackService,
            RecipeTools recipeTools) {
        this.recipeNormalizer = recipeNormalizer;
        this.contentRetriever = contentRetriever;
        this.recipeDocumentsById = recipeDocumentsById;
        this.retrievalChunksByDishId = retrievalChunksByDishId;
        this.recipePromptBuilder = recipePromptBuilder;
        this.recipeRenderService = recipeRenderService;
        this.chatLanguageModel = chatLanguageModel;
        this.webRecipeFallbackService = webRecipeFallbackService;
        this.recipeAssistant = chatLanguageModel == null ? null : AiServices.builder(RecipeAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .contentRetriever(contentRetriever)
                .tools(recipeTools)
                .build();
        this.recipeStreamingAssistant = streamingChatLanguageModel == null ? null : AiServices.builder(RecipeStreamingAssistant.class)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .contentRetriever(contentRetriever)
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
                metadata.put("answerMode", "ai_rag_tools");
                return new ChatResponse(
                        sessionId,
                        reply,
                        List.of("contentRetriever", "searchWebRecipePages", "fetchRecipePage"),
                        metadata
                );
            } catch (Exception e) {
                log.warn("AI RAG chat failed: sessionId={}, error={}, falling back to deterministic flow", sessionId, e.getMessage(), e);
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
                List.of("contentRetriever"),
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

        RecipeSearchHit hit = searchBest(normalizedQuery);
        if (hit == null || hit.confidence() < 0.35D) {
            return webRecipeFallbackService.fallback(sessionId, query, normalizedQuery);
        }

        String reply = renderReply(query, normalizedQuery, hit);

        RecipeMatch match = new RecipeMatch(
                hit.recipe().id(),
                hit.recipe().name(),
                hit.canonicalName(),
                round(hit.totalScore()),
                round(hit.confidence()),
                hit.recipe().category()
        );
        RecipeSource source = new RecipeSource(
                "local",
                hit.recipe().name(),
                hit.recipe().sourcePath(),
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

    @Nullable
    public TokenStream streamChat(String message) {
        if (recipeStreamingAssistant == null || message == null || message.isBlank()) {
            return null;
        }
        return recipeStreamingAssistant.chat(message.trim());
    }

    private RecipeSearchHit searchBest(String normalizedQuery) {
        List<Content> contents = contentRetriever.retrieve(Query.from(normalizedQuery));
        if (contents.isEmpty()) {
            return null;
        }

        Map<String, Double> scores = new LinkedHashMap<>();
        for (int i = 0; i < contents.size(); i++) {
            Content content = contents.get(i);
            String dishId = content.textSegment().metadata().getString("dishId");
            if (dishId == null || dishId.isBlank()) {
                continue;
            }
            double rankScore = 1.0D / (i + 1);
            double textScore = overlapScore(recipeNormalizer.normalize(content.textSegment().text()), normalizedQuery);
            scores.merge(dishId, (rankScore * 0.7D) + (textScore * 0.3D), Double::sum);
        }
        if (scores.isEmpty()) {
            return null;
        }

        Map.Entry<String, Double> best = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        if (best == null) {
            return null;
        }

        RecipeDocument recipe = recipeDocumentsById.get(best.getKey());
        if (recipe == null) {
            return null;
        }

        String canonicalName = recipeNormalizer.toCanonicalName(recipe.name());
        double nameScore = computeNameScore(recipe, normalizedQuery, canonicalName);
        double keywordScore = computeKeywordScore(recipe, normalizedQuery, canonicalName);
        double retrievalScore = best.getValue();
        double totalScore = (nameScore * 0.45D) + (keywordScore * 0.20D) + (retrievalScore * 0.35D);
        double confidence = Math.max(nameScore, (keywordScore * 0.4D) + (retrievalScore * 0.6D));

        List<RecipeChunk> contextChunks = retrievalChunksByDishId.getOrDefault(recipe.id(), List.of()).stream()
                .sorted(java.util.Comparator.comparingDouble((RecipeChunk chunk) -> chunkScore(chunk, normalizedQuery)).reversed())
                .limit(3)
                .toList();

        return new RecipeSearchHit(
                recipe,
                canonicalName,
                totalScore,
                nameScore,
                keywordScore,
                retrievalScore,
                confidence,
                contextChunks
        );
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

    private double computeNameScore(RecipeDocument recipe, String normalizedQuery, String canonicalName) {
        String normalizedName = recipeNormalizer.normalize(canonicalName);
        if (normalizedName.equals(normalizedQuery)) {
            return 1.0D;
        }
        if (normalizedName.contains(normalizedQuery)) {
            return containmentScore(normalizedQuery, normalizedName, 0.82D);
        }
        if (normalizedQuery.contains(normalizedName)) {
            return containmentScore(normalizedName, normalizedQuery, 0.52D);
        }
        return overlapScore(normalizedName, normalizedQuery);
    }

    private double computeKeywordScore(RecipeDocument recipe, String normalizedQuery, String canonicalName) {
        double maxScore = 0.0D;
        for (String term : recipeNormalizer.buildSearchTerms(recipe, canonicalName)) {
            if (term == null || term.isBlank()) {
                continue;
            }
            String normalizedTerm = recipeNormalizer.normalize(term);
            if (normalizedTerm.equals(normalizedQuery)) {
                maxScore = Math.max(maxScore, 1.0D);
            } else if (normalizedTerm.contains(normalizedQuery)) {
                maxScore = Math.max(maxScore, containmentScore(normalizedQuery, normalizedTerm, 0.72D));
            } else if (normalizedQuery.contains(normalizedTerm)) {
                maxScore = Math.max(maxScore, containmentScore(normalizedTerm, normalizedQuery, 0.42D));
            } else {
                maxScore = Math.max(maxScore, overlapScore(normalizedTerm, normalizedQuery));
            }
        }
        if (recipeNormalizer.buildSearchText(recipe, canonicalName).contains(normalizedQuery)) {
            maxScore = Math.max(maxScore, 0.72D);
        }
        return maxScore;
    }

    private double chunkScore(RecipeChunk chunk, String normalizedQuery) {
        String normalized = recipeNormalizer.normalize(chunk.text());
        if (normalized.contains(normalizedQuery)) {
            return 1.0D;
        }
        return overlapScore(normalized, normalizedQuery);
    }

    private double overlapScore(String left, String right) {
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            return 0.0D;
        }
        int common = 0;
        for (int i = 0; i < right.length(); i++) {
            if (left.indexOf(right.charAt(i)) >= 0) {
                common++;
            }
        }
        return (double) common / (double) Math.max(left.length(), right.length());
    }

    private double containmentScore(String shorter, String longer, double maxBase) {
        if (shorter == null || shorter.isBlank() || longer == null || longer.isBlank()) {
            return 0.0D;
        }
        double ratio = (double) shorter.length() / (double) longer.length();
        double score = maxBase * ratio;
        if (longer.startsWith(shorter) || longer.endsWith(shorter)) {
            score += 0.05D;
        }
        return Math.min(score, maxBase);
    }

    private double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }
}
