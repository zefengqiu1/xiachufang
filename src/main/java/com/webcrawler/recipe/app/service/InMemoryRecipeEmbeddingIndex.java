package com.webcrawler.recipe.app.service;

import com.webcrawler.recipe.app.model.recipe.RecipeIndexedDocument;
import com.webcrawler.recipe.app.repository.FileRecipeRepository;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


/**
 *
 * Embedding 是干嘛的
 *
 * LLM（大模型）不能直接高效做“海量文本相似搜索”，所以需要：
 *
 * 把文本转成向量
 * 存到向量数据库
 * 用户提问时也转向量
 * 比较“距离”
 * 找出最相似内容
 *
 * 这就是：
 *
 * RAG（检索增强生成）
 * AI 知识库
 * 文档问答
 * 语义搜索
 *
 * 的核心。
 *
 */

/**
 * 系统已启动就把所有内存里的菜谱名字进行vector数值化，然后存进embeddings,
 * similarity 函数，把输入参数和已有菜谱名字进行对比vector对比
 * cosine 函数 是计算公式
 */
@Service
public class InMemoryRecipeEmbeddingIndex {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRecipeEmbeddingIndex.class);

    private final Optional<EmbeddingModel> embeddingModel;
    private final FileRecipeRepository fileRecipeRepository;
    private final Map<String, float[]> embeddings = new HashMap<>();
    private volatile boolean available = true;

    public InMemoryRecipeEmbeddingIndex(
            Optional<EmbeddingModel> embeddingModel,
            FileRecipeRepository fileRecipeRepository) {
        this.embeddingModel = embeddingModel;
        this.fileRecipeRepository = fileRecipeRepository;
    }

    @PostConstruct
    void initialize() {
        if (embeddingModel.isEmpty()) {
            return;
        }

        try {
            EmbeddingModel model = embeddingModel.get();
            for (RecipeIndexedDocument recipe : fileRecipeRepository.findAll()) {
                String text = recipe.normalizedSearchText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                float[] vector = model.embed(text).content().vector();
                embeddings.put(recipe.document().id(), vector);
            }
        } catch (Exception e) {
            available = false;
            embeddings.clear();
            log.warn("Embedding index disabled because initialization failed: {}", e.getMessage());
        }
    }

    public boolean enabled() {
        return available && embeddingModel.isPresent() && !embeddings.isEmpty();
    }

    public double similarity(String query, String recipeId) {
        if (!enabled()) {
            return 0.0D;
        }
        float[] recipeVector = embeddings.get(recipeId);
        if (recipeVector == null) {
            return 0.0D;
        }
        try {
            float[] queryVector = embeddingModel.get().embed(query).content().vector();
            return cosine(queryVector, recipeVector); // 进行对比
        } catch (Exception e) {
            available = false;
            embeddings.clear();
            log.warn("Embedding similarity disabled because query embedding failed: {}", e.getMessage());
            return 0.0D;
        }
    }

    private double cosine(float[] left, float[] right) {
        if (left.length != right.length) {
            return 0.0D;
        }
        double dot = 0.0D;
        double leftNorm = 0.0D;
        double rightNorm = 0.0D;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0D || rightNorm == 0.0D) {
            return 0.0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
