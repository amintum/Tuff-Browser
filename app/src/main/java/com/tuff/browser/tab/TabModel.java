package com.tuff.browser.tab;

import com.tuff.browser.web.TuffWebView;

public class TabModel {
    private final String id;
    private String title;
    private String url;
    private TuffWebView webView;

    public TabModel(String id, String title, String url, TuffWebView webView) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.webView = webView;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return (title == null || title.isEmpty()) ? "New Tab" : title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return (url == null || url.isEmpty()) ? "about:blank" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public TuffWebView getWebView() {
        return webView;
    }

    public void setWebView(TuffWebView webView) {
        this.webView = webView;
    }
}
