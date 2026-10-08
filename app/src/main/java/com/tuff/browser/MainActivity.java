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
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.tuff.browser.download.DownloadManagerSheet;
import com.tuff.browser.download.DownloadService;
import com.tuff.browser.download.FileUtils;
import com.tuff.browser.search.CornerEngineSwitcherDialog;
import com.tuff.browser.search.OnboardingDialog;
import com.tuff.browser.search.PermissionsOnboardingDialog;
import com.tuff.browser.search.SearchEngine;
import com.tuff.browser.search.SearchEngineManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.app.Dialog;
import android.view.Window;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
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

    private LinearLayout rootLayout;
    private LinearLayout containerOmnibar;
    private View omnibarDivider;
    private EditText etOmnibox;
    private View btnTabs;
    private TextView tvTabsCount;
    private View btnMenu;

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

        Intent intent = getIntent();
        String startUrl = engineManager.getHomeUrl();
        if (intent != null && intent.getData() != null) {
            startUrl = intent.getData().toString();
        }
        tabManager.createTab(startUrl);

        // Check first launch
        if (prefs.isFirstLaunch()) {
            showOnboarding();
        }

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

    @Override
    protected void onResume() {
        super.onResume();
        if (FileUtils.pendingInstallApk != null) {
            File apk = FileUtils.pendingInstallApk;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getPackageManager().canRequestPackageInstalls()) {
                FileUtils.pendingInstallApk = null;
                if (apk.exists()) {
                    FileUtils.openDownloadedFile(this, apk, "application/vnd.android.package-archive");
                }
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                FileUtils.pendingInstallApk = null;
                if (apk.exists()) {
                    FileUtils.openDownloadedFile(this, apk, "application/vnd.android.package-archive");
                }
            } else {
                FileUtils.pendingInstallApk = null;
            }
        }
    }

    private void initViews() {
        webViewContainer = findViewById(R.id.webview_container);
        progressBar = findViewById(R.id.progress_loading);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);

        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.surface_card));

        rootLayout = findViewById(R.id.root_layout);
        containerOmnibar = findViewById(R.id.container_omnibar);
        omnibarDivider = findViewById(R.id.omnibar_divider);
        etOmnibox = findViewById(R.id.et_omnibox);
        btnTabs = findViewById(R.id.btn_tabs);
        tvTabsCount = findViewById(R.id.tv_tabs_count);
        btnMenu = findViewById(R.id.btn_menu);

        initFindInPage();
    }

    private void setupListeners() {
        setupOmnibox(etOmnibox);
        btnTabs.setOnClickListener(v -> openTabSheet());
        btnMenu.setOnClickListener(v -> openMenuSheet());

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
        containerOmnibar.setVisibility(View.GONE);
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
        containerOmnibar.setVisibility(View.VISIBLE);
        hideKeyboard(etFindInPage);
        etFindInPage.setText("");
        tvFindMatchCount.setVisibility(View.GONE);
        TuffWebView wv = getActiveWebView();
        if (wv != null) {
            wv.clearMatches();
        }
    }

    private void setupOmnibox(EditText et) {
        et.setSelectAllOnFocus(true);
        et.setOnClickListener(v -> et.post(et::selectAll));
        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                et.post(et::selectAll);
            }
        });
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
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.surface_card));
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
        }
    }

    private void applySearchBarPosition() {
        boolean isTop = Prefs.POSITION_TOP.equals(prefs.getSearchBarPosition());
        rootLayout.removeView(containerOmnibar);
        if (isTop) {
            rootLayout.addView(containerOmnibar, 0);
            if (omnibarDivider != null) omnibarDivider.setVisibility(View.GONE);
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.surface_card));
        } else {
            rootLayout.addView(containerOmnibar);
            if (omnibarDivider != null) omnibarDivider.setVisibility(View.VISIBLE);
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_dark));
        }
    }

    private EditText getActiveOmnibox() {
        return etOmnibox;
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
        if (tvTabsCount != null) tvTabsCount.setText(String.valueOf(count));
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
                progressBar.setVisibility(View.INVISIBLE);
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
                    progressBar.setVisibility(View.INVISIBLE);
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
        if (etOmnibox != null) {
            etOmnibox.setText((url == null || "about:blank".equals(url)) ? "" : url);
        }
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

        // Quick Navigation Actions in Top Bar
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

        ImageButton btnZoomOut = view.findViewById(R.id.menu_quick_zoom_out);
        if (btnZoomOut != null) {
            btnZoomOut.setOnClickListener(v -> {
                if (wv != null) {
                    wv.zoomOutText();
                    showZoomToast(wv.getTextZoomLevel());
                }
            });
        }

        ImageButton btnZoomIn = view.findViewById(R.id.menu_quick_zoom_in);
        if (btnZoomIn != null) {
            btnZoomIn.setOnClickListener(v -> {
                if (wv != null) {
                    wv.zoomInText();
                    showZoomToast(wv.getTextZoomLevel());
                }
            });
        }

        ImageButton btnDesktop = view.findViewById(R.id.menu_quick_desktop);
        if (btnDesktop != null && wv != null) {
            updateDesktopHighlight(btnDesktop, wv.isDesktopMode());
            btnDesktop.setOnClickListener(v -> {
                boolean newState = !wv.isDesktopMode();
                wv.setDesktopMode(newState);
                updateDesktopHighlight(btnDesktop, newState);
            });
        }

        ImageButton btnDownloadPage = view.findViewById(R.id.menu_quick_download_page);
        if (btnDownloadPage != null) {
            btnDownloadPage.setOnClickListener(v -> {
                menuDialog.dismiss();
                downloadCurrentPage();
            });
        }

        ImageButton btnFire = view.findViewById(R.id.menu_quick_fire);
        if (btnFire != null) {
            btnFire.setOnClickListener(v -> {
                menuDialog.dismiss();
                showClearDataDialog();
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

        // History
        View itemHistory = view.findViewById(R.id.menu_item_history);
        if (itemHistory != null) {
            itemHistory.setOnClickListener(v -> {
                menuDialog.dismiss();
                showHistoryDialog();
            });
        }

        // Downloads
        view.findViewById(R.id.menu_item_downloads).setOnClickListener(v -> {
            menuDialog.dismiss();
            new DownloadManagerSheet(MainActivity.this).show();
        });

        // About
        View itemAbout = view.findViewById(R.id.menu_item_about);
        if (itemAbout != null) {
            itemAbout.setOnClickListener(v -> {
                menuDialog.dismiss();
                showAboutDialog();
            });
        }

        menuDialog.show();
    }

    private void showAboutDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_about);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        View btnDev = dialog.findViewById(R.id.btn_about_dev);
        if (btnDev != null) {
            btnDev.setOnClickListener(v -> {
                dialog.dismiss();
                loadQueryOrUrl("https://github.com/amintum");
            });
        }

        View btnRepo = dialog.findViewById(R.id.btn_about_repo);
        if (btnRepo != null) {
            btnRepo.setOnClickListener(v -> {
                dialog.dismiss();
                loadQueryOrUrl("https://github.com/amintum/Tuff-Browser");
            });
        }

        View btnClose = dialog.findViewById(R.id.btn_about_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private Toast currentZoomToast;

    private void showZoomToast(int zoomLevel) {
        if (currentZoomToast != null) {
            currentZoomToast.cancel();
        }
        currentZoomToast = Toast.makeText(this, zoomLevel + "% zoom", Toast.LENGTH_SHORT);
        currentZoomToast.show();
    }

    private void updateDesktopHighlight(ImageButton btn, boolean isDesktop) {
        if (btn == null) return;
        if (isDesktop) {
            btn.setBackgroundResource(R.drawable.bg_active_pill);
            btn.setColorFilter(0xFF4DA3FF);
        } else {
            btn.setBackgroundResource(android.R.color.transparent);
            btn.setColorFilter(0xFFB0B0B0);
        }
    }

    private void downloadCurrentPage() {
        TuffWebView wv = getActiveWebView();
        if (wv != null) {
            String currentUrl = wv.getUrl();
            if (currentUrl != null && !currentUrl.isEmpty() && !"about:blank".equals(currentUrl)) {
                String title = wv.getTitle();
                if (title == null || title.trim().isEmpty()) title = "webpage";
                String safeName = title.replaceAll("[^a-zA-Z0-9_.-]", "_");
                if (!safeName.endsWith(".html") && !safeName.endsWith(".htm")) safeName += ".html";
                startDownloadWithPermissionCheck(currentUrl, wv.getSettings().getUserAgentString(), "attachment; filename=\"" + safeName + "\"", "text/html");
            } else {
                Toast.makeText(this, "No page to download", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showHistoryDialog() {
        TuffWebView wv = getActiveWebView();
        if (wv == null) return;

        BottomSheetDialog historyDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_history, null);
        historyDialog.setContentView(view);

        android.webkit.WebBackForwardList historyList = wv.copyBackForwardList();
        int count = historyList != null ? historyList.getSize() : 0;

        TextView tvNoHistory = view.findViewById(R.id.tv_no_history);
        androidx.recyclerview.widget.RecyclerView rvHistory = view.findViewById(R.id.rv_history);
        View btnClear = view.findViewById(R.id.btn_clear_history);

        if (count == 0) {
            tvNoHistory.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
        } else {
            tvNoHistory.setVisibility(View.GONE);
            rvHistory.setVisibility(View.VISIBLE);
            rvHistory.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));

            List<android.webkit.WebHistoryItem> items = new ArrayList<>();
            for (int i = count - 1; i >= 0; i--) {
                android.webkit.WebHistoryItem item = historyList.getItemAtIndex(i);
                if (item != null && item.getUrl() != null && !item.getUrl().isEmpty()) {
                    items.add(item);
                }
            }

            rvHistory.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                @NonNull
                @Override
                public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
                    View itemV = getLayoutInflater().inflate(R.layout.item_history, parent, false);
                    return new androidx.recyclerview.widget.RecyclerView.ViewHolder(itemV) {};
                }

                @Override
                public void onBindViewHolder(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                    android.webkit.WebHistoryItem item = items.get(position);
                    TextView title = holder.itemView.findViewById(R.id.tv_history_title);
                    TextView url = holder.itemView.findViewById(R.id.tv_history_url);
                    String itemTitle = item.getTitle();
                    title.setText((itemTitle != null && !itemTitle.isEmpty()) ? itemTitle : item.getUrl());
                    url.setText(item.getUrl());
                    holder.itemView.setOnClickListener(v -> {
                        historyDialog.dismiss();
                        wv.loadUrl(item.getUrl());
                    });
                }

                @Override
                public int getItemCount() {
                    return items.size();
                }
            });
        }

        btnClear.setOnClickListener(v -> {
            wv.clearHistory();
            historyDialog.dismiss();
            Toast.makeText(MainActivity.this, "History cleared", Toast.LENGTH_SHORT).show();
        });

        historyDialog.show();
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showClearDataDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_fire_confirm, null);
        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btn_fire_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_fire_confirm).setOnClickListener(v -> {
            dialog.dismiss();
            clearAllBrowserData();
        });

        dialog.show();
    }

    private void clearAllBrowserData() {
        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
        cookieManager.flush();

        android.webkit.WebStorage.getInstance().deleteAllData();
        android.webkit.GeolocationPermissions.getInstance().clearAll();

        for (com.tuff.browser.tab.TabModel tab : tabManager.getTabs()) {
            if (tab.getWebView() != null) {
                tab.getWebView().clearCache(true);
                tab.getWebView().clearHistory();
                tab.getWebView().clearFormData();
                tab.getWebView().clearSslPreferences();
            }
        }

        tabManager.closeAllTabs();
        navigateHome();
        updateOmniboxText("");

        try {
            clearDirectory(getCacheDir());
            clearDirectory(getCodeCacheDir());
            java.io.File extCache = getExternalCacheDir();
            if (extCache != null) clearDirectory(extCache);
        } catch (Throwable ignored) {}

        Toast.makeText(this, "All browsing data cleared", Toast.LENGTH_SHORT).show();
    }

    private void clearDirectory(java.io.File dir) {
        if (dir != null && dir.isDirectory()) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    if (f.isDirectory()) clearDirectory(f);
                    f.delete();
                }
            }
        }
    }
}
