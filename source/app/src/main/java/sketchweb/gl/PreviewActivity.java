package sketchweb.gl;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
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

import java.util.ArrayList;
import java.util.List;

public class PreviewActivity extends AppCompatActivity {

    private WebView webviewPreview;
    private TextView tvPreviewTitle, tvDeviceInfo, tvPageCount;
    private ChipGroup chipGroupResponsive, chipGroupPages;
    private Chip chipMobile, chipTablet, chipDesktop, chipFullWidth;
    private Button btnClose, btnCopy, btnSave, btnViewSource;

    private List<String> pageNames = new ArrayList<>();
    private List<String> pageCodes = new ArrayList<>();
    private int currentPageIndex = 0;
    private int currentWidth = 375;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        initViews();
        loadIntentData();
        setupWebView();
        setupResponsiveToggle();
        setupPageTabs();
        setupButtons();
        loadCurrentPage();
    }

    private void initViews() {
        webviewPreview = findViewById(R.id.webviewPreview);
        tvPreviewTitle = findViewById(R.id.tvPreviewTitle);
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo);
        tvPageCount = findViewById(R.id.tvPageCount);
        chipGroupResponsive = findViewById(R.id.chipGroupResponsive);
        chipGroupPages = findViewById(R.id.chipGroupPages);
        chipMobile = findViewById(R.id.chipMobile);
        chipTablet = findViewById(R.id.chipTablet);
        chipDesktop = findViewById(R.id.chipDesktop);
        chipFullWidth = findViewById(R.id.chipFullWidth);
        btnClose = findViewById(R.id.btnClose);
        btnCopy = findViewById(R.id.btnCopy);
        btnSave = findViewById(R.id.btnSave);
        btnViewSource = findViewById(R.id.btnViewSource);
    }

    private void loadIntentData() {
        if (getIntent() == null) return;

        // Load page names
        ArrayList<String> names = getIntent().getStringArrayListExtra("page_names");
        if (names != null && !names.isEmpty()) {
            pageNames.addAll(names);
        }

        // Load page codes
        ArrayList<String> codes = getIntent().getStringArrayListExtra("page_codes");
        if (codes != null && !codes.isEmpty()) {
            pageCodes.addAll(codes);
        }

        // Fallback: single page code
        if (pageCodes.isEmpty()) {
            String singleCode = getIntent().getStringExtra("finalCode");
            if (singleCode != null && !singleCode.isEmpty()) {
                pageCodes.add(singleCode);
                if (pageNames.isEmpty()) {
                    pageNames.add("index");
                }
            }
        }

        // Start page index
        int startIndex = getIntent().getIntExtra("start_page_index", 0);
        if (startIndex >= 0 && startIndex < pageNames.size()) {
            currentPageIndex = startIndex;
        }

        // Hide page tabs if only one page
        View pageTabScroll = findViewById(R.id.pageTabScroll);
        if (pageNames.size() <= 1 && pageTabScroll != null) {
            pageTabScroll.setVisibility(View.GONE);
        }
    }

    private void setupWebView() {
        WebSettings settings = webviewPreview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setDomStorageEnabled(true);

        webviewPreview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
            }
        });
    }

    private void setupResponsiveToggle() {
        if (chipGroupResponsive == null) return;

        chipGroupResponsive.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chipMobile) {
                currentWidth = 375;
                tvDeviceInfo.setText("375px");
                tvPreviewTitle.setText("Preview - Mobile");
            } else if (checkedId == R.id.chipTablet) {
                currentWidth = 768;
                tvDeviceInfo.setText("768px");
                tvPreviewTitle.setText("Preview - Tablet");
            } else if (checkedId == R.id.chipDesktop) {
                currentWidth = 1024;
                tvDeviceInfo.setText("1024px");
                tvPreviewTitle.setText("Preview - Desktop");
            } else if (checkedId == R.id.chipFullWidth) {
                currentWidth = 0; // full width
                tvDeviceInfo.setText("Full");
                tvPreviewTitle.setText("Preview - Full Width");
            }
            loadCurrentPage();
        });
    }

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

        btnSave.setOnClickListener(v -> {
            Toast.makeText(this, "Use Export from the main editor", Toast.LENGTH_SHORT).show();
        });

        btnViewSource.setOnClickListener(v -> showSourceDialog());
    }

    private void loadCurrentPage() {
        String code = getCurrentCode();
        if (code == null || code.isEmpty()) {
            webviewPreview.loadData("<html><body><p style='padding:20px;color:#666;'>No content to preview</p></body></html>",
                "text/html", "utf-8");
            return;
        }

        String modifiedHtml = code;

        if (currentWidth > 0) {
            // Inject viewport meta tag with specific width
            String viewportMeta = "<meta name=\"viewport\" content=\"width=" + currentWidth + ", initial-scale=1.0\">";
            if (modifiedHtml.contains("<meta name=\"viewport\"")) {
                modifiedHtml = modifiedHtml.replaceAll(
                    "<meta\\s+name=\"viewport\"[^>]*>",
                    viewportMeta
                );
            } else if (modifiedHtml.contains("<head>")) {
                modifiedHtml = modifiedHtml.replace("<head>", "<head>" + viewportMeta);
            }
        }

        webviewPreview.loadDataWithBaseURL(null, modifiedHtml, "text/html", "utf-8", null);
    }

    private String getCurrentCode() {
        if (currentPageIndex >= 0 && currentPageIndex < pageCodes.size()) {
            return pageCodes.get(currentPageIndex);
        }
        return "";
    }

    private void updatePageInfo() {
        if (tvPageCount != null) {
            String pageName = currentPageIndex < pageNames.size() ? pageNames.get(currentPageIndex) : "Unknown";
            tvPageCount.setText(pageName + " - Page " + (currentPageIndex + 1) + " of " + pageNames.size());
        }
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

    @Override
    public void onBackPressed() {
        if (webviewPreview.canGoBack()) {
            webviewPreview.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
