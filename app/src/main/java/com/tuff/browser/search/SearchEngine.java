package com.tuff.browser.search;

import androidx.annotation.DrawableRes;
import com.tuff.browser.R;

public class SearchEngine {
    public static final String ID_BRAVE = "brave";
    public static final String ID_DUCKDUCKGO = "duckduckgo";
    public static final String ID_STARTPAGE = "startpage";
    public static final String ID_CUSTOM = "custom";

    private final String id;
    private final String name;
    private final String searchUrlTemplate;
    @DrawableRes
    private final int iconRes;

    public SearchEngine(String id, String name, String searchUrlTemplate, @DrawableRes int iconRes) {
        this.id = id;
        this.name = name;
        this.searchUrlTemplate = searchUrlTemplate;
        this.iconRes = iconRes;
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

    public static SearchEngine createBrave() {
        return new SearchEngine(ID_BRAVE, "Brave Search", "https://search.brave.com/search?q=%s", R.drawable.ic_brave);
    }

    public static SearchEngine createDuckDuckGo() {
        return new SearchEngine(ID_DUCKDUCKGO, "DuckDuckGo", "https://duckduckgo.com/?q=%s", R.drawable.ic_duckduckgo);
    }

    public static SearchEngine createStartpage() {
        return new SearchEngine(ID_STARTPAGE, "Startpage", "https://www.startpage.com/sp/search?query=%s", R.drawable.ic_startpage);
    }

    public static SearchEngine createCustom(String customUrl) {
        return new SearchEngine(ID_CUSTOM, "Custom Engine", customUrl, R.drawable.ic_custom_search);
    }
}
