package com.webcrawler.recipe.app.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RecipeAiConfig {

    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${langchain4j.open-ai.api-key:}')")
    ChatLanguageModel chatLanguageModel(
            @Value("${langchain4j.open-ai.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${langchain4j.open-ai.model:deepseek-chat}") String modelName,
            @Value("${langchain4j.open-ai.timeout:PT60S}") Duration timeout,
            @Value("${langchain4j.open-ai.max-retries:2}") Integer maxRetries) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .build();
    }

    @Bean
    EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }
}
