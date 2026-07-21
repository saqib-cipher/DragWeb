package sketchweb.gl;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ImportSiteActivity extends AppCompatActivity {

    private TextInputEditText etProjectName;
    private MaterialButton btnImport;
    private View layoutLoading;
    private MaterialSwitch switchConvertCss;
    private MaterialSwitch switchConvertJs;

    private TextView tvCssFileName;
    private TextView tvJsFileName;
    private View btnRemoveCss;
    private View btnRemoveJs;

    private RecyclerView rvHtmlFiles;
    private HtmlFilesAdapter htmlFilesAdapter;

    private final List<Uri> htmlUris = new ArrayList<>();
    private final List<String> htmlNames = new ArrayList<>();

    private Uri cssUri = null;
    private Uri jsUri = null;

    private ActivityResultLauncher<Intent> htmlPickerLauncher;
    private ActivityResultLauncher<Intent> cssPickerLauncher;
    private ActivityResultLauncher<Intent> jsPickerLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
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
        switchConvertCss = findViewById(R.id.switchConvertCss);
        switchConvertJs = findViewById(R.id.switchConvertJs);

        tvCssFileName = findViewById(R.id.tvCssFileName);
        tvJsFileName = findViewById(R.id.tvJsFileName);
        btnRemoveCss = findViewById(R.id.btnRemoveCss);
        btnRemoveJs = findViewById(R.id.btnRemoveJs);

        rvHtmlFiles = findViewById(R.id.rvHtmlFiles);
        rvHtmlFiles.setLayoutManager(new LinearLayoutManager(this));
        htmlFilesAdapter = new HtmlFilesAdapter();
        rvHtmlFiles.setAdapter(htmlFilesAdapter);

        // Pickers launchers
        htmlPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            Uri uri = data.getClipData().getItemAt(i).getUri();
                            addHtmlUri(uri);
                        }
                    } else if (data.getData() != null) {
                        addHtmlUri(data.getData());
                    }
                    htmlFilesAdapter.notifyDataSetChanged();
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
                    }
                }
            }
        );

        findViewById(R.id.btnSelectHtml).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("text/html");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            htmlPickerLauncher.launch(Intent.createChooser(intent, "Select HTML File(s)"));
        });

        findViewById(R.id.btnSelectCss).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("text/css");
            cssPickerLauncher.launch(intent);
        });

        findViewById(R.id.btnSelectJs).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/javascript");
            jsPickerLauncher.launch(intent);
        });

        btnRemoveCss.setOnClickListener(v -> {
            cssUri = null;
            tvCssFileName.setText("Select CSS File");
            btnRemoveCss.setVisibility(View.GONE);
        });

        btnRemoveJs.setOnClickListener(v -> {
            jsUri = null;
            tvJsFileName.setText("Select JavaScript File");
            btnRemoveJs.setVisibility(View.GONE);
        });

        btnImport.setOnClickListener(v -> {
            if (layoutLoading != null) layoutLoading.setVisibility(View.VISIBLE);
            v.postDelayed(this::performSiteImport, 100);
        });
    }

    private void addHtmlUri(Uri uri) {
        if (!htmlUris.contains(uri)) {
            htmlUris.add(uri);
            String name = resolveFileName(uri);
            if (name == null) name = "page_" + (htmlUris.size()) + ".html";
            htmlNames.add(name);
        }
    }

    private void performSiteImport() {
        if (htmlUris.isEmpty()) {
            Toast.makeText(this, "Please select at least one HTML page to import", Toast.LENGTH_SHORT).show();
            return;
        }

        String rawName = etProjectName.getText().toString().trim();
        final String projectName = rawName.isEmpty() ? "Imported Website" : rawName;
        final String projectId = ProjectDataManager.generateProjectId(this);

        File dir = new File(getFilesDir(), "projects");
        if (!dir.exists()) dir.mkdirs();

        File logicDir = new File(dir, "logic");
        if (!logicDir.exists()) logicDir.mkdirs();

        // Standard detected libraries mapping
        List<String> combinedLibraries = new ArrayList<>();

        // Setup external structure paths
        String extPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dragweb/projects/" + projectId;
        File extAssetsDir = new File(extPath, "assets");
        extAssetsDir.mkdirs();

        List<String> pageNamesList = new ArrayList<>();
        Map<String, List<Map<String, Object>>> pageWidgetTrees = new HashMap<>();
        Map<String, List<Map<String, Object>>> pageLogicBlocks = new HashMap<>();

        // Find primary index page index or default to first
        int indexIdx = -1;
        for (int i = 0; i < htmlNames.size(); i++) {
            String name = htmlNames.get(i).toLowerCase();
            if (name.contains("index")) {
                indexIdx = i;
                break;
            }
        }
        if (indexIdx == -1) indexIdx = 0;

        // Process all HTML pages
        HtmlCssImporter importer = new HtmlCssImporter(this);
        for (int i = 0; i < htmlUris.size(); i++) {
            Uri uri = htmlUris.get(i);
            String origName = htmlNames.get(i).toLowerCase();
            String pageName = origName;
            if (pageName.contains(".")) {
                pageName = pageName.substring(0, pageName.lastIndexOf('.'));
            }
            
            // Map the primary designated page to "index"
            if (i == indexIdx || pageName.equals("index")) {
                pageName = "index";
            }

            pageNamesList.add(pageName);

            String htmlContent = readUriContent(uri);
            if (htmlContent != null) {
                HtmlCssImporter.ImportResult result = importer.importHtmlCss(htmlContent, null);
                if (result.success) {
                    pageWidgetTrees.put(pageName, result.widgetTree);
                    pageLogicBlocks.put(pageName, result.logicBlocks);
                    if (result.enabledLibraries != null) {
                        for (String lib : result.enabledLibraries) {
                            if (!combinedLibraries.contains(lib)) combinedLibraries.add(lib);
                        }
                    }
                } else {
                    Toast.makeText(this, "Failed to parse " + origName + ": " + result.message, Toast.LENGTH_SHORT).show();
                }

                // Copy original HTML page to external assets under pageName.html (or index.html)
                try {
                    File targetHtmlFile = new File(extAssetsDir, pageName + ".html");
                    FileUtil.writeFile(targetHtmlFile.getAbsolutePath(), htmlContent);
                } catch (Exception e) {
                    Log.e("ImportSiteActivity", "Failed to copy original html page: " + e.getMessage());
                }
            }
        }

        // Save layouts & logic
        for (String pName : pageNamesList) {
            List<Map<String, Object>> widgetTree = pageWidgetTrees.get(pName);
            if (widgetTree == null) widgetTree = new ArrayList<>();
            List<Map<String, Object>> htmlLogic = pageLogicBlocks.get(pName);

            if (pName.equals("index")) {
                File projectFile = new File(dir, projectId + ".json");
                FileUtil.writeFile(projectFile.getAbsolutePath(), new Gson().toJson(widgetTree));
                if (htmlLogic != null && !htmlLogic.isEmpty()) {
                    File extLogicFile = new File(extPath, "index_logic.json");
                    FileUtil.writeFile(extLogicFile.getAbsolutePath(), new Gson().toJson(htmlLogic));
                }
            } else {
                File projectFile = new File(dir, projectId + "_" + pName + ".json");
                FileUtil.writeFile(projectFile.getAbsolutePath(), new Gson().toJson(widgetTree));
                if (htmlLogic != null && !htmlLogic.isEmpty()) {
                    String cleanPName = DesignDataManager.getCleanPageName(pName);
                    File extLogicFile = new File(extPath, cleanPName + "_logic.json");
                    FileUtil.writeFile(extLogicFile.getAbsolutePath(), new Gson().toJson(htmlLogic));
                }
            }

            // Save layout file to external pages dir
            try {
                File extPagesDir = new File(extPath, "pages");
                extPagesDir.mkdirs();
                File extPageLayoutFile = new File(extPagesDir, pName + ".json");
                FileUtil.writeFile(extPageLayoutFile.getAbsolutePath(), new Gson().toJson(widgetTree));
            } catch (Exception e) {
                Log.w("ImportSite", "Failed to write external layouts: " + e.getMessage());
            }
        }

        // Save project pages.json
        try {
            File pagesJsonFile = new File(extPath, "pages.json");
            FileUtil.writeFile(pagesJsonFile.getAbsolutePath(), new Gson().toJson(pageNamesList));
        } catch (Exception e) {
            Log.e("ImportSiteActivity", "Failed to save pages list metadata: " + e.getMessage());
        }

        // Process global CSS
        List<Map<String, Object>> cssLogicBlocks = null;
        String cssContent = cssUri != null ? readUriContent(cssUri) : null;
        if (cssContent != null && !cssContent.trim().isEmpty()) {
            if (switchConvertCss.isChecked()) {
                cssLogicBlocks = importer.importCssOnly(cssContent);
            } else {
                // Wrap raw code in asdCss block
                cssLogicBlocks = new ArrayList<>();
                String blockId = "blk_asd_css_" + System.currentTimeMillis();
                Map<String, Object> block = new HashMap<>();
                block.put("id", blockId);
                block.put("action", "asdCss");
                block.put("category", "asd");
                block.put("shape", "stack");
                block.put("spec", "CSS source %s");
                block.put("paramValues", java.util.Arrays.asList(cssContent));
                block.put("params", cssContent);
                block.put("event", "immediate");
                block.put("parentBlockId", "");
                cssLogicBlocks.add(block);
            }

            if (!cssLogicBlocks.isEmpty()) {
                String cleanCssName = DesignDataManager.getCleanPageName("css/style.css");
                File cssLogicFile = new File(extPath, cleanCssName + "_logic.json");
                FileUtil.writeFile(cssLogicFile.getAbsolutePath(), new Gson().toJson(cssLogicBlocks));

                // Compile and write compiled css output to external assets/css/style.css
                try {
                    LogicBlockManager cssLogic = new LogicBlockManager(this);
                    cssLogic.fromJson(new Gson().toJson(cssLogicBlocks));
                    String baseRules = cssLogic.generateBaseCssRules();
                    String pseudoRules = cssLogic.generateCssPseudoRules();
                    String asdCss = cssLogic.generateAsdSource("css");
                    StringBuilder compiledCss = new StringBuilder();
                    compiledCss.append("/* Compiled by DragWeb */\n\n");
                    if (baseRules != null && !baseRules.trim().isEmpty()) {
                        compiledCss.append(baseRules).append("\n");
                    }
                    if (pseudoRules != null && !pseudoRules.trim().isEmpty()) {
                        compiledCss.append(pseudoRules).append("\n");
                    }
                    if (asdCss != null && !asdCss.trim().isEmpty()) {
                        compiledCss.append(asdCss).append("\n");
                    }
                    File targetStyleFile = new File(extAssetsDir, "css/style.css");
                    targetStyleFile.getParentFile().mkdirs();
                    FileUtil.writeFile(targetStyleFile.getAbsolutePath(), compiledCss.toString());
                } catch (Exception e) {
                    Log.w("ImportSite", "Failed to compile style.css: " + e.getMessage());
                }
            }
        }

        // Process global JS
        List<Map<String, Object>> jsLogicBlocks = null;
        String jsContent = jsUri != null ? readUriContent(jsUri) : null;
        if (jsContent != null && !jsContent.trim().isEmpty()) {
            if (switchConvertJs.isChecked()) {
                jsLogicBlocks = importer.importJsOnly(jsContent);
            } else {
                // Wrap raw code in asdJs block
                jsLogicBlocks = new ArrayList<>();
                String blockId = "blk_asd_js_" + System.currentTimeMillis();
                Map<String, Object> block = new HashMap<>();
                block.put("id", blockId);
                block.put("action", "asdJs");
                block.put("category", "asd");
                block.put("shape", "stack");
                block.put("spec", "JS source %s");
                block.put("paramValues", java.util.Arrays.asList(jsContent));
                block.put("params", jsContent);
                block.put("event", "immediate");
                block.put("parentBlockId", "");
                jsLogicBlocks.add(block);
            }

            if (!jsLogicBlocks.isEmpty()) {
                String cleanJsName = DesignDataManager.getCleanPageName("js/script.js");
                File jsLogicFile = new File(extPath, cleanJsName + "_logic.json");
                FileUtil.writeFile(jsLogicFile.getAbsolutePath(), new Gson().toJson(jsLogicBlocks));

                // Write script.js to external assets/js/script.js
                try {
                    File targetJsFile = new File(extAssetsDir, "js/script.js");
                    targetJsFile.getParentFile().mkdirs();
                    
                    // Compile compiled JS output or write original jsContent
                    LogicBlockManager jsLogic = new LogicBlockManager(this);
                    jsLogic.fromJson(new Gson().toJson(jsLogicBlocks));
                    String jsBlocks = jsLogic.generateJavaScript();
                    String asdJs = jsLogic.generateAsdSource("js");
                    StringBuilder compiledJs = new StringBuilder();
                    compiledJs.append("/* Compiled by DragWeb */\n\n");
                    if (asdJs != null && !asdJs.trim().isEmpty()) {
                        compiledJs.append(asdJs).append("\n\n");
                    }
                    if (jsBlocks != null && !jsBlocks.trim().isEmpty()) {
                        compiledJs.append(jsBlocks).append("\n");
                    }
                    FileUtil.writeFile(targetJsFile.getAbsolutePath(), compiledJs.toString());
                } catch (Exception e) {
                    Log.w("ImportSite", "Failed to write script.js: " + e.getMessage());
                }
            }
        }

        // Save metadata
        File metaFile = new File(dir, projectId + ".meta");
        Map<String, String> meta = new HashMap<>();
        meta.put("id", projectId);
        meta.put("name", projectName);
        meta.put("description", "Imported website");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        meta.put("created", sdf.format(new Date()));
        FileUtil.writeFile(metaFile.getAbsolutePath(), new Gson().toJson(meta));

        // Save theme settings: disable default styles and inline styles since CSS is imported
        ThemeManager tm = new ThemeManager();
        tm.setUseInlineStyles(false);
        tm.setDisableDefaultStyles(true);
        File themeFile = new File(dir, projectId + ".theme");
        FileUtil.writeFile(themeFile.getAbsolutePath(), tm.toJson());

        // Enable detected standard icon libraries
        if (!combinedLibraries.isEmpty()) {
            IconLibraryManager ilm = new IconLibraryManager(this, projectId);
            for (String libId : combinedLibraries) {
                ilm.enable(libId);
            }
        }

        // Save external project.config.json
        try {
            File configFile = new File(extPath, "project.config.json");
            Map<String, String> config = new HashMap<>();
            config.put("id", projectId);
            config.put("name", projectName);
            config.put("description", "Imported website");
            FileUtil.writeFile(configFile.getAbsolutePath(), new Gson().toJson(config));
        } catch (Exception e) {
            Log.e("ImportSite", "Failed to write config: " + e.getMessage());
        }

        // Open the newly created project in MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("project_id", projectId);
        intent.putExtra("project_name", projectName);
        startActivity(intent);
        finish();
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
        } catch (Exception ignored) { android.util.Log.e("ImportSite", "Error", ignored); }
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

            // Group styling with list drawables
            if (htmlNames.size() == 1) {
                holder.itemView.setBackgroundResource(R.drawable.item_single_bg);
            } else if (position == 0) {
                holder.itemView.setBackgroundResource(R.drawable.item_top_bg);
            } else if (position == htmlNames.size() - 1) {
                holder.itemView.setBackgroundResource(R.drawable.item_bottom_bg);
            } else {
                holder.itemView.setBackgroundResource(R.drawable.item_mid_bg);
            }

            // Adjust vertical margins dynamically so they stack seamlessly
            ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) layoutParams;
                int marginHorizontal = 0;
                int marginTop = 0;
                int marginBottom = 0;
                lp.setMargins(marginHorizontal, marginTop, marginHorizontal, marginBottom);
                holder.itemView.setLayoutParams(lp);
            }

            holder.btnDeleteHtml.setOnClickListener(v -> {
                htmlUris.remove(position);
                htmlNames.remove(position);
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return htmlNames.size();
        }
    }
}
