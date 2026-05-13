package com.webcrawler.recipe.app.service;

import com.webcrawler.recipe.app.tool.RecipeTools;
import com.webcrawler.recipe.app.util.RecipeStreamingAssistant;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class RecipeAgentService {

    private static final Logger log = LoggerFactory.getLogger(RecipeAgentService.class);

    private final RecipeStreamingAssistant recipeStreamingAssistant;
    private final boolean debugRag;

    public RecipeAgentService(
            ContentRetriever contentRetriever,
            @Nullable StreamingChatLanguageModel streamingChatLanguageModel,
            RecipeTools recipeTools,
            @Value("${recipe.debug.rag:false}") boolean debugRag) {
        this.debugRag = debugRag;
        this.recipeStreamingAssistant = streamingChatLanguageModel == null ? null : AiServices.builder(RecipeStreamingAssistant.class)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .contentRetriever(contentRetriever)
                .tools(recipeTools)
                .build();
    }

    @Nullable
    public TokenStream streamChat(String message) {
        if (recipeStreamingAssistant == null || message == null || message.isBlank()) {
            return null;
        }
        String trimmed = message.trim();
        if (debugRag) {
            log.info("[RAG-STREAM] message='{}'", trimmed);
        }
        return recipeStreamingAssistant.chat(trimmed);
    }
}
