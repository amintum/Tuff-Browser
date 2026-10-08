package com.tuff.browser.web;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class TuffWebClient extends WebViewClient {

    public interface Callback {
        void onPageStarted(String url);
        void onPageCommitVisible(String url);
        void onPageFinished(String url);
        void onTitleUpdated(String title);
    }

    private final Callback callback;
    private final Context context;

    public TuffWebClient(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        if (callback != null) {
            callback.onPageStarted(url);
        }
    }

    @Override
    public void onPageCommitVisible(WebView view, String url) {
        super.onPageCommitVisible(view, url);
        if (view instanceof TuffWebView) {
            ((TuffWebView) view).applyDesktopViewport();
        }
        if (callback != null) {
            callback.onPageCommitVisible(url);
        }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        if (view instanceof TuffWebView) {
            ((TuffWebView) view).applyDesktopViewport();
        }
        if (callback != null) {
            callback.onPageFinished(url);
            callback.onTitleUpdated(view.getTitle());
        }
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        return handleCustomUri(uri);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        Uri uri = Uri.parse(url);
        return handleCustomUri(uri);
    }

    private boolean handleCustomUri(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;

        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) 
                || "file".equalsIgnoreCase(scheme) || "about".equalsIgnoreCase(scheme)) {
            return false; // Let WebView handle standard web requests
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return true;
        }
    }
}
