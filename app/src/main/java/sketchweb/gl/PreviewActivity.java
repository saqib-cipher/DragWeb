package sketchweb.gl;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceResponse;
import android.net.Uri;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import androidx.webkit.WebViewAssetLoader;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    private List<String> pageNames = new ArrayList<>();
    private List<String> pageCodes = new ArrayList<>();
    private int currentPageIndex = 0;
    private int currentWidth = 375;

    /** Optional project id and base asset path passed from the caller. */
    private String projectId = null;
    private String assetBasePath = null;

    /** Base URL for virtual preview origin */
    private static final String PREVIEW_BASE_URL = "https://preview.local/";

    /** Temp directory holding per-page HTML files served via local HTTP. */
    private File tempPreviewDir = null;

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
        // startLocalServer();       // Server socket no longer used, handled via shouldInterceptRequest
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
            assetBasePath = Environment.getExternalStorageDirectory().getAbsolutePath()
                    + "/.dragweb/projects/" + projectId + "/";
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
        if (previewProjectDir != null) {
            tempPreviewDir = new File(previewProjectDir);
            pageCodes.clear();
            for (String name : pageNames) {
                File file = new File(tempPreviewDir, sanitizeName(name) + ".html");
                if (file.exists()) {
                    pageCodes.add(readFileContent(file));
                } else {
                    pageCodes.add("<html><body><p>Page " + name + " not found.</p></body></html>");
                }
            }
            return;
        }

        if (pageCodes.isEmpty()) return;

        tempPreviewDir = new File(getCacheDir(), "dw_preview_" + System.currentTimeMillis());
        tempPreviewDir.mkdirs();

        // Copy project assets into the temp dir so relative paths work
        copyAssetsToTempDir();

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
     * Copy project assets from external storage into the temp preview directory
     * so that relative references resolve correctly.
     */
    private void copyAssetsToTempDir() {
        if (assetBasePath == null || tempPreviewDir == null) return;
        try {
            File srcAssets = new File(assetBasePath, "assets");
            if (!srcAssets.exists() || !srcAssets.isDirectory()) return;

            File[] files = srcAssets.listFiles();
            if (files == null) return;
            for (File file : files) {
                if (file.isDirectory()) {
                    File target = new File(tempPreviewDir, file.getName());
                    target.mkdirs();
                    copyDir(file, target);
                } else {
                    copyFile(file, new File(tempPreviewDir, file.getName()));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not copy assets to temp dir: " + e.getMessage());
        }
    }

    private void copyDir(File src, File dest) throws IOException {
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            File target = new File(dest, f.getName());
            if (f.isDirectory()) {
                target.mkdirs();
                copyDir(f, target);
            } else {
                copyFile(f, target);
            }
        }
    }

    private void copyFile(File src, File dest) throws IOException {
        byte[] buf = new byte[4096];
        try (java.io.FileInputStream in = new java.io.FileInputStream(src);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    /**
     * Start a localhost-only HTTP server rooted at {@link #tempPreviewDir} so
     * pages can be loaded over {@code http://127.0.0.1:PORT/} instead of
     * {@code file://}. This matches a normal local-host preview environment
     * and unlocks browser features that require an http origin.
     */
    private void startLocalServer() {
        if (tempPreviewDir == null) return;
        try {
            localServer = new LocalHttpServer(tempPreviewDir);

            // Expose the project's external assets directory at /assets/* so
            // generated <img src="assets/foo.png"> tags resolve even if the
            // copy-to-temp step couldn't run (no project_id) or hasn't
            // finished. Aliases are sandboxed to the target dir.
            if (assetBasePath != null) {
                File externalAssets = new File(assetBasePath, "assets");
                if (externalAssets.exists() && externalAssets.isDirectory()) {
                    localServer.addAlias("/assets", externalAssets);
                    localServer.setFallbackDir(externalAssets);
                }
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

        // Disable cache to ensure live edits apply instantly
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // Local server uses http://127.0.0.1:PORT/, so file:// access is no
        // longer required — but harmless to allow for any leftover assets.
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .setDomain("preview.local")
                .addPathHandler("/", new WebViewAssetLoader.PathHandler() {
                    @Nullable
                    @Override
                    public WebResourceResponse handle(@NonNull String path) {
                        if (tempPreviewDir == null) return null;
                        if (path.startsWith("/")) path = path.substring(1);
                        if (path.isEmpty()) path = "index.html";

                        File file = new File(tempPreviewDir, path);
                        if (!file.exists()) {
                            if (path.startsWith("assets/")) {
                                file = new File(tempPreviewDir, path.substring(7));
                            }
                        }

                        if (file.exists() && file.isFile()) {
                            try {
                                String mimeType = mimeFor(file.getName());
                                String encoding = encodingFor(mimeType);
                                java.io.InputStream data = new java.io.FileInputStream(file);
                                return new WebResourceResponse(mimeType, encoding, data);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to resolve file via AssetLoader: " + path, e);
                            }
                        }
                        return null;
                    }
                })
                .build();

        webviewPreview.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "Console: " + consoleMessage.message() + " -- Line "
                        + consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
                return true;
            }
        });

        webviewPreview.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Let in-app links (page-to-page navigation served by the
                // local HTTP server) load naturally.
                if (isLocalPreviewUrl(url)) return false;
                if (url.startsWith("file://")) return false;
                // Block external URLs from loading inside the preview
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse response = assetLoader.shouldInterceptRequest(request.getUrl());
                if (response != null) return response;
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
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
        if (pageCodes.isEmpty()) {
            webviewPreview.loadData(
                "<html><body style='padding:20px;color:#666;font-family:sans-serif;'>"
                + "<p>No content to preview.</p></body></html>",
                "text/html", "utf-8");
            return;
        }

        if (tempPreviewDir != null && currentPageIndex < pageNames.size()) {
            String name = pageNames.get(currentPageIndex);
            File pageFile = new File(tempPreviewDir, sanitizeName(name) + ".html");
            if (pageFile.exists()) {
                webviewPreview.loadUrl(PREVIEW_BASE_URL + sanitizeName(name) + ".html");
                return;
            }
        }

        // Final fallback: load raw HTML string (assets may not resolve).
        String code = getCurrentCode();
        if (code == null || code.isEmpty()) return;

        String baseUrl = (assetBasePath != null) ? "file://" + assetBasePath : null;
        webviewPreview.loadDataWithBaseURL(baseUrl, injectViewportMeta(code), "text/html", "utf-8", null);
    }

    /** True when the URL points at our local preview server. */
    private boolean isLocalPreviewUrl(String url) {
        return url != null && url.startsWith(PREVIEW_BASE_URL);
    }

    private String mimeFor(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String ext = name.substring(dot + 1).toLowerCase();
        switch (ext) {
            case "html": case "htm": return "text/html";
            case "css": return "text/css";
            case "js": case "mjs": return "application/javascript";
            case "json": return "application/json";
            case "svg": return "image/svg+xml";
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "ico": return "image/x-icon";
            case "ttf": return "font/ttf";
            case "otf": return "font/otf";
            case "woff": return "font/woff";
            case "woff2": return "font/woff2";
            default: return "application/octet-stream";
        }
    }

    private String encodingFor(String mimeType) {
        if (mimeType != null && (mimeType.startsWith("text/") || mimeType.equals("application/javascript") || mimeType.equals("application/json"))) {
            return "UTF-8";
        }
        return null;
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

        File htmlFile = tempPreviewDir != null ? new File(tempPreviewDir, htmlFileName) : null;
        File cssFile = tempPreviewDir != null ? new File(new File(tempPreviewDir, "css"), "style.css") : null;
        File jsFile = tempPreviewDir != null ? new File(new File(tempPreviewDir, "js"), "script.js") : null;

        if (htmlFile == null || !htmlFile.exists()) {
            // Fallback to old behavior if preview folder is not initialized
            String code = getCurrentCode();
            if (code == null || code.isEmpty()) {
                Toast.makeText(this, "No source code available", Toast.LENGTH_SHORT).show();
                return;
            }
            showSimpleSourceDialog("Source: " + pageName, code, "html");
            return;
        }

        final String htmlCode = readFileContent(htmlFile);
        final String cssCode = cssFile.exists() ? readFileContent(cssFile) : "";
        final String jsCode = jsFile.exists() ? readFileContent(jsFile) : "";

        // Setup container layout
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);

        Toolbar dialogToolbar = new Toolbar(this);
        dialogToolbar.setTitle("Source Code Viewer");
        dialogToolbar.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFFE0E0E0));
        dialogToolbar.setTitleTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
        
        container.addView(dialogToolbar);

        final WebView webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        
        android.widget.LinearLayout.LayoutParams webViewParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(400));
        webView.setLayoutParams(webViewParams);
        container.addView(webView);

        // Keep track of active section for copy
        final String[] activeCode = { htmlCode };
        final String[] activeLang = { "html" };

        // Set up menu inside toolbar: 1=HTML, 2=CSS, 3=JS
        MenuItem itemHtml = dialogToolbar.getMenu().add(0, 1, 0, "HTML");
        itemHtml.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        MenuItem itemCss = dialogToolbar.getMenu().add(0, 2, 0, "CSS");
        itemCss.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        MenuItem itemJs = dialogToolbar.getMenu().add(0, 3, 0, "JS");
        itemJs.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        // Initial highlights
        updateToolbarMenu(dialogToolbar, 1);
        loadHighlightedCode(webView, htmlCode.isEmpty() ? "<!-- No HTML content -->" : htmlCode, "html");

        dialogToolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) { // HTML
                updateToolbarMenu(dialogToolbar, 1);
                activeCode[0] = htmlCode;
                activeLang[0] = "html";
                loadHighlightedCode(webView, htmlCode.isEmpty() ? "<!-- No HTML content -->" : htmlCode, "html");
                return true;
            } else if (id == 2) { // CSS
                updateToolbarMenu(dialogToolbar, 2);
                activeCode[0] = cssCode;
                activeLang[0] = "css";
                loadHighlightedCode(webView, cssCode.isEmpty() ? "/* No CSS generated */" : cssCode, "css");
                return true;
            } else if (id == 3) { // JS
                updateToolbarMenu(dialogToolbar, 3);
                activeCode[0] = jsCode;
                activeLang[0] = "javascript";
                loadHighlightedCode(webView, jsCode.isEmpty() ? "/* No Javascript generated */" : jsCode, "javascript");
                return true;
            }
            return false;
        });

        new MaterialAlertDialogBuilder(this)
            .setView(container)
            .setPositiveButton("Copy", (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText(activeLang[0], activeCode[0]));
                Toast.makeText(this, "Source copied", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private void updateToolbarMenu(Toolbar toolbar, int selectedTab) {
        MenuItem itemHtml = toolbar.getMenu().findItem(1);
        MenuItem itemCss = toolbar.getMenu().findItem(2);
        MenuItem itemJs = toolbar.getMenu().findItem(3);
        if (itemHtml != null && itemCss != null && itemJs != null) {
            if (selectedTab == 1) { // HTML
                itemHtml.setTitle(android.text.Html.fromHtml("<b><font color='#2196F3'>HTML</font></b>", android.text.Html.FROM_HTML_MODE_LEGACY));
                itemCss.setTitle(android.text.Html.fromHtml("<font color='#888888'>CSS</font>", android.text.Html.FROM_HTML_MODE_LEGACY));
                itemJs.setTitle(android.text.Html.fromHtml("<font color='#888888'>JS</font>", android.text.Html.FROM_HTML_MODE_LEGACY));
            } else if (selectedTab == 2) { // CSS
                itemHtml.setTitle(android.text.Html.fromHtml("<font color='#888888'>HTML</font>", android.text.Html.FROM_HTML_MODE_LEGACY));
                itemCss.setTitle(android.text.Html.fromHtml("<b><font color='#2196F3'>CSS</font></b>", android.text.Html.FROM_HTML_MODE_LEGACY));
                itemJs.setTitle(android.text.Html.fromHtml("<font color='#888888'>JS</font>", android.text.Html.FROM_HTML_MODE_LEGACY));
            } else { // JS
                itemHtml.setTitle(android.text.Html.fromHtml("<font color='#888888'>HTML</font>", android.text.Html.FROM_HTML_MODE_LEGACY));
                itemCss.setTitle(android.text.Html.fromHtml("<font color='#888888'>CSS</font>", android.text.Html.FROM_HTML_MODE_LEGACY));
                itemJs.setTitle(android.text.Html.fromHtml("<b><font color='#2196F3'>JS</font></b>", android.text.Html.FROM_HTML_MODE_LEGACY));
            }
        }
    }

    private void loadHighlightedCode(WebView webView, String code, String language) {
        String escapedCode = escapeHtml(code);
        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism-tomorrow.min.css\">\n" +
                "  <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/line-numbers/prism-line-numbers.min.css\">\n" +
                "  <style>\n" +
                "    body { margin: 0; padding: 12px; background: #2d2d2d; font-family: monospace; font-size: 13px; }\n" +
                "    pre { margin: 0; white-space: pre-wrap; word-wrap: break-word; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <pre class=\"line-numbers\"><code class=\"language-" + language + "\">" + escapedCode + "</code></pre>\n" +
                "  <script src=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-core.min.js\"></script>\n" +
                "  <script src=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/autoloader/prism-autoloader.min.js\"></script>\n" +
                "  <script src=\"https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/line-numbers/prism-line-numbers.min.js\"></script>\n" +
                "</body>\n" +
                "</html>";
        webView.loadDataWithBaseURL("https://localhost", html, "text/html", "UTF-8", null);
    }

    private void showSimpleSourceDialog(String title, String code, String label) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16));
        TextView tvSource = new TextView(this);
        tvSource.setText(code);
        tvSource.setTextSize(11);
        tvSource.setTextIsSelectable(true);
        tvSource.setTypeface(android.graphics.Typeface.MONOSPACE);
        scrollView.addView(tvSource);

        new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("Copy", (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText(label, code));
                Toast.makeText(this, "Source copied", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Close", null)
            .show();
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
        // Clean up temp preview files safely only if it lies inside the cache directory
        if (tempPreviewDir != null && tempPreviewDir.getAbsolutePath().startsWith(getCacheDir().getAbsolutePath())) {
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
