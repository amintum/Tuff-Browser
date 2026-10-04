package com.tuff.browser;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tuff.browser.download.DownloadManagerSheet;
import com.tuff.browser.download.DownloadService;
import com.tuff.browser.search.CornerEngineSwitcherDialog;
import com.tuff.browser.search.OnboardingDialog;
import com.tuff.browser.search.PermissionsOnboardingDialog;
import com.tuff.browser.search.SearchEngine;
import com.tuff.browser.search.SearchEngineManager;

import java.util.ArrayList;
import java.util.List;
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
    private SwipeRefreshLayout swipeRefreshLayout;

    private LinearLayout containerTopBar;
    private LinearLayout containerBottomBar;

    private EditText etOmniboxTop;
    private EditText etOmniboxBottom;

    private View btnTabsTop;
    private View btnTabsBottom;
    private TextView tvTabsCountTop;
    private TextView tvTabsCountBottom;
    private View btnMenuTop;
    private View btnMenuBottom;

    // In-page search bar
    private LinearLayout containerFindInPage;
    private EditText etFindInPage;
    private TextView tvFindMatchCount;
    private ImageView btnFindPrev;
    private ImageView btnFindNext;
    private ImageView btnFindClose;

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
        setupSystemBars();
        applySearchBarPosition();
        checkNetworkStatus();

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

        if (intent != null && intent.getBooleanExtra("open_downloads", false)) {
            new DownloadManagerSheet(this).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("open_downloads", false)) {
            new DownloadManagerSheet(this).show();
        }
    }

    private void initViews() {
        webViewContainer = findViewById(R.id.webview_container);
        progressBar = findViewById(R.id.progress_loading);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);

        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.surface_card));

        containerTopBar = findViewById(R.id.container_top_bar);
        containerBottomBar = findViewById(R.id.container_bottom_bar);

        etOmniboxTop = findViewById(R.id.et_omnibox_top);
        etOmniboxBottom = findViewById(R.id.et_omnibox_bottom);

        btnTabsTop = findViewById(R.id.btn_tabs_top);
        btnTabsBottom = findViewById(R.id.btn_tabs_bottom);
        tvTabsCountTop = findViewById(R.id.tv_tabs_count_top);
        tvTabsCountBottom = findViewById(R.id.tv_tabs_count_bottom);
        btnMenuTop = findViewById(R.id.btn_menu_top);
        btnMenuBottom = findViewById(R.id.btn_menu_bottom);

        initFindInPage();
    }

    private void setupListeners() {
        // Omnibox actions
        setupOmnibox(etOmniboxTop);
        setupOmnibox(etOmniboxBottom);

        // Tab sheet action
        View.OnClickListener tabsListener = v -> openTabSheet();
        btnTabsTop.setOnClickListener(tabsListener);
        btnTabsBottom.setOnClickListener(tabsListener);

        // Menu sheet action
        View.OnClickListener menuListener = v -> openMenuSheet();
        btnMenuTop.setOnClickListener(menuListener);
        btnMenuBottom.setOnClickListener(menuListener);

        // Swipe-down-to-refresh with circular indicator
        swipeRefreshLayout.setOnRefreshListener(() -> {
            TuffWebView wv = getActiveWebView();
            if (wv != null) {
                wv.reload();
            } else {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> {
            TuffWebView wv = getActiveWebView();
            return wv != null && wv.getScrollY() > 0;
        });
    }

    private void initFindInPage() {
        containerFindInPage = findViewById(R.id.container_find_in_page);
        etFindInPage = findViewById(R.id.et_find_in_page);
        tvFindMatchCount = findViewById(R.id.tv_find_match_count);
        btnFindPrev = findViewById(R.id.btn_find_prev);
        btnFindNext = findViewById(R.id.btn_find_next);
        btnFindClose = findViewById(R.id.btn_find_close);

        etFindInPage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                TuffWebView wv = getActiveWebView();
                if (wv == null) return;
                String query = s.toString();
                if (query.trim().isEmpty()) {
                    wv.clearMatches();
                    tvFindMatchCount.setVisibility(View.GONE);
                } else {
                    wv.findAllAsync(query);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnFindPrev.setOnClickListener(v -> {
            TuffWebView wv = getActiveWebView();
            if (wv != null) wv.findNext(false);
        });

        btnFindNext.setOnClickListener(v -> {
            TuffWebView wv = getActiveWebView();
            if (wv != null) wv.findNext(true);
        });

        btnFindClose.setOnClickListener(v -> hideFindInPage());
    }

    private void showFindInPage() {
        containerTopBar.setVisibility(View.GONE);
        containerBottomBar.setVisibility(View.GONE);
        containerFindInPage.setVisibility(View.VISIBLE);

        etFindInPage.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etFindInPage, InputMethodManager.SHOW_IMPLICIT);
        }

        TuffWebView wv = getActiveWebView();
        if (wv != null) {
            wv.setFindListener((activeMatchOrdinal, numberOfMatches, isDoneCounting) -> {
                if (numberOfMatches == 0) {
                    tvFindMatchCount.setText("0/0");
                    tvFindMatchCount.setVisibility(View.VISIBLE);
                } else {
                    tvFindMatchCount.setText((activeMatchOrdinal + 1) + "/" + numberOfMatches);
                    tvFindMatchCount.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private void hideFindInPage() {
        containerFindInPage.setVisibility(View.GONE);
        hideKeyboard(etFindInPage);
        etFindInPage.setText("");
        tvFindMatchCount.setVisibility(View.GONE);
        TuffWebView wv = getActiveWebView();
        if (wv != null) {
            wv.clearMatches();
        }
        applySearchBarPosition();
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
        if (containerFindInPage != null && containerFindInPage.getVisibility() == View.VISIBLE) {
            hideFindInPage();
            return;
        }
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

    private static final int REQUEST_CODE_ESSENTIAL_PERMISSIONS = 102;

    private void showOnboarding() {
        OnboardingDialog dialog = new OnboardingDialog(this, engineManager, prefs, engine -> {
            showPermissionsOnboarding();
        });
        dialog.show();
    }

    private void showPermissionsOnboarding() {
        PermissionsOnboardingDialog dialog = new PermissionsOnboardingDialog(this, new PermissionsOnboardingDialog.OnPermissionRequestListener() {
            @Override
            public void onRequestPermissions() {
                requestEssentialPermissions();
                navigateHome();
            }

            @Override
            public void onDismissed() {
                navigateHome();
            }
        });
        dialog.show();
    }

    private void requestEssentialPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!perms.isEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), REQUEST_CODE_ESSENTIAL_PERMISSIONS);
        }
    }

    private void checkNetworkStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            Network active = cm.getActiveNetwork();
            NetworkCapabilities caps = (active != null) ? cm.getNetworkCapabilities(active) : null;
            boolean connected = (caps != null) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            if (!connected) {
                View root = findViewById(R.id.root_layout);
                if (root != null) {
                    Snackbar snackbar = Snackbar.make(root, "Internet connection is restricted or unavailable.", Snackbar.LENGTH_LONG);
                    snackbar.setAction("Settings", v -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception ignored) {
                            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                        }
                    });
                    snackbar.setActionTextColor(ContextCompat.getColor(this, R.color.accent));
                    snackbar.show();
                }
            }
        }
    }

    private void openCornerEngineSwitcher() {
        CornerEngineSwitcherDialog dialog = new CornerEngineSwitcherDialog(this, engineManager, newEngine -> {
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

    private void setupSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.surface_card));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        }
    }

    private void applySearchBarPosition() {
        boolean isTop = Prefs.POSITION_TOP.equals(prefs.getSearchBarPosition());
        if (isTop) {
            containerTopBar.setVisibility(View.VISIBLE);
            containerBottomBar.setVisibility(View.GONE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.surface_card));
            }
        } else {
            containerTopBar.setVisibility(View.GONE);
            containerBottomBar.setVisibility(View.VISIBLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
            }
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
        String countStr = String.valueOf(count);
        if (tvTabsCountTop != null) tvTabsCountTop.setText(countStr);
        if (tvTabsCountBottom != null) tvTabsCountBottom.setText(countStr);
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
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
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
            startDownloadWithPermissionCheck(url, userAgent, contentDisposition, mimetype);
        });
    }

    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 101;
    private String[] pendingDownloadArgs = null;

    private void startDownloadWithPermissionCheck(String url, String userAgent, String contentDisposition, String mimeType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                pendingDownloadArgs = new String[]{url, userAgent, contentDisposition, mimeType};
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
                return;
            }
        }
        enqueueDownloadWithFeedback(url, userAgent, contentDisposition, mimeType);
    }

    private void enqueueDownloadWithFeedback(String url, String userAgent, String contentDisposition, String mimeType) {
        DownloadService.enqueue(MainActivity.this, url, userAgent, contentDisposition, mimeType);
        showDownloadStartedSnackbar();
    }

    private void showDownloadStartedSnackbar() {
        View root = findViewById(R.id.root_layout);
        if (root != null) {
            Snackbar snackbar = Snackbar.make(root, R.string.download_started, Snackbar.LENGTH_LONG);
            snackbar.setAction(R.string.see_details, v -> {
                new DownloadManagerSheet(MainActivity.this).show();
            });
            snackbar.setActionTextColor(ContextCompat.getColor(MainActivity.this, R.color.accent));
            snackbar.show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            if (pendingDownloadArgs != null) {
                enqueueDownloadWithFeedback(pendingDownloadArgs[0], pendingDownloadArgs[1], pendingDownloadArgs[2], pendingDownloadArgs[3]);
                pendingDownloadArgs = null;
            }
        }
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

        // Quick Navigation Actions (Back, Forward, Refresh, Find in Page)
        ImageButton btnBack = view.findViewById(R.id.menu_quick_back);
        if (btnBack != null) {
            boolean canBack = wv != null && wv.canGoBack();
            btnBack.setEnabled(canBack);
            btnBack.setAlpha(canBack ? 1.0f : 0.4f);
            btnBack.setOnClickListener(v -> {
                if (wv != null && wv.canGoBack()) {
                    wv.goBack();
                }
                menuDialog.dismiss();
            });
        }

        ImageButton btnFwd = view.findViewById(R.id.menu_quick_forward);
        if (btnFwd != null) {
            boolean canFwd = wv != null && wv.canGoForward();
            btnFwd.setEnabled(canFwd);
            btnFwd.setAlpha(canFwd ? 1.0f : 0.4f);
            btnFwd.setOnClickListener(v -> {
                if (wv != null && wv.canGoForward()) {
                    wv.goForward();
                }
                menuDialog.dismiss();
            });
        }

        ImageButton btnRefresh = view.findViewById(R.id.menu_quick_refresh);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                menuDialog.dismiss();
                if (wv != null) wv.reload();
            });
        }

        ImageButton btnFindInPage = view.findViewById(R.id.menu_quick_find_in_page);
        if (btnFindInPage != null) {
            btnFindInPage.setOnClickListener(v -> {
                menuDialog.dismiss();
                showFindInPage();
            });
        }

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

        // Search Engine Selection
        View itemEngine = view.findViewById(R.id.menu_item_search_engine);
        TextView tvEngine = view.findViewById(R.id.tv_current_engine);
        if (tvEngine != null) {
            tvEngine.setText(engineManager.getActiveEngine().getName());
        }
        if (itemEngine != null) {
            itemEngine.setOnClickListener(v -> {
                menuDialog.dismiss();
                openCornerEngineSwitcher();
            });
        }

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
