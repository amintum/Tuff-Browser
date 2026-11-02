package com.tuff.browser.tab;

import org.json.JSONException;
import org.json.JSONObject;

public class SavedTab {
    private final String title;
    private final String url;

    public SavedTab(String title, String url) {
        this.title = title;
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("title", title);
            obj.put("url", url);
        } catch (JSONException ignored) {}
        return obj;
    }

    public static SavedTab fromJson(JSONObject obj) {
        if (obj == null) return null;
        String title = obj.optString("title", "New Tab");
        String url = obj.optString("url", "about:home");
        return new SavedTab(title, url);
    }
}
