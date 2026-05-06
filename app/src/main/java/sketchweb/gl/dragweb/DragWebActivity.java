package sketchweb.gl.dragweb;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sketchweb.gl.R;

/**
 * Sketchware-style 4-pane editor for the template-based DragWeb engine.
 *
 * <pre>
 *  ┌────────────┬──────────────────────┬────────────┐
 *  │  Palette   │    Workspace         │ Properties │
 *  └────────────┴──────────────────────┴────────────┘
 *  │              Generated Code Preview              │
 *  └──────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>The activity keeps the editor state in {@link #project}; every change
 * triggers {@link #regenerate()} which (a) runs the template engine, (b)
 * writes index.html / style.css to <code>/.dragweb/projects/&lt;name&gt;/</code>,
 * (c) updates the bottom code panel, (d) refreshes selector autocomplete.
 *
 * <p>Launch with an optional intent extra <code>EXTRA_PROJECT_NAME</code>; a
 * fresh project is created if the name is missing or unknown.
 */
public class DragWebActivity extends AppCompatActivity implements WorkspaceAdapter.Listener {

    public static final String EXTRA_PROJECT_NAME = "dragweb.projectName";

    private DragWebBlockRegistry registry;
    private DragWebProjectManager projectManager;
    private DragWebCodeGenerator generator;
    private DragWebExportManager exportManager;
    private SelectorIndex selectorIndex = new SelectorIndex();

    private DragWebProject project;
    private String currentPage = "index.html";
    private String currentCategory = null;
    private String currentCodeTab = "html";

    private PaletteAdapter paletteAdapter;
    private WorkspaceAdapter workspaceAdapter;

    private RecyclerView paletteList;
    private RecyclerView workspaceList;
    private ChipGroup categoryChips;
    private Spinner pageSpinner;
    private TextView codeView;
    private TabLayout codeTabs;
    private LinearLayout propertiesPanel;

