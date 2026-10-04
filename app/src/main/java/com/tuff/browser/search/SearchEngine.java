package com.tuff.browser.search;

import androidx.annotation.DrawableRes;
import com.tuff.browser.R;

public class SearchEngine {
    public static final String ID_BRAVE = "brave";
    public static final String ID_DUCKDUCKGO = "duckduckgo";
    public static final String ID_GOOGLE = "google";
    public static final String ID_CUSTOM = "custom";

    private final String id;
    private final String name;
    private final String searchUrlTemplate;
    @DrawableRes
    private final int iconRes;
    private final String warningText;

    public SearchEngine(String id, String name, String searchUrlTemplate, @DrawableRes int iconRes, String warningText) {
        this.id = id;
        this.name = name;
        this.searchUrlTemplate = searchUrlTemplate;
        this.iconRes = iconRes;
        this.warningText = warningText;
    }

    public SearchEngine(String id, String name, String searchUrlTemplate, @DrawableRes int iconRes) {
        this(id, name, searchUrlTemplate, iconRes, null);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSearchUrlTemplate() {
        return searchUrlTemplate;
    }

    @DrawableRes
    public int getIconRes() {
        return iconRes;
    }

    public String getWarningText() {
        return warningText;
    }

    public boolean hasWarning() {
        return warningText != null && !warningText.isEmpty();
    }

    public static SearchEngine createBrave() {
        return new SearchEngine(ID_BRAVE, "Brave Search", "https://search.brave.com/search?q=%s", R.drawable.ic_brave);
    }

    public static SearchEngine createDuckDuckGo() {
        return new SearchEngine(ID_DUCKDUCKGO, "DuckDuckGo", "https://duckduckgo.com/?q=%s", R.drawable.ic_duckduckgo);
    }

    public static SearchEngine createGoogle() {
        return new SearchEngine(ID_GOOGLE, "Google", "https://www.google.com/search?q=%s", R.drawable.ic_google, "Unsafe · Not recommended");
    }

    public static SearchEngine createCustom(String customUrl) {
        String url = (customUrl == null) ? "" : customUrl.trim();
        return new SearchEngine(ID_CUSTOM, "Another Search Engine", url, R.drawable.ic_custom_search);
    }
}
