package glab.dragweb.activities;

import glab.dragweb.R;
import glab.dragweb.activities.*;
import glab.dragweb.fragments.*;
import glab.dragweb.engine.*;
import glab.dragweb.ui.*;
import glab.dragweb.logic.*;
import glab.dragweb.codegen.*;
import glab.dragweb.data.*;
import glab.dragweb.adapters.*;
import glab.dragweb.models.*;
import glab.dragweb.util.*;
import glab.dragweb.colorpicker.*;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.view.MenuItem;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PreviewActivity extends AppCompatActivity {

    private static final String TAG = "PreviewActivity";

    private WebView webviewPreview;
    private TextView tvPreviewTitle, tvDeviceInfo, tvPageCount;
    private ChipGroup chipGroupResponsive, chipGroupPages;
    private Chip chipMobile, chipTablet, chipDesktop, chipFullWidth;
    private Button btnClose, btnViewSource;
    private View layoutLoading;

    private List<String> pageNames = new ArrayList<>();
    private List<String> pageCodes = new ArrayList<>();
    private int currentPageIndex = 0;
    private int currentWidth = 375;

    /** Optional project id and base asset path passed from the caller. */
    private String projectId = null;
    private String assetBasePath = null;

    /** Directory holding generated project assets or temp fallback pages served via local HTTP. */
    private File tempPreviewDir = null;
    private boolean ownsTempDir = false;

    /** Tiny localhost HTTP server that serves files from {@link #tempPreviewDir}. */
    private LocalHttpServer localServer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_preview);

        initViews();
        loadIntentData();
        setupWebView();
        preparePageFiles();       // Write pages to temp dir for the local HTTP server
        startLocalServer();       // Serve them over http://127.0.0.1:PORT/
        setupResponsiveToggle();
        setupPageTabs();
        setupButtons();
        loadCurrentPage();

        final View topBar = findViewById(R.id.topBar);
        final int topBarInitialTop = topBar != null ? topBar.getPaddingTop() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            if (topBar != null) {
                topBar.setPadding(topBar.getPaddingLeft(), topBarInitialTop + systemBars.top, topBar.getPaddingRight(), topBar.getPaddingBottom());
            }

            // Apply left/right insets to the main root view
            v.setPadding(systemBars.left, 0, systemBars.right, 0);

            return insets;
        });
    }

    private void initViews() {
        webviewPreview     = findViewById(R.id.webviewPreview);
        tvPreviewTitle     = findViewById(R.id.tvPreviewTitle);
        tvDeviceInfo       = findViewById(R.id.tvDeviceInfo);
        tvPageCount        = findViewById(R.id.tvPageCount);
        chipGroupResponsive = findViewById(R.id.chipGroupResponsive);
        chipGroupPages     = findViewById(R.id.chipGroupPages);
        chipMobile         = findViewById(R.id.chipMobile);
        chipTablet         = findViewById(R.id.chipTablet);
        chipDesktop        = findViewById(R.id.chipDesktop);
        chipFullWidth      = findViewById(R.id.chipFullWidth);
        btnClose           = findViewById(R.id.btnClose);
        btnViewSource      = findViewById(R.id.btnViewSource);
        layoutLoading      = findViewById(R.id.layoutLoading);
    }

    private void loadIntentData() {
        if (getIntent() == null) return;

        ArrayList<String> names = getIntent().getStringArrayListExtra("page_names");
        if (names != null && !names.isEmpty()) pageNames.addAll(names);

        ArrayList<String> codes = getIntent().getStringArrayListExtra("page_codes");
        if (codes != null && !codes.isEmpty()) pageCodes.addAll(codes);

        // Fallback: single-page code
        if (pageCodes.isEmpty()) {
            String singleCode = getIntent().getStringExtra("finalCode");
            if (singleCode != null && !singleCode.isEmpty()) {
                pageCodes.add(singleCode);
                if (pageNames.isEmpty()) pageNames.add("index");
            }
        }

        int startIndex = getIntent().getIntExtra("start_page_index", 0);
        if (startIndex >= 0 && startIndex < pageNames.size()) {
            currentPageIndex = startIndex;
        }

        // Optional: project id and asset base path for resolving local resources
        projectId     = getIntent().getStringExtra("project_id");
        assetBasePath = getIntent().getStringExtra("asset_base_path");

        // Derive asset base path from projectId if not explicitly provided
        if (assetBasePath == null && projectId != null) {
            assetBasePath = FileUtil.getDragWebDir().getAbsolutePath() + "/projects/" + projectId + "/";
        }

        // Hide page tab strip when there's only one page
        View pageTabScroll = findViewById(R.id.pageTabScroll);
        if (pageNames.size() <= 1 && pageTabScroll != null) {
            pageTabScroll.setVisibility(View.GONE);
        }
    }

    // -------------------------------------------------------------------------
    // Temp file preparation (served via the local HTTP server)
    // -------------------------------------------------------------------------

    /**
     * Write every page as an HTML file into a temp directory. The directory
     * is then exposed via {@link LocalHttpServer} so the WebView loads from
     * a real {@code http://127.0.0.1:PORT/} origin, matching how a page would
     * behave on a local-host development server.
     */
    private void preparePageFiles() {
        String previewProjectDir = getIntent().getStringExtra("preview_project_dir");
        if (previewProjectDir != null && !previewProjectDir.trim().isEmpty()) {
            tempPreviewDir = new File(previewProjectDir);
            ownsTempDir = false;
            return;
        }

        if (assetBasePath != null && pageCodes.isEmpty()) {
            File assetsDir = new File(assetBasePath, "assets");
            if (assetsDir.exists() && assetsDir.isDirectory()) {
                tempPreviewDir = assetsDir;
                ownsTempDir = false;
                return;
            }
        }

        if (pageCodes.isEmpty()) return;

        tempPreviewDir = new File(getCacheDir(), "dw_preview_" + System.currentTimeMillis());
        tempPreviewDir.mkdirs();
        ownsTempDir = true;

        for (int i = 0; i < pageCodes.size(); i++) {
            String name = i < pageNames.size() ? pageNames.get(i) : "page" + i;
            writePageFile(name, pageCodes.get(i), i);
        }
    }

    private void writePageFile(String name, String htmlCode, int pageIndex) {
        String processed = injectPageNavBar(htmlCode, pageIndex);
        File file = new File(tempPreviewDir, sanitizeName(name) + ".html");
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(processed);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write page file: " + name, e);
        }
    }

    /**
     * Inject a fixed navigation bar into the page HTML so the user can
     * switch between pages directly inside the WebView (no need to go back to
     * the chip tabs for multi-page projects).
     */
    private String injectPageNavBar(String html, int currentIndex) {
        if (pageNames.size() <= 1) return html;

        StringBuilder nav = new StringBuilder();
        nav.append("<div id=\"dw-preview-nav\" style=\"")
           .append("position:fixed;top:0;left:0;right:0;")
           .append("background:#1a1a2e;padding:6px 12px;")
           .append("display:flex;gap:6px;z-index:99999;")
           .append("border-bottom:1px solid #333;overflow-x:auto;")
           .append("font-family:system-ui,sans-serif;")
           .append("\">");

        for (int i = 0; i < pageNames.size(); i++) {
            String active = (i == currentIndex)
                    ? "background:#2196F3;color:#fff;"
                    : "background:#2a2a40;color:#aaa;";
            nav.append("<a href=\"")
               .append(sanitizeName(pageNames.get(i)))
               .append(".html\" style=\"")
               .append(active)
               .append("padding:4px 12px;border-radius:4px;")
               .append("text-decoration:none;font-size:12px;white-space:nowrap;")
               .append("\">")
               .append(escapeHtml(pageNames.get(i)))
               .append("</a>");
        }
        nav.append("</div>");
        // Push body content below the nav bar
        nav.append("<div style=\"height:36px\"></div>");

        String navStr = nav.toString();
        if (html.contains("<body>")) {
            return html.replace("<body>", "<body>" + navStr);
        } else if (html.contains("<body ")) {
            return html.replaceFirst("(<body[^>]*>)", "$1" + navStr.replace("$", "\\$"));
        }
        return navStr + html;
    }

    /**
     * Start a localhost-only HTTP server rooted at {@link #tempPreviewDir} so
     * pages load directly from the project's generated assets directory over
     * {@code http://127.0.0.1:PORT/}.
     */
    private void startLocalServer() {
        if (tempPreviewDir == null) return;
        try {
            localServer = new LocalHttpServer(tempPreviewDir);

            // Expose the project's assets directory both at root and /assets/*
            // so relative (css/style.css, img.png) and assets/-prefixed paths resolve directly.
            File externalAssets = (assetBasePath != null) ? new File(assetBasePath, "assets") : null;
            File aliasAssets = (externalAssets != null && externalAssets.exists() && externalAssets.isDirectory())
                    ? externalAssets
                    : (!ownsTempDir && tempPreviewDir.exists() && tempPreviewDir.isDirectory() ? tempPreviewDir : null);
            if (aliasAssets != null) {
                localServer.addAlias("/assets", aliasAssets);
                localServer.setFallbackDir(aliasAssets);
            }

            localServer.start();
        } catch (IOException e) {
            Log.e(TAG, "Failed to start local preview server: " + e.getMessage());
            localServer = null;
        }
    }

    // -------------------------------------------------------------------------
    // WebView setup
    // -------------------------------------------------------------------------

    @SuppressWarnings("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webviewPreview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setDomStorageEnabled(true);

        // Local server uses http://127.0.0.1:PORT/, so file:// access is no
        // longer required — but harmless to allow for any leftover assets.
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webviewPreview.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                return handleUrlLoading(view, request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlLoading(view, url);
            }

            private boolean handleUrlLoading(WebView view, String url) {
                if (url == null || url.trim().isEmpty() || url.equals("#") || url.startsWith("javascript:")) {
                    return false;
                }
                if (isLocalPreviewUrl(url) || url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")) {
                    return false;
                }
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.w(TAG, "Cannot launch external URI: " + url, e);
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                LoadingOverlay.show(layoutLoading, "Loading preview...");
                // Sync the UI when the user navigates via the in-page nav bar
                if (url != null && (isLocalPreviewUrl(url) || url.startsWith("file://"))
                        && tempPreviewDir != null) {
                    for (int i = 0; i < pageNames.size(); i++) {
                        if (url.endsWith(sanitizeName(pageNames.get(i)) + ".html")) {
                            final int idx = i;
                            if (idx != currentPageIndex) {
                                currentPageIndex = idx;
                                runOnUiThread(() -> {
                                    updatePageInfo();
                                    updatePageTabUI();
                                });
                            }
                            break;
                        }
                    }
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                LoadingOverlay.hide(layoutLoading);
                // Inject responsive viewport width via JavaScript after load
                applyResponsiveViewport(view);
            }
        });
    }

    /**
     * Inject or update the viewport meta tag via JavaScript so the responsive
     * width toggle works without rewriting temp files.
     */
    private void applyResponsiveViewport(WebView view) {
        if (currentWidth <= 0) return;
        String content = "width=" + currentWidth + ", initial-scale=1.0";
        String js = "javascript:(function(){"
                + "var vp=document.querySelector('meta[name=\"viewport\"]');"
                + "if(vp){vp.setAttribute('content','" + content + "');}"
                + "else{var m=document.createElement('meta');"
                + "m.name='viewport';m.content='" + content + "';"
                + "document.head.appendChild(m);}})();";
        view.loadUrl(js);
    }

    // -------------------------------------------------------------------------
    // Page loading
    // -------------------------------------------------------------------------

    private void loadCurrentPage() {
        // Preferred path: load over the local HTTP server directly from assets/
        if (localServer != null && tempPreviewDir != null && currentPageIndex < pageNames.size()) {
            String name = pageNames.get(currentPageIndex);
            File pageFile = new File(tempPreviewDir, sanitizeName(name) + ".html");
            if (pageFile.exists()) {
                webviewPreview.loadUrl(localServer.urlFor(sanitizeName(name) + ".html"));
                return;
            }
        }

        // Fallback if the server failed to start: file:// from assets/dir
        if (tempPreviewDir != null && currentPageIndex < pageNames.size()) {
            String name = pageNames.get(currentPageIndex);
            File pageFile = new File(tempPreviewDir, sanitizeName(name) + ".html");
            if (pageFile.exists()) {
                webviewPreview.loadUrl("file://" + pageFile.getAbsolutePath());
                return;
            }
        }

        if (pageCodes.isEmpty()) {
            webviewPreview.loadData(
                "<html><body style='padding:20px;color:#666;font-family:sans-serif;'>"
                + "<p>No content to preview.</p></body></html>",
                "text/html", "utf-8");
            return;
        }

        // Final fallback: load raw HTML string
        String code = getCurrentCode();
        if (code == null || code.isEmpty()) return;

        String baseUrl = (assetBasePath != null) ? "file://" + assetBasePath : null;
        webviewPreview.loadDataWithBaseURL(baseUrl, injectViewportMeta(code), "text/html", "utf-8", null);
    }

    /** True when the URL points at our local preview server. */
    private boolean isLocalPreviewUrl(String url) {
        if (url == null || localServer == null) return false;
        return url.startsWith("http://127.0.0.1:" + localServer.getPort() + "/")
            || url.startsWith("http://localhost:" + localServer.getPort() + "/");
    }

    /**
     * Ensure a viewport meta tag exists when loading via loadDataWithBaseURL.
     */
    private String injectViewportMeta(String html) {
        if (currentWidth <= 0) return html;
        String vp = "<meta name=\"viewport\" content=\"width=" + currentWidth + ", initial-scale=1.0\">";
        if (html.contains("name=\"viewport\"")) {
            return html.replaceAll("<meta\\s+name=\"viewport\"[^>]*>", vp);
        } else if (html.contains("<head>")) {
            return html.replace("<head>", "<head>" + vp);
        }
        return html;
    }

    private String getCurrentCode() {
        if (currentPageIndex >= 0 && currentPageIndex < pageCodes.size()) {
            return pageCodes.get(currentPageIndex);
        }
        if (tempPreviewDir != null && currentPageIndex >= 0 && currentPageIndex < pageNames.size()) {
            File pageFile = new File(tempPreviewDir, sanitizeName(pageNames.get(currentPageIndex)) + ".html");
            if (pageFile.exists()) {
                return readFileContent(pageFile);
            }
        }
        return "";
    }

    // -------------------------------------------------------------------------
    // Responsive toggle
    // -------------------------------------------------------------------------

    private void setupResponsiveToggle() {
        if (chipGroupResponsive == null) return;
        chipGroupResponsive.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipMobile) {
                currentWidth = 375; tvDeviceInfo.setText("375px"); tvPreviewTitle.setText("Preview - Mobile");
            } else if (id == R.id.chipTablet) {
                currentWidth = 768; tvDeviceInfo.setText("768px"); tvPreviewTitle.setText("Preview - Tablet");
            } else if (id == R.id.chipDesktop) {
                currentWidth = 1024; tvDeviceInfo.setText("1024px"); tvPreviewTitle.setText("Preview - Desktop");
            } else if (id == R.id.chipFullWidth) {
                currentWidth = 0; tvDeviceInfo.setText("Full"); tvPreviewTitle.setText("Preview - Full Width");
            }
            // For file:// loaded pages, inject the new viewport via JS without reloading
            if (currentWidth > 0) {
                applyResponsiveViewport(webviewPreview);
            } else {
                // Full width: remove viewport constraint
                webviewPreview.loadUrl("javascript:(function(){"
                        + "var vp=document.querySelector('meta[name=\"viewport\"]');"
                        + "if(vp)vp.setAttribute('content','width=device-width, initial-scale=1.0');"
                        + "})();");
            }
        });
    }

    // -------------------------------------------------------------------------
    // Page tabs
    // -------------------------------------------------------------------------

    private void setupPageTabs() {
        if (chipGroupPages == null || pageNames.isEmpty()) return;
        chipGroupPages.removeAllViews();
        for (int i = 0; i < pageNames.size(); i++) {
            Chip chip = new Chip(this);
            chip.setText(pageNames.get(i));
            chip.setTextSize(12);
            chip.setCheckable(true);
            chip.setChecked(i == currentPageIndex);
            final int index = i;
            chip.setOnClickListener(v -> {
                currentPageIndex = index;
                loadCurrentPage();
                updatePageInfo();
            });
            chipGroupPages.addView(chip);
        }
        updatePageInfo();
    }

    /** Sync chip checked states when the user navigates via the in-page nav bar. */
    private void updatePageTabUI() {
        if (chipGroupPages == null) return;
        for (int i = 0; i < chipGroupPages.getChildCount(); i++) {
            View child = chipGroupPages.getChildAt(i);
            if (child instanceof Chip) {
                ((Chip) child).setChecked(i == currentPageIndex);
            }
        }
        updatePageInfo();
    }

    // -------------------------------------------------------------------------
    // Buttons
    // -------------------------------------------------------------------------

    private void setupButtons() {
        btnClose.setOnClickListener(v -> finish());

        btnViewSource.setOnClickListener(v -> showSourceDialog());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void updatePageInfo() {
        if (tvPageCount == null) return;
        String name = currentPageIndex < pageNames.size() ? pageNames.get(currentPageIndex) : "Unknown";
        tvPageCount.setText(name + "  –  " + (currentPageIndex + 1) + " / " + pageNames.size());
    }

    private void showSourceDialog() {
        String pageName = currentPageIndex < pageNames.size() ? pageNames.get(currentPageIndex) : "index";
        final String htmlFileName = sanitizeName(pageName) + ".html";

        String cssRel = "css/style.css";
        String jsRel = "js/script.js";
        if (projectId != null && !projectId.isEmpty()) {
            PageManager pm = new PageManager(this, projectId);
            PageManager.PageConfig cfg = pm.getPageConfig(pageName);
            cssRel = glab.dragweb.codegen.ProjectCodeGenerator.resolvePageCssFile(cfg);
            jsRel = glab.dragweb.codegen.ProjectCodeGenerator.resolvePageJsFile(cfg);
        }

        File htmlFile = tempPreviewDir != null ? new File(tempPreviewDir, htmlFileName) : null;
        File cssFile = tempPreviewDir != null ? new File(tempPreviewDir, cssRel) : null;
        File jsFile = tempPreviewDir != null ? new File(tempPreviewDir, jsRel) : null;

        if (htmlFile == null || !htmlFile.exists()) {
            String code = getCurrentCode();
            if (code == null || code.isEmpty()) {
                Toast.makeText(this, "No source code available", Toast.LENGTH_SHORT).show();
                return;
            }
            SourceCodeActivity.openSingle(this, "Source: " + pageName, null, code, "htmlmixed");
            return;
        }

        SourceCodeActivity.openWebFiles(this, "Source Code: " + pageName, htmlFile, cssFile, jsFile);
    }

    private String readFileContent(File file) {
        if (!file.exists()) return "";
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read file: " + file.getAbsolutePath(), e);
            return "";
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onBackPressed() {
        if (webviewPreview.canGoBack()) {
            webviewPreview.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (localServer != null) {
            localServer.stop();
            localServer = null;
        }
        // Clean up temp fallback directory only when owned by PreviewActivity inside cacheDir
        if (ownsTempDir && tempPreviewDir != null && tempPreviewDir.getAbsolutePath().startsWith(getCacheDir().getAbsolutePath())) {
            deleteDir(tempPreviewDir);
        }
    }

    private void deleteDir(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) deleteDir(f);
            }
        }
        dir.delete();
    }
}
