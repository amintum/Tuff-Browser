package com.tuff.browser.tab;

import android.content.Context;
import com.tuff.browser.web.TuffWebView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TabManager {

    public interface TabListener {
        void onTabSwitched(TabModel tab, int index);
        void onTabCountChanged(int count);
    }

    private final Context context;
    private final List<TabModel> tabs = new ArrayList<>();
    private int currentTabIndex = 0;
    private TabListener listener;

    public TabManager(Context context) {
        this.context = context;
    }

    public void setListener(TabListener listener) {
        this.listener = listener;
    }

    public List<TabModel> getTabs() {
        return tabs;
    }

    public int getCurrentTabIndex() {
        return currentTabIndex;
    }

    public TabModel getCurrentTab() {
        if (tabs.isEmpty()) {
            return null;
        }
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) {
            currentTabIndex = 0;
        }
        return tabs.get(currentTabIndex);
    }

    public TabModel createTab(String initialUrl) {
        String id = UUID.randomUUID().toString();
        TuffWebView webView = new TuffWebView(context);
        TabModel tab = new TabModel(id, "New Tab", initialUrl, webView);
        tabs.add(tab);
        currentTabIndex = tabs.size() - 1;

        if (listener != null) {
            listener.onTabCountChanged(tabs.size());
            listener.onTabSwitched(tab, currentTabIndex);
        }
        return tab;
    }

    public void switchTab(int index) {
        if (index >= 0 && index < tabs.size()) {
            currentTabIndex = index;
            if (listener != null) {
                listener.onTabSwitched(tabs.get(index), currentTabIndex);
            }
        }
    }

    public void closeTab(int index) {
        if (index >= 0 && index < tabs.size()) {
            TabModel removed = tabs.remove(index);
            if (removed != null && removed.getWebView() != null) {
                removed.getWebView().destroy();
            }

            if (tabs.isEmpty()) {
                createTab("about:blank");
                return;
            }

            if (currentTabIndex >= tabs.size()) {
                currentTabIndex = tabs.size() - 1;
            }

            if (listener != null) {
                listener.onTabCountChanged(tabs.size());
                listener.onTabSwitched(tabs.get(currentTabIndex), currentTabIndex);
            }
        }
    }

    public void closeAllTabs() {
        for (TabModel tab : tabs) {
            if (tab.getWebView() != null) {
                tab.getWebView().destroy();
            }
        }
        tabs.clear();
        createTab("about:blank");
    }

    public int getTabCount() {
        return tabs.size();
    }
}
