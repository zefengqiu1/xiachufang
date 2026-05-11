package com.webcrawler.recipe.app.util;

import com.webcrawler.recipe.app.model.recipe.RecipeDocument;
import com.webcrawler.recipe.app.model.recipe.RecipeIngredient;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RecipeNormalizer {

    public String toCanonicalName(String rawName) {
        String value = rawName == null ? "" : rawName.trim();
        value = value.replaceAll("\\s+", "");
        value = value.replace("（", "(").replace("）", ")");
        value = value.replaceFirst("的做法$", "");
        return value;
    }

    public String normalizeUserQuery(String rawQuery) {
        String normalized = normalize(rawQuery);
        normalized = normalized
                .replace("请问", "")
                .replace("想做", "")
                .replace("想学", "")
                .replace("教程", "")
                .replace("家常", "")
                .replace("做法", "")
                .replace("怎么做", "")
                .replace("如何做", "")
                .replace("怎么烧", "")
                .replace("怎么弄", "")
                .trim();
        normalized = normalizeAliases(normalized);
        return normalized;
    }

    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        return normalizeAliases(text
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}]", " ")
                .replaceAll("\\s+", "")
                .trim());
    }
    //  canonicalName , description. category,tags,ingredient
    public String buildSearchText(RecipeDocument recipe, String canonicalName) {
        List<String> parts = new ArrayList<>();
        parts.add(canonicalName);
        parts.add(normalizePlain(recipe.description()));
        if (recipe.category() != null) {
            parts.add(recipe.category());
        }
        if (recipe.tags() != null) {
            parts.addAll(recipe.tags());
        }
        if (recipe.ingredients() != null) {
            for (RecipeIngredient ingredient : recipe.ingredients()) {
                if (ingredient.name() != null) {
                    parts.add(ingredient.name());
                }
                if (ingredient.textQuantity() != null) {
                    parts.add(ingredient.textQuantity());
                }
            }
        }
        return normalize(String.join(" ", parts));
    }

    public List<String> buildSearchTerms(RecipeDocument recipe, String canonicalName) {
        Set<String> terms = new LinkedHashSet<>();
        terms.add(canonicalName);
        if (recipe.category() != null) {
            terms.add(recipe.category());
        }
        if (recipe.tags() != null) {
            terms.addAll(recipe.tags());
        }
        if (recipe.ingredients() != null) {
            recipe.ingredients().stream()
                    .map(RecipeIngredient::name)
                    .filter(name -> name != null && !name.isBlank())
                    .map(this::normalize)
                    .forEach(terms::add);
        }
        terms.add(normalize(canonicalName));
        return List.copyOf(terms);
    }

    public String normalizePlain(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("(?m)^#+\\s*", "")
                .replaceAll("\\[(.+?)]\\((.+?)\\)", "$1")
                .replace("\r", "")
                .trim();
    }

    private String normalizeAliases(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
                .replace("宫爆", "宫保")
                .replace("鱼香肉末", "鱼香肉丝");
    }
}
