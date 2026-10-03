package com.tuff.browser.search;

import android.content.Context;
import android.net.Uri;
import com.tuff.browser.util.Prefs;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SearchEngineManager {
    private final Prefs prefs;

    // Pattern matching domain names like example.com, www.google.com, sub.domain.org/path, localhost:8080, 192.168.1.1
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?:(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}|localhost|\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(?::\\d+)?(?:/.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    public SearchEngineManager(Context context) {
        this.prefs = new Prefs(context);
    }

    public List<SearchEngine> getAvailableEngines() {
        List<SearchEngine> list = new ArrayList<>();
        list.add(SearchEngine.createBrave());
        list.add(SearchEngine.createDuckDuckGo());
        list.add(SearchEngine.createGoogle());
        list.add(SearchEngine.createCustom(prefs.getCustomEngineUrl()));
        return list;
    }

    public SearchEngine getActiveEngine() {
        String id = prefs.getSearchEngineId();
        if (SearchEngine.ID_DUCKDUCKGO.equals(id)) {
            return SearchEngine.createDuckDuckGo();
        } else if (SearchEngine.ID_GOOGLE.equals(id)) {
            return SearchEngine.createGoogle();
        } else if (SearchEngine.ID_CUSTOM.equals(id)) {
            return SearchEngine.createCustom(prefs.getCustomEngineUrl());
        }
        // Default is Brave Search
        return SearchEngine.createBrave();
    }

    public void setActiveEngine(String engineId) {
        prefs.setSearchEngineId(engineId);
    }

    public String getHomeUrl() {
        SearchEngine engine = getActiveEngine();
        String id = engine.getId();
        if (SearchEngine.ID_DUCKDUCKGO.equals(id)) {
            return "https://duckduckgo.com";
        } else if (SearchEngine.ID_GOOGLE.equals(id)) {
            return "https://www.google.com";
        } else if (SearchEngine.ID_CUSTOM.equals(id)) {
            String template = engine.getSearchUrlTemplate();
            try {
                Uri uri = Uri.parse(template);
                if (uri.getHost() != null) {
                    return uri.getScheme() + "://" + uri.getHost();
                }
            } catch (Exception ignored) {}
            return "https://search.brave.com";
        }
        return "https://search.brave.com";
    }

    public void setCustomEngineUrl(String customUrl) {
        if (customUrl != null && !customUrl.trim().isEmpty()) {
            String url = customUrl.trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            if (!url.contains("%s")) {
                if (url.endsWith("=")) {
                    url = url + "%s";
                } else if (url.contains("?")) {
                    url = url + "&q=%s";
                } else {
                    url = url + "/search?q=%s";
                }
            }
            prefs.setCustomEngineUrl(url);
            prefs.setSearchEngineId(SearchEngine.ID_CUSTOM);
        }
    }

    /**
     * Resolves user omnibox input to either a direct web URL or a search query.
     */
    public String resolveInput(String input) {
        if (input == null) return "about:blank";
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return "about:blank";

        // Check if explicit scheme
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") 
                || trimmed.startsWith("file://") || trimmed.startsWith("about:")
                || trimmed.startsWith("data:") || trimmed.startsWith("javascript:")) {
            return trimmed;
        }

        // Check if web domain / IP address (e.g. google.com, example.org/test, 192.168.1.1)
        if (isDomain(trimmed)) {
            return "https://" + trimmed;
        }

        // Otherwise format as search query using active search engine
        return buildSearchUrl(trimmed);
    }

    public String buildSearchUrl(String query) {
        SearchEngine engine = getActiveEngine();
        String template = engine.getSearchUrlTemplate();
        try {
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            if (template.contains("%s")) {
                return template.replace("%s", encodedQuery);
            } else {
                return template + encodedQuery;
            }
        } catch (UnsupportedEncodingException e) {
            return "https://search.brave.com/search?q=" + query;
        }
    }

    /**
     * Attempts to extract the search query parameter if the user is currently on a search engine result page.
     */
    public String extractSearchQuery(String currentUrl) {
        if (currentUrl == null || !currentUrl.startsWith("http")) return null;
        try {
            Uri uri = Uri.parse(currentUrl);
            String host = uri.getHost();
            if (host == null) return null;

            // Check if this is a known search engine URL
            if (host.contains("brave.com") || host.contains("duckduckgo.com") 
                    || host.contains("google.com") || host.contains("bing.com") || host.contains("yahoo.com")) {
                String q = uri.getQueryParameter("q");
                if (q != null && !q.trim().isEmpty()) {
                    return q.trim();
                }
                String query = uri.getQueryParameter("query");
                if (query != null && !query.trim().isEmpty()) {
                    return query.trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public boolean isSearchEnginePage(String currentUrl) {
        if (currentUrl == null) return false;
        try {
            Uri uri = Uri.parse(currentUrl);
            String host = uri.getHost();
            if (host == null) return false;
            return host.contains("brave.com") || host.contains("duckduckgo.com") 
                    || host.contains("google.com");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isDomain(String text) {
        if (text.contains(" ")) {
            return false;
        }
        return DOMAIN_PATTERN.matcher(text).matches();
    }
}
