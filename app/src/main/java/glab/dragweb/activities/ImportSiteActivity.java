package glab.dragweb.activities;

import glab.dragweb.R;
import glab.dragweb.activities.*;
import glab.dragweb.fragments.*;
import glab.dragweb.engine.*;
import glab.dragweb.ui.*;
import glab.dragweb.logic.*;
import glab.dragweb.codegen.*;
import glab.dragweb.core.BlocksLoader;
import glab.dragweb.data.*;
import glab.dragweb.adapters.*;
import glab.dragweb.models.*;
import glab.dragweb.util.*;
import glab.dragweb.colorpicker.*;

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
            if (htmlUris.isEmpty()) {
                Toast.makeText(this, "Please select at least one HTML page to import", Toast.LENGTH_SHORT).show();
                return;
            }
            if (layoutLoading != null) layoutLoading.setVisibility(View.VISIBLE);
            btnImport.setEnabled(false);
            performSiteImport();
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
        String rawName = etProjectName != null && etProjectName.getText() != null
                ? etProjectName.getText().toString().trim()
                : "";
        final String projectName = rawName.isEmpty() ? "Imported Website" : rawName;
        final boolean convertCss = switchConvertCss == null || switchConvertCss.isChecked();
        final boolean convertJs = switchConvertJs == null || switchConvertJs.isChecked();
        final List<Uri> htmlUrisCopy = new ArrayList<>(htmlUris);
        final List<String> htmlNamesCopy = new ArrayList<>(htmlNames);
        final Uri cssUriCopy = cssUri;
        final Uri jsUriCopy = jsUri;

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

            int indexIdx = -1;
            for (int i = 0; i < htmlNamesCopy.size(); i++) {
                if (htmlNamesCopy.get(i).toLowerCase(Locale.ROOT).contains("index")) {
                    indexIdx = i;
                    break;
                }
            }
            if (indexIdx == -1) indexIdx = 0;

            HtmlCssImporter treeExtractor = new HtmlCssImporter(this);
            for (int i = 0; i < htmlUrisCopy.size(); i++) {
                Uri uri = htmlUrisCopy.get(i);
                String origName = htmlNamesCopy.get(i).toLowerCase(Locale.ROOT);
                String pageName = origName.contains(".")
                        ? origName.substring(0, origName.lastIndexOf('.'))
                        : origName;
                if (i == indexIdx || "index".equals(pageName)) {
                    pageName = "index";
                }
                pageNamesList.add(pageName);

                String htmlContent = readUriContent(uri);
                if (htmlContent != null && !htmlContent.trim().isEmpty()) {
                    // Convert HTML to DragWeb blocks via Rust engine
                    ArrayList<BlockBean> rustHtmlBeans = BlocksLoader.generateHtmlBlocksFromCode(this, htmlContent);
                    if (rustHtmlBeans != null && !rustHtmlBeans.isEmpty()) {
                        pageHtmlBlocks.put(pageName, rustHtmlBeans);
                    }

                    // Extract widget tree & detected libraries for visual canvas
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
                        Log.e("ImportSiteActivity", "Failed to copy original html page: " + e.getMessage());
                    }
                }
            }

            // Save page layouts and Rust-generated HTML blocks
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

            // Convert & save global CSS via Rust engine
            String cssContent = cssUriCopy != null ? readUriContent(cssUriCopy) : null;
            if (cssContent != null && !cssContent.trim().isEmpty()) {
                if (convertCss) {
                    ArrayList<BlockBean> cssBeans = BlocksLoader.generateCssBlocksFromCode(this, cssContent);
                    if (cssBeans != null && !cssBeans.isEmpty()) {
                        String cleanCssName = DesignDataManager.getCleanPageName("css/style.css");
                        DesignDataManager.setBlocks(cleanCssName, "onCreate_initializeLogic", cssBeans);
                    }
                }
                File targetStyleFile = new File(extAssetsDir, "css/style.css");
                if (targetStyleFile.getParentFile() != null) targetStyleFile.getParentFile().mkdirs();
                FileUtil.writeFile(targetStyleFile.getAbsolutePath(), cssContent);
            }

            // Convert & save global JS via Rust engine
            String jsContent = jsUriCopy != null ? readUriContent(jsUriCopy) : null;
            if (jsContent != null && !jsContent.trim().isEmpty()) {
                if (convertJs) {
                    ArrayList<BlockBean> jsBeans = BlocksLoader.generateJsBlocksFromCode(this, jsContent);
                    if (jsBeans != null && !jsBeans.isEmpty()) {
                        String cleanJsName = DesignDataManager.getCleanPageName("js/script.js");
                        DesignDataManager.setBlocks(cleanJsName, "onCreate_initializeLogic", jsBeans);
                    }
                }
                File targetJsFile = new File(extAssetsDir, "js/script.js");
                if (targetJsFile.getParentFile() != null) targetJsFile.getParentFile().mkdirs();
                FileUtil.writeFile(targetJsFile.getAbsolutePath(), jsContent);
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

            // Disable inline styles & default theme CSS when importing external site
            ThemeManager tm = new ThemeManager();
            tm.setUseInlineStyles(false);
            tm.setDisableDefaultStyles(true);
            FileUtil.writeFile(new File(extPath, "theme.json").getAbsolutePath(), tm.toJson());

            if (!combinedLibraries.isEmpty()) {
                IconLibraryManager ilm = new IconLibraryManager(this, projectId);
                for (String libId : combinedLibraries) {
                    ilm.enable(libId);
                }
            }

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
