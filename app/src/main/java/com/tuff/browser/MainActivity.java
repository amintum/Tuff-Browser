package com.tuff.browser;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tuff.browser.download.DownloadManagerSheet;
import com.tuff.browser.download.DownloadService;
import com.tuff.browser.search.CornerEngineSwitcherDialog;
import com.tuff.browser.search.OnboardingDialog;
import com.tuff.browser.search.SearchEngine;
import com.tuff.browser.search.SearchEngineManager;
import com.tuff.browser.tab.TabManager;
import com.tuff.browser.tab.TabModel;
import com.tuff.browser.tab.TabSheetDialog;
import com.tuff.browser.util.Prefs;
import com.tuff.browser.web.TuffChromeClient;
import com.tuff.browser.web.TuffWebClient;
import com.tuff.browser.web.TuffWebView;

public class MainActivity extends AppCompatActivity implements TabManager.TabListener {

    private Prefs prefs;
    private SearchEngineManager engineManager;
    private TabManager tabManager;

    private FrameLayout webViewContainer;
    private ProgressBar progressBar;

    private LinearLayout containerTopBar;
    private LinearLayout layoutBottomOmnibar;

    private EditText etOmniboxTop;
    private EditText etOmniboxBottom;
    private ImageView btnCornerEngineTop;
    private ImageView btnCornerEngineBottom;
    private ImageView btnRefreshTop;
    private ImageView btnRefreshBottom;

