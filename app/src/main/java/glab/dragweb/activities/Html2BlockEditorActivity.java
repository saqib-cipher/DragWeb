package glab.dragweb.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import glab.dragweb.R;
import glab.dragweb.codegen.HtmlCode2Block;
import glab.dragweb.core.BlocksLoader;
import glab.dragweb.logic.BlockBean;

/**
 * Html2Block code editor modeled on Sketchware DayGreen's Xml2BlockEditorActivity.
 * Allows editing page HTML directly in CodeMirror and converting to native DragWeb HTML blocks.
 */
public class Html2BlockEditorActivity extends AppCompatActivity {

    public static final String EXTRA_HTML_CONTENT = "html_content";
    public static final String EXTRA_PAGE_NAME = "page_name";
    public static final String RESULT_EXTRA_BLOCKS = "converted_blocks";
    public static final String RESULT_EXTRA_HTML = "converted_html";

    private static final String[] QUICK_SNIPPETS = {
            "<div class=\"\">\n  \n</div>",
            "<section id=\"\">\n  \n</section>",
            "<p>Text</p>",
            "<h1>Heading</h1>",
            "<button type=\"button\">Click</button>",
            "<img src=\"\" alt=\"Image\" />",
            "<input type=\"text\" placeholder=\"\" />",
            "<a href=\"#\">Link</a>",
            "<ul>\n  <li>Item</li>\n</ul>",
            "style=\"\"",
            "class=\"\"",
            "id=\"\""
    };

    private static final String[] QUICK_LABELS = {
            "div", "section", "p", "h1", "button", "img", "input", "a", "ul", "style", "class", "id"
    };

    private MaterialToolbar toolbar;
    private AppBarLayout appBar;
    private WebView webEditor;
    private FrameLayout progressOverlay;
    private ExtendedFloatingActionButton fabConvert;
    private LinearLayout quickChipsContainer;
    private View quickBar;

