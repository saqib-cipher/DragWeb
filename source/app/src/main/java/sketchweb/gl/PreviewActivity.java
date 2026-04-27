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
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
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
    private Button btnClose, btnCopy, btnSave, btnViewSource;

    private List<String> pageNames = new ArrayList<>();
    private List<String> pageCodes = new ArrayList<>();
    private int currentPageIndex = 0;
    private int currentWidth = 375;

    /** Optional project id and base asset path passed from the caller. */
    private String projectId = null;
    private String assetBasePath = null;

    /** Temp directory holding per-page HTML files for file:// loading. */
    private File tempPreviewDir = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        initViews();
        loadIntentData();
        setupWebView();
        preparePageFiles();       // Write pages to temp dir for proper file:// loading
        setupResponsiveToggle();
        setupPageTabs();
        setupButtons();
        loadCurrentPage();
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
        btnCopy            = findViewById(R.id.btnCopy);
        btnSave            = findViewById(R.id.btnSave);
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
    // Temp file preparation (enables file:// loading with relative asset paths)
    // -------------------------------------------------------------------------

    /**
     * Write every page as an HTML file into a temp directory.
     * Using file:// URLs allows WebView to resolve relative asset references
     * (e.g. src="assets/photo.jpg") against the project's asset directory.
     */
    private void preparePageFiles() {
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
     * so that relative src="assets/..." references resolve correctly.
     */
    private void copyAssetsToTempDir() {
        if (assetBasePath == null || tempPreviewDir == null) return;
        try {
            File srcAssets = new File(assetBasePath, "assets");
            if (!srcAssets.exists() || !srcAssets.isDirectory()) return;
            File destAssets = new File(tempPreviewDir, "assets");
            destAssets.mkdirs();
            copyDir(srcAssets, destAssets);
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

        // Allow file:// URLs and cross-origin file access so local assets load
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        // These are needed so pages in tempDir can load relative assets
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        webviewPreview.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Let local file:// links (page-to-page navigation) load naturally
                if (url.startsWith("file://")) return false;
                // Block external URLs from loading inside the preview
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // Sync the UI when the user navigates via the in-page nav bar
                if (url != null && url.startsWith("file://") && tempPreviewDir != null) {
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

        // Preferred path: load from temp file so assets resolve correctly
        if (tempPreviewDir != null && currentPageIndex < pageNames.size()) {
            String name = pageNames.get(currentPageIndex);
            File pageFile = new File(tempPreviewDir, sanitizeName(name) + ".html");
            if (pageFile.exists()) {
                webviewPreview.loadUrl("file://" + pageFile.getAbsolutePath());
                return;
            }
        }

        // Fallback: load raw HTML string (assets may not resolve)
        String code = getCurrentCode();
        if (code == null || code.isEmpty()) return;

        String baseUrl = (assetBasePath != null) ? "file://" + assetBasePath : null;
        webviewPreview.loadDataWithBaseURL(baseUrl, injectViewportMeta(code), "text/html", "utf-8", null);
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

        btnCopy.setOnClickListener(v -> {
            String code = getCurrentCode();
            if (code != null && !code.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("html", code));
                Toast.makeText(this, "HTML copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        btnSave.setOnClickListener(v ->
            Toast.makeText(this, "Use Export from the main editor", Toast.LENGTH_SHORT).show());

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
        String code = getCurrentCode();
        if (code == null || code.isEmpty()) {
            Toast.makeText(this, "No source code available", Toast.LENGTH_SHORT).show();
            return;
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setPadding(24, 16, 24, 16);
        TextView tvSource = new TextView(this);
        tvSource.setText(code);
        tvSource.setTextSize(11);
        tvSource.setTextIsSelectable(true);
        tvSource.setTypeface(android.graphics.Typeface.MONOSPACE);
        scrollView.addView(tvSource);

        String pageName = currentPageIndex < pageNames.size() ? pageNames.get(currentPageIndex) : "Page";
        new MaterialAlertDialogBuilder(this)
            .setTitle("Source: " + pageName)
            .setView(scrollView)
            .setPositiveButton("Copy", (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("html", code));
                Toast.makeText(this, "Source copied", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Close", null)
            .show();
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
        // Clean up temp preview files
        if (tempPreviewDir != null) {
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
