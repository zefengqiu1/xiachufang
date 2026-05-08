package com.webcrawler.recipe.app.service.websearch;

import com.webcrawler.recipe.app.model.web.WebSearchResult;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WebSearchService {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final boolean enabled;
    private final int timeoutMillis;
    private final int maxResults;

    public WebSearchService(
            @Value("${recipe.web.enabled:true}") boolean enabled,
            @Value("${recipe.web.timeout-ms:8000}") int timeoutMillis,
            @Value("${recipe.web.max-results:5}") int maxResults) {
        this.enabled = enabled;
        this.timeoutMillis = timeoutMillis;
        this.maxResults = maxResults;
    }

    public boolean enabled() {
        return enabled;
    }

    public List<WebSearchResult> search(String normalizedQuery) {
        if (!enabled) {
            return List.of();
        }

        String webQuery = normalizedQuery + " 做法 菜谱";
        List<WebSearchResult> bing = searchBing(webQuery, normalizedQuery);
        if (!bing.isEmpty()) {
            return bing;
        }
        return searchDuckDuckGo(webQuery, normalizedQuery);
    }

    private List<WebSearchResult> searchBing(String query, String normalizedQuery) {
        try {
            String url = "https://www.bing.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document document = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(timeoutMillis)
                    .referrer("https://www.bing.com/")
                    .get();

            List<WebSearchResult> results = new ArrayList<>();
            for (Element item : document.select("li.b_algo")) {
                Element link = item.selectFirst("h2 a[href]");
                if (link == null) {
                    continue;
                }
                String targetUrl = normalizeResultUrl(link.attr("href"));
                if (!isUsefulUrl(targetUrl)) {
                    continue;
                }
                String title = link.text();
                String snippet = textOrEmpty(item.selectFirst(".b_caption p"));
                results.add(new WebSearchResult(title, targetUrl, snippet, rankScore(title, snippet, normalizedQuery)));
                if (results.size() >= maxResults) {
                    break;
                }
            }
            return results.stream()
                    .sorted(Comparator.comparingDouble(WebSearchResult::score).reversed())
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<WebSearchResult> searchDuckDuckGo(String query, String normalizedQuery) {
        try {
            Connection connection = Jsoup.connect("https://html.duckduckgo.com/html/")
                    .userAgent(USER_AGENT)
                    .timeout(timeoutMillis)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .data("q", query)
                    .data("kl", "cn-zh")
                    .method(Connection.Method.POST);
            Document document = connection.post();

            List<WebSearchResult> results = new ArrayList<>();
            for (Element link : document.select("a.result__a[href]")) {
                String targetUrl = normalizeDuckDuckGoUrl(link.attr("href"));
                if (!isUsefulUrl(targetUrl)) {
                    continue;
                }
                Element resultContainer = link.closest(".result");
                String snippet = resultContainer == null ? "" : textOrEmpty(resultContainer.selectFirst(".result__snippet"));
                String title = link.text();
                results.add(new WebSearchResult(title, targetUrl, snippet, rankScore(title, snippet, normalizedQuery)));
                if (results.size() >= maxResults) {
                    break;
                }
            }
            return results.stream()
                    .sorted(Comparator.comparingDouble(WebSearchResult::score).reversed())
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String normalizeDuckDuckGoUrl(String href) {
        String decoded = decodeRedirectUrl(href, "uddg");
        return decoded == null ? href : decoded;
    }

    private String normalizeResultUrl(String href) {
        return href == null ? "" : href.trim();
    }

    private String decodeRedirectUrl(String href, String parameter) {
        try {
            URI uri = URI.create(href);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return href;
            }
            for (String pair : query.split("&")) {
                int separator = pair.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = pair.substring(0, separator);
                if (!parameter.equals(key)) {
                    continue;
                }
                String value = pair.substring(separator + 1);
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException ignored) {
            return href;
        }
        return href;
    }

    private boolean isUsefulUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http")) {
            return false;
        }
        return !lower.contains("bing.com/search")
                && !lower.contains("duckduckgo.com/")
                && !lower.contains("/maps")
                && !lower.contains("/images");
    }

    private double rankScore(String title, String snippet, String normalizedQuery) {
        String combined = (title + " " + snippet).toLowerCase(Locale.ROOT);
        double score = 0.2D;
        if (combined.contains(normalizedQuery.toLowerCase(Locale.ROOT))) {
            score += 0.45D;
        }
        if (combined.contains("做法")) {
            score += 0.2D;
        }
        if (combined.contains("菜谱")) {
            score += 0.1D;
        }
        if (combined.contains("步骤")) {
            score += 0.05D;
        }
        return score;
    }

    private String textOrEmpty(Element element) {
        return element == null ? "" : element.text().trim();
    }
}
