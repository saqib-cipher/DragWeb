package glab.dragweb.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import org.eclipse.tm4e.core.registry.IThemeSource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import glab.dragweb.R;
import glab.dragweb.databinding.ActivitySourceCodeBinding;
import glab.dragweb.util.WebCodeFormatter;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * Full-screen read-only source code viewer backed by Sora Editor and TextMate syntax grammars.
 * Supports single-source inspection (LogicBlockActivity) and multi-tab HTML/CSS/JS inspection (PreviewActivity),
 * with Word Wrap, Pretty Print, Find, Line Numbers, Font Size, and Copy actions.
 */
public class SourceCodeActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SUBTITLE = "subtitle";
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_HTML_PATH = "html_path";
    public static final String EXTRA_CSS_PATH = "css_path";
    public static final String EXTRA_JS_PATH = "js_path";
    public static final String EXTRA_HTML_CODE = "html_code";
    public static final String EXTRA_CSS_CODE = "css_code";
    public static final String EXTRA_JS_CODE = "js_code";

    private static final String PREFS_NAME = "EditorSettings";
    private static final String PREF_FONT_SIZE = "editor_font_size";
    private static final String PREF_WORD_WRAP = "src_viewer_word_wrap";
    private static final String PREF_LINE_NUMBERS = "src_viewer_line_numbers";

    private static final String THEME_LIGHT = "github_light.json";
    private static final String THEME_DARK = "github_dark.json";
    private static boolean textMateInitialized = false;

    private static final class SourceTab {
        final String label;
        final String mode;
        String code;

        SourceTab(String label, String mode, String code) {
            this.label = label;
            this.mode = mode;
            this.code = code != null ? code : "";
        }
    }

    private ActivitySourceCodeBinding binding;
    private final List<SourceTab> tabs = new ArrayList<>();
    private int activeTabIndex = 0;
    private SharedPreferences prefs;

    public static void openSingle(Context context, String title, String subtitle, String code, String mode) {
        if (context == null) return;
        Intent intent = new Intent(context, SourceCodeActivity.class);
        intent.putExtra(EXTRA_TITLE, title != null ? title : "Source Code");
        if (subtitle != null && !subtitle.isEmpty()) {
            intent.putExtra(EXTRA_SUBTITLE, subtitle);
        }
        String safeCode = code != null ? code : "";
        if (safeCode.length() > 100_000) {
            try {
                File tmp = new File(context.getCacheDir(), "dw_source_preview.txt");
                Files.write(tmp.toPath(), safeCode.getBytes(StandardCharsets.UTF_8));
                intent.putExtra(EXTRA_HTML_PATH, tmp.getAbsolutePath());
                intent.putExtra(EXTRA_MODE, mode != null ? mode : "javascript");
            } catch (Exception e) {
                intent.putExtra(EXTRA_CODE, safeCode.substring(0, 100_000));
            }
        } else {
            intent.putExtra(EXTRA_CODE, safeCode);
            intent.putExtra(EXTRA_MODE, mode != null ? mode : "javascript");
        }
        context.startActivity(intent);
    }

    public static void openWebFiles(Context context, String title, File htmlFile, File cssFile, File jsFile) {
        if (context == null) return;
        Intent intent = new Intent(context, SourceCodeActivity.class);
        intent.putExtra(EXTRA_TITLE, title != null ? title : "Source Code Viewer");
        if (htmlFile != null && htmlFile.exists()) intent.putExtra(EXTRA_HTML_PATH, htmlFile.getAbsolutePath());
        if (cssFile != null && cssFile.exists()) intent.putExtra(EXTRA_CSS_PATH, cssFile.getAbsolutePath());
        if (jsFile != null && jsFile.exists()) intent.putExtra(EXTRA_JS_PATH, jsFile.getAbsolutePath());
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivitySourceCodeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.searchPanel.getVisibility() == View.VISIBLE) {
                    closeSearchPanel();
                } else {
                    finish();
                }
            }
        });

        ensureTextMateLoaded(this);
        loadTabsFromIntent();
        setupToolbar();
        setupTabs();
        setupSearchPanel();
        setupSoraEditor();
        applyActiveTabToEditor();
    }

    private static synchronized void ensureTextMateLoaded(Context context) {
        if (textMateInitialized) return;
        try {
            FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(context.getAssets()));
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json");
            ThemeRegistry registry = ThemeRegistry.getInstance();
            for (String theme : new String[]{THEME_LIGHT, THEME_DARK}) {
                String path = "textmate/themes/" + theme;
                registry.loadTheme(new ThemeModel(
                        IThemeSource.fromInputStream(
                                FileProviderRegistry.getInstance().tryGetInputStream(path),
                                path,
                                null),
                        theme));
            }
            textMateInitialized = true;
        } catch (Throwable ignored) {}
    }

    private void loadTabsFromIntent() {
        Intent intent = getIntent();
        if (intent == null) return;

        if (intent.hasExtra(EXTRA_CODE)) {
            String code = intent.getStringExtra(EXTRA_CODE);
            String mode = intent.getStringExtra(EXTRA_MODE);
            tabs.add(new SourceTab("Code", normalizeMode(mode), code));
            return;
        }

        if (intent.hasExtra(EXTRA_MODE) && intent.hasExtra(EXTRA_HTML_PATH)
                && !intent.hasExtra(EXTRA_CSS_PATH) && !intent.hasExtra(EXTRA_JS_PATH)) {
            String code = readFileSafe(intent.getStringExtra(EXTRA_HTML_PATH));
            tabs.add(new SourceTab("Code", normalizeMode(intent.getStringExtra(EXTRA_MODE)), code));
            return;
        }

        String html = intent.hasExtra(EXTRA_HTML_PATH)
                ? readFileSafe(intent.getStringExtra(EXTRA_HTML_PATH))
                : intent.getStringExtra(EXTRA_HTML_CODE);
        String css = intent.hasExtra(EXTRA_CSS_PATH)
                ? readFileSafe(intent.getStringExtra(EXTRA_CSS_PATH))
                : intent.getStringExtra(EXTRA_CSS_CODE);
        String js = intent.hasExtra(EXTRA_JS_PATH)
                ? readFileSafe(intent.getStringExtra(EXTRA_JS_PATH))
                : intent.getStringExtra(EXTRA_JS_CODE);

        tabs.add(new SourceTab("HTML", "htmlmixed", (html == null || html.isEmpty()) ? "<!-- No HTML content -->" : html));
        tabs.add(new SourceTab("CSS", "css", (css == null || css.trim().isEmpty()) ? "/* No CSS generated */" : css));
        tabs.add(new SourceTab("JS", "javascript", (js == null || js.trim().isEmpty()) ? "/* No JavaScript generated */" : js));
    }

    private void setupToolbar() {
        String title = getIntent() != null ? getIntent().getStringExtra(EXTRA_TITLE) : null;
        String subtitle = getIntent() != null ? getIntent().getStringExtra(EXTRA_SUBTITLE) : null;
        binding.toolbar.setTitle(title != null && !title.isEmpty() ? title : "Source Code");
        if (subtitle != null && !subtitle.isEmpty()) {
            binding.toolbar.setSubtitle(subtitle);
        }
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (binding.searchPanel.getVisibility() == View.VISIBLE) {
                closeSearchPanel();
            } else {
                finish();
            }
        });

        binding.toolbar.inflateMenu(R.menu.menu_source_code);
        MenuItem wrapItem = binding.toolbar.getMenu().findItem(R.id.action_word_wrap);
        if (wrapItem != null) {
            wrapItem.setChecked(prefs.getBoolean(PREF_WORD_WRAP, false));
        }
        MenuItem lineItem = binding.toolbar.getMenu().findItem(R.id.action_line_numbers);
        if (lineItem != null) {
            lineItem.setChecked(prefs.getBoolean(PREF_LINE_NUMBERS, true));
        }

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_copy) {
                copyActiveTab();
                return true;
            } else if (id == R.id.action_pretty_print) {
                prettyPrintActiveTab();
                return true;
            } else if (id == R.id.action_find) {
                toggleSearchPanel();
                return true;
            } else if (id == R.id.action_word_wrap) {
                boolean next = !item.isChecked();
                item.setChecked(next);
                prefs.edit().putBoolean(PREF_WORD_WRAP, next).apply();
                binding.editor.setWordwrap(next);
                return true;
            } else if (id == R.id.action_line_numbers) {
                boolean next = !item.isChecked();
                item.setChecked(next);
                prefs.edit().putBoolean(PREF_LINE_NUMBERS, next).apply();
                binding.editor.setLineNumberEnabled(next);
                binding.editor.setPinLineNumber(next);
                return true;
            } else if (id == R.id.action_font_size) {
                showFontSizeDialog();
                return true;
            }
            return false;
        });
    }

    private void setupTabs() {
        if (tabs.size() <= 1) {
            binding.tabLayout.setVisibility(View.GONE);
            return;
        }
        binding.tabLayout.setVisibility(View.VISIBLE);
        for (SourceTab tab : tabs) {
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(tab.label));
        }
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                activeTabIndex = tab.getPosition();
                applyActiveTabToEditor();
                if (binding.searchPanel.getVisibility() == View.VISIBLE && binding.etFindQuery.getText() != null) {
                    runEditorSearch(binding.etFindQuery.getText().toString());
                }
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupSearchPanel() {
        binding.etFindQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                runEditorSearch(s != null ? s.toString() : "");
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        binding.btnFindNext.setOnClickListener(v -> {
            try {
                binding.editor.getSearcher().gotoNext();
            } catch (Exception ignored) {}
        });
        binding.btnFindPrev.setOnClickListener(v -> {
            try {
                binding.editor.getSearcher().gotoPrevious();
            } catch (Exception ignored) {}
        });
        binding.btnCloseFind.setOnClickListener(v -> closeSearchPanel());
    }

    private void toggleSearchPanel() {
        if (binding.searchPanel.getVisibility() == View.VISIBLE) {
            closeSearchPanel();
        } else {
            binding.searchPanel.setVisibility(View.VISIBLE);
            binding.etFindQuery.requestFocus();
        }
    }

    private void closeSearchPanel() {
        binding.searchPanel.setVisibility(View.GONE);
        try {
            binding.editor.getSearcher().stopSearch();
        } catch (Exception ignored) {}
    }

    private void runEditorSearch(String query) {
        try {
            if (query == null || query.isEmpty()) {
                binding.editor.getSearcher().stopSearch();
            } else {
                binding.editor.getSearcher().search(query, new EditorSearcher.SearchOptions(false, false));
            }
        } catch (Exception ignored) {}
    }

    private void setupSoraEditor() {
        try {
            Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/jetbrainsmono-regular.ttf");
            binding.editor.setTypefaceText(tf);
            binding.editor.setTypefaceLineNumber(tf);
        } catch (Exception ignored) {}

        int fontSize = prefs.getInt(PREF_FONT_SIZE, 14);
        boolean wordWrap = prefs.getBoolean(PREF_WORD_WRAP, false);
        boolean lineNumbers = prefs.getBoolean(PREF_LINE_NUMBERS, true);

        binding.editor.setEditable(false);
        binding.editor.setTextSize(fontSize);
        binding.editor.setWordwrap(wordWrap);
        binding.editor.setLineNumberEnabled(lineNumbers);
        binding.editor.setPinLineNumber(lineNumbers);
        applyMaterialColorScheme();
    }

    private void applyMaterialColorScheme() {
        boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        EditorColorScheme scheme;
        try {
            String themeName = isDark ? THEME_DARK : THEME_LIGHT;
            ThemeRegistry.getInstance().setTheme(themeName);
            scheme = TextMateColorScheme.create(ThemeRegistry.getInstance());
        } catch (Throwable t) {
            scheme = new EditorColorScheme();
        }

        int surface = MaterialColors.getColor(binding.editor, com.google.android.material.R.attr.colorSurface, isDark ? 0xFF141218 : 0xFFFEF7FF);
        int surfaceLow = MaterialColors.getColor(binding.editor, com.google.android.material.R.attr.colorSurfaceContainerLow, isDark ? 0xFF1D1B20 : 0xFFF7F2FA);
        int surfaceContainer = MaterialColors.getColor(binding.editor, com.google.android.material.R.attr.colorSurfaceContainer, isDark ? 0xFF211F26 : 0xFFF3EDF7);
        int surfaceHighest = MaterialColors.getColor(binding.editor, com.google.android.material.R.attr.colorSurfaceContainerHighest, isDark ? 0xFF36343B : 0xFFE6E0E9);
        int onSurface = MaterialColors.getColor(binding.editor, com.google.android.material.R.attr.colorOnSurface, isDark ? 0xFFE6E0E9 : 0xFF1D1B20);
        int onSurfaceVariant = MaterialColors.getColor(binding.editor, com.google.android.material.R.attr.colorOnSurfaceVariant, isDark ? 0xFFCAC4D0 : 0xFF49454F);

        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, surface);
        scheme.setColor(EditorColorScheme.CURRENT_LINE, surfaceLow);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_PANEL, surfaceContainer);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, surfaceContainer);
        scheme.setColor(EditorColorScheme.LINE_NUMBER, onSurfaceVariant);
        scheme.setColor(EditorColorScheme.LINE_DIVIDER, surfaceHighest);
        scheme.setColor(EditorColorScheme.SELECTION_INSERT, onSurfaceVariant);
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, onSurface);
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, surfaceHighest);
        if (!(scheme instanceof TextMateColorScheme)) {
            scheme.setColor(EditorColorScheme.TEXT_NORMAL, onSurface);
        }
        binding.editor.setColorScheme(scheme);
    }

    private void applyActiveTabToEditor() {
        if (tabs.isEmpty() || activeTabIndex < 0 || activeTabIndex >= tabs.size()) return;
        SourceTab tab = tabs.get(activeTabIndex);
        binding.editor.setEditorLanguage(resolveSoraLanguage(tab.mode));
        binding.editor.setText(tab.code);
        binding.editor.setSelection(0, 0);
    }

    private Language resolveSoraLanguage(String mode) {
        String scope = switch (mode) {
            case "htmlmixed" -> "text.xml";
            case "css" -> "source.css";
            case "json" -> "source.json";
            default -> "source.javascript";
        };
        try {
            return TextMateLanguage.create(scope, false);
        } catch (Throwable t) {
            return new EmptyLanguage();
        }
    }

    private void prettyPrintActiveTab() {
        if (tabs.isEmpty() || activeTabIndex < 0 || activeTabIndex >= tabs.size()) return;
        SourceTab tab = tabs.get(activeTabIndex);
        String formatted = WebCodeFormatter.format(tab.code, tab.mode);
        tab.code = formatted;
        binding.editor.setText(formatted);
    }

    private void showFontSizeDialog() {
        int currentSize = prefs.getInt(PREF_FONT_SIZE, 14);
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(9);
        picker.setMaxValue(28);
        picker.setWrapSelectorWheel(false);
        picker.setValue(Math.max(9, Math.min(28, currentSize)));

        LinearLayout container = new LinearLayout(this);
        container.setGravity(Gravity.CENTER);
        container.setPadding(24, 16, 24, 8);
        container.addView(picker);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Font Size")
                .setView(container)
                .setPositiveButton("Apply", (d, w) -> {
                    int next = picker.getValue();
                    prefs.edit().putInt(PREF_FONT_SIZE, next).apply();
                    binding.editor.setTextSize(next);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void copyActiveTab() {
        if (tabs.isEmpty() || activeTabIndex < 0 || activeTabIndex >= tabs.size()) return;
        SourceTab tab = tabs.get(activeTabIndex);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(tab.label, tab.code));
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.trim().isEmpty()) return "javascript";
        String m = mode.trim().toLowerCase();
        if ("html".equals(m) || "htmlmixed".equals(m) || "xml".equals(m)) return "htmlmixed";
        if ("css".equals(m)) return "css";
        if ("json".equals(m)) return "json";
        return "javascript";
    }

    private static String readFileSafe(String path) {
        if (path == null || path.isEmpty()) return "";
        try {
            File f = new File(path);
            if (!f.exists()) return "";
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onDestroy() {
        if (binding != null && binding.editor != null) {
            try {
                binding.editor.release();
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
