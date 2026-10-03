package com.tuff.browser.search;

import android.content.Context;
import android.util.Patterns;
import com.tuff.browser.util.Prefs;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class SearchEngineManager {
    private final Prefs prefs;

    public SearchEngineManager(Context context) {
        this.prefs = new Prefs(context);
    }

    public List<SearchEngine> getAvailableEngines() {
        List<SearchEngine> list = new ArrayList<>();
        list.add(SearchEngine.createBrave());
        list.add(SearchEngine.createDuckDuckGo());
        list.add(SearchEngine.createStartpage());
        list.add(SearchEngine.createCustom(prefs.getCustomEngineUrl()));
        return list;
    }

    public SearchEngine getActiveEngine() {
        String id = prefs.getSearchEngineId();
        if (SearchEngine.ID_DUCKDUCKGO.equals(id)) {
            return SearchEngine.createDuckDuckGo();
        } else if (SearchEngine.ID_STARTPAGE.equals(id)) {
            return SearchEngine.createStartpage();
        } else if (SearchEngine.ID_CUSTOM.equals(id)) {
            return SearchEngine.createCustom(prefs.getCustomEngineUrl());
        }
        // Default is Brave Search
        return SearchEngine.createBrave();
    }

    public void setActiveEngine(String engineId) {
        prefs.setSearchEngineId(engineId);
    }

    public void setCustomEngineUrl(String customUrl) {
        if (customUrl != null && !customUrl.trim().isEmpty()) {
            if (!customUrl.contains("%s")) {
                if (customUrl.endsWith("=")) {
                    customUrl = customUrl + "%s";
                } else if (customUrl.contains("?")) {
                    customUrl = customUrl + "&q=%s";
                } else {
                    customUrl = customUrl + "?q=%s";
                }
            }
            prefs.setCustomEngineUrl(customUrl);
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

        // Check if already full URL
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") 
                || trimmed.startsWith("file://") || trimmed.startsWith("about:")) {
            return trimmed;
        }

        // Check if domain pattern like 'example.com' or 'sub.domain.org/path'
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

    private boolean isDomain(String text) {
        if (text.contains(" ") || !text.contains(".")) {
            return false;
        }
        return Patterns.WEB_URL.matcher("https://" + text).matches();
    }
}