    private String initialCode = "";
    private String currentCode = "";
    private boolean wordWrapEnabled = true;
    private boolean editorReady = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_html2block_editor);

        String passedHtml = getIntent().getStringExtra(EXTRA_HTML_CONTENT);
        String pageName = getIntent().getStringExtra(EXTRA_PAGE_NAME);

        initialCode = passedHtml != null ? passedHtml : "";
        currentCode = initialCode;

        initViews(pageName);
        setupInsets();
        setupQuickChips();
        setupCodeMirror();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackNavigation();
            }
        });
    }

    private void initViews(String pageName) {
        appBar = findViewById(R.id.app_bar);
        toolbar = findViewById(R.id.toolbar);
        webEditor = findViewById(R.id.web_editor);
        progressOverlay = findViewById(R.id.progress_overlay);
        fabConvert = findViewById(R.id.fab_convert);
        quickChipsContainer = findViewById(R.id.quick_chips_container);
        quickBar = findViewById(R.id.quick_bar);

        setSupportActionBar(toolbar);
        toolbar.setTitle("Html2Block");
        if (pageName != null && !pageName.trim().isEmpty()) {
            toolbar.setSubtitle(pageName);
        }
        toolbar.setNavigationOnClickListener(v -> handleBackNavigation());

        fabConvert.setOnClickListener(v -> convertAndReturn());
    }

    private void setupInsets() {
        View root = findViewById(R.id.root_layout);
        if (root == null) return;
        final int fabBaseBottom = (int) (64 * getResources().getDisplayMetrics().density);
        final int fabBaseEnd = (int) (16 * getResources().getDisplayMetrics().density);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(sys.bottom, ime.bottom);

            if (appBar != null) {
                appBar.setPadding(sys.left, sys.top, sys.right, 0);
            }
            if (quickBar != null) {
                quickBar.setPadding(sys.left, 0, sys.right, bottom);
            }
            if (fabConvert != null && fabConvert.getLayoutParams() instanceof androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) {
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams lp =
                        (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) fabConvert.getLayoutParams();
                lp.bottomMargin = fabBaseBottom + bottom;
                lp.rightMargin = fabBaseEnd + sys.right;
                fabConvert.setLayoutParams(lp);
            }
            return insets;
        });
    }

    private void setupQuickChips() {
        if (quickChipsContainer == null) return;
        float density = getResources().getDisplayMetrics().density;
        int gap = (int) (6 * density);

        for (int i = 0; i < QUICK_LABELS.length; i++) {
            final String snippet = QUICK_SNIPPETS[i];
            Chip chip = new Chip(this);
            chip.setText(QUICK_LABELS[i]);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipMinHeight(32 * density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(gap);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> insertSnippet(snippet));
            quickChipsContainer.addView(chip);
        }
    }

    private void setupCodeMirror() {
        WebSettings settings = webEditor.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webEditor.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void updateCode(String code) {
                currentCode = code != null ? code : "";
            }
        }, "AndroidEditor");

        webEditor.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                editorReady = true;

                int fontSize = getSharedPreferences("EditorSettings", MODE_PRIVATE)
                        .getInt("editor_font_size", 14);
                boolean isDark = (getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

                String escaped = new Gson().toJson(initialCode);
                String js = "setFontSize(" + fontSize + ");"
                        + "setMode('htmlmixed');"
                        + "editor.setOption('lineWrapping', " + wordWrapEnabled + ");"
                        + "editor.setOption('theme', '" + (isDark ? "dracula" : "default") + "');"
                        + (isDark ? "" : "document.body.style.backgroundColor='#ffffff';document.documentElement.style.backgroundColor='#ffffff';")
                        + "setEditorValue(" + escaped + ");";
                webEditor.evaluateJavascript(js, null);
            }
        });

        webEditor.loadUrl("file:///android_asset/codemirror/editor.html");
    }

    private void insertSnippet(String snippet) {
        if (!editorReady) return;
        String escaped = new Gson().toJson(snippet);
        webEditor.evaluateJavascript(
                "editor.replaceSelection(" + escaped + "); editor.focus();",
                null
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_html2block_editor, menu);
        MenuItem wrapItem = menu.findItem(R.id.action_word_wrap);
        if (wrapItem != null) {
            wrapItem.setChecked(wordWrapEnabled);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_undo) {
            if (editorReady) {
                webEditor.evaluateJavascript("editor.undo();", null);
            }
            return true;
        } else if (id == R.id.action_redo) {
            if (editorReady) {
                webEditor.evaluateJavascript("editor.redo();", null);
            }
            return true;
        } else if (id == R.id.action_format) {
            formatCurrentHtml();
            return true;
        } else if (id == R.id.action_word_wrap) {
            wordWrapEnabled = !wordWrapEnabled;
            item.setChecked(wordWrapEnabled);
            if (editorReady) {
                webEditor.evaluateJavascript("editor.setOption('lineWrapping', " + wordWrapEnabled + ");", null);
            }
            return true;
        } else if (id == R.id.action_templates) {
            showTemplatesDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void formatCurrentHtml() {
        if (!editorReady) return;
        webEditor.evaluateJavascript("editor.getValue()", val -> {
            String code = decodeJsString(val);
            if (code == null) code = currentCode;
            String formatted = formatHtmlSimple(code);
            currentCode = formatted;
            String escaped = new Gson().toJson(formatted);
            webEditor.evaluateJavascript("editor.setValue(" + escaped + ");", null);
        });
    }

    private void showTemplatesDialog() {
        final String[] names = {
                "Card Container",
                "Flex Navigation Bar",
                "Form Section",
                "Two-Column Row"
        };
        final String[] snippets = {
                "<div class=\"card\" style=\"padding: 16px; border-radius: 12px; background-color: #ffffff;\">\n  <h2>Card Title</h2>\n  <p>Description text goes here.</p>\n  <button type=\"button\">Action</button>\n</div>",
                "<nav style=\"display: flex; justify-content: space-between; align-items: center; padding: 12px;\">\n  <a href=\"#\">Brand</a>\n  <div style=\"display: flex; gap: 12px;\">\n    <a href=\"#home\">Home</a>\n    <a href=\"#about\">About</a>\n  </div>\n</nav>",
                "<form style=\"display: flex; flex-direction: column; gap: 12px; padding: 16px;\">\n  <label for=\"email\">Email</label>\n  <input id=\"email\" type=\"email\" placeholder=\"you@example.com\" required />\n  <button type=\"submit\">Continue</button>\n</form>",
                "<section style=\"display: flex; gap: 16px; padding: 16px;\">\n  <div style=\"width: 50%;\">\n    <p>Left Column</p>\n  </div>\n  <div style=\"width: 50%;\">\n    <p>Right Column</p>\n  </div>\n</section>"
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle("Insert HTML Template")
                .setItems(names, (d, which) -> insertSnippet(snippets[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void convertAndReturn() {
        if (!editorReady) {
            runConversion(currentCode);
            return;
        }
        webEditor.evaluateJavascript("editor.getValue()", value -> {
            String code = decodeJsString(value);
            if (code == null) {
                code = currentCode;
            }
            currentCode = code;
            runConversion(code);
        });
    }

    private void runConversion(final String htmlCode) {
        if (progressOverlay != null) progressOverlay.setVisibility(View.VISIBLE);
        if (fabConvert != null) fabConvert.setEnabled(false);

        final String trimmed = htmlCode != null ? htmlCode.trim() : "";
        if (trimmed.isEmpty()) {
            Intent resIntent = new Intent();
            resIntent.putParcelableArrayListExtra(RESULT_EXTRA_BLOCKS, new ArrayList<>());
            resIntent.putExtra(RESULT_EXTRA_HTML, "");
            setResult(RESULT_OK, resIntent);
            finish();
            return;
        }

        executor.execute(() -> {
            HtmlCode2Block.BlockResult result = BlocksLoader.convertHtmlCode(getApplicationContext(), trimmed);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (progressOverlay != null) progressOverlay.setVisibility(View.GONE);
                if (fabConvert != null) fabConvert.setEnabled(true);

                if (result != null && (result.isSuccess || (result.blocks != null && !result.blocks.isEmpty()))) {
                    ArrayList<BlockBean> blocks = result.blocks != null ? result.blocks : new ArrayList<>();
                    Intent resIntent = new Intent();
                    resIntent.putParcelableArrayListExtra(RESULT_EXTRA_BLOCKS, blocks);
                    resIntent.putExtra(RESULT_EXTRA_HTML, trimmed);
                    setResult(RESULT_OK, resIntent);
                    finish();
                } else {
                    String err = (result != null && result.errorMessage != null && !result.errorMessage.isEmpty())
                            ? result.errorMessage
                            : "Could not convert HTML into blocks.";
                    showErrorDialog(err);
                }
            });
        });
    }

    private void showErrorDialog(String error) {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        TextView tv = new TextView(this);
        tv.setText(error);
        tv.setTextIsSelectable(true);
        tv.setPadding(pad, pad / 2, pad, pad / 2);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Conversion Error")
                .setView(tv)
                .setNeutralButton("Copy Error", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("Html2Block Error", error));
                    }
                })
                .setPositiveButton("OK", null)
                .show();
    }

    private void handleBackNavigation() {
        if (editorReady) {
            webEditor.evaluateJavascript("editor.getValue()", val -> {
                String code = decodeJsString(val);
                if (code != null) currentCode = code;
                confirmOrFinish();
            });
        } else {
            confirmOrFinish();
        }
    }

    private void confirmOrFinish() {
        if (currentCode.equals(initialCode)) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Discard Changes?")
                .setMessage("Your HTML edits have not been converted to blocks yet.")
                .setNegativeButton("Keep Editing", null)
                .setPositiveButton("Discard", (d, w) -> {
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .show();
    }

    private static String decodeJsString(String jsResult) {
        if (jsResult == null || "null".equals(jsResult)) return null;
        try {
            return new Gson().fromJson(jsResult, String.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatHtmlSimple(String html) {
        if (html == null) return "";
        String normalized = html.replaceAll(">\\s+<", ">\n<").trim();
        String[] lines = normalized.split("\\r?\\n");
        StringBuilder out = new StringBuilder();
        int indent = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("</")) {
                indent = Math.max(0, indent - 1);
            }
            for (int i = 0; i < indent; i++) {
                out.append("  ");
            }
            out.append(line).append('\n');
            if (line.startsWith("<")
                    && !line.startsWith("</")
                    && !line.startsWith("<!")
                    && !line.endsWith("/>")
                    && !line.contains("</")
                    && !isVoidOpenTag(line)) {
                indent++;
            }
        }
        return out.toString().trim();
    }

    private static boolean isVoidOpenTag(String line) {
        String lower = line.toLowerCase();
        return lower.startsWith("<img") || lower.startsWith("<input") || lower.startsWith("<br")
                || lower.startsWith("<hr") || lower.startsWith("<meta") || lower.startsWith("<link")
                || lower.startsWith("<source") || lower.startsWith("<wbr");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
