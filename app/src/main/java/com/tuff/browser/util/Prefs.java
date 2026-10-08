package com.tuff.browser.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;

public class Prefs {
    private static final String PREF_NAME = "tuff_preferences";

    private static final String KEY_FIRST_LAUNCH = "key_first_launch";
    private static final String KEY_SEARCH_ENGINE = "key_search_engine";
    private static final String KEY_CUSTOM_ENGINE_URL = "key_custom_engine_url";
    private static final String KEY_SEARCH_BAR_POSITION = "key_search_bar_position";
    private static final String KEY_DESKTOP_MODE = "key_desktop_mode";
    private static final String KEY_HOMEPAGE_SHORTCUTS = "key_homepage_shortcuts";

    public static final String POSITION_TOP = "top";
    public static final String POSITION_BOTTOM = "bottom";

    private final SharedPreferences prefs;

    public Prefs(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    public void setFirstLaunchCompleted() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply();
    }

    public String getSearchEngineId() {
        return prefs.getString(KEY_SEARCH_ENGINE, "brave");
    }

    public void setSearchEngineId(String id) {
        prefs.edit().putString(KEY_SEARCH_ENGINE, id).apply();
    }

    public String getCustomEngineUrl() {
        return prefs.getString(KEY_CUSTOM_ENGINE_URL, "");
    }

    public void setCustomEngineUrl(String url) {
        prefs.edit().putString(KEY_CUSTOM_ENGINE_URL, url).apply();
    }

    public String getSearchBarPosition() {
        return prefs.getString(KEY_SEARCH_BAR_POSITION, POSITION_BOTTOM);
    }

    public void setSearchBarPosition(String position) {
        prefs.edit().putString(KEY_SEARCH_BAR_POSITION, position).apply();
    }

    public boolean isDesktopMode() {
        return prefs.getBoolean(KEY_DESKTOP_MODE, false);
    }

    public void setDesktopMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_DESKTOP_MODE, enabled).apply();
    }

    public List<ShortcutModel> getHomepageShortcuts() {
        List<ShortcutModel> list = new ArrayList<>();
        String jsonStr = prefs.getString(KEY_HOMEPAGE_SHORTCUTS, "[]");
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                ShortcutModel model = ShortcutModel.fromJson(arr.getJSONObject(i));
                if (model != null && !model.getUrl().isEmpty()) {
                    list.add(model);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void saveHomepageShortcuts(List<ShortcutModel> list) {
        JSONArray arr = new JSONArray();
        if (list != null) {
            for (ShortcutModel m : list) {
                if (m != null) arr.put(m.toJson());
            }
        }
        prefs.edit().putString(KEY_HOMEPAGE_SHORTCUTS, arr.toString()).apply();
    }
}
