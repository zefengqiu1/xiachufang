package com.webcrawler.recipe.app.service.websearch;

import com.webcrawler.recipe.app.model.web.WebPageExtract;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WebPageExtractorService {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final int timeoutMillis;

    public WebPageExtractorService(@Value("${recipe.web.timeout-ms:8000}") int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public WebPageExtract extract(String url) {
        try {
            Document document = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(timeoutMillis)
                    .ignoreContentType(true)
                    .get();

            String title = firstNonBlank(
                    document.title(),
                    attr(document, "meta[property=og:title]", "content"),
                    attr(document, "meta[name=title]", "content")
            );
            String summary = firstNonBlank(
                    attr(document, "meta[name=description]", "content"),
                    attr(document, "meta[property=og:description]", "content"),
                    ""
            );
            String bodyText = bestContent(document);
            List<String> lines = cleanLines(bodyText);
            return new WebPageExtract(
                    title,
                    url,
                    summary,
                    extractIngredients(lines),
                    extractSteps(lines),
                    extractTips(lines),
                    bodyText
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private String bestContent(Document document) {
        List<Element> candidates = new ArrayList<>();
        candidates.addAll(document.select("article"));
        candidates.addAll(document.select("main"));
        candidates.addAll(document.select("[class*=recipe], [class*=content], [class*=article], [class*=post], [id*=content], [id*=article]"));
        candidates.add(document.body());

        Element best = null;
        int bestLength = 0;
        for (Element candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String text = candidate.text();
            int length = text == null ? 0 : text.length();
            if (length > bestLength) {
                best = candidate;
                bestLength = length;
            }
        }
        return best == null ? "" : best.text();
    }

    private List<String> cleanLines(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        String expanded = rawText
                .replace("。", "。\n")
                .replace("；", "；\n")
                .replace("：", "：\n");
        String[] pieces = expanded.split("\n");
        List<String> lines = new ArrayList<>();
        for (String piece : pieces) {
            String line = piece.replaceAll("\\s+", " ").trim();
            if (line.length() < 2 || line.length() > 120) {
                continue;
            }
            if (isNoise(line)) {
                continue;
            }
            lines.add(line);
        }
        return lines;
    }

    private List<String> extractIngredients(List<String> lines) {
        Set<String> values = new LinkedHashSet<>();
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("食材") || lower.contains("原料") || lower.contains("用料") || hasQuantityHint(line)) {
                values.add(stripPrefix(line));
            }
            if (values.size() >= 10) {
                break;
            }
        }
        return values.stream().filter(this::looksLikeIngredient).limit(10).toList();
    }

    private List<String> extractSteps(List<String> lines) {
        Set<String> values = new LinkedHashSet<>();
        for (String line : lines) {
            if (looksLikeStep(line)) {
                values.add(stripLeadingStep(line));
            }
            if (values.size() >= 10) {
                break;
            }
        }
        return values.stream().limit(10).toList();
    }

    private List<String> extractTips(List<String> lines) {
        Set<String> values = new LinkedHashSet<>();
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("小贴士") || lower.contains("技巧") || lower.contains("注意") || lower.contains("提示")) {
                values.add(stripPrefix(line));
            }
            if (values.size() >= 6) {
                break;
            }
        }
        return values.stream().limit(6).toList();
    }

    private boolean looksLikeIngredient(String line) {
        return hasQuantityHint(line)
                || line.contains("适量")
                || line.contains("少许")
                || line.contains("克")
                || line.contains("ml")
                || line.contains("毫升")
                || line.contains("勺")
                || line.contains("个");
    }

    private boolean hasQuantityHint(String line) {
        return line.matches(".*\\d+\\s*(克|g|kg|毫升|ml|勺|茶匙|汤匙|个|只|块|片|根|瓣|碗|斤).*");
    }

    private boolean looksLikeStep(String line) {
        return line.matches("^(第?[0-9一二三四五六七八九十]+[、.．步:]?.*)")
                || line.contains("放入")
                || line.contains("加入")
                || line.contains("倒入")
                || line.contains("翻炒")
                || line.contains("焖煮")
                || line.contains("蒸")
                || line.contains("煎")
                || line.contains("炸");
    }

    private String stripLeadingStep(String line) {
        return line.replaceFirst("^第?([0-9一二三四五六七八九十]+)[、.．步:： ]*", "").trim();
    }

    private String stripPrefix(String line) {
        return line.replaceFirst("^(食材|原料|用料|小贴士|技巧|注意|提示)[:：]?", "").trim();
    }

    private boolean isNoise(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("广告")
                || lower.contains("下载")
                || lower.contains("登录")
                || lower.contains("注册")
                || lower.contains("版权")
                || lower.contains("隐私")
                || lower.contains("cookie")
                || lower.contains("上一篇")
                || lower.contains("下一篇");
    }

    private String attr(Document document, String cssQuery, String attribute) {
        Element element = document.selectFirst(cssQuery);
        return element == null ? "" : element.attr(attribute).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
