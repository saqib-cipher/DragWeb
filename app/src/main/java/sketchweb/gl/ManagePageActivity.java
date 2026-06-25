package sketchweb.gl;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManagePageActivity extends AppCompatActivity {

    private String projectId = "";
    private String pageName = "";

    private MaterialToolbar toolbar;
    private TextInputEditText etPageName;
    private MaterialSwitch switchGlobalCss;
    private TextInputLayout tilCssSelector;
    private TextInputEditText etCssSelector;
    private Button btnDeletePage;
    private Button btnCancel;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manage_page_layout);

        // Get intents
        projectId = getIntent().getStringExtra("project_id");
        pageName = getIntent().getStringExtra("page_name");

        if (projectId == null || projectId.isEmpty() || pageName == null || pageName.isEmpty()) {
            Toast.makeText(this, "Invalid arguments", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize views
        toolbar = findViewById(R.id.toolbar);
        etPageName = findViewById(R.id.page_name);
        switchGlobalCss = findViewById(R.id.switch1);
        tilCssSelector = findViewById(R.id.css_selector_icon_til);
        etCssSelector = findViewById(R.id.css_selector);
        btnDeletePage = findViewById(R.id.deletepage);
        btnCancel = findViewById(R.id.events_creator_cancel);
        btnSave = findViewById(R.id.events_creator_save);

        // Edge to edge padding adjustment
        View rootLayout = findViewById(R.id.root_layout);
        View appBarLayout = findViewById(R.id.app_bar_layout);
        if (rootLayout != null) {
            final int appBarInitialTop = appBarLayout != null ? appBarLayout.getPaddingTop() : 0;
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                if (appBarLayout != null) {
                    appBarLayout.setPadding(appBarLayout.getPaddingLeft(), appBarInitialTop + systemBars.top, appBarLayout.getPaddingRight(), appBarLayout.getPaddingBottom());
                }
                View scrollContent = findViewById(R.id.scroll_content);
                if (scrollContent != null) {
                    scrollContent.setPadding(scrollContent.getPaddingLeft(), scrollContent.getPaddingTop(), scrollContent.getPaddingRight(), systemBars.bottom);
                }
                return insets;
            });
        }

        // Toolbar setup
        if (toolbar != null) {
            toolbar.setTitle("Manage Page: " + pageName);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        // Prefill values
        etPageName.setText(pageName);
        
        // Disabled editing of name if it is index page
        if ("index".equals(pageName)) {
            etPageName.setEnabled(false);
            btnDeletePage.setEnabled(false);
            btnDeletePage.setAlpha(0.5f);
        }

        // Load current metadata configurations
        loadPageMetadata();

        // Switch change listener
        switchGlobalCss.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tilCssSelector.setEnabled(!isChecked);
        });

        // CSS picker dropdown selection
        etCssSelector.setOnClickListener(v -> showCssDropdown());

        // Cancel click
        btnCancel.setOnClickListener(v -> finish());

        // Save click
        btnSave.setOnClickListener(v -> savePageConfig());

        // Delete page click
        btnDeletePage.setOnClickListener(v -> confirmDeletePage());
    }

    private void loadPageMetadata() {
        File metaFile = new File(getFilesDir(), "projects/" + projectId + "_" + pageName + ".meta");
        boolean useGlobal = true;
        String customCss = "css/style.css";

        if (metaFile.exists()) {
            try {
                String json = FileUtil.readFile(metaFile.getAbsolutePath());
                Map<String, Object> map = new Gson().fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
                if (map != null) {
                    if (map.containsKey("useGlobalCss")) {
                        useGlobal = (Boolean) map.get("useGlobalCss");
                    }
                    if (map.containsKey("customCssPath")) {
                        customCss = (String) map.get("customCssPath");
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        switchGlobalCss.setChecked(useGlobal);
        etCssSelector.setText(customCss);
        tilCssSelector.setEnabled(!useGlobal);
    }

    private void showCssDropdown() {
        List<String> cssFiles = getCssFiles();
        if (cssFiles.isEmpty()) {
            Toast.makeText(this, "No CSS files found in project assets", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = cssFiles.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
            .setTitle("Select CSS File")
            .setItems(items, (dialog, which) -> {
                etCssSelector.setText(items[which]);
            })
            .show();
    }

    private List<String> getCssFiles() {
        List<String> list = new ArrayList<>();
        list.add("css/style.css");

        // Scan assets folder
        File assetsDir = new File(Environment.getExternalStorageDirectory(), "/.dragweb/projects/" + projectId + "/assets");
        if (assetsDir.exists() && assetsDir.isDirectory()) {
            scanForCss(assetsDir, "", list);
        }
        return list;
    }

    private void scanForCss(File dir, String relativePath, List<String> list) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                String rel = relativePath.isEmpty() ? name : relativePath + "/" + name;
                if (f.isDirectory()) {
                    scanForCss(f, rel, list);
                } else if (f.isFile() && name.toLowerCase().endsWith(".css")) {
                    if (!list.contains(rel)) {
                        list.add(rel);
                    }
                }
            }
        }
    }

    private void savePageConfig() {
        String rawName = etPageName.getText().toString().trim();
        String cleanName = rawName.replaceAll("[^a-zA-Z0-9_-]", "");
        
        if (cleanName.isEmpty()) {
            Toast.makeText(this, "Invalid page name", Toast.LENGTH_SHORT).show();
            return;
        }

        PageManager pm = new PageManager(this, projectId);

        // Rename logic
        if (!cleanName.equals(pageName)) {
            if (pm.getPages().contains(cleanName)) {
                Toast.makeText(this, "Page name already exists", Toast.LENGTH_SHORT).show();
                return;
            }
            
            pm.renamePage(pageName, cleanName);

            // Rename logic file
            File dir = new File(getFilesDir(), "projects");
            File oldLogic = new File(dir, projectId + "_" + pageName + ".logic");
            File newLogic = new File(dir, projectId + "_" + cleanName + ".logic");
            if (oldLogic.exists()) {
                oldLogic.renameTo(newLogic);
            }

            // Rename metadata file
            File oldMeta = new File(getFilesDir(), "projects/" + projectId + "_" + pageName + ".meta");
            File newMeta = new File(getFilesDir(), "projects/" + projectId + "_" + cleanName + ".meta");
            if (oldMeta.exists()) {
                oldMeta.renameTo(newMeta);
            }

            // Switch current active page in manager if we renamed it
            if (pageName.equals(pm.getCurrentPage())) {
                pm.setCurrentPage(cleanName);
            }
        }

        // Save page metadata
        File saveMetaFile = new File(getFilesDir(), "projects/" + projectId + "_" + cleanName + ".meta");
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put("useGlobalCss", switchGlobalCss.isChecked());
        metaMap.put("customCssPath", etCssSelector.getText().toString());
        FileUtil.writeFile(saveMetaFile.getAbsolutePath(), new Gson().toJson(metaMap));

        // Return intent details
        Intent resultIntent = new Intent();
        resultIntent.putExtra("action", "save");
        resultIntent.putExtra("old_page_name", pageName);
        resultIntent.putExtra("new_page_name", cleanName);
        resultIntent.putExtra("use_global_css", switchGlobalCss.isChecked());
        resultIntent.putExtra("custom_css_path", etCssSelector.getText().toString());
        setResult(RESULT_OK, resultIntent);

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDeletePage() {
        if ("index".equals(pageName)) {
            Toast.makeText(this, "Index page cannot be deleted", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("Delete Page")
            .setMessage("Are you sure you want to delete '" + pageName + "'? This cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
                PageManager pm = new PageManager(this, projectId);
                pm.removePage(pageName);

                // Switch back active page index if we deleted current
                if (pageName.equals(pm.getCurrentPage())) {
                    pm.setCurrentPage("index");
                }

                // Delete logic file
                File dir = new File(getFilesDir(), "projects");
                File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
                if (logicFile.exists()) {
                    logicFile.delete();
                }

                // Delete metadata file
                File metaFileToDelete = new File(getFilesDir(), "projects/" + projectId + "_" + pageName + ".meta");
                if (metaFileToDelete.exists()) {
                    metaFileToDelete.delete();
                }

                // Return deleted action
                Intent resultIntent = new Intent();
                resultIntent.putExtra("action", "delete");
                resultIntent.putExtra("page_name", pageName);
                setResult(RESULT_OK, resultIntent);

                Toast.makeText(this, "Page deleted", Toast.LENGTH_SHORT).show();
                finish();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
