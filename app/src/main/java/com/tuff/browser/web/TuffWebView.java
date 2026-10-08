package com.tuff.browser.web;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class TuffWebView extends WebView {

    public static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private String defaultMobileUA;
    private boolean isDesktopMode = false;
    private int currentTextZoom = 100;

    public TuffWebView(Context context) {
        super(context);
        init();
    }

    public TuffWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void init() {
        WebSettings settings = getSettings();
        this.defaultMobileUA = settings.getUserAgentString();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);

        // Zoom controls
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false); // Hide ugly overlay buttons

        // Viewport
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Hardware acceleration hints
        setFocusable(true);
        setFocusableInTouchMode(true);

        // Dark mode defaults
        setBackgroundColor(0xFF121212);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                settings.setForceDark(WebSettings.FORCE_DARK_ON);
            } catch (Throwable ignored) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                settings.setAlgorithmicDarkeningAllowed(true);
            } catch (Throwable ignored) {}
        }

        // Mixed content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);
    }

    public boolean isDesktopMode() {
        return isDesktopMode;
    }

    public void setDesktopMode(boolean enabled) {
        this.isDesktopMode = enabled;
        WebSettings settings = getSettings();
        if (enabled) {
            settings.setUserAgentString(DESKTOP_UA);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);
        } else {
            settings.setUserAgentString(defaultMobileUA);
            settings.setUseWideViewPort(false);
            settings.setLoadWithOverviewMode(false);
        }
        setInitialScale(0);

        String currentUrl = getUrl();
        if (currentUrl != null && !currentUrl.isEmpty() && !"about:blank".equals(currentUrl)) {
            if (enabled) {
                String desktopUrl = currentUrl.replace("://m.", "://www.").replace("://mobile.", "://www.");
                if (!desktopUrl.equals(currentUrl)) {
                    loadUrl(desktopUrl);
                    return;
                }
            }
            reload();
        }
    }

    public void applyDesktopViewport() {
        if (!isDesktopMode) return;
        evaluateJavascript(
            "(function() {" +
            "  var meta = document.querySelector('meta[name=\"viewport\"]');" +
            "  if (meta) {" +
            "    meta.setAttribute('content', 'width=1024');" +
            "  } else {" +
            "    meta = document.createElement('meta');" +
            "    meta.name = 'viewport';" +
            "    meta.content = 'width=1024';" +
            "    if (document.head) document.head.appendChild(meta);" +
            "  }" +
            "})();", null);
    }

    public int getTextZoomLevel() {
        return currentTextZoom;
    }

    public void setTextZoomLevel(int zoomPercent) {
        if (zoomPercent < 50) zoomPercent = 50;
        if (zoomPercent > 200) zoomPercent = 200;
        this.currentTextZoom = zoomPercent;
        getSettings().setTextZoom(zoomPercent);
    }

    public void zoomInText() {
        setTextZoomLevel(currentTextZoom + 15);
    }

    public void zoomOutText() {
        setTextZoomLevel(currentTextZoom - 15);
    }
}