    private TextView tvTabsCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);
        engineManager = new SearchEngineManager(this);
        tabManager = new TabManager(this);
        tabManager.setListener(this);

        initViews();
        setupListeners();
        applySearchBarPosition();
        updateCornerEngineIcons();

        // Check first launch
        if (prefs.isFirstLaunch()) {
            showOnboarding();
        }

        // Initialize first tab
        String startUrl = "https://search.brave.com";
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            startUrl = intent.getData().toString();
        }
        tabManager.createTab(startUrl);
    }

    private void initViews() {
        webViewContainer = findViewById(R.id.webview_container);
        progressBar = findViewById(R.id.progress_loading);

        containerTopBar = findViewById(R.id.container_top_bar);
        layoutBottomOmnibar = findViewById(R.id.layout_bottom_omnibar);

        etOmniboxTop = findViewById(R.id.et_omnibox_top);
        etOmniboxBottom = findViewById(R.id.et_omnibox_bottom);
        btnCornerEngineTop = findViewById(R.id.btn_corner_engine_top);
        btnCornerEngineBottom = findViewById(R.id.btn_corner_engine_bottom);
        btnRefreshTop = findViewById(R.id.btn_refresh_top);
        btnRefreshBottom = findViewById(R.id.btn_refresh_bottom);

        tvTabsCount = findViewById(R.id.tv_tabs_count);
    }

    private void setupListeners() {
        // Corner search engine switcher
        View.OnClickListener cornerSwitchListener = v -> openCornerEngineSwitcher();
        btnCornerEngineTop.setOnClickListener(cornerSwitchListener);
        btnCornerEngineBottom.setOnClickListener(cornerSwitchListener);

        // Omnibox actions
        setupOmnibox(etOmniboxTop);
        setupOmnibox(etOmniboxBottom);

        // Refresh buttons
        View.OnClickListener refreshListener = v -> {
            TuffWebView wv = getActiveWebView();
            if (wv != null) wv.reload();
        };
        btnRefreshTop.setOnClickListener(refreshListener);
        btnRefreshBottom.setOnClickListener(refreshListener);

        // Navigation bar
        findViewById(R.id.btn_back).setOnClickListener(v -> handleBack());
        findViewById(R.id.btn_forward).setOnClickListener(v -> handleForward());
        findViewById(R.id.btn_home).setOnClickListener(v -> navigateHome());
        findViewById(R.id.btn_tabs).setOnClickListener(v -> openTabSheet());
        findViewById(R.id.btn_menu).setOnClickListener(v -> openMenuSheet());
    }

    private void setupOmnibox(EditText et) {
        et.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String input = et.getText().toString();
                loadQueryOrUrl(input);
                hideKeyboard(et);
                et.clearFocus();
                return true;
            }
            return false;
        });
    }

    private void loadQueryOrUrl(String input) {
        String resolved = engineManager.resolveInput(input);
        TuffWebView wv = getActiveWebView();
        if (wv != null) {
            wv.loadUrl(resolved);
        }
    }

    private void navigateHome() {
        String homeUrl = engineManager.getHomeUrl();
        TuffWebView wv = getActiveWebView();
        if (wv != null) wv.loadUrl(homeUrl);
    }

    private void handleBack() {
        TuffWebView wv = getActiveWebView();
        if (wv != null && wv.canGoBack()) {
            wv.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void handleForward() {
        TuffWebView wv = getActiveWebView();
        if (wv != null && wv.canGoForward()) {
            wv.goForward();
        }
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void showOnboarding() {
        OnboardingDialog dialog = new OnboardingDialog(this, engineManager, prefs, engine -> {
            updateCornerEngineIcons();
            navigateHome();
        });
        dialog.show();
    }

    private void openCornerEngineSwitcher() {
        CornerEngineSwitcherDialog dialog = new CornerEngineSwitcherDialog(this, engineManager, newEngine -> {
            updateCornerEngineIcons();
            TuffWebView wv = getActiveWebView();
            if (wv == null) return;

            String currentUrl = wv.getUrl();
            EditText activeEt = getActiveOmnibox();
            String currentInput = (activeEt != null) ? activeEt.getText().toString().trim() : "";

            // 1. If currently on a search engine results page, switch query to new engine
            String query = engineManager.extractSearchQuery(currentUrl);
            if (query != null && !query.isEmpty()) {
                String newSearchUrl = engineManager.buildSearchUrl(query);
                wv.loadUrl(newSearchUrl);
                return;
            }

            // 2. If the user had typed an unsubmitted query in the search box
            if (!currentInput.isEmpty() && !currentInput.startsWith("http://") && !currentInput.startsWith("https://")) {
                loadQueryOrUrl(currentInput);
                return;
            }

            // 3. If currently on a home page, blank page, or search engine home page, load new engine home
            if (currentUrl == null || "about:blank".equals(currentUrl) || engineManager.isSearchEnginePage(currentUrl)) {
                wv.loadUrl(engineManager.getHomeUrl());
                return;
            }

            // 4. Otherwise user is browsing regular site (e.g. github.com)
            Toast.makeText(this, "Default engine: " + newEngine.getName(), Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }

    private void updateCornerEngineIcons() {
        int iconRes = engineManager.getActiveEngine().getIconRes();
        btnCornerEngineTop.setImageResource(iconRes);
        btnCornerEngineBottom.setImageResource(iconRes);
    }

    private void applySearchBarPosition() {
        boolean isTop = Prefs.POSITION_TOP.equals(prefs.getSearchBarPosition());
        if (isTop) {
            containerTopBar.setVisibility(View.VISIBLE);
            layoutBottomOmnibar.setVisibility(View.GONE);
        } else {
            containerTopBar.setVisibility(View.GONE);
            layoutBottomOmnibar.setVisibility(View.VISIBLE);
        }
    }

    private EditText getActiveOmnibox() {
        return Prefs.POSITION_TOP.equals(prefs.getSearchBarPosition()) ? etOmniboxTop : etOmniboxBottom;
    }

    private TuffWebView getActiveWebView() {
        TabModel current = tabManager.getCurrentTab();
        return current != null ? current.getWebView() : null;
    }

    // --- Tab Management ---

    @Override
    public void onTabSwitched(TabModel tab, int index) {
        webViewContainer.removeAllViews();
        TuffWebView webView = tab.getWebView();
        if (webView.getParent() != null) {
            ((FrameLayout) webView.getParent()).removeView(webView);
        }
        webViewContainer.addView(webView);
        attachWebViewCallbacks(tab);

        if ("about:blank".equals(tab.getUrl()) || tab.getUrl().isEmpty()) {
            navigateHome();
        } else if (webView.getUrl() == null) {
            webView.loadUrl(tab.getUrl());
        }

        updateOmniboxText(tab.getUrl());
    }

    @Override
    public void onTabCountChanged(int count) {
        tvTabsCount.setText(String.valueOf(count));
    }

    private void attachWebViewCallbacks(TabModel tab) {
        TuffWebView wv = tab.getWebView();

        wv.setWebViewClient(new TuffWebClient(this, new TuffWebClient.Callback() {
            @Override
            public void onPageStarted(String url) {
                progressBar.setVisibility(View.VISIBLE);
                tab.setUrl(url);
                updateOmniboxText(url);
            }

            @Override
            public void onPageFinished(String url) {
                progressBar.setVisibility(View.GONE);
                tab.setUrl(url);
                updateOmniboxText(url);
            }

            @Override
            public void onTitleUpdated(String title) {
                tab.setTitle(title);
            }
        }));

        wv.setWebChromeClient(new TuffChromeClient(new TuffChromeClient.Callback() {
            @Override
            public void onProgressChanged(int progress) {
                progressBar.setProgress(progress);
                if (progress >= 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onReceivedTitle(String title) {
                tab.setTitle(title);
            }
        }));

        // Built-in Downloader listener
        wv.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            Toast.makeText(MainActivity.this, getString(R.string.downloading), Toast.LENGTH_SHORT).show();
            DownloadService.enqueue(MainActivity.this, url, userAgent, contentDisposition, mimetype);
        });
    }

    private void updateOmniboxText(String url) {
        if (url == null || "about:blank".equals(url)) {
            etOmniboxTop.setText("");
            etOmniboxBottom.setText("");
            return;
        }
        etOmniboxTop.setText(url);
        etOmniboxBottom.setText(url);
    }

    private void openTabSheet() {
        TabSheetDialog dialog = new TabSheetDialog(this, tabManager, new TabSheetDialog.TabActionListener() {
            @Override
            public void onTabSelected(int index) {
                tabManager.switchTab(index);
            }

            @Override
            public void onNewTabRequested() {
                tabManager.createTab("about:blank");
                navigateHome();
            }

            @Override
            public void onCloseAllTabs() {
                tabManager.closeAllTabs();
                navigateHome();
            }
        });
        dialog.show();
    }

    // --- Action Menu Sheet ---

    private void openMenuSheet() {
        BottomSheetDialog menuDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_menu, null);
        menuDialog.setContentView(view);

        TuffWebView wv = getActiveWebView();

        // New Tab
        view.findViewById(R.id.menu_item_new_tab).setOnClickListener(v -> {
            menuDialog.dismiss();
            tabManager.createTab("about:blank");
            navigateHome();
        });

        // Desktop Mode
        SwitchMaterial switchDesktop = view.findViewById(R.id.switch_desktop_mode);
        if (switchDesktop != null && wv != null) {
            switchDesktop.setChecked(wv.isDesktopMode());
            switchDesktop.setOnCheckedChangeListener((buttonView, isChecked) -> {
                wv.setDesktopMode(isChecked);
                menuDialog.dismiss();
            });
        }

        // Search Bar Position Toggle
        TextView tvPos = view.findViewById(R.id.tv_current_position);
        boolean isTop = Prefs.POSITION_TOP.equals(prefs.getSearchBarPosition());
        if (tvPos != null) {
            tvPos.setText(isTop ? R.string.position_top : R.string.position_bottom);
        }
        view.findViewById(R.id.menu_item_position).setOnClickListener(v -> {
            String newPos = isTop ? Prefs.POSITION_BOTTOM : Prefs.POSITION_TOP;
            prefs.setSearchBarPosition(newPos);
            applySearchBarPosition();
            menuDialog.dismiss();
        });

        // Zoom Controls
        TextView tvZoom = view.findViewById(R.id.tv_zoom_level);
        if (tvZoom != null && wv != null) {
            tvZoom.setText(wv.getTextZoomLevel() + "%");
        }
        view.findViewById(R.id.btn_zoom_in).setOnClickListener(v -> {
            if (wv != null) {
                wv.zoomInText();
                if (tvZoom != null) tvZoom.setText(wv.getTextZoomLevel() + "%");
            }
        });
        view.findViewById(R.id.btn_zoom_out).setOnClickListener(v -> {
            if (wv != null) {
                wv.zoomOutText();
                if (tvZoom != null) tvZoom.setText(wv.getTextZoomLevel() + "%");
            }
        });

        // Downloads
        view.findViewById(R.id.menu_item_downloads).setOnClickListener(v -> {
            menuDialog.dismiss();
            new DownloadManagerSheet(MainActivity.this).show();
        });

        menuDialog.show();
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
