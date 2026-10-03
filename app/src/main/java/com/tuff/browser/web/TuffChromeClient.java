package com.tuff.browser.web;

import android.webkit.WebChromeClient;
import android.webkit.WebView;

public class TuffChromeClient extends WebChromeClient {

    public interface Callback {
        void onProgressChanged(int progress);
        void onReceivedTitle(String title);
    }

    private final Callback callback;

    public TuffChromeClient(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        if (callback != null) {
            callback.onProgressChanged(newProgress);
        }
    }

    @Override
    public void onReceivedTitle(WebView view, String title) {
        super.onReceivedTitle(view, title);
        if (callback != null) {
            callback.onReceivedTitle(title);
        }
    }
}
