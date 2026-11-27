package glab.dragweb.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import glab.dragweb.data.PageManager;
import glab.dragweb.databinding.ManagePageLayoutBinding;
import glab.dragweb.ui.AppDialogs;
import glab.dragweb.util.FileUtil;

public class ManagePageActivity extends AppCompatActivity {

    private String projectId = "";
    private String pageName = "";
    private ManagePageLayoutBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ManagePageLayoutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        projectId = getIntent().getStringExtra("project_id");
        pageName = getIntent().getStringExtra("page_name");

        if (projectId == null || projectId.isEmpty() || pageName == null || pageName.isEmpty()) {
            Toast.makeText(this, "Invalid arguments", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final int appBarInitialTop = binding.appBarLayout.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            binding.appBarLayout.setPadding(
                    binding.appBarLayout.getPaddingLeft(),
                    appBarInitialTop + systemBars.top,
                    binding.appBarLayout.getPaddingRight(),
                    binding.appBarLayout.getPaddingBottom()
            );
            int bottomInset = Math.max(systemBars.bottom, ime.bottom);
            binding.buttonContainer.setPadding(
                    binding.buttonContainer.getPaddingLeft(),
                    binding.buttonContainer.getPaddingTop(),
                    binding.buttonContainer.getPaddingRight(),
                    bottomInset
            );
            return insets;
        });

        binding.toolbar.setTitle("Manage Page: " + pageName);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.pageName.setText(pageName);

        if ("index".equals(pageName)) {
            binding.pageName.setEnabled(false);
            binding.deletepage.setEnabled(false);
            binding.deletepage.setAlpha(0.5f);
        }

        loadPageMetadata();

        binding.switch1.setOnCheckedChangeListener((buttonView, isChecked) ->
                binding.cssSelectorIconTil.setEnabled(!isChecked));

        binding.switchGlobalJs.setOnCheckedChangeListener((buttonView, isChecked) ->
                binding.tilJsSelector.setEnabled(!isChecked));

        binding.cssSelector.setOnClickListener(v -> showAssetFilePicker("Select CSS File", ".css", "css/style.css", chosen ->
                binding.cssSelector.setText(chosen)));

        binding.jsSelector.setOnClickListener(v -> showAssetFilePicker("Select JS File", ".js", "js/script.js", chosen ->
                binding.jsSelector.setText(chosen)));

        binding.btnPickFavicon.setOnClickListener(v -> {
            String current = textOf(binding.etFavicon);
            AppDialogs.showImageSelector(this, projectId, current, chosen -> binding.etFavicon.setText(chosen));
        });

        binding.btnPickOgImage.setOnClickListener(v -> {
            String current = textOf(binding.etOgImage);
            AppDialogs.showImageSelector(this, projectId, current, chosen -> binding.etOgImage.setText(chosen));
        });

