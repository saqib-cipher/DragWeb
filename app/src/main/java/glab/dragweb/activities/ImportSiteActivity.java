package glab.dragweb.activities;

import glab.dragweb.R;
import glab.dragweb.codegen.*;
import glab.dragweb.core.BlocksLoader;
import glab.dragweb.data.*;
import glab.dragweb.logic.*;
import glab.dragweb.util.*;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ImportSiteActivity extends AppCompatActivity {

    private static final int MODE_FILES = 0;
    private static final int MODE_ZIP = 1;
    private static final int MODE_FOLDER = 2;

    private static final Pattern STYLE_TAG_PATTERN = Pattern.compile(
            "(?is)<style\\b[^>]*>(.*?)</style>"
    );
    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile(
            "(?is)<script\\b([^>]*)>(.*?)</script>"
    );
    private static final Pattern SRC_ATTR_PATTERN = Pattern.compile(
            "(?i)\\bsrc\\s*="
    );
    private static final Pattern INLINE_STYLE_ATTR_PATTERN = Pattern.compile(
            "(?is)<[a-z][a-z0-9-]*\\b[^>]*\\bstyle\\s*=\\s*[\"'][^\"']+[\"']"
    );

    private TextInputEditText etProjectName;
    private MaterialButton btnImport;
    private View layoutLoading;

    private MaterialButtonToggleGroup toggleImportMode;
    private View layoutZipPicker;
    private View layoutFolderPicker;
    private TextView tvZipFileName;
    private TextView tvFolderName;
    private TextView tvImportSummary;

    private View btnSelectHtml;
    private View btnSelectCss;
    private View btnSelectJs;
    private View layoutSeparateCss;
    private View layoutSeparateJs;
    private MaterialSwitch switchSeparateCss;
    private MaterialSwitch switchSeparateJs;
    private MaterialSwitch switchConvertCss;
    private MaterialSwitch switchConvertJs;
    private MaterialButtonToggleGroup toggleImportStylingMode;
    private TextView tvConvertCssDesc;
    private TextView tvConvertJsDesc;
    private TextView tvInlineStylesDesc;
    private TextView tvImportNotice;

    private TextView tvCssFileName;
    private TextView tvJsFileName;
    private View btnRemoveCss;
    private View btnRemoveJs;

    private RecyclerView rvHtmlFiles;
    private HtmlFilesAdapter htmlFilesAdapter;

    private int currentMode = MODE_FILES;

    // Single-files mode state
    private final List<Uri> htmlUris = new ArrayList<>();
    private final List<String> htmlNames = new ArrayList<>();
    private Uri cssUri = null;
    private Uri jsUri = null;

    // ZIP / Folder staged files state (relativePath -> File)
    private File stagingRootDir = null;
    private final Map<String, File> stagedHtmlFiles = new LinkedHashMap<>();
    private final Map<String, File> stagedCssFiles = new LinkedHashMap<>();
    private final Map<String, File> stagedJsFiles = new LinkedHashMap<>();
    private final Map<String, File> stagedAssetFiles = new LinkedHashMap<>();

    // HTML inspection flags
    private boolean detectedEmbeddedCss = false;
    private boolean detectedEmbeddedJs = false;
    private boolean detectedInlineStyleAttr = false;

    private ActivityResultLauncher<Intent> htmlPickerLauncher;
    private ActivityResultLauncher<Intent> cssPickerLauncher;
    private ActivityResultLauncher<Intent> jsPickerLauncher;
    private ActivityResultLauncher<Intent> zipPickerLauncher;
    private ActivityResultLauncher<Intent> folderPickerLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_import_site);

        View root = findViewById(R.id.rootImportLayout);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets insetsType = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars() |
                    androidx.core.view.WindowInsetsCompat.Type.ime()
            );
            v.setPadding(0, insetsType.top, 0, insetsType.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        etProjectName = findViewById(R.id.etProjectName);
        btnImport = findViewById(R.id.btnImport);
        layoutLoading = findViewById(R.id.layoutLoading);

        toggleImportMode = findViewById(R.id.toggleImportMode);
        layoutZipPicker = findViewById(R.id.layoutZipPicker);
        layoutFolderPicker = findViewById(R.id.layoutFolderPicker);
        tvZipFileName = findViewById(R.id.tvZipFileName);
        tvFolderName = findViewById(R.id.tvFolderName);
        tvImportSummary = findViewById(R.id.tvImportSummary);

        btnSelectHtml = findViewById(R.id.btnSelectHtml);
        btnSelectCss = findViewById(R.id.btnSelectCss);
        btnSelectJs = findViewById(R.id.btnSelectJs);
        layoutSeparateCss = findViewById(R.id.layoutSeparateCss);
        layoutSeparateJs = findViewById(R.id.layoutSeparateJs);
        switchSeparateCss = findViewById(R.id.switchSeparateCss);
        switchSeparateJs = findViewById(R.id.switchSeparateJs);
        switchConvertCss = findViewById(R.id.switchConvertCss);
        switchConvertJs = findViewById(R.id.switchConvertJs);
        toggleImportStylingMode = findViewById(R.id.toggleImportStylingMode);
        tvConvertCssDesc = findViewById(R.id.tvConvertCssDesc);
        tvConvertJsDesc = findViewById(R.id.tvConvertJsDesc);
        tvInlineStylesDesc = findViewById(R.id.tvInlineStylesDesc);
        tvImportNotice = findViewById(R.id.tvImportNotice);

        tvCssFileName = findViewById(R.id.tvCssFileName);
        tvJsFileName = findViewById(R.id.tvJsFileName);
        btnRemoveCss = findViewById(R.id.btnRemoveCss);
        btnRemoveJs = findViewById(R.id.btnRemoveJs);

        rvHtmlFiles = findViewById(R.id.rvHtmlFiles);
        rvHtmlFiles.setLayoutManager(new LinearLayoutManager(this));
        htmlFilesAdapter = new HtmlFilesAdapter();
        rvHtmlFiles.setAdapter(htmlFilesAdapter);

        setupLaunchers();
        setupListeners();
        applyModeUi(MODE_FILES);
        refreshConversionOptionsUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupStagingDir();
    }

    private void setupLaunchers() {
        htmlPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        if (data.getClipData() != null) {
                            int count = data.getClipData().getItemCount();
                            for (int i = 0; i < count; i++) {
                                addHtmlUri(data.getClipData().getItemAt(i).getUri());
                            }
                        } else if (data.getData() != null) {
                            addHtmlUri(data.getData());
                        }
                        htmlFilesAdapter.notifyDataSetChanged();
                        analyzeSelectedHtmlAsync();
                    }
                }
        );

        cssPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            cssUri = uri;
                            String name = resolveFileName(uri);
                            tvCssFileName.setText(name != null ? name : "style.css");
                            btnRemoveCss.setVisibility(View.VISIBLE);
                            refreshConversionOptionsUi();
                        }
                    }
                }
        );

        jsPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            jsUri = uri;
                            String name = resolveFileName(uri);
                            tvJsFileName.setText(name != null ? name : "script.js");
                            btnRemoveJs.setVisibility(View.VISIBLE);
                            refreshConversionOptionsUi();
                        }
                    }
                }
        );

        zipPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            loadZipWebsiteAsync(uri);
                        }
                    }
                }
        );

        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri treeUri = result.getData().getData();
                        if (treeUri != null) {
                            loadFolderWebsiteAsync(treeUri);
                        }
                    }
                }
        );
    }

    private void setupListeners() {
        if (toggleImportMode != null) {
            toggleImportMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                if (checkedId == R.id.btnModeZip) {
                    applyModeUi(MODE_ZIP);
                } else if (checkedId == R.id.btnModeFolder) {
                    applyModeUi(MODE_FOLDER);
                } else {
                    applyModeUi(MODE_FILES);
                }
            });
        }

        findViewById(R.id.btnSelectZip).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            zipPickerLauncher.launch(Intent.createChooser(intent, "Select Website ZIP"));
        });

        findViewById(R.id.btnSelectFolder).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            folderPickerLauncher.launch(intent);
        });

        btnSelectHtml.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("text/html");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            htmlPickerLauncher.launch(Intent.createChooser(intent, "Select HTML File(s)"));
        });

        btnSelectCss.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("text/css");
            cssPickerLauncher.launch(intent);
        });

        btnSelectJs.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/javascript");
            jsPickerLauncher.launch(intent);
        });

        btnRemoveCss.setOnClickListener(v -> {
            cssUri = null;
            stagedCssFiles.clear();
            tvCssFileName.setText("Select CSS File");
            btnRemoveCss.setVisibility(View.GONE);
            refreshConversionOptionsUi();
        });

        btnRemoveJs.setOnClickListener(v -> {
            jsUri = null;
            stagedJsFiles.clear();
            tvJsFileName.setText("Select JavaScript File");
            btnRemoveJs.setVisibility(View.GONE);
            refreshConversionOptionsUi();
        });

        if (switchSeparateCss != null) {
            switchSeparateCss.setOnCheckedChangeListener((btn, isChecked) -> refreshConversionOptionsUi());
        }
        if (switchSeparateJs != null) {
            switchSeparateJs.setOnCheckedChangeListener((btn, isChecked) -> refreshConversionOptionsUi());
        }
        if (toggleImportStylingMode != null) {
            toggleImportStylingMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) updateImportStylingModeDesc(checkedId);
            });
        }

        btnImport.setOnClickListener(v -> {
            if (currentMode == MODE_FILES && htmlUris.isEmpty()) {
                Toast.makeText(this, "Please select at least one HTML page to import", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentMode != MODE_FILES && stagedHtmlFiles.isEmpty()) {
                Toast.makeText(this, "No HTML pages found in the selected " + (currentMode == MODE_ZIP ? "ZIP archive" : "folder"), Toast.LENGTH_SHORT).show();
                return;
            }
            if (layoutLoading != null) layoutLoading.setVisibility(View.VISIBLE);
            btnImport.setEnabled(false);
            performSiteImport();
        });
    }

    private void applyModeUi(int mode) {
        currentMode = mode;
        boolean isFiles = (mode == MODE_FILES);
        layoutZipPicker.setVisibility(mode == MODE_ZIP ? View.VISIBLE : View.GONE);
        layoutFolderPicker.setVisibility(mode == MODE_FOLDER ? View.VISIBLE : View.GONE);
        btnSelectHtml.setVisibility(isFiles ? View.VISIBLE : View.GONE);
        btnSelectCss.setVisibility(isFiles ? View.VISIBLE : View.GONE);
        btnSelectJs.setVisibility(isFiles ? View.VISIBLE : View.GONE);
        if (isFiles) {
            tvImportSummary.setVisibility(View.GONE);
        }
        refreshConversionOptionsUi();
    }

    private void addHtmlUri(Uri uri) {
        if (!htmlUris.contains(uri)) {
            htmlUris.add(uri);
            String name = resolveFileName(uri);
            if (name == null) name = "page_" + (htmlUris.size()) + ".html";
            htmlNames.add(name);
        }
    }

    private void loadZipWebsiteAsync(Uri zipUri) {
        if (layoutLoading != null) layoutLoading.setVisibility(View.VISIBLE);
        String zipDisplayName = resolveFileName(zipUri);
        AppExecutors.io().execute(() -> {
            cleanupStagingDir();
            File tempDir = new File(getCacheDir(), "import_staging/" + UUID.randomUUID());
            tempDir.mkdirs();

            boolean ok = false;
            try (InputStream is = getContentResolver().openInputStream(zipUri);
                 ZipInputStream zis = new ZipInputStream(is)) {
                String canonicalRoot = tempDir.getCanonicalPath() + File.separator;
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        zis.closeEntry();
                        continue;
                    }
                    String entryName = entry.getName().replace('\\', '/');
                    if (entryName.startsWith("__MACOSX/") || entryName.endsWith(".DS_Store")) {
                        zis.closeEntry();
                        continue;
                    }
                    File outFile = new File(tempDir, entryName);
                    if (!outFile.getCanonicalPath().startsWith(canonicalRoot)) {
                        zis.closeEntry();
                        continue;
                    }
                    if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    zis.closeEntry();
                }
                ok = true;
            } catch (Exception e) {
                Log.e("ImportSiteActivity", "ZIP extraction failed: " + e.getMessage(), e);
            }

            final boolean success = ok;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (layoutLoading != null) layoutLoading.setVisibility(View.GONE);
                if (!success) {
                    Toast.makeText(this, "Failed to read ZIP archive", Toast.LENGTH_LONG).show();
                    return;
                }
                stagingRootDir = tempDir;
                if (zipDisplayName != null) {
                    tvZipFileName.setText(zipDisplayName);
                    if (etProjectName != null && (etProjectName.getText() == null || etProjectName.getText().toString().trim().isEmpty())) {
                        String base = zipDisplayName.replaceAll("(?i)\\.zip$", "");
                        etProjectName.setText(base);
                    }
                }
                indexStagedDirectory(tempDir);
            });
        });
    }

    private void loadFolderWebsiteAsync(Uri treeUri) {
        if (layoutLoading != null) layoutLoading.setVisibility(View.VISIBLE);
        DocumentFile rootDoc = DocumentFile.fromTreeUri(this, treeUri);
        String folderName = rootDoc != null && rootDoc.getName() != null ? rootDoc.getName() : "Website Folder";

        AppExecutors.io().execute(() -> {
            cleanupStagingDir();
            File tempDir = new File(getCacheDir(), "import_staging/" + UUID.randomUUID());
            tempDir.mkdirs();

            boolean ok = false;
            if (rootDoc != null && rootDoc.isDirectory()) {
                try {
                    copyDocumentTree(rootDoc, tempDir);
                    ok = true;
                } catch (Exception e) {
                    Log.e("ImportSiteActivity", "Folder copy failed: " + e.getMessage(), e);
                }
            }

            final boolean success = ok;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (layoutLoading != null) layoutLoading.setVisibility(View.GONE);
                if (!success) {
                    Toast.makeText(this, "Failed to read selected folder", Toast.LENGTH_LONG).show();
                    return;
                }
                stagingRootDir = tempDir;
                tvFolderName.setText(folderName);
                if (etProjectName != null && (etProjectName.getText() == null || etProjectName.getText().toString().trim().isEmpty())) {
                    etProjectName.setText(folderName);
                }
                indexStagedDirectory(tempDir);
            });
        });
    }

    private void copyDocumentTree(DocumentFile dirDoc, File destDir) throws Exception {
        byte[] buffer = new byte[8192];
        for (DocumentFile child : dirDoc.listFiles()) {
            String name = child.getName();
            if (name == null || name.startsWith(".")) continue;
            if (child.isDirectory()) {
                File sub = new File(destDir, name);
                sub.mkdirs();
                copyDocumentTree(child, sub);
            } else if (child.isFile()) {
                File out = new File(destDir, name);
                try (InputStream is = getContentResolver().openInputStream(child.getUri());
                     FileOutputStream fos = new FileOutputStream(out)) {
                    if (is != null) {
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    private void indexStagedDirectory(File rootDir) {
        stagedHtmlFiles.clear();
        stagedCssFiles.clear();
        stagedJsFiles.clear();
        stagedAssetFiles.clear();
        htmlNames.clear();
        htmlUris.clear();

        // If rootDir contains a single top-level directory and no files, descend into it
        File scanRoot = rootDir;
        File[] topEntries = rootDir.listFiles();
        if (topEntries != null && topEntries.length == 1 && topEntries[0].isDirectory()) {
            scanRoot = topEntries[0];
        }

        collectStagedFilesRecursive(scanRoot, scanRoot);

        htmlNames.addAll(stagedHtmlFiles.keySet());
        htmlFilesAdapter.notifyDataSetChanged();

        tvImportSummary.setVisibility(View.VISIBLE);
        tvImportSummary.setText(String.format(
                Locale.US,
                "Detected: %d HTML, %d CSS, %d JS, %d asset(s)",
                stagedHtmlFiles.size(), stagedCssFiles.size(), stagedJsFiles.size(), stagedAssetFiles.size()
        ));

        analyzeSelectedHtmlAsync();
    }

    private void collectStagedFilesRecursive(File baseDir, File currentDir) {
        File[] children = currentDir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                collectStagedFilesRecursive(baseDir, f);
            } else {
                String rel = baseDir.toURI().relativize(f.toURI()).getPath().replace('\\', '/');
                String lower = rel.toLowerCase(Locale.US);
                if (lower.endsWith(".html") || lower.endsWith(".htm")) {
                    stagedHtmlFiles.put(rel, f);
                } else if (lower.endsWith(".css")) {
                    stagedCssFiles.put(rel, f);
                } else if (lower.endsWith(".js")) {
                    stagedJsFiles.put(rel, f);
                } else {
                    stagedAssetFiles.put(rel, f);
                }
            }
        }
    }

    private void analyzeSelectedHtmlAsync() {
        final int modeSnapshot = currentMode;
        final List<Uri> uriSnapshot = new ArrayList<>(htmlUris);
        final List<File> stagedSnapshot = new ArrayList<>(stagedHtmlFiles.values());

        AppExecutors.io().execute(() -> {
            boolean foundEmbeddedCss = false;
            boolean foundEmbeddedJs = false;
            boolean foundInlineStyleAttr = false;

            List<String> contents = new ArrayList<>();
            if (modeSnapshot == MODE_FILES) {
                for (Uri u : uriSnapshot) {
                    String c = readUriContent(u);
                    if (c != null) contents.add(c);
                }
            } else {
                for (File f : stagedSnapshot) {
                    String c = FileUtil.readFile(f.getAbsolutePath());
                    if (c != null) contents.add(c);
                }
            }

            for (String html : contents) {
                if (!foundEmbeddedCss) {
                    Matcher sm = STYLE_TAG_PATTERN.matcher(html);
                    while (sm.find()) {
                        if (sm.group(1) != null && !sm.group(1).trim().isEmpty()) {
                            foundEmbeddedCss = true;
                            break;
                        }
                    }
                }
                if (!foundEmbeddedJs) {
                    Matcher jm = SCRIPT_TAG_PATTERN.matcher(html);
                    while (jm.find()) {
                        String attrs = jm.group(1) != null ? jm.group(1) : "";
                        String body = jm.group(2) != null ? jm.group(2) : "";
                        if (!SRC_ATTR_PATTERN.matcher(attrs).find() && !body.trim().isEmpty()) {
                            foundEmbeddedJs = true;
                            break;
                        }
                    }
                }
                if (!foundInlineStyleAttr && INLINE_STYLE_ATTR_PATTERN.matcher(html).find()) {
                    foundInlineStyleAttr = true;
                }
            }

            final boolean embCss = foundEmbeddedCss;
            final boolean embJs = foundEmbeddedJs;
            final boolean inlStyle = foundInlineStyleAttr;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                detectedEmbeddedCss = embCss;
                detectedEmbeddedJs = embJs;
                detectedInlineStyleAttr = inlStyle;

                if (toggleImportStylingMode != null) {
                    if (inlStyle) {
                        toggleImportStylingMode.check(R.id.btnImportStyleInline);
                        updateImportStylingModeDesc(R.id.btnImportStyleInline);
                    } else {
                        toggleImportStylingMode.check(R.id.btnImportStyleUserOnly);
                        updateImportStylingModeDesc(R.id.btnImportStyleUserOnly);
                    }
                }

                refreshConversionOptionsUi();
            });
        });
    }

    private void updateImportStylingModeDesc(int checkedId) {
        if (tvInlineStylesDesc == null) return;
        if (checkedId == R.id.btnImportStyleInline) {
            tvInlineStylesDesc.setText(detectedInlineStyleAttr
                    ? "Inline style=\"...\" detected — Inline mode enabled."
                    : "Inline mode: widget styles are written inline in HTML.");
        } else if (checkedId == R.id.btnImportStyleConvert) {
            tvInlineStylesDesc.setText("Convert mode: widget styles are converted into CSS blocks in Logic Editor.");
        } else {
            tvInlineStylesDesc.setText(detectedInlineStyleAttr
                    ? "User Only: skips auto-generated widget styles and keeps your CSS."
                    : "No inline style=\"...\" found — User Only selected to prevent extra widget CSS blocks.");
        }
    }

    private void refreshConversionOptionsUi() {
        boolean hasSeparateCss = (currentMode == MODE_FILES) ? (cssUri != null) : !stagedCssFiles.isEmpty();
        boolean hasSeparateJs = (currentMode == MODE_FILES) ? (jsUri != null) : !stagedJsFiles.isEmpty();

        // CSS options
        if (layoutSeparateCss != null && switchSeparateCss != null) {
            if (detectedEmbeddedCss) {
                layoutSeparateCss.setVisibility(View.VISIBLE);
            } else {
                layoutSeparateCss.setVisibility(View.GONE);
                switchSeparateCss.setChecked(false);
            }
        }
        boolean separateCssOn = switchSeparateCss != null && switchSeparateCss.isChecked();
        boolean canConvertCss = hasSeparateCss || (detectedEmbeddedCss && separateCssOn);
        if (switchConvertCss != null) {
            boolean wasEnabled = switchConvertCss.isEnabled();
            switchConvertCss.setEnabled(canConvertCss);
            if (!canConvertCss) {
                switchConvertCss.setChecked(false);
            } else if (!wasEnabled) {
                switchConvertCss.setChecked(true);
            }
        }
        if (tvConvertCssDesc != null) {
            if (!canConvertCss && detectedEmbeddedCss) {
                tvConvertCssDesc.setText("HTML contains embedded <style>; will import directly into assets");
            } else if (!canConvertCss) {
                tvConvertCssDesc.setText("No separate CSS file detected");
            } else {
                tvConvertCssDesc.setText("Convert CSS rules to visual blocks");
            }
        }

        // JS options
        if (layoutSeparateJs != null && switchSeparateJs != null) {
            if (detectedEmbeddedJs) {
                layoutSeparateJs.setVisibility(View.VISIBLE);
            } else {
                layoutSeparateJs.setVisibility(View.GONE);
                switchSeparateJs.setChecked(false);
            }
        }
        boolean separateJsOn = switchSeparateJs != null && switchSeparateJs.isChecked();
        boolean canConvertJs = hasSeparateJs || (detectedEmbeddedJs && separateJsOn);
        if (switchConvertJs != null) {
            boolean wasEnabled = switchConvertJs.isEnabled();
            switchConvertJs.setEnabled(canConvertJs);
            if (!canConvertJs) {
                switchConvertJs.setChecked(false);
            } else if (!wasEnabled) {
                switchConvertJs.setChecked(true);
            }
        }
        if (tvConvertJsDesc != null) {
            if (!canConvertJs && detectedEmbeddedJs) {
                tvConvertJsDesc.setText("HTML contains embedded <script>; will import directly into assets");
            } else if (!canConvertJs) {
                tvConvertJsDesc.setText("No separate JS file detected");
            } else {
                tvConvertJsDesc.setText("Convert JS actions & functions to visual blocks");
            }
        }
    }

    private void performSiteImport() {
        String rawName = etProjectName != null && etProjectName.getText() != null
                ? etProjectName.getText().toString().trim()
                : "";
        final String projectName = rawName.isEmpty() ? "Imported Website" : rawName;
        final boolean separateCss = switchSeparateCss != null && switchSeparateCss.isChecked();
        final boolean separateJs = switchSeparateJs != null && switchSeparateJs.isChecked();
        final boolean convertCss = switchConvertCss != null && switchConvertCss.isEnabled() && switchConvertCss.isChecked();
        final boolean convertJs = switchConvertJs != null && switchConvertJs.isEnabled() && switchConvertJs.isChecked();
        final int stylingCheckedId = toggleImportStylingMode != null
                ? toggleImportStylingMode.getCheckedButtonId()
                : R.id.btnImportStyleUserOnly;
        final boolean useInlineStyles = (stylingCheckedId == R.id.btnImportStyleInline);
        final boolean userCssOnly = (stylingCheckedId == R.id.btnImportStyleUserOnly);

        final int modeSnapshot = currentMode;
        final List<Uri> htmlUrisCopy = new ArrayList<>(htmlUris);
        final List<String> htmlNamesCopy = new ArrayList<>(htmlNames);
        final Uri cssUriCopy = cssUri;
        final Uri jsUriCopy = jsUri;
        final Map<String, File> stagedHtmlCopy = new LinkedHashMap<>(stagedHtmlFiles);
        final Map<String, File> stagedCssCopy = new LinkedHashMap<>(stagedCssFiles);
        final Map<String, File> stagedJsCopy = new LinkedHashMap<>(stagedJsFiles);
        final Map<String, File> stagedAssetsCopy = new LinkedHashMap<>(stagedAssetFiles);

        AppExecutors.io().execute(() -> {
            final String projectId = ProjectDataManager.generateProjectId(this);
            File dir = new File(FileUtil.getDragWebDir(this), "projects");
            if (!dir.exists()) dir.mkdirs();

            String extPath = FileUtil.getDragWebDir(this).getAbsolutePath() + "/projects/" + projectId;
            File extAssetsDir = new File(extPath, "assets");
            extAssetsDir.mkdirs();

            List<String> combinedLibraries = new ArrayList<>();
            List<String> pageNamesList = new ArrayList<>();
            Map<String, List<Map<String, Object>>> pageWidgetTrees = new HashMap<>();
            Map<String, ArrayList<BlockBean>> pageHtmlBlocks = new HashMap<>();

            // Build unified list of HTML entries (relName -> rawContent)
            List<String> entryNames = new ArrayList<>();
            List<String> entryContents = new ArrayList<>();
            if (modeSnapshot == MODE_FILES) {
                for (int i = 0; i < htmlUrisCopy.size(); i++) {
                    String content = readUriContent(htmlUrisCopy.get(i));
                    if (content != null && !content.trim().isEmpty()) {
                        entryNames.add(htmlNamesCopy.get(i));
                        entryContents.add(content);
                    }
                }
            } else {
                for (Map.Entry<String, File> e : stagedHtmlCopy.entrySet()) {
                    String content = FileUtil.readFile(e.getValue().getAbsolutePath());
                    if (content != null && !content.trim().isEmpty()) {
                        entryNames.add(e.getKey());
                        entryContents.add(content);
                    }
                }
            }

            int indexIdx = -1;
            for (int i = 0; i < entryNames.size(); i++) {
                String lower = entryNames.get(i).toLowerCase(Locale.ROOT);
                if (lower.equals("index.html") || lower.endsWith("/index.html") || lower.contains("index")) {
                    indexIdx = i;
                    break;
                }
            }
            if (indexIdx == -1 && !entryNames.isEmpty()) indexIdx = 0;

            StringBuilder extractedCssSb = new StringBuilder();
            StringBuilder extractedJsSb = new StringBuilder();
            HtmlCssImporter treeExtractor = new HtmlCssImporter(this);

            for (int i = 0; i < entryNames.size(); i++) {
                String rawEntryName = entryNames.get(i);
                String simpleName = new File(rawEntryName).getName().toLowerCase(Locale.ROOT);
                String pageName = simpleName.contains(".")
                        ? simpleName.substring(0, simpleName.lastIndexOf('.'))
                        : simpleName;
                if (i == indexIdx || "index".equals(pageName)) {
                    pageName = "index";
                }
                if (pageNamesList.contains(pageName)) {
                    pageName = pageName + "_" + i;
                }
                pageNamesList.add(pageName);

                String htmlContent = entryContents.get(i);
                if (separateCss) {
                    htmlContent = extractEmbeddedStyles(htmlContent, extractedCssSb);
                }
                if (separateJs) {
                    htmlContent = extractEmbeddedScripts(htmlContent, extractedJsSb);
                }

                ArrayList<BlockBean> rustHtmlBeans = BlocksLoader.generateHtmlBlocksFromCode(this, htmlContent);
                if (rustHtmlBeans != null && !rustHtmlBeans.isEmpty()) {
                    pageHtmlBlocks.put(pageName, rustHtmlBeans);
                }

                HtmlCssImporter.ImportResult result = treeExtractor.importHtmlCss(htmlContent, null);
                if (result.success) {
                    pageWidgetTrees.put(pageName, result.widgetTree);
                    if (result.enabledLibraries != null) {
                        for (String lib : result.enabledLibraries) {
                            if (!combinedLibraries.contains(lib)) combinedLibraries.add(lib);
                        }
                    }
                }

                try {
                    File targetHtmlFile = new File(extAssetsDir, pageName + ".html");
                    FileUtil.writeFile(targetHtmlFile.getAbsolutePath(), htmlContent);
                } catch (Exception e) {
                    Log.e("ImportSiteActivity", "Failed to copy html page: " + e.getMessage());
                }
            }

            // Save page layouts and HTML blocks
            DesignDataManager.initMaps();
            PageManager pm = new PageManager(this, projectId);
            for (String pName : pageNamesList) {
                List<Map<String, Object>> widgetTree = pageWidgetTrees.get(pName);
                if (widgetTree == null) widgetTree = new ArrayList<>();
                pm.savePageLayout(pName, new Gson().toJson(widgetTree));

                ArrayList<BlockBean> htmlBeans = pageHtmlBlocks.get(pName);
                if (htmlBeans != null && !htmlBeans.isEmpty()) {
                    String cleanPName = DesignDataManager.getCleanPageName(pName);
                    DesignDataManager.setBlocks(cleanPName, "onCreate_initializeLogic", htmlBeans);
                }
            }

            // Process CSS files + extracted <style>
            Map<String, String> cssToSave = new LinkedHashMap<>();
            if (modeSnapshot == MODE_FILES) {
                String cssContent = cssUriCopy != null ? readUriContent(cssUriCopy) : null;
                if (cssContent != null && !cssContent.trim().isEmpty()) {
                    cssToSave.put("css/style.css", cssContent);
                }
            } else {
                for (Map.Entry<String, File> e : stagedCssCopy.entrySet()) {
                    String c = FileUtil.readFile(e.getValue().getAbsolutePath());
                    if (c != null) cssToSave.put(e.getKey(), c);
                }
            }
            if (extractedCssSb.length() > 0) {
                String existing = cssToSave.getOrDefault("css/style.css", "");
                cssToSave.put("css/style.css", (existing.isEmpty() ? "" : existing + "\n\n") + extractedCssSb.toString().trim());
            }

            for (Map.Entry<String, String> e : cssToSave.entrySet()) {
                String relCss = e.getKey();
                String cssContent = e.getValue();
                if (cssContent == null || cssContent.trim().isEmpty()) continue;

                if (convertCss) {
                    ArrayList<BlockBean> cssBeans = BlocksLoader.generateCssBlocksFromCode(this, cssContent);
                    if (cssBeans != null && !cssBeans.isEmpty()) {
                        String cleanCssName = DesignDataManager.getCleanPageName(relCss);
                        DesignDataManager.setBlocks(cleanCssName, "onCreate_initializeLogic", cssBeans);
                    }
                }
                File targetStyleFile = new File(extAssetsDir, relCss);
                if (targetStyleFile.getParentFile() != null) targetStyleFile.getParentFile().mkdirs();
                FileUtil.writeFile(targetStyleFile.getAbsolutePath(), cssContent);
            }

            // Process JS files + extracted <script> (converting functions into MoreBlocks when convertJs is enabled)
            Map<String, String> jsToSave = new LinkedHashMap<>();
            if (modeSnapshot == MODE_FILES) {
                String jsContent = jsUriCopy != null ? readUriContent(jsUriCopy) : null;
                if (jsContent != null && !jsContent.trim().isEmpty()) {
                    jsToSave.put("js/script.js", jsContent);
                }
            } else {
                for (Map.Entry<String, File> e : stagedJsCopy.entrySet()) {
                    String c = FileUtil.readFile(e.getValue().getAbsolutePath());
                    if (c != null) jsToSave.put(e.getKey(), c);
                }
            }
            if (extractedJsSb.length() > 0) {
                String existing = jsToSave.getOrDefault("js/script.js", "");
                jsToSave.put("js/script.js", (existing.isEmpty() ? "" : existing + "\n\n") + extractedJsSb.toString().trim());
            }

            for (Map.Entry<String, String> e : jsToSave.entrySet()) {
                String relJs = e.getKey();
                String jsContent = e.getValue();
                if (jsContent == null || jsContent.trim().isEmpty()) continue;

                if (convertJs) {
                    ArrayList<BlockBean> jsBeans = JsMoreBlockConverter.convertJsWithMoreBlocks(
                            this, projectId, relJs, jsContent);
                    if (jsBeans != null && !jsBeans.isEmpty()) {
                        String cleanJsName = DesignDataManager.getCleanPageName(relJs);
                        DesignDataManager.setBlocks(cleanJsName, "onCreate_initializeLogic", jsBeans);
                    }
                }
                File targetJsFile = new File(extAssetsDir, relJs);
                if (targetJsFile.getParentFile() != null) targetJsFile.getParentFile().mkdirs();
                FileUtil.writeFile(targetJsFile.getAbsolutePath(), jsContent);
            }

            // Copy remaining non-code assets (images, fonts, etc.) from ZIP / Folder
            for (Map.Entry<String, File> e : stagedAssetsCopy.entrySet()) {
                File dest = new File(extAssetsDir, e.getKey());
                copyFileBinary(e.getValue(), dest);
            }

            DesignDataManager.saveAllSavedLogic(this, projectId);

            // Save project metadata
            File metaFile = new File(extPath, "project.meta");
            Map<String, String> meta = new HashMap<>();
            meta.put("id", projectId);
            meta.put("name", projectName);
            meta.put("description", "Imported website");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            meta.put("created", sdf.format(new Date()));
            FileUtil.writeFile(metaFile.getAbsolutePath(), new Gson().toJson(meta));

            // Configure Theme styling mode based on HTML detection / user choice
            ThemeManager tm = new ThemeManager();
            tm.setUseInlineStyles(useInlineStyles);
            tm.setDisableAllStyling(userCssOnly);
            tm.setDisableDefaultStyles(true);
            FileUtil.writeFile(new File(extPath, "theme.json").getAbsolutePath(), tm.toJson());

            if (!combinedLibraries.isEmpty()) {
                IconLibraryManager ilm = new IconLibraryManager(this, projectId);
                for (String libId : combinedLibraries) {
                    ilm.enable(libId);
                }
            }

            cleanupStagingDir();

            AppExecutors.mainThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (layoutLoading != null) layoutLoading.setVisibility(View.GONE);
                btnImport.setEnabled(true);
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("project_id", projectId);
                intent.putExtra("project_name", projectName);
                startActivity(intent);
                finish();
            });
        });
    }

    private String extractEmbeddedStyles(String html, StringBuilder cssOut) {
        Matcher m = STYLE_TAG_PATTERN.matcher(html);
        StringBuffer sb = new StringBuffer();
        boolean extracted = false;
        while (m.find()) {
            String css = m.group(1) != null ? m.group(1).trim() : "";
            if (!css.isEmpty()) {
                if (cssOut.length() > 0) cssOut.append("\n\n");
                cssOut.append(css);
                extracted = true;
            }
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        String result = sb.toString();
        if (extracted && !result.toLowerCase(Locale.ROOT).contains("css/style.css")) {
            String linkTag = "<link rel=\"stylesheet\" href=\"css/style.css\">";
            int headClose = result.toLowerCase(Locale.ROOT).indexOf("</head>");
            if (headClose >= 0) {
                result = result.substring(0, headClose) + "  " + linkTag + "\n" + result.substring(headClose);
            } else {
                result = linkTag + "\n" + result;
            }
        }
        return result;
    }

    private String extractEmbeddedScripts(String html, StringBuilder jsOut) {
        Matcher m = SCRIPT_TAG_PATTERN.matcher(html);
        StringBuffer sb = new StringBuffer();
        boolean extracted = false;
        while (m.find()) {
            String attrs = m.group(1) != null ? m.group(1) : "";
            String body = m.group(2) != null ? m.group(2).trim() : "";
            if (!SRC_ATTR_PATTERN.matcher(attrs).find() && !body.isEmpty()) {
                if (jsOut.length() > 0) jsOut.append("\n\n");
                jsOut.append(body);
                extracted = true;
                m.appendReplacement(sb, "");
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        String result = sb.toString();
        if (extracted && !result.toLowerCase(Locale.ROOT).contains("js/script.js")) {
            String scriptTag = "<script src=\"js/script.js\"></script>";
            int bodyClose = result.toLowerCase(Locale.ROOT).lastIndexOf("</body>");
            if (bodyClose >= 0) {
                result = result.substring(0, bodyClose) + "  " + scriptTag + "\n" + result.substring(bodyClose);
            } else {
                result = result + "\n" + scriptTag;
            }
        }
        return result;
    }

    private void copyFileBinary(File src, File dest) {
        try {
            if (dest.getParentFile() != null) dest.getParentFile().mkdirs();
            byte[] buf = new byte[8192];
            try (FileInputStream fis = new FileInputStream(src);
                 FileOutputStream fos = new FileOutputStream(dest)) {
                int len;
                while ((len = fis.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
            }
        } catch (Exception e) {
            Log.e("ImportSiteActivity", "Failed to copy asset " + src.getName() + ": " + e.getMessage());
        }
    }

    private void cleanupStagingDir() {
        if (stagingRootDir != null && stagingRootDir.exists()) {
            deleteRecursive(stagingRootDir);
            stagingRootDir = null;
        }
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        f.delete();
    }

    private String resolveFileName(Uri uri) {
        try {
            android.database.Cursor c = getContentResolver().query(
                    uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                    null, null, null
            );
            if (c != null) {
                if (c.moveToFirst()) {
                    int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        String name = c.getString(idx);
                        c.close();
                        return name;
                    }
                }
                c.close();
            }
        } catch (Exception ignored) {
            Log.e("ImportSite", "Error", ignored);
        }
        return null;
    }

    private String readUriContent(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e("ImportSiteActivity", "Failed to read URI: " + e.getMessage());
            return null;
        }
    }

    // HTML List adapter
    class HtmlFilesAdapter extends RecyclerView.Adapter<HtmlFilesAdapter.ViewHolder> {

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvHtmlName;
            ImageButton btnDeleteHtml;

            ViewHolder(View view) {
                super(view);
                tvHtmlName = view.findViewById(R.id.tvHtmlName);
                btnDeleteHtml = view.findViewById(R.id.btnDeleteHtml);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_imported_html, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String name = htmlNames.get(position);
            holder.tvHtmlName.setText(name);

            if (htmlNames.size() == 1) {
                holder.itemView.setBackgroundResource(R.drawable.item_single_bg);
            } else if (position == 0) {
                holder.itemView.setBackgroundResource(R.drawable.item_top_bg);
            } else if (position == htmlNames.size() - 1) {
                holder.itemView.setBackgroundResource(R.drawable.item_bottom_bg);
            } else {
                holder.itemView.setBackgroundResource(R.drawable.item_mid_bg);
            }

            ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) layoutParams;
                lp.setMargins(0, 0, 0, 0);
                holder.itemView.setLayoutParams(lp);
            }

            holder.btnDeleteHtml.setOnClickListener(v -> {
                if (currentMode == MODE_FILES) {
                    if (position < htmlUris.size()) htmlUris.remove(position);
                    if (position < htmlNames.size()) htmlNames.remove(position);
                } else {
                    if (position < htmlNames.size()) {
                        String removed = htmlNames.remove(position);
                        stagedHtmlFiles.remove(removed);
                    }
                }
                notifyDataSetChanged();
                analyzeSelectedHtmlAsync();
            });
        }

        @Override
        public int getItemCount() {
            return htmlNames.size();
        }
    }
}
