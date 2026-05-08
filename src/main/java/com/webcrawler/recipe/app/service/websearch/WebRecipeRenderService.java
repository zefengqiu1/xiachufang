package com.webcrawler.recipe.app.service.websearch;

import com.webcrawler.recipe.app.model.web.WebPageExtract;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WebRecipeRenderService {

    public String render(String normalizedQuery, List<WebPageExtract> pages) {
        List<String> lines = new ArrayList<>();
        lines.add("菜名：" + normalizedQuery);
        lines.add("");
        lines.add("简介：" + summarize(pages));
        lines.add("");
        lines.add("食材：");
        appendOrFallback(lines, collect(pages, WebPageExtract::ingredients), "联网结果里没有提取到稳定的食材列表。");
        lines.add("");
        lines.add("步骤：");
        appendSteps(lines, collect(pages, WebPageExtract::steps));
        lines.add("");
        lines.add("技巧：");
        appendOrFallback(lines, collect(pages, WebPageExtract::tips), "不同网页写法略有差异，建议按原始来源再核对火候、调味和用量。");
        lines.add("");
        lines.add("来源：");
        for (WebPageExtract page : pages) {
            lines.add("- " + page.title() + " " + page.url());
        }
        lines.add("");
        lines.add("说明：以下内容来自联网检索整理，建议以下厨前再核对原始来源。");
        return String.join("\n", lines);
    }

    private void appendOrFallback(List<String> lines, List<String> values, String fallback) {
        if (values.isEmpty()) {
            lines.add("- " + fallback);
            return;
        }
        for (String value : values) {
            lines.add("- " + value);
        }
    }

    private void appendSteps(List<String> lines, List<String> steps) {
        if (steps.isEmpty()) {
            lines.add("- 联网结果里没有提取到稳定的步骤列表。");
            return;
        }
        int index = 1;
        for (String step : steps) {
            lines.add(index++ + ". " + step);
        }
    }

    private List<String> collect(List<WebPageExtract> pages, java.util.function.Function<WebPageExtract, List<String>> extractor) {
        Set<String> merged = new LinkedHashSet<>();
        for (WebPageExtract page : pages) {
            for (String value : extractor.apply(page)) {
                String cleaned = value == null ? "" : value.trim();
                if (!cleaned.isBlank() && cleaned.length() <= 120) {
                    merged.add(cleaned);
                }
            }
        }
        return merged.stream().limit(12).toList();
    }

    private String summarize(List<WebPageExtract> pages) {
        for (WebPageExtract page : pages) {
            if (page.summary() != null && !page.summary().isBlank()) {
                return page.summary();
            }
        }
        return "以下做法根据联网结果整理，不同来源之间可能存在细节差异。";
    }
}
