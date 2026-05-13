package com.webcrawler.recipe.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.recipe.app.model.chat.ChatRequest;
import com.webcrawler.recipe.app.model.chat.RecipeAskRequest;
import com.webcrawler.recipe.app.model.chat.RecipeAskResponse;
import com.webcrawler.recipe.app.model.recipe.RecipeDocument;
import com.webcrawler.recipe.app.model.recipe.RecipeSummary;
import com.webcrawler.recipe.app.service.RecipeAgentService;
import com.webcrawler.recipe.app.service.RecipeFallbackChatService;
import com.webcrawler.recipe.app.util.RecipeNormalizer;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RecipeController {

    private static final Logger log = LoggerFactory.getLogger(RecipeController.class);

    private final RecipeAgentService recipeAgentService;
    private final RecipeFallbackChatService recipeFallbackChatService;
    private final List<RecipeDocument> recipeDocuments;
    private final Map<String, RecipeDocument> recipeDocumentsById;
    private final RecipeNormalizer recipeNormalizer;
    private final ObjectMapper objectMapper;

    public RecipeController(
            RecipeAgentService recipeAgentService,
            RecipeFallbackChatService recipeFallbackChatService,
            List<RecipeDocument> recipeDocuments,
            Map<String, RecipeDocument> recipeDocumentsById,
            RecipeNormalizer recipeNormalizer,
            ObjectMapper objectMapper) {
        this.recipeAgentService = recipeAgentService;
        this.recipeFallbackChatService = recipeFallbackChatService;
        this.recipeDocuments = recipeDocuments;
        this.recipeDocumentsById = recipeDocumentsById;
        this.recipeNormalizer = recipeNormalizer;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/chat/stream", produces = "application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> streamChat(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.sessionId();
        StreamingResponseBody body = outputStream -> {
            Object lock = new Object();
            writeJsonLine(outputStream, Map.of(
                    "type", "start",
                    "sessionId", sessionId
            ));

            TokenStream tokenStream = recipeAgentService.streamChat(request.message());
            if (tokenStream == null) {
                RecipeAskResponse response = recipeFallbackChatService.ask(new RecipeAskRequest(sessionId, request.message()));
                synchronized (lock) {
                    writeJsonLine(outputStream, Map.of(
                            "type", "chunk",
                            "content", response.reply()
                    ));
                    writeJsonLine(outputStream, Map.of(
                            "type", "end",
                            "usedTools", List.of("contentRetriever"),
                            "metadata", Map.of(
                                    "answerMode", response.answerMode(),
                                    "normalizedQuery", response.normalizedQuery(),
                                    "matchedRecipe", response.matchedRecipe(),
                                    "sources", response.sources()
                            )
                    ));
                }
                return;
            }

            CountDownLatch done = new CountDownLatch(1);
            Set<String> usedTools = new LinkedHashSet<>();
            AtomicReference<Throwable> errorRef = new AtomicReference<>();

            tokenStream
                    .onNext(token -> {
                        try {
                            synchronized (lock) {
                                writeJsonLine(outputStream, Map.of(
                                        "type", "chunk",
                                        "content", token
                                ));
                            }
                        } catch (IOException e) {
                            log.warn("Streaming chat write failed: sessionId={}, error={}", sessionId, e.getMessage());
                            errorRef.set(e);
                            done.countDown();
                        }
                    })
                    .onToolExecuted(toolExecution -> recordToolExecution(usedTools, toolExecution))
                    .onComplete(response -> {
                        try {
                            List<String> toolNames;
                            synchronized (usedTools) {
                                toolNames = List.copyOf(usedTools);
                            }
                            synchronized (lock) {
                                writeJsonLine(outputStream, Map.of(
                                        "type", "end",
                                        "usedTools", toolNames,
                                        "metadata", Map.of("answerMode", "ai_tools_stream")
                                ));
                            }
                            log.info("Streaming chat completed: sessionId={}, usedTools={}", sessionId, toolNames);
                        } catch (IOException e) {
                            errorRef.set(e);
                        } finally {
                            done.countDown();
                        }
                    })
                    .onError(error -> {
                        log.error("Streaming chat failed: sessionId={}, error={}", sessionId, error.getMessage(), error);
                        errorRef.set(error);
                        done.countDown();
                    })
                    .start();

            awaitCompletion(done);
            Throwable error = errorRef.get();
            if (error != null) {
                throw new IllegalStateException("Streaming chat failed", error);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .body(body);
    }

    @PostMapping("/recipes/ask")
    public RecipeAskResponse ask(@RequestBody RecipeAskRequest request) {
        return recipeFallbackChatService.ask(request);
    }

    @GetMapping("/recipes")
    public Map<String, Object> listRecipes(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limit) {
        String normalized = recipeNormalizer.normalizeUserQuery(q == null ? "" : q);
        List<RecipeSummary> records = recipeDocuments.stream()
                .filter(recipe -> normalized.isBlank() || recipeNormalizer.buildSearchText(recipe, recipeNormalizer.toCanonicalName(recipe.name())).contains(normalized))
                .sorted(java.util.Comparator.comparing(recipe -> recipeNormalizer.toCanonicalName(recipe.name())))
                .limit(Math.max(1, limit))
                .map(this::toSummary)
                .toList();
        return Map.of("records", records, "count", records.size());
    }

    @GetMapping("/recipes/{id}")
    public ResponseEntity<Map<String, Object>> getRecipe(@PathVariable String id) {
        RecipeDocument recipe = recipeDocumentsById.get(id);
        if (recipe == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("record", toSummary(recipe)));
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }

    private RecipeSummary toSummary(RecipeDocument recipe) {
        return new RecipeSummary(
                recipe.id(),
                recipe.name(),
                recipeNormalizer.toCanonicalName(recipe.name()),
                recipe.category(),
                recipe.difficulty(),
                recipe.servings(),
                recipe.sourcePath()
        );
    }

    private void awaitCompletion(CountDownLatch done) {
        boolean interrupted = false;
        try {
            while (done.getCount() > 0) {
                try {
                    if (done.await(250, TimeUnit.MILLISECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                    if (done.getCount() == 0) {
                        return;
                    }
                    log.warn("Streaming chat wait interrupted before completion");
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void recordToolExecution(Set<String> usedTools, ToolExecution toolExecution) {
        if (toolExecution == null || toolExecution.request() == null || toolExecution.request().name() == null) {
            return;
        }
        synchronized (usedTools) {
            usedTools.add(toolExecution.request().name());
        }
        log.info("Streaming tool executed: tool={}", toolExecution.request().name());
    }

    private void writeJsonLine(java.io.OutputStream outputStream, Map<String, Object> payload) throws IOException {
        String json = objectMapper.writeValueAsString(payload) + "\n";
        outputStream.write(json.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }
}
