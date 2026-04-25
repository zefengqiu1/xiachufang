package com.webcrawler.recipe.app.controller;

import com.webcrawler.recipe.app.model.chat.ChatRequest;
import com.webcrawler.recipe.app.model.chat.ChatResponse;
import com.webcrawler.recipe.app.model.chat.RecipeAskRequest;
import com.webcrawler.recipe.app.model.chat.RecipeAskResponse;
import com.webcrawler.recipe.app.model.recipe.RecipeSummary;
import com.webcrawler.recipe.app.service.RecipeAgentService;
import com.webcrawler.recipe.app.service.RecipeCatalogService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RecipeController {

    private final RecipeAgentService recipeAgentService;
    private final RecipeCatalogService recipeCatalogService;

    public RecipeController(RecipeAgentService recipeAgentService, RecipeCatalogService recipeCatalogService) {
        this.recipeAgentService = recipeAgentService;
        this.recipeCatalogService = recipeCatalogService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return recipeAgentService.chat(request);
    }

    @PostMapping(value = "/chat/stream", produces = "application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> streamChat(@RequestBody ChatRequest request) {
        ChatResponse response = recipeAgentService.chat(request);
        StreamingResponseBody body = outputStream -> {
            writeJsonLine(outputStream, Map.of(
                    "type", "start",
                    "sessionId", response.sessionId()
            ));

            for (String chunk : recipeAgentService.splitForStreaming(response.reply())) {
                writeJsonLine(outputStream, Map.of(
                        "type", "chunk",
                        "content", chunk
                ));
            }

            writeJsonLine(outputStream, Map.of(
                    "type", "end",
                    "usedTools", response.usedTools(),
                    "metadata", response.metadata()
            ));
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
        List<RecipeSummary> records = recipeCatalogService.listRecipes(q, limit);
        return Map.of("records", records, "count", records.size());
    }

    @GetMapping("/recipes/{id}")
    public ResponseEntity<Map<String, Object>> getRecipe(@PathVariable String id) {
        RecipeSummary record = recipeCatalogService.getRecipe(id);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("record", record));
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok");
    }

    private void writeJsonLine(java.io.OutputStream outputStream, Map<String, Object> payload) throws IOException {
        String json = recipeAgentService.toJson(payload) + "\n";
        outputStream.write(json.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }
}