        binding.eventsCreatorCancel.setOnClickListener(v -> finish());
        binding.eventsCreatorSave.setOnClickListener(v -> savePageConfig());
        binding.deletepage.setOnClickListener(v -> confirmDeletePage());
    }

    private void loadPageMetadata() {
        PageManager pm = new PageManager(this, projectId);
        PageManager.PageConfig cfg = pm.getPageConfig(pageName);

        binding.switch1.setChecked(cfg.useGlobalCss);
        binding.cssSelector.setText(cfg.cssFile != null ? cfg.cssFile : "css/style.css");
        binding.cssSelectorIconTil.setEnabled(!cfg.useGlobalCss);

        binding.switchGlobalJs.setChecked(cfg.useGlobalJs);
        binding.jsSelector.setText(cfg.jsFile != null ? cfg.jsFile : "js/script.js");
        binding.tilJsSelector.setEnabled(!cfg.useGlobalJs);

        binding.etPageTitle.setText(nonNull(cfg.pageTitle));
        binding.etFavicon.setText(nonNull(cfg.favicon));
        binding.etMetaDescription.setText(nonNull(cfg.metaDescription));
        binding.etMetaKeywords.setText(nonNull(cfg.metaKeywords));
        binding.etMetaAuthor.setText(nonNull(cfg.metaAuthor));
        binding.etMetaThemeColor.setText(nonNull(cfg.metaThemeColor));
        binding.etOgTitle.setText(nonNull(cfg.ogTitle));
        binding.etOgDescription.setText(nonNull(cfg.ogDescription));
        binding.etOgImage.setText(nonNull(cfg.ogImage));
        binding.etCustomHeadCode.setText(nonNull(cfg.customHeadCode));
    }

    private void showAssetFilePicker(String title, String ext, String defaultFile, AppDialogs.OnText onPick) {
        List<String> files = new ArrayList<>();
        files.add(defaultFile);
        File assetsDir = new File(FileUtil.getDragWebDir(this), "projects/" + projectId + "/assets");
        if (assetsDir.exists() && assetsDir.isDirectory()) {
            scanAssetsByExt(assetsDir, "", ext, files);
        }
        String[] items = files.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setItems(items, (dialog, which) -> onPick.onText(items[which]))
                .show();
    }

    private void scanAssetsByExt(File dir, String relativePath, String ext, List<String> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            String rel = relativePath.isEmpty() ? name : relativePath + "/" + name;
            if (f.isDirectory()) {
                scanAssetsByExt(f, rel, ext, list);
            } else if (f.isFile() && name.toLowerCase(Locale.ROOT).endsWith(ext)) {
                if (!list.contains(rel)) {
                    list.add(rel);
                }
            }
        }
    }

    private void savePageConfig() {
        String rawName = textOf(binding.pageName);
        String cleanName = rawName.replaceAll("[^a-zA-Z0-9_-]", "");

        if (cleanName.isEmpty()) {
            Toast.makeText(this, "Invalid page name", Toast.LENGTH_SHORT).show();
            return;
        }

        PageManager pm = new PageManager(this, projectId);
        if (!cleanName.equals(pageName)) {
            if (pm.getPages().contains(cleanName)) {
                Toast.makeText(this, "Page name already exists", Toast.LENGTH_SHORT).show();
                return;
            }
            pm.renamePage(pageName, cleanName);
            if (pageName.equals(pm.getCurrentPage())) {
                pm.setCurrentPage(cleanName);
            }
        }

        PageManager.PageConfig cfg = pm.getPageConfig(cleanName);
        cfg.useGlobalCss = binding.switch1.isChecked();
        String cssPath = textOf(binding.cssSelector);
        cfg.cssFile = cssPath.isEmpty() ? "css/style.css" : cssPath;
        cfg.useGlobalJs = binding.switchGlobalJs.isChecked();
        String jsPath = textOf(binding.jsSelector);
        cfg.jsFile = jsPath.isEmpty() ? "js/script.js" : jsPath;
        cfg.pageTitle = textOf(binding.etPageTitle);
        cfg.favicon = textOf(binding.etFavicon);
        cfg.metaDescription = textOf(binding.etMetaDescription);
        cfg.metaKeywords = textOf(binding.etMetaKeywords);
        cfg.metaAuthor = textOf(binding.etMetaAuthor);
        cfg.metaThemeColor = textOf(binding.etMetaThemeColor);
        cfg.ogTitle = textOf(binding.etOgTitle);
        cfg.ogDescription = textOf(binding.etOgDescription);
        cfg.ogImage = textOf(binding.etOgImage);
        cfg.customHeadCode = binding.etCustomHeadCode.getText() != null
                ? binding.etCustomHeadCode.getText().toString().trim()
                : "";
        pm.savePageConfig(cfg);

        Intent resultIntent = new Intent();
        resultIntent.putExtra("action", "save");
        resultIntent.putExtra("old_page_name", pageName);
        resultIntent.putExtra("new_page_name", cleanName);
        resultIntent.putExtra("use_global_css", cfg.useGlobalCss);
        resultIntent.putExtra("custom_css_path", cfg.cssFile);
        resultIntent.putExtra("use_global_js", cfg.useGlobalJs);
        resultIntent.putExtra("custom_js_path", cfg.jsFile);
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
                    if (pageName.equals(pm.getCurrentPage())) {
                        pm.setCurrentPage("index");
                    }

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

    private static String textOf(com.google.android.material.textfield.TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }

    private static String nonNull(String s) {
        return s != null ? s : "";
    }
}
