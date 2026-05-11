package com.webcrawler.recipe.app.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.recipe.app.model.recipe.RecipeChunk;
import com.webcrawler.recipe.app.model.recipe.RecipeDocument;
import com.webcrawler.recipe.app.retriever.BM25RecipeRetriever;
import com.webcrawler.recipe.app.retriever.HybridRecipeRetriever;
import com.webcrawler.recipe.app.retriever.VectorRecipeRetriever;
import com.webcrawler.recipe.app.util.RecipeDocumentChunker;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${langchain4j.open-ai.api-key:}')")
    StreamingChatLanguageModel streamingChatLanguageModel(
            @Value("${langchain4j.open-ai.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${langchain4j.open-ai.model:deepseek-chat}") String modelName,
            @Value("${langchain4j.open-ai.timeout:PT60S}") Duration timeout) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(timeout)
                .build();
    }

    @Bean
    EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    List<RecipeDocument> recipeDocuments(
            ObjectMapper objectMapper,
            @Value("${recipe.data.path:caipu.txt}") String dataPath) throws IOException {
        Path path = Path.of(dataPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IOException("Recipe data file not found: " + path);
        }

        return objectMapper.readValue(Files.readString(path), new TypeReference<>() {
        });
    }

    @Bean
    List<RecipeChunk> retrievalChunks(List<RecipeDocument> recipeDocuments) {
        return recipeDocuments.stream()
                .flatMap(recipe -> RecipeDocumentChunker.chunk(recipe).stream())
                .toList();
    }

    @Bean
    Map<String, RecipeDocument> recipeDocumentsById(List<RecipeDocument> recipeDocuments) {
        Map<String, RecipeDocument> byId = new LinkedHashMap<>();
        for (RecipeDocument recipe : recipeDocuments) {
            byId.put(recipe.id(), recipe);
        }
        return Collections.unmodifiableMap(byId);
    }

    @Bean
    Map<String, List<RecipeChunk>> retrievalChunksByDishId(List<RecipeChunk> retrievalChunks) {
        return retrievalChunks.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(RecipeChunk::getDishId, LinkedHashMap::new, Collectors.toList()),
                        Collections::unmodifiableMap
                ));
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore(List<RecipeChunk> retrievalChunks, EmbeddingModel embeddingModel) {
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        for (RecipeChunk chunk : retrievalChunks) {
            Embedding embedding = embeddingModel.embed(chunk.getText()).content();
            Metadata metadata = Metadata.from(Map.of(
                    "dishId", defaultString(chunk.getDishId()),
                    "dishName", defaultString(chunk.getDishName()),
                    "view", defaultString(chunk.getView()),
                    "category", defaultString(chunk.getCategory()),
                    "difficulty", chunk.getDifficulty() == null ? "" : String.valueOf(chunk.getDifficulty()),
                    "stepRange", defaultString(chunk.getStepRange())
            ));
            store.add(embedding, TextSegment.from(chunk.getText(), metadata));
        }

        return store;
    }

    @Bean
    BM25RecipeRetriever bm25RecipeRetriever(List<RecipeChunk> retrievalChunks) {
        return new BM25RecipeRetriever(retrievalChunks);
    }

    @Bean
    VectorRecipeRetriever vectorRecipeRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {
        return new VectorRecipeRetriever(embeddingStore, embeddingModel);
    }

    @Bean
    HybridRecipeRetriever hybridRecipeRetriever(
            BM25RecipeRetriever bm25RecipeRetriever,
            VectorRecipeRetriever vectorRecipeRetriever) {
        return new HybridRecipeRetriever(bm25RecipeRetriever, vectorRecipeRetriever);
    }

    @Bean
    @Primary
    ContentRetriever contentRetriever(HybridRecipeRetriever hybridRecipeRetriever) {
        return hybridRecipeRetriever;
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
