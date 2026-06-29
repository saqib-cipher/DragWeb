package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TextEditorActivity extends AppCompatActivity {

    private String filePath;
    private String projectId;
    private String relativePath;

    private MaterialToolbar toolbar;
    private WebView webEditor;
    private View rootLayout;
    private View suggestionScroll;
    private LinearLayout suggestionContainer;

    private String initialCode = "";
    private String currentContent = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_text_editor);

        filePath = getIntent().getStringExtra("file_path");
        projectId = getIntent().getStringExtra("project_id");
        relativePath = getIntent().getStringExtra("relative_path");

        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(this, "No file specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadFileContent();
        setupSuggestions();

        // Edge-to-edge window inset handling
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

                int bottomInset = Math.max(systemBars.bottom, ime.bottom);
                if (suggestionScroll != null && suggestionScroll.getVisibility() == View.VISIBLE) {
                    suggestionScroll.setPadding(suggestionScroll.getPaddingLeft(), suggestionScroll.getPaddingTop(),
                            suggestionScroll.getPaddingRight(), bottomInset);
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
                } else {
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomInset);
                }

                return insets;
            });
        }
    }

    private void initViews() {
        rootLayout = findViewById(R.id.root_layout);
        toolbar = findViewById(R.id.toolbar);
        webEditor = findViewById(R.id.web_editor);
        suggestionScroll = findViewById(R.id.suggestion_scroll);
        suggestionContainer = findViewById(R.id.suggestion_container);

        File file = new File(filePath);
        toolbar.setTitle(file.getName());
        toolbar.setNavigationOnClickListener(v -> handleBackPress());

        // Add save icon to toolbar
        toolbar.getMenu().add(0, 1, 0, "Save")
               .setIcon(R.drawable.device_floppy)
               .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                saveFile();
                return true;
            }
            return false;
        });

        // WebView configuration
        WebSettings settings = webEditor.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webEditor.requestFocus();
        webEditor.requestFocusFromTouch();

        webEditor.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void updateCode(String code) {
                currentContent = code;
            }
        }, "AndroidEditor");

        webEditor.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                
                // Load preferences for font size
                android.content.SharedPreferences prefs = getSharedPreferences("EditorSettings", MODE_PRIVATE);
                int fontSize = prefs.getInt("editor_font_size", 14);
                webEditor.evaluateJavascript("setFontSize(" + fontSize + ");", null);

                // Set mode
                String mode = getCodeMirrorMode(filePath);
                webEditor.evaluateJavascript("setMode('" + mode + "');", null);

                // Load initial code
                String escapedCode = new Gson().toJson(initialCode);
                webEditor.evaluateJavascript("setEditorValue(" + escapedCode + ");", null);
            }
        });
    }

    private void loadFileContent() {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                String content = FileUtil.readFile(file.getAbsolutePath());
                initialCode = content != null ? content : "";
                currentContent = initialCode;

                // Load local assets CodeMirror editor.html page
                webEditor.loadUrl("file:///android_asset/codemirror/editor.html");
            } else {
                Toast.makeText(this, "File does not exist", Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error loading file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupSuggestions() {
        if (suggestionContainer == null) return;

        android.content.SharedPreferences prefs = getSharedPreferences("EditorSettings", MODE_PRIVATE);
        boolean showSuggestions = prefs.getBoolean("editor_show_suggestions", true);

        if (suggestionScroll != null) {
            suggestionScroll.setVisibility(showSuggestions ? View.VISIBLE : View.GONE);
        }

        if (!showSuggestions) return;

        suggestionContainer.removeAllViews();

        List<Map<String, String>> list = loadSuggestionsList();

        int padding = dpToPx(8);
        int margin = dpToPx(4);

        for (Map<String, String> item : list) {
            String display = item.get("display");
            String insert = item.get("insert");
            if (display == null || display.isEmpty()) continue;

            com.google.android.material.button.MaterialButton btn = new com.google.android.material.button.MaterialButton(
                    this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(display);
            btn.setTextSize(14);
            btn.setPadding(padding, 0, padding, 0);
            btn.setMinWidth(dpToPx(36));
            btn.setMinimumWidth(dpToPx(36));
            btn.setHeight(dpToPx(36));
            btn.setMinimumHeight(dpToPx(36));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(36)
            );
            lp.setMargins(margin, 0, margin, 0);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> insertTextInEditor(insert != null ? insert : ""));
            suggestionContainer.addView(btn);
        }
    }

    private List<Map<String, String>> loadSuggestionsList() {
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dragweb/suggestions.json");
        if (!file.exists()) {
            // Write defaults
            String defaultJson = "[\n" +
                    "  { \"display\": \"<\", \"insert\": \"<\" },\n" +
                    "  { \"display\": \">\", \"insert\": \">\" },\n" +
                    "  { \"display\": \"/\", \"insert\": \"/\" },\n" +
                    "  { \"display\": \"\\\"\", \"insert\": \"\\\"\" },\n" +
                    "  { \"display\": \"'\", \"insert\": \"'\" },\n" +
                    "  { \"display\": \"=\", \"insert\": \"=\" },\n" +
                    "  { \"display\": \"{\", \"insert\": \"{\" },\n" +
                    "  { \"display\": \"}\", \"insert\": \"}\" },\n" +
                    "  { \"display\": \"(\", \"insert\": \"(\" },\n" +
                    "  { \"display\": \")\", \"insert\": \")\" },\n" +
                    "  { \"display\": \"[\", \"insert\": \"[\" },\n" +
                    "  { \"display\": \"]\", \"insert\": \"]\" },\n" +
                    "  { \"display\": \";\", \"insert\": \";\" },\n" +
                    "  { \"display\": \".\", \"insert\": \".\" },\n" +
                    "  { \"display\": \":\", \"insert\": \":\" },\n" +
                    "  { \"display\": \"!\", \"insert\": \"!\" },\n" +
                    "  { \"display\": \"-\", \"insert\": \"-\" },\n" +
                    "  { \"display\": \"_\", \"insert\": \"_\" },\n" +
                    "  { \"display\": \"+\", \"insert\": \"+\" }\n" +
                    "]";
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                FileUtil.writeFile(file.getAbsolutePath(), defaultJson);
            } catch (Exception e) {
                Log.e("TextEditorActivity", "Failed to write default suggestions file", e);
            }
            return new Gson().fromJson(defaultJson, new TypeToken<List<Map<String, String>>>(){}.getType());
        }
        try {
            String json = FileUtil.readFile(file.getAbsolutePath());
            return new Gson().fromJson(json, new TypeToken<List<Map<String, String>>>(){}.getType());
        } catch (Exception e) {
            Log.e("TextEditorActivity", "Failed to read suggestions", e);
            return new ArrayList<>();
        }
    }

    private void insertTextInEditor(String text) {
        String js = "javascript:if(window.editor){ window.editor.replaceSelection('"
                + text.replace("'", "\\'").replace("\n", "\\n") + "'); window.editor.focus(); }";
        webEditor.loadUrl(js);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private String getCodeMirrorMode(String path) {
        if (path == null) return "htmlmixed";
        String lower = path.toLowerCase(java.util.Locale.US);
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".js") || lower.endsWith(".json")) return "javascript";
        return "htmlmixed";
    }

    private void saveFile() {
        String content = currentContent;
        File file = new File(filePath);

        try {
            FileUtil.writeFile(file.getAbsolutePath(), content);
            initialCode = content; // Update initialCode so back press checks work correctly
            Toast.makeText(this, "Saved successfully", Toast.LENGTH_SHORT).show();

            // If CSS or JS, convert back to logic blocks
            if (relativePath != null && (relativePath.toLowerCase().endsWith(".css") || relativePath.toLowerCase().endsWith(".js") || relativePath.toLowerCase().endsWith(".htm") || relativePath.toLowerCase().endsWith(".html"))) {
                try {
                    if (relativePath.toLowerCase().endsWith(".css")) {
                        HtmlCssImporter importer = new HtmlCssImporter(TextEditorActivity.this);
                        List<Map<String, Object>> blocks = importer.importCssOnly(content);
                        String blocksJson = new Gson().toJson(blocks);

                        File dir = new File(getFilesDir(), "projects/logic");
                        if (!dir.exists()) dir.mkdirs();

                        String safePageName = relativePath.replace("/", "_").replace(".", "_");
                        File logicFile = new File(dir, projectId + "_" + safePageName + ".logic");
                        FileUtil.writeFile(logicFile.getAbsolutePath(), blocksJson);

                        Toast.makeText(this, "CSS rules synced to block editor", Toast.LENGTH_SHORT).show();
                    } else if (relativePath.toLowerCase().endsWith(".js")) {
                        HtmlCssImporter importer = new HtmlCssImporter(TextEditorActivity.this);
                        List<Map<String, Object>> blocks = importer.importJsOnly(content);
                        String blocksJson = new Gson().toJson(blocks);

                        File dir = new File(getFilesDir(), "projects/logic");
                        if (!dir.exists()) dir.mkdirs();

                        String safePageName = relativePath.replace("/", "_").replace(".", "_");
                        File logicFile = new File(dir, projectId + "_" + safePageName + ".logic");
                        FileUtil.writeFile(logicFile.getAbsolutePath(), blocksJson);

                        Toast.makeText(this, "JavaScript synced to block editor", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e("TextEditorActivity", "Conversion failed", e);
                    Toast.makeText(this, "Sync to block editor failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            setResult(RESULT_OK);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleBackPress() {
        if (!initialCode.equals(currentContent)) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("Discard Changes?")
                .setMessage("You have unsaved changes. Do you want to discard them?")
                .setPositiveButton("Discard", (dialog, which) -> finish())
                .setNegativeButton("Keep Editing", null)
                .show();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBackPress();
    }
}
