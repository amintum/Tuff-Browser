package com.tuff.browser.util;

import org.json.JSONException;
import org.json.JSONObject;

public class ShortcutModel {
    private final String id;
    private final String title;
    private final String url;

    public ShortcutModel(String id, String title, String url) {
        this.id = id;
        this.title = title;
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getInitials() {
        if (title == null || title.trim().isEmpty()) {
            return "W";
        }
        String trimmed = title.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length >= 2 && parts[0].length() > 0 && parts[1].length() > 0) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        }
        if (trimmed.length() <= 2) {
            return trimmed.toUpperCase();
        }
        return trimmed.substring(0, 2).toUpperCase();
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("title", title);
            obj.put("url", url);
        } catch (JSONException ignored) {}
        return obj;
    }

    public static ShortcutModel fromJson(JSONObject obj) {
        if (obj == null) return null;
        String id = obj.optString("id", String.valueOf(System.currentTimeMillis()));
        String title = obj.optString("title", "Site");
        String url = obj.optString("url", "");
        return new ShortcutModel(id, title, url);
    }
}
