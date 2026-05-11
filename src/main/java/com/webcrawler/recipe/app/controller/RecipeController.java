package com.webcrawler.recipe.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webcrawler.recipe.app.model.chat.ChatRequest;
import com.webcrawler.recipe.app.model.chat.ChatResponse;
import com.webcrawler.recipe.app.model.chat.RecipeAskRequest;
import com.webcrawler.recipe.app.model.chat.RecipeAskResponse;
import com.webcrawler.recipe.app.model.recipe.RecipeDocument;
import com.webcrawler.recipe.app.model.recipe.RecipeSummary;
import com.webcrawler.recipe.app.service.RecipeAgentService;
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
import java.util.concurrent.atomic.AtomicReference;
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

    private final RecipeAgentService recipeAgentService;
    private final List<RecipeDocument> recipeDocuments;
    private final Map<String, RecipeDocument> recipeDocumentsById;
    private final RecipeNormalizer recipeNormalizer;
    private final ObjectMapper objectMapper;

    public RecipeController(
            RecipeAgentService recipeAgentService,
            List<RecipeDocument> recipeDocuments,
            Map<String, RecipeDocument> recipeDocumentsById,
            RecipeNormalizer recipeNormalizer,
            ObjectMapper objectMapper) {
        this.recipeAgentService = recipeAgentService;
        this.recipeDocuments = recipeDocuments;
        this.recipeDocumentsById = recipeDocumentsById;
        this.recipeNormalizer = recipeNormalizer;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return recipeAgentService.chat(request);
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
                ChatResponse response = recipeAgentService.chat(new ChatRequest(sessionId, request.message()));
                synchronized (lock) {
                    writeJsonLine(outputStream, Map.of(
                            "type", "chunk",
                            "content", response.reply()
                    ));
                    writeJsonLine(outputStream, Map.of(
                            "type", "end",
                            "usedTools", response.usedTools(),
                            "metadata", response.metadata()
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
                            errorRef.set(e);
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
                        } catch (IOException e) {
                            errorRef.set(e);
                        } finally {
                            done.countDown();
                        }
                    })
                    .onError(error -> {
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
        return recipeAgentService.ask(request);
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
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Streaming chat interrupted", e);
        }
    }

    private void recordToolExecution(Set<String> usedTools, ToolExecution toolExecution) {
        if (toolExecution == null || toolExecution.request() == null || toolExecution.request().name() == null) {
            return;
        }
        synchronized (usedTools) {
            usedTools.add(toolExecution.request().name());
        }
    }

    private void writeJsonLine(java.io.OutputStream outputStream, Map<String, Object> payload) throws IOException {
        String json = objectMapper.writeValueAsString(payload) + "\n";
        outputStream.write(json.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }
}