    private ActivityResultLauncher<String> exportZipLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dragweb);

        registry = new DragWebBlockRegistry(this);
        projectManager = new DragWebProjectManager(this);
        generator = new DragWebCodeGenerator(registry);
        exportManager = new DragWebExportManager(this);

        String name = getIntent().getStringExtra(EXTRA_PROJECT_NAME);
        if (name == null || name.isEmpty()) name = "MyWebsite";

        try {
            project = projectManager.load(name);
        } catch (IOException e) {
            Toast.makeText(this, "Could not load project: " + e.getMessage(), Toast.LENGTH_LONG).show();
            project = new DragWebProject(name);
        }

        bindViews();
        wireToolbar();
        wirePalette();
        wireWorkspace();
        wirePages();
        wireCodePanel();
        wireExport();

        regenerate();
    }

    private void bindViews() {
        paletteList = findViewById(R.id.dragweb_palette);
        workspaceList = findViewById(R.id.dragweb_workspace);
        categoryChips = findViewById(R.id.dragweb_categories);
        pageSpinner = findViewById(R.id.dragweb_page_spinner);
        codeView = findViewById(R.id.dragweb_code_view);
        codeTabs = findViewById(R.id.dragweb_code_tabs);
        propertiesPanel = findViewById(R.id.dragweb_properties);
    }

    private void wireToolbar() {
        MaterialToolbar tb = findViewById(R.id.dragweb_toolbar);
        if (tb != null) {
            tb.setTitle("DragWeb · " + project.name);
            tb.setNavigationIcon(R.drawable.icon_undo_round);
            tb.setNavigationOnClickListener(v -> finish());
        }
    }

    private void wirePalette() {
        paletteAdapter = new PaletteAdapter(this::addBlockFromPalette);
        paletteList.setLayoutManager(new LinearLayoutManager(this));
        paletteList.setAdapter(paletteAdapter);

        categoryChips.removeAllViews();
        Chip all = new Chip(this);
        all.setText("All");
        all.setCheckable(true);
        all.setChecked(true);
        all.setOnClickListener(v -> {
            currentCategory = null;
            paletteAdapter.setBlocks(registry.all());
        });
        categoryChips.addView(all);
        for (String cat : registry.categories()) {
            Chip chip = new Chip(this);
            chip.setText(cat);
            chip.setCheckable(true);
            chip.setOnClickListener(v -> {
                currentCategory = cat;
                paletteAdapter.setBlocks(registry.byCategory(cat));
            });
            categoryChips.addView(chip);
        }
        paletteAdapter.setBlocks(registry.all());

        MaterialButton btnNew = findViewById(R.id.dragweb_btn_new_custom);
        btnNew.setOnClickListener(v -> showNewCustomBlockDialog());
    }

    private void wireWorkspace() {
        workspaceAdapter = new WorkspaceAdapter(registry, this);
        workspaceList.setLayoutManager(new LinearLayoutManager(this));
        workspaceList.setAdapter(workspaceAdapter);
        refreshWorkspace();
    }

    private void wirePages() {
        refreshPageSpinner();
        pageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentPage = (String) parent.getItemAtPosition(position);
                refreshWorkspace();
                refreshCodeView();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        MaterialButton btnAdd = findViewById(R.id.dragweb_btn_add_page);
        btnAdd.setOnClickListener(v -> showAddPageDialog());
    }

    private void refreshPageSpinner() {
        if (project.pages == null || project.pages.isEmpty()) {
            project.pages = new ArrayList<>();
            project.pages.add("index.html");
        }
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new ArrayList<>(project.pages));
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pageSpinner.setAdapter(ad);

        if (!project.pages.contains(currentPage)) currentPage = project.pages.get(0);
        pageSpinner.setSelection(project.pages.indexOf(currentPage));
    }

    private void wireCodePanel() {
        codeTabs.addTab(codeTabs.newTab().setText("HTML"));
        codeTabs.addTab(codeTabs.newTab().setText("CSS"));
        codeTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentCodeTab = tab.getPosition() == 0 ? "html" : "css";
                refreshCodeView();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        MaterialButton previewBtn = findViewById(R.id.dragweb_btn_preview);
        previewBtn.setOnClickListener(v -> showPreview());
    }

    private void wireExport() {
        exportZipLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/zip"),
                this::handleExportZip);
        MaterialButton exportBtn = findViewById(R.id.dragweb_btn_export);
        exportBtn.setOnClickListener(v ->
                exportZipLauncher.launch(DragWebPaths.sanitize(project.name) + ".zip"));
    }

    private void handleExportZip(Uri uri) {
        if (uri == null) return;
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) {
                Toast.makeText(this, "Could not open destination", Toast.LENGTH_SHORT).show();
                return;
            }
            // Make sure files are fresh on disk before zipping.
            DragWebCodeGenerator.GenerationResult r = generator.generate(project);
            DragWebCodeGenerator.writeToProjectDir(this, project, r);
            exportManager.exportZip(project.name, os);
            Toast.makeText(this, "Exported ZIP", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---- Workspace edits ----

    private void addBlockFromPalette(DragWebBlock block) {
        BlockInstance inst = new BlockInstance(block.id);
        inst.page = currentPage;
        // Pre-size inputs to the count tokens detected in the display string.
        int n = Math.max(block.inputCount(), 0);
        for (int i = 0; i < n; i++) inst.inputs.add("");
        project.blocks.add(inst);
        persistAndRefresh();
    }

    @Override
    public void onChanged() {
        persistAndRefresh();
    }

    @Override
    public void onMoveUp(int pos) {
        List<BlockInstance> page = project.blocksForPage(currentPage);
        if (pos <= 0 || pos >= page.size()) return;
        int realA = project.blocks.indexOf(page.get(pos - 1));
        int realB = project.blocks.indexOf(page.get(pos));
        if (realA < 0 || realB < 0) return;
        java.util.Collections.swap(project.blocks, realA, realB);
        persistAndRefresh();
    }

    @Override
    public void onMoveDown(int pos) {
        List<BlockInstance> page = project.blocksForPage(currentPage);
        if (pos < 0 || pos >= page.size() - 1) return;
        int realA = project.blocks.indexOf(page.get(pos));
        int realB = project.blocks.indexOf(page.get(pos + 1));
        if (realA < 0 || realB < 0) return;
        java.util.Collections.swap(project.blocks, realA, realB);
        persistAndRefresh();
    }

    @Override
    public void onDuplicate(int pos) {
        List<BlockInstance> page = project.blocksForPage(currentPage);
        if (pos < 0 || pos >= page.size()) return;
        BlockInstance src = page.get(pos);
        BlockInstance copy = src.copy();
        int real = project.blocks.indexOf(src);
        if (real < 0) project.blocks.add(copy);
        else project.blocks.add(real + 1, copy);
        persistAndRefresh();
    }

    @Override
    public void onDelete(int pos) {
        List<BlockInstance> page = project.blocksForPage(currentPage);
        if (pos < 0 || pos >= page.size()) return;
        BlockInstance target = page.get(pos);
        project.blocks.remove(target);
        persistAndRefresh();
    }

    @Override
    public SelectorIndex selectorIndex() {
        return selectorIndex;
    }

    private void persistAndRefresh() {
        try {
            projectManager.save(project);
        } catch (IOException e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        regenerate();
        refreshWorkspace();
    }

    private void refreshWorkspace() {
        workspaceAdapter.setInstances(project.blocksForPage(currentPage));
        refreshPropertyPanel();
    }

    private void regenerate() {
        DragWebCodeGenerator.GenerationResult result = generator.generate(project);
        try {
            DragWebCodeGenerator.writeToProjectDir(this, project, result);
        } catch (IOException e) {
            Toast.makeText(this, "Write failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        selectorIndex = result.selectors;
        refreshCodeView(result);
    }

    private void refreshCodeView() { refreshCodeView(generator.generate(project)); }

    private void refreshCodeView(DragWebCodeGenerator.GenerationResult result) {
        if ("css".equals(currentCodeTab)) {
            codeView.setText(result.css);
        } else {
            String html = result.pages.get(currentPage);
            codeView.setText(html == null ? "" : html);
        }
    }

    // ---- Properties panel ----
    //
    // We expose simple class-keyed CSS overrides — per spec, the property
    // editor owns width/height/margin/padding/font-size/colors/border-radius.
    // Edits flow into project.globalCss, which the generator emits into
    // style.css ahead of the block-derived rules.

    private void refreshPropertyPanel() {
        propertiesPanel.removeAllViews();
        TextView help = new TextView(this);
        help.setText("Apply quick CSS to a class:");
        help.setPadding(0, 0, 0, 8);
        propertiesPanel.addView(help);

        TextInputLayout selectorLayout = new TextInputLayout(this);
        selectorLayout.setHint(".my-class");
        TextInputEditText selectorEt = new TextInputEditText(this);
        selectorLayout.addView(selectorEt);
        propertiesPanel.addView(selectorLayout);

        String[] props = {"width", "height", "margin", "padding", "font-size",
                "background", "color", "border-radius"};

        Map<String, TextInputEditText> editors = new LinkedHashMap<>();
        for (String p : props) {
            TextInputLayout til = new TextInputLayout(this);
            til.setHint(p);
            TextInputEditText et = new TextInputEditText(this);
            til.addView(et);
            propertiesPanel.addView(til);
            editors.put(p, et);
        }

        MaterialButton apply = new MaterialButton(this);
        apply.setText("Apply CSS");
        apply.setOnClickListener(v -> {
            String sel = selectorEt.getText() == null ? "" : selectorEt.getText().toString().trim();
            if (sel.isEmpty()) {
                Toast.makeText(this, "Selector required", Toast.LENGTH_SHORT).show();
                return;
            }
            StringBuilder rules = new StringBuilder();
            for (Map.Entry<String, TextInputEditText> e : editors.entrySet()) {
                String value = e.getValue().getText() == null ? "" : e.getValue().getText().toString().trim();
                if (!value.isEmpty()) {
                    rules.append(e.getKey()).append(":").append(value).append(";");
                }
            }
            if (rules.length() == 0) {
                Toast.makeText(this, "Set at least one property", Toast.LENGTH_SHORT).show();
                return;
            }
            project.globalCss.put(sel, rules.toString());
            persistAndRefresh();
        });
        propertiesPanel.addView(apply);

        if (!project.globalCss.isEmpty()) {
            TextView header = new TextView(this);
            header.setText("\nCurrent:");
            header.setPadding(0, 16, 0, 4);
            propertiesPanel.addView(header);
            for (Map.Entry<String, String> e : new ArrayList<>(project.globalCss.entrySet())) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                TextView t = new TextView(this);
                t.setText(e.getKey() + " { " + e.getValue() + " }");
                t.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                MaterialButton del = new MaterialButton(this);
                del.setText("✕");
                del.setOnClickListener(v -> {
                    project.globalCss.remove(e.getKey());
                    persistAndRefresh();
                });
                row.addView(t);
                row.addView(del);
                propertiesPanel.addView(row);
            }
        }
    }

    // ---- Dialogs ----

    private void showAddPageDialog() {
        TextInputLayout til = new TextInputLayout(this);
        til.setHint("e.g. about.html");
        TextInputEditText et = new TextInputEditText(this);
        til.addView(et);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Add page")
                .setView(til)
                .setPositiveButton("Add", (d, w) -> {
                    String v = et.getText() == null ? "" : et.getText().toString().trim();
                    if (v.isEmpty()) return;
                    if (!v.endsWith(".html")) v = v + ".html";
                    if (!project.pages.contains(v)) project.pages.add(v);
                    currentPage = v;
                    refreshPageSpinner();
                    persistAndRefresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNewCustomBlockDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, 0);

        TextInputLayout idLayout = new TextInputLayout(this);
        idLayout.setHint("id (e.g. menu_link)");
        TextInputEditText idEt = new TextInputEditText(this);
        idLayout.addView(idEt);

        TextInputLayout displayLayout = new TextInputLayout(this);
        displayLayout.setHint("display (e.g. Add menu link %s to %s)");
        TextInputEditText displayEt = new TextInputEditText(this);
        displayLayout.addView(displayEt);

        TextInputLayout templateLayout = new TextInputLayout(this);
        templateLayout.setHint("template (e.g. <li><a href='%1$s'>%2$s</a></li>)");
        TextInputEditText templateEt = new TextInputEditText(this);
        templateLayout.addView(templateEt);

        TextInputLayout categoryLayout = new TextInputLayout(this);
        categoryLayout.setHint("category (HTML, CSS, Layout, ...)");
        TextInputEditText categoryEt = new TextInputEditText(this);
        categoryEt.setText("HTML");
        categoryLayout.addView(categoryEt);

        container.addView(idLayout);
        container.addView(displayLayout);
        container.addView(templateLayout);
        container.addView(categoryLayout);

        new MaterialAlertDialogBuilder(this)
                .setTitle("New custom block")
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    DragWebBlock b = new DragWebBlock(
                            valueOf(idEt),
                            valueOf(displayEt),
                            valueOf(templateEt),
                            valueOf(categoryEt));
                    if (b.id == null || b.id.trim().isEmpty()) {
                        Toast.makeText(this, "id required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        registry.addOrUpdateCustomBlock(b);
                        Toast.makeText(this, "Saved custom block", Toast.LENGTH_SHORT).show();
                        wirePalette();
                    } catch (IOException e) {
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String valueOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void showPreview() {
        DragWebCodeGenerator.GenerationResult r = generator.generate(project);
        try {
            DragWebCodeGenerator.writeToProjectDir(this, project, r);
        } catch (IOException ignored) {}
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(false);
        String html = r.pages.get(currentPage);
        if (html == null) html = "";
        // Inline the CSS so preview doesn't need a base URL with disk access.
        String inlined = html.replace("<link rel='stylesheet' href='style.css'>",
                "<style>" + r.css + "</style>");
        wv.loadDataWithBaseURL(null, inlined, "text/html", "utf-8", null);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Preview · " + currentPage)
                .setView(wv)
                .setPositiveButton("Close", null)
                .show();
    }
}
