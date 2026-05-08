package sketchweb.gl;

import android.content.ClipData;
import android.content.ClipDescription;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Separate activity for Logic Block editing with puzzle-like block design,
 * Material 3 expressive styling, and Sketchware-Pro-like spinner selection.
 */
public class LogicBlockActivity extends AppCompatActivity {

    // Block categories
    private static final String CAT_EVENT = "event";
    private static final String CAT_CSS = "css";
    private static final String CAT_HTML = "html";
    private static final String CAT_LOGIC = "logic";
    private static final String CAT_VARIABLE = "variable";
    private static final String CAT_ANIMATION = "animation";
    private static final String CAT_ASD = "asd";
    private static final String CAT_VALUE = "value";

    // Category colors
    private static final int COLOR_EVENT = Color.parseColor("#FF9800");
    private static final int COLOR_CSS = Color.parseColor("#2196F3");
    private static final int COLOR_HTML = Color.parseColor("#4CAF50");
    private static final int COLOR_LOGIC = Color.parseColor("#E91E63");
    private static final int COLOR_VARIABLE = Color.parseColor("#00BCD4");
    private static final int COLOR_ANIMATION = Color.parseColor("#9C27B0");
    private static final int COLOR_ASD = Color.parseColor("#455A64");
    private static final int COLOR_VALUE = Color.parseColor("#7E57C2");

    private LogicBlockManager logicBlockManager;
    private String projectId;
    private String pageName = "index";
    private int currentMode = 0; // 0 = Style (CSS), 1 = Source (ASD)

    // Views
    private MaterialToolbar toolbar;
    private DrawerLayout drawerLayout;
    private LinearLayout palettePanel;
    private LinearLayout categoryListContainer;
    private LinearLayout blockPaletteContainer;
    private LinearLayout blockWorkspace;
    private FloatingActionButton fabBlockPalette;
    private Button btnBlockDelete, btnBlockDuplicate, btnSaveAllToCollection;
    private LinearLayout dropSaveCollection, dropDeleteCollection, dropDuplicateCollection;
    private LinearLayout dropSaveAllCollection;
    private LinearLayout collectionList;
    private TextView tvBlockCount;

    private String currentCategory = CAT_CSS;
    private List<BlockDef> allBlockDefs = new ArrayList<>();

    // Undo/redo
    private List<String> undoStack = new ArrayList<>();
    private List<String> redoStack = new ArrayList<>();
    private static final int MAX_UNDO = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_logic_block);

        projectId = getIntent().getStringExtra("project_id");
        if (projectId == null) projectId = "";

        pageName = getIntent().getStringExtra("page_name");
        if (pageName == null || pageName.isEmpty()) pageName = "index";

        logicBlockManager = new LogicBlockManager(this);

        // Load existing blocks – swallow any error so a corrupt file can't
        // crash the activity at startup. fromJson itself rejects mismatched
        // payloads (e.g. custom-block library JSON) and supplies non-null
        // defaults for the fields the renderer relies on.
        try {
            File dir = new File(getFilesDir(), "projects");
            File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
            if (logicFile.exists()) {
                String json = FileUtil.readFile(logicFile.getAbsolutePath());
                if (json != null && !json.isEmpty()) {
                    logicBlockManager.fromJson(json);
                }
            }
        } catch (Exception e) {
            Log.w("LogicBlockActivity", "Could not load logic blocks: " + e.getMessage());
        }

        initViews();
        setupToolbar();
        setupQuickActionButtons();
        setupFab();
        setupCollectionDrawer();
        setupWorkspaceDragDrop();

        loadBlockDefinitions();
        setupCategoryButtons();
        showCategory(CAT_CSS);
        refreshWorkspace();
        refreshCollectionList();
        saveUndoState();

        final int toolbarInitialTop = toolbar != null ? toolbar.getPaddingTop() : 0;
        final int workspaceInitialBottom = blockWorkspace != null ? blockWorkspace.getPaddingBottom() : 0;

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            if (toolbar != null) {
                toolbar.setPadding(toolbar.getPaddingLeft(), toolbarInitialTop + systemBars.top, toolbar.getPaddingRight(), toolbar.getPaddingBottom());
            }
            
            // Also handle bottom insets for the workspace
            if (blockWorkspace != null) {
                 blockWorkspace.setPadding(blockWorkspace.getPaddingLeft(), blockWorkspace.getPaddingTop(), blockWorkspace.getPaddingRight(), workspaceInitialBottom + systemBars.bottom);
            }

            // Apply side paddings to the main root view
            v.setPadding(systemBars.left, 0, systemBars.right, 0);

            return insets;
        });
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbarLogic);
        drawerLayout = findViewById(R.id.drawerLogic);
        palettePanel = findViewById(R.id.palettePanel);
        categoryListContainer = findViewById(R.id.categoryListContainer);
        blockPaletteContainer = findViewById(R.id.blockPaletteContainer);
        blockWorkspace = findViewById(R.id.blockWorkspace);
        fabBlockPalette = findViewById(R.id.fabBlockPalette);
        btnBlockDelete = findViewById(R.id.btnBlockDelete);
        btnBlockDuplicate = findViewById(R.id.btnBlockDuplicate);
        btnSaveAllToCollection = findViewById(R.id.btnSaveAllToCollection);
        dropSaveCollection = findViewById(R.id.dropSaveCollection);
        dropSaveAllCollection = findViewById(R.id.dropSaveAllCollection);
        dropDeleteCollection = findViewById(R.id.dropDeleteCollection);
        dropDuplicateCollection = findViewById(R.id.dropDuplicateCollection);
        collectionList = findViewById(R.id.collectionList);
        tvBlockCount = findViewById(R.id.tvBlockCount);

        com.google.android.material.tabs.TabLayout tabLayoutMode = findViewById(R.id.tabLayoutMode);
        if (tabLayoutMode != null) {
            tabLayoutMode.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                    currentMode = tab.getPosition();
                    setupCategoryButtons();
                    showCategory(currentMode == 0 ? CAT_CSS : CAT_ASD);
                    refreshWorkspace();
                }
                @Override
                public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
                @Override
                public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            });
        }
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> saveAndFinish());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_undo) { undo(); return true; }
            if (id == R.id.action_redo) { redo(); return true; }
            if (id == R.id.action_view_code) { showJsPreview(); return true; }
            if (id == R.id.action_import) { showImportDialog(); return true; }
            if (id == R.id.action_export) { showExportDialog(); return true; }
            if (id == R.id.action_collections) {
                if (drawerLayout != null) {
                    if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                        drawerLayout.closeDrawer(GravityCompat.END);
                    } else {
                        drawerLayout.openDrawer(GravityCompat.END);
                    }
                }
                return true;
            }
            return false;
        });
    }

    private String getTargetMode() {
        // Selector row removed: blocks default to "id" target with empty value
        // and the user edits the target chip in the workspace.
        return "id";
    }

    private String getTargetValue() {
        return "";
    }

    /**
     * Build a vertical list of category buttons in the right column of the
     * bottom palette. Tapping a category swaps the block list on the left.
     */
    private void setupCategoryButtons() {
        if (categoryListContainer == null) return;
        categoryListContainer.removeAllViews();

        // Style tab (mode 0): CSS pseudo-class scopes, CSS properties (incl.
        // animations) and Value tokens. Source tab (mode 1): ASD raw-source
        // blocks only — every other action emits CSS, so the two workspaces
        // stay strictly separated.
        String[][] cats;
        if (currentMode == 0) {
            cats = new String[][] {
                {CAT_CSS, "CSS"},
                {CAT_VALUE, "Value"}
            };
            if (!CAT_CSS.equals(currentCategory) && !CAT_VALUE.equals(currentCategory)) {
                currentCategory = CAT_CSS;
            }
        } else {
            cats = new String[][] {
                {CAT_ASD, "ASD"}
            };
            currentCategory = CAT_ASD;
        }

        for (String[] cat : cats) {
            categoryListContainer.addView(createCategoryButton(cat[0], cat[1]));
        }
    }

    private View createCategoryButton(String category, String label) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(12);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(8), dp(12), dp(8), dp(12));

        int color = getCategoryColor(category);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(color);
        bg.setStroke(dp(2), category.equals(currentCategory) ? Color.WHITE : darken(color));
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            showCategory(category);
            setupCategoryButtons();
        });
        return btn;
    }

    private void setupFab() {
        if (fabBlockPalette == null || palettePanel == null) return;
        fabBlockPalette.setOnClickListener(v -> {
            boolean visible = palettePanel.getVisibility() == View.VISIBLE;
            palettePanel.setVisibility(visible ? View.GONE : View.VISIBLE);
        });
    }

    private void setupQuickActionButtons() {
        if (btnSaveAllToCollection != null) {
            // Click = save all blocks
            btnSaveAllToCollection.setOnClickListener(v -> saveAllBlocksToCollection());
            // Drop = also save all blocks (any block dropped saves the whole workspace)
            btnSaveAllToCollection.setOnDragListener((v, event) -> {
                if (event.getAction() == DragEvent.ACTION_DROP) {
                    saveAllBlocksToCollection();
                    return true;
                }
                return true;
            });
        }
        if (btnBlockDelete != null) {
            btnBlockDelete.setOnDragListener((v, event) -> {
                if (event.getAction() == DragEvent.ACTION_DROP) {
                    Object state = event.getLocalState();
                    if (state instanceof Integer) {
                        saveUndoState();
                        logicBlockManager.removeBlock((Integer) state);
                        refreshWorkspace();
                    }
                    return true;
                }
                return true;
            });
        }

        if (btnBlockDuplicate != null) {
            btnBlockDuplicate.setOnDragListener((v, event) -> {
                if (event.getAction() == DragEvent.ACTION_DROP) {
                    Object state = event.getLocalState();
                    if (state instanceof Integer) {
                        saveUndoState();
                        int idx = (Integer) state;
                        LogicBlockManager.LogicBlock orig = logicBlockManager.getBlocks().get(idx);
                        LogicBlockManager.LogicBlock copy = cloneBlock(orig);
                        logicBlockManager.addBlock(copy);
                        refreshWorkspace();
                    }
                    return true;
                }
                return true;
            });
        }
    }

    private LogicBlockManager.LogicBlock cloneBlock(LogicBlockManager.LogicBlock orig) {
        LogicBlockManager.LogicBlock copy = new LogicBlockManager.LogicBlock();
        copy.targetWidget = orig.targetWidget;
        copy.targetMode = orig.targetMode;
        copy.event = orig.event;
        copy.action = orig.action;
        copy.params = orig.params;
        return copy;
    }

    // ---- Collection Drawer (shared across all projects) ----

    private File getCollectionDir() {
        File dir = new File(android.os.Environment.getExternalStorageDirectory(),
            ".dragweb/collections");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void saveAllBlocksToCollection() {
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (blocks.isEmpty()) {
            Toast.makeText(this, "No blocks to save", Toast.LENGTH_SHORT).show();
            return;
        }
        List<LogicBlockManager.LogicBlock> chain = new ArrayList<>();
        for (LogicBlockManager.LogicBlock b : blocks) chain.add(cloneBlock(b));
        showSaveCollectionDialog(chain);
    }

    private void setupCollectionDrawer() {
        if (dropSaveAllCollection != null) {
            dropSaveAllCollection.setOnDragListener((v, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return event.getClipDescription() != null
                            && event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
                    case DragEvent.ACTION_DRAG_ENTERED:
                        v.setAlpha(0.7f);
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                    case DragEvent.ACTION_DRAG_ENDED:
                        v.setAlpha(1.0f);
                        return true;
                    case DragEvent.ACTION_DROP:
                        v.setAlpha(1.0f);
                        saveAllBlocksToCollection();
                        return true;
                }
                return true;
            });
        }
        if (dropSaveCollection != null) {
            dropSaveCollection.setOnDragListener((v, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return event.getClipDescription() != null
                            && event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
                    case DragEvent.ACTION_DRAG_ENTERED:
                        v.setAlpha(0.7f);
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                    case DragEvent.ACTION_DRAG_ENDED:
                        v.setAlpha(1.0f);
                        return true;
                    case DragEvent.ACTION_DROP:
                        v.setAlpha(1.0f);
                        Object state = event.getLocalState();
                        if (state instanceof Integer) {
                            saveBlockChainToCollection((Integer) state);
                        }
                        return true;
                }
                return true;
            });
        }
        if (dropDeleteCollection != null) {
            dropDeleteCollection.setOnDragListener((v, event) -> {
                if (event.getAction() == DragEvent.ACTION_DROP) {
                    Object state = event.getLocalState();
                    if (state instanceof Integer) {
                        saveUndoState();
                        logicBlockManager.removeBlock((Integer) state);
                        refreshWorkspace();
                    } else if (event.getClipData() != null && event.getClipData().getItemCount() > 0) {
                        String dropData = event.getClipData().getItemAt(0).getText().toString();
                        if (dropData.startsWith("reorder_event|")) {
                            String evKey = dropData.substring(14);
                            saveUndoState();
                            logicBlockManager.getBlocks().removeIf(b -> evKey.equals(b.event));
                            refreshWorkspace();
                        }
                    }
                    return true;
                }
                if (event.getAction() == DragEvent.ACTION_DRAG_ENTERED) v.setAlpha(0.7f);
                if (event.getAction() == DragEvent.ACTION_DRAG_EXITED
                    || event.getAction() == DragEvent.ACTION_DRAG_ENDED) v.setAlpha(1.0f);
                return true;
            });
        }
        if (dropDuplicateCollection != null) {
            dropDuplicateCollection.setOnDragListener((v, event) -> {
                if (event.getAction() == DragEvent.ACTION_DROP) {
                    Object state = event.getLocalState();
                    if (state instanceof Integer) {
                        saveUndoState();
                        int idx = (Integer) state;
                        LogicBlockManager.LogicBlock orig = logicBlockManager.getBlocks().get(idx);
                        logicBlockManager.addBlock(cloneBlock(orig));
                        refreshWorkspace();
                    }
                    return true;
                }
                if (event.getAction() == DragEvent.ACTION_DRAG_ENTERED) v.setAlpha(0.7f);
                if (event.getAction() == DragEvent.ACTION_DRAG_EXITED
                    || event.getAction() == DragEvent.ACTION_DRAG_ENDED) v.setAlpha(1.0f);
                return true;
            });
        }
    }

    /**
     * Save the dragged block plus every block under it (same and following
     * indices) as a reusable collection in /sdcard/.dragweb/collections.
     */
    private void saveBlockChainToCollection(int fromIndex) {
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (fromIndex < 0 || fromIndex >= blocks.size()) return;
        List<LogicBlockManager.LogicBlock> chain = new ArrayList<>();
        for (int i = fromIndex; i < blocks.size(); i++) {
            chain.add(cloneBlock(blocks.get(i)));
        }
        showSaveCollectionDialog(chain);
    }

    private void showSaveCollectionDialog(List<LogicBlockManager.LogicBlock> chain) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), 0);

        TextInputLayout til = createTil("Collection name");
        TextInputEditText input = (TextInputEditText) til.getEditText();
        if (input != null) input.setText("collection_" + System.currentTimeMillis());
        layout.addView(til);

        TextView count = new TextView(this);
        count.setText("Saving " + chain.size() + " block(s)");
        count.setTextSize(12);
        count.setTextColor(Color.parseColor("#7A8B9C"));
        count.setPadding(0, dp(6), 0, 0);
        layout.addView(count);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Save to Collection")
            .setView(layout)
            .setPositiveButton("Save", (d, w) -> {
                String name = getText(til);
                if (name.isEmpty()) name = "collection_" + System.currentTimeMillis();
                try {
                    File dir = getCollectionDir();
                    File file = new File(dir, name.replaceAll("[^a-zA-Z0-9_-]", "_") + ".json");
                    String json = new com.google.gson.Gson().toJson(chain);
                    FileUtil.writeFile(file.getAbsolutePath(), json);
                    Toast.makeText(this, "Saved: " + file.getName(), Toast.LENGTH_SHORT).show();
                    refreshCollectionList();
                } catch (Exception e) {
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void refreshCollectionList() {
        if (collectionList == null) return;
        collectionList.removeAllViews();
        File dir = getCollectionDir();
        File[] files = dir.listFiles((f, n) -> n.endsWith(".json"));
        if (files == null || files.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("Drop a block on \"Save to Collection\" to add one.");
            empty.setTextSize(12);
            empty.setTextColor(Color.parseColor("#7A8B9C"));
            empty.setPadding(dp(4), dp(8), dp(4), 0);
            collectionList.addView(empty);
            return;
        }
        for (File f : files) {
            collectionList.addView(createCollectionRow(f));
        }
    }

    private View createCollectionRow(File file) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(Color.parseColor("#22000000"));
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(lp);

        TextView name = new TextView(this);
        name.setText(file.getName().replace(".json", ""));
        name.setTextColor(Color.parseColor("#0D47A1"));
        name.setTextSize(13);
        name.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        name.setLayoutParams(nameLp);
        row.addView(name);

        TextView load = new TextView(this);
        load.setText("LOAD");
        load.setTextSize(11);
        load.setTypeface(null, Typeface.BOLD);
        load.setTextColor(Color.WHITE);
        load.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable loadBg = new GradientDrawable();
        loadBg.setCornerRadius(dp(4));
        loadBg.setColor(COLOR_CSS);
        load.setBackground(loadBg);
        LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        loadLp.setMargins(dp(4), 0, dp(4), 0);
        load.setLayoutParams(loadLp);
        load.setOnClickListener(v -> loadCollection(file));
        row.addView(load);

        TextView del = new TextView(this);
        del.setText("✕");
        del.setTextSize(12);
        del.setTypeface(null, Typeface.BOLD);
        del.setTextColor(Color.WHITE);
        del.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable delBg = new GradientDrawable();
        delBg.setCornerRadius(dp(4));
        delBg.setColor(Color.parseColor("#C62828"));
        del.setBackground(delBg);
        del.setOnClickListener(v -> {
            file.delete();
            refreshCollectionList();
        });
        row.addView(del);

        return row;
    }

    private void loadCollection(File file) {
        try {
            String json = FileUtil.readFile(file.getAbsolutePath());
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<LogicBlockManager.LogicBlock>>(){}.getType();
            List<LogicBlockManager.LogicBlock> chain =
                new com.google.gson.Gson().fromJson(json, type);
            if (chain == null || chain.isEmpty()) {
                Toast.makeText(this, "Empty collection", Toast.LENGTH_SHORT).show();
                return;
            }
            saveUndoState();
            for (LogicBlockManager.LogicBlock b : chain) {
                logicBlockManager.addBlock(cloneBlock(b));
            }
            refreshWorkspace();
            Toast.makeText(this, "Loaded " + chain.size() + " block(s)", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupWorkspaceDragDrop() {
        blockWorkspace.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return event.getClipDescription() != null
                        && event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
                case DragEvent.ACTION_DRAG_LOCATION:
                    showDropIndicator(event.getY());
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    hideDropIndicator();
                    setWorkspaceHighlight(false, null);
                    return true;
                case DragEvent.ACTION_DROP:
                    hideDropIndicator();
                    setWorkspaceHighlight(false, null);
                    Object localState = event.getLocalState();
                    if (localState instanceof BlockDef) {
                        BlockDef def = (BlockDef) localState;
                        if ("C".equals(def.shape) || "E".equals(def.shape) || CAT_EVENT.equals(def.category)) {
                            addBlockFromDef(def);
                        } else if (CAT_VALUE.equals(def.category)) {
                            // Value tokens dropped onto the workspace (not a chip) are
                            // ignored — they are only meaningful inside a logic param.
                            Toast.makeText(LogicBlockActivity.this,
                                "Drop value tokens onto a chip slot",
                                Toast.LENGTH_SHORT).show();
                        } else {
                            float y = event.getY();
                            List<ViewInfo> allViews = new ArrayList<>();
                            collectBlockViewInfo(blockWorkspace, 0, allViews);
                            List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
                            int targetIdx = -1;
                            for (ViewInfo vi : allViews) {
                                float midY = vi.top + vi.height / 2f;
                                if (y < midY) {
                                    targetIdx = vi.blockIndex;
                                    break;
                                }
                                targetIdx = vi.blockIndex + 1;
                            }
                            if (targetIdx < 0) targetIdx = blocks.size();
                            if (targetIdx > blocks.size()) targetIdx = blocks.size();

                            String newEventKey = inferEventForInsertion(def, targetIdx);

                            saveUndoState();
                            LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                            block.targetWidget = "";
                            block.targetMode = CAT_LOGIC.equals(def.category) ? "logic"
                                : CAT_VARIABLE.equals(def.category) ? "variable"
                                : CAT_ASD.equals(def.category) ? "source" : "id";
                            block.event = mapEventKey(newEventKey);
                            if (CAT_ASD.equals(def.category)) block.event = "asd";
                            block.action = mapActionKey(def.id);
                            block.params = defaultParamsFor(def);
                            blocks.add(targetIdx, block);
                            refreshWorkspace();
                        }
                    } else if (localState instanceof Integer) {
                        int fromIndex = (Integer) localState;
                        float dropY = event.getY();
                        reorderBlock(fromIndex, dropY);
                    } else if (localState instanceof String && ((String) localState).startsWith("event:")) {
                        String eventKey = ((String) localState).substring(6);
                        float dropY = event.getY();
                        reorderEventGroup(eventKey, dropY);
                    }
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    hideDropIndicator();
                    setWorkspaceHighlight(false, null);
                    return true;
            }
            return false;
        });
    }

    /**
     * Drop-anywhere reorder. Walks every workspace row that carries a
     * block index tag, picks the one whose midpoint is closest to the
     * drop Y, and inserts the moving block before/after based on which
     * half it fell into. If the drop is below the last row, append.
     * The block inherits its new neighbour's event so cross-parent moves
     * just work.
     */
    private void reorderBlock(int fromIndex, float y) {
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (fromIndex < 0 || fromIndex >= blocks.size()) return;

        List<ViewInfo> allViews = new ArrayList<>();
        collectBlockViewInfo(blockWorkspace, 0, allViews);

        int targetIdx = -1;
        for (ViewInfo vi : allViews) {
            float midY = vi.top + vi.height / 2f;
            if (y < midY) {
                targetIdx = vi.blockIndex;
                break;
            }
            targetIdx = vi.blockIndex + 1;
        }

        if (targetIdx < 0) targetIdx = blocks.size();
        if (targetIdx > blocks.size()) targetIdx = blocks.size();

        if (targetIdx == fromIndex || targetIdx == fromIndex + 1) {
            refreshWorkspace();
            return;
        }

        saveUndoState();
        LogicBlockManager.LogicBlock moving = blocks.remove(fromIndex);
        if (targetIdx > fromIndex) targetIdx--;

        // Adopt the event of whatever group the block landed in.
        String newEventKey;
        if (targetIdx <= 0) {
            newEventKey = blocks.isEmpty() ? activeEventKey : blocks.get(0).event;
        } else {
            newEventKey = blocks.get(targetIdx - 1).event;
        }

        // Don't drop a CSS rule into a JS event (or vice versa) silently — coerce
        // the event so the block stays valid in its new parent. Tab layout already
        // separates the workspaces, so this only matters when blocks are loaded
        // from disk into a mixed list.
        BlockDef def = findBlockDef(moving.action);
        boolean isCssEventTarget = newEventKey != null && newEventKey.startsWith("css:");
        boolean isCssAction = def != null && CAT_CSS.equals(def.category);
        if (isCssAction && !isCssEventTarget) {
            newEventKey = "css:hover";
        } else if (!isCssAction && isCssEventTarget) {
            newEventKey = activeEventKey != null ? activeEventKey : "load";
        }

        moving.event = newEventKey;
        blocks.add(targetIdx, moving);
        refreshWorkspace();
    }

    /**
     * Pick the event key a block should inherit when newly inserted at
     * {@code insertIdx}. Falls back to {@link #activeEventKey} for empty
     * lists. CSS actions are forced under a CSS pseudo-class event so the
     * generator emits them as static rules rather than runtime JS.
     */
    private String inferEventForInsertion(BlockDef def, int insertIdx) {
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        String key;
        if (blocks.isEmpty()) {
            key = activeEventKey;
        } else if (insertIdx <= 0) {
            key = blocks.get(0).event;
        } else if (insertIdx >= blocks.size()) {
            key = blocks.get(blocks.size() - 1).event;
        } else {
            key = blocks.get(insertIdx - 1).event;
        }
        if (key == null || key.isEmpty()) key = "load";

        boolean keyIsCss = key.startsWith("css:");
        boolean defIsCss = def != null && CAT_CSS.equals(def.category);
        if (defIsCss && !keyIsCss) return "css:hover";
        if (!defIsCss && keyIsCss) return activeEventKey != null && !activeEventKey.startsWith("css:")
            ? activeEventKey : "load";
        return key;
    }

    private void reorderEventGroup(String eventKey, float y) {
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        List<LogicBlockManager.LogicBlock> movingGroup = new ArrayList<>();
        for (LogicBlockManager.LogicBlock b : blocks) {
            if (eventKey.equals(b.event)) movingGroup.add(b);
        }
        if (movingGroup.isEmpty()) return;

        List<ViewInfo> allViews = new ArrayList<>();
        collectBlockViewInfo(blockWorkspace, 0, allViews);
        
        int targetIdx = -1;
        for (ViewInfo vi : allViews) {
            // When moving a group, we only want to drop between other groups or blocks.
            float midY = vi.top + vi.height / 2f;
            if (y < midY) {
                targetIdx = vi.blockIndex;
                break;
            }
            targetIdx = vi.blockIndex + 1;
        }

        if (targetIdx < 0) targetIdx = blocks.size();

        saveUndoState();
        blocks.removeIf(b -> eventKey.equals(b.event));
        if (targetIdx > blocks.size()) targetIdx = blocks.size();
        blocks.addAll(targetIdx, movingGroup);
        refreshWorkspace();
    }

    private static class ViewInfo {
        int blockIndex;
        float top;
        float height;
    }

    private void collectBlockViewInfo(ViewGroup parent, float parentTop, List<ViewInfo> result) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            float childTop = parentTop + child.getTop();
            Object tag = child.getTag();
            if (tag instanceof Integer) {
                ViewInfo vi = new ViewInfo();
                vi.blockIndex = (Integer) tag;
                vi.top = childTop;
                vi.height = child.getHeight();
                result.add(vi);
            } else if (child instanceof ViewGroup) {
                collectBlockViewInfo((ViewGroup) child, childTop, result);
            }
        }
    }

    private View dropIndicator;

    private void showDropIndicator(float y) {
        if (dropIndicator == null) {
            dropIndicator = new View(this);
            dropIndicator.setBackgroundColor(Color.parseColor("#448AFF"));
            dropIndicator.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));
        }
        
        if (dropIndicator.getParent() != null) {
            ((ViewGroup) dropIndicator.getParent()).removeView(dropIndicator);
        }

        int insertPos = -1;
        for (int i = 0; i < blockWorkspace.getChildCount(); i++) {
            View child = blockWorkspace.getChildAt(i);
            if (child == dropIndicator) continue;
            float midY = child.getTop() + child.getHeight() / 2f;
            if (y < midY) {
                insertPos = i;
                break;
            }
            insertPos = i + 1;
        }
        
        if (insertPos != -1) {
            blockWorkspace.addView(dropIndicator, insertPos);
        }
    }

    private void hideDropIndicator() {
        if (dropIndicator != null && dropIndicator.getParent() != null) {
            ((ViewGroup) dropIndicator.getParent()).removeView(dropIndicator);
        }
    }

    private void setBlockChildrenVisible(View v, boolean visible) {
        if (v == null) return;
        // Restore the ghost row itself so it doesn't stay invisible when the
        // drag is cancelled outside any drop target.
        v.setAlpha(visible ? 1f : 0.55f);
        if (!(v instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) v;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child instanceof TextView) {
                child.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            } else if (child instanceof ViewGroup) {
                setBlockChildrenVisible(child, visible);
            }
        }
    }

    /**
     * Recursively search the View / its children for an Integer tag set
     * by the workspace renderer (which tags both wrapper and inner block).
     */
    private Integer findBlockIndexTag(View v) {
        if (v == null) return null;
        Object t = v.getTag();
        if (t instanceof Integer) return (Integer) t;
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                Integer found = findBlockIndexTag(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void loadBlockDefinitions() {
        try {
            StringBuilder sb = new StringBuilder();
            java.io.InputStream is = getAssets().open("blocks.json");
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<BlockDef>>(){}.getType();
            allBlockDefs = new com.google.gson.Gson().fromJson(sb.toString(), type);
        } catch (Exception e) {
            Log.e("LogicBlockActivity", "Error loading blocks.json", e);
        }
    }

    private void setWorkspaceHighlight(boolean highlight, Object localState) {
        if (!highlight) {
            blockWorkspace.setBackground(null);
            return;
        }
        int drawableRes = R.drawable.bg_block_shape_rect;
        if (localState instanceof BlockDef) {
            BlockDef def = (BlockDef) localState;
            if ("C".equals(def.shape)) drawableRes = R.drawable.bg_block_shape_event;
            else if ("E".equals(def.shape)) drawableRes = R.drawable.bg_block_shape_logic;
        }
        blockWorkspace.setBackground(androidx.core.content.ContextCompat
            .getDrawable(this, drawableRes));
    }

    // ---- Block Palette ----

    private void showCategory(String category) {
        currentCategory = category;
        blockPaletteContainer.removeAllViews();

        BlockDef[] blocks = getBlocksForCategory(category);
        int color = getCategoryColor(category);

        for (BlockDef def : blocks) {
            blockPaletteContainer.addView(createPuzzleBlock(def, color));
        }
    }

    private View createPuzzleBlock(BlockDef def, int baseColor) {
        // Value tokens render as compact chips with no description so they
        // look like the inline literal pieces they represent.
        boolean isValue = "value".equals(def.shape) || CAT_VALUE.equals(def.category);

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(12), dp(isValue ? 6 : 8), dp(12), dp(isValue ? 6 : 10));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            isValue ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), dp(3), dp(4), dp(3));
        block.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        if ("C".equals(def.shape)) {
            bg.setCornerRadii(new float[]{dp(8), dp(8), dp(8), dp(8), dp(8), dp(8), 0, 0});
        } else if ("E".equals(def.shape)) {
            bg.setCornerRadii(new float[]{dp(2), dp(2), dp(8), dp(8), dp(8), dp(8), dp(2), dp(2)});
        } else if (isValue) {
            bg.setCornerRadius(dp(14));
        } else {
            bg.setCornerRadius(dp(6));
        }

        bg.setColor(baseColor);
        bg.setStroke(dp(1), darken(baseColor));
        block.setBackground(bg);
        block.setElevation(2);

        TextView nameText = new TextView(this);
        nameText.setText(def.label);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(isValue ? 13 : 12);
        nameText.setTypeface(null, Typeface.BOLD);
        block.addView(nameText);

        if (!isValue) {
            TextView descText = new TextView(this);
            descText.setText(def.description);
            descText.setTextColor(Color.parseColor("#E1F5FE"));
            descText.setTextSize(10);
            descText.setPadding(0, dp(2), 0, 0);
            block.addView(descText);
        }

        block.setOnLongClickListener(v -> {
            ClipData.Item item = new ClipData.Item(def.id + "|" + def.category);
            ClipData dragData = new ClipData("block", new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            v.startDragAndDrop(dragData, shadow, def, 0);
            return true;
        });
        block.setOnClickListener(v -> addBlockFromDef(def));

        return block;
    }

    // ---- Add Block Dialogs (legacy stubs kept for callers) ----

    /**
     * Universal Sketchware-style value editor:
     *   [ EditText ] [ px ] [ rem ] [ % ]   for sizes
     *   [ ColorPickerHexEditText ]          for colors
     *   [ EditText ]                        for plain strings
     *
     * @param onCommit called with the final stringified value (e.g. "16px")
     */
    private void showUniversalValueDialog(String title, String hint, String initialValue,
                                          boolean wantsUnit, boolean wantsColor,
                                          ValueCommitListener onCommit) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), 0);

        // Numeric / value field
        TextInputLayout til = createTil(hint != null ? hint : "Value");
        TextInputEditText input = (TextInputEditText) til.getEditText();
        if (input != null && initialValue != null) {
            // Strip a trailing unit from initialValue if it matches one of the chips
            String stripped = stripUnit(initialValue);
            input.setText(stripped);
            input.setSelection(input.getText().length());
        }
        layout.addView(til);

        // Unit chip row (only shown when sizes are expected)
        final String[] unitChips = {"px", "rem", "%", "em", "vw", "vh", "auto", "0"};
        final String[] selectedUnit = { detectUnit(initialValue, "px") };
        if (wantsUnit) {
            android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout chipRow = new LinearLayout(this);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);
            chipRow.setPadding(0, dp(8), 0, dp(4));

            for (String unit : unitChips) {
                TextView chip = makeUnitChip(unit, unit.equals(selectedUnit[0]));
                chip.setOnClickListener(v -> {
                    selectedUnit[0] = unit;
                    for (int i = 0; i < chipRow.getChildCount(); i++) {
                        View child = chipRow.getChildAt(i);
                        if (child instanceof TextView) {
                            String t = ((TextView) child).getText().toString();
                            styleUnitChip((TextView) child, t.equals(unit));
                        }
                    }
                });
                chipRow.addView(chip);
            }
            hsv.addView(chipRow);
            layout.addView(hsv);
        }

        // Color preview and grid (only for color actions)
        if (wantsColor && input != null) {
            input.setHint("#FF6B35 or red");

            TextView preview = new TextView(this);
            preview.setHeight(dp(28));
            preview.setBackgroundColor(parseColorSafe(initialValue, Color.LTGRAY));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28));
            lp.setMargins(0, dp(8), 0, dp(8));
            preview.setLayoutParams(lp);
            layout.addView(preview);

            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    preview.setBackgroundColor(parseColorSafe(s.toString(), Color.LTGRAY));
                }
            });

            // Grid of preset colors
            String[] colors = {
                "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
                "#03A9F4", "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
                "#FFEB3B", "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E",
                "#607D8B", "#000000", "#FFFFFF"
            };

            android.widget.GridLayout grid = new android.widget.GridLayout(this);
            grid.setColumnCount(7);
            grid.setPadding(0, dp(4), 0, dp(8));

            for (String colorHex : colors) {
                View colorView = new View(this);
                int size = dp(32);
                android.widget.GridLayout.LayoutParams glp = new android.widget.GridLayout.LayoutParams();
                glp.width = size;
                glp.height = size;
                glp.setMargins(dp(2), dp(2), dp(2), dp(2));
                colorView.setLayoutParams(glp);

                GradientDrawable cbg = new GradientDrawable();
                cbg.setCornerRadius(dp(4));
                cbg.setColor(Color.parseColor(colorHex));
                cbg.setStroke(dp(1), Color.parseColor("#22000000"));
                colorView.setBackground(cbg);

                colorView.setOnClickListener(v -> {
                    input.setText(colorHex);
                    preview.setBackgroundColor(Color.parseColor(colorHex));
                });
                grid.addView(colorView);
            }
            layout.addView(grid);
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("OK", (d, w) -> {
                String v = input != null && input.getText() != null
                    ? input.getText().toString().trim() : "";
                if (wantsUnit && !v.isEmpty()) {
                    if (!hasAnyUnit(v) && !"auto".equals(selectedUnit[0]) && !"0".equals(selectedUnit[0])) {
                        v = v + selectedUnit[0];
                    } else if ("auto".equals(selectedUnit[0])) {
                        v = "auto";
                    }
                }
                onCommit.onCommit(v);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    interface ValueCommitListener { void onCommit(String value); }

    private TextView makeUnitChip(String text, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(12);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        styleUnitChip(chip, selected);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), 0);
        chip.setLayoutParams(lp);
        return chip;
    }

    private void styleUnitChip(TextView chip, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        if (selected) {
            bg.setColor(COLOR_CSS);
            chip.setTextColor(Color.WHITE);
            chip.setTypeface(null, Typeface.BOLD);
        } else {
            bg.setColor(Color.parseColor("#11000000"));
            bg.setStroke(dp(1), Color.parseColor("#33000000"));
            chip.setTextColor(Color.parseColor("#0D47A1"));
            chip.setTypeface(null, Typeface.NORMAL);
        }
        chip.setBackground(bg);
    }

    private String detectUnit(String value, String fallback) {
        if (value == null) return fallback;
        String[] units = {"px", "rem", "%", "em", "vw", "vh"};
        for (String u : units) if (value.endsWith(u)) return u;
        if ("auto".equals(value)) return "auto";
        return fallback;
    }

    private String stripUnit(String value) {
        if (value == null) return "";
        String[] units = {"px", "rem", "%", "em", "vw", "vh"};
        for (String u : units) {
            if (value.endsWith(u)) return value.substring(0, value.length() - u.length());
        }
        return value;
    }

    private boolean hasAnyUnit(String value) {
        if (value == null) return false;
        String[] units = {"px", "rem", "%", "em", "vw", "vh", "auto"};
        for (String u : units) if (value.endsWith(u)) return true;
        return false;
    }

    private boolean isSizingAction(String id) {
        BlockDef def = findBlockDef(id);
        if (def != null && "sizing".equals(def.spec)) return true;
        if (id == null) return false;
        switch (id) {
            case "setWidth": case "setHeight": case "setMargin": case "setPadding":
            case "setRadius": case "setFontSize":
                return true;
            default: return false;
        }
    }

    private boolean isColorAction(String id) {
        BlockDef def = findBlockDef(id);
        if (def != null && "color".equals(def.spec)) return true;
        if (id == null) return false;
        switch (id) {
            case "setColor": case "setBackground": case "setBorder":
                return true;
            default: return false;
        }
    }

    private int parseColorSafe(String hex, int fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            String s = hex.trim();
            if (!s.startsWith("#")) {
                // try named colors via Color.parseColor
                return Color.parseColor(s);
            }
            return Color.parseColor(s);
        } catch (Exception e) {
            return fallback;
        }
    }

    // ---- Block Creation ----

    /** Most recent CSS pseudo-class scope chosen — new rules attach here. */
    private String activeEventKey = "css:hover";

    /**
     * Create a block from a palette definition with sensible defaults — no
     * dialogs. Users tweak the block in the workspace by tapping its chips.
     * If an "onLoad" event header already exists, CSS/animation blocks
     * attach to it as a child instead of asking.
     */
    private void addBlockFromDef(BlockDef def) {
        if (def == null) return;

        // CSS pseudo-class C-shapes (hover/before/after/focus/active)
        // act as scope headers — they switch the activeEventKey so that
        // subsequent CSS rules attach under that pseudo-class group.
        if (CAT_CSS.equals(def.category) && "C".equals(def.shape)) {
            activeEventKey = pseudoEventKey(def.id);
            addEmptyEventContainer(activeEventKey);
            return;
        }

        // Value tokens are not standalone — they only make sense after being
        // dropped onto a chip slot.
        if (CAT_VALUE.equals(def.category)) {
            Toast.makeText(this, "Drag value tokens onto a chip slot",
                Toast.LENGTH_SHORT).show();
            return;
        }

        saveUndoState();
        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
        block.targetWidget = "";
        block.targetMode = "id";
        block.action = mapActionKey(def.id);
        block.params = defaultParamsFor(def);

        if (CAT_ASD.equals(def.category)) {
            block.targetMode = "source";
            block.event = "asd";
            block.action = def.id;
            // Params populated by the source dialog below.
        } else {
            // Every remaining block type emits CSS — attach to the active
            // pseudo-class scope, defaulting to :hover so the user always
            // lands inside a valid group.
            String key = activeEventKey;
            if (key == null || !key.startsWith("css:")) key = "css:hover";
            activeEventKey = key;
            block.event = key;
        }

        logicBlockManager.addBlock(block);
        int idx = logicBlockManager.getBlocks().size() - 1;
        refreshWorkspace();

        if (CAT_ASD.equals(def.category)) {
            showSourceCodeDialog(idx, def);
        }
    }

    /** Map CSS pseudo-class block ids to the event key used by the renderer. */
    private String pseudoEventKey(String id) {
        if (id == null) return "css:hover";
        switch (id) {
            case "cssHover": return "css:hover";
            case "cssFocus": return "css:focus";
            case "cssActive": return "css:active";
            case "cssVisited": return "css:visited";
            case "cssBefore": return "css:before";
            case "cssAfter": return "css:after";
            case "cssFirstChild": return "css:first-child";
            case "cssLastChild": return "css:last-child";
            case "cssNthChild": return "css:nth-child(2n)";
            default: return "css:hover";
        }
    }

    /**
     * Edit raw HTML / CSS / JS source for an ASD block. Stored verbatim
     * in `block.params` and emitted directly into the page bundle.
     */
    private void showSourceCodeDialog(int blockIndex, BlockDef def) {
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (blockIndex < 0 || blockIndex >= blocks.size()) return;
        LogicBlockManager.LogicBlock block = blocks.get(blockIndex);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), 0);

        TextView hint = new TextView(this);
        hint.setText("Paste raw " + (def != null ? def.label : "source")
            + " — appended directly to the generated page.");
        hint.setTextSize(12);
        hint.setTextColor(Color.parseColor("#7A8B9C"));
        layout.addView(hint);

        TextInputLayout til = createTil("Source");
        TextInputEditText input = (TextInputEditText) til.getEditText();
        if (input != null) {
            input.setMinLines(8);
            input.setMaxLines(20);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setTypeface(Typeface.MONOSPACE);
            input.setTextSize(12);
            if (block.params != null) input.setText(block.params);
        }
        layout.addView(til);

        new MaterialAlertDialogBuilder(this)
            .setTitle(def != null ? def.label : "Source")
            .setView(layout)
            .setPositiveButton("Save", (d, w) -> {
                saveUndoState();
                block.params = getText(til);
                refreshWorkspace();
            })
            .setNeutralButton("Delete", (d, w) -> {
                saveUndoState();
                logicBlockManager.removeBlock(blockIndex);
                refreshWorkspace();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private boolean hasEvent(String eventKey) {
        for (LogicBlockManager.LogicBlock b : logicBlockManager.getBlocks()) {
            if (eventKey.equals(b.event)) return true;
        }
        return false;
    }

    private String defaultParamsFor(BlockDef def) {
        if (def == null || def.id == null) return "";
        switch (def.id) {
            case "setDisplay": return "display:block";
            case "setPosition": return "position:relative";
            case "setOverflow": return "overflow:hidden";
            case "setColor": return "color:#000000";
            case "setBackground": return "background:#FFFFFF";
            case "setBackgroundImage": return "backgroundImage:url('image.png')";
            case "setWidth": return "width:100px";
            case "setHeight": return "height:100px";
            case "setMaxWidth": return "maxWidth:100%";
            case "setMaxHeight": return "maxHeight:100%";
            case "setMinWidth": return "minWidth:0";
            case "setMinHeight": return "minHeight:0";
            case "setMargin": return "margin:0px";
            case "setPadding": return "padding:8px";
            case "setBorder": return "border:1px solid #000000";
            case "setRadius": return "borderRadius:4px";
            case "setBoxShadow": return "boxShadow:0 2px 8px rgba(0,0,0,0.15)";
            case "setOpacity": return "opacity:1";
            case "setZIndex": return "zIndex:1";
            case "setCursor": return "cursor:pointer";
            case "setFontSize": return "fontSize:14px";
            case "setFontFamily": return "fontFamily:sans-serif";
            case "setFontWeight": return "fontWeight:normal";
            case "setFontStyle": return "fontStyle:normal";
            case "setTextAlign": return "textAlign:left";
            case "setTextDecoration": return "textDecoration:none";
            case "setLineHeight": return "lineHeight:1.5";
            case "setLetterSpacing": return "letterSpacing:0";
            case "setFlexDirection": return "flexDirection:row";
            case "setJustifyContent": return "justifyContent:flex-start";
            case "setAlignItems": return "alignItems:stretch";
            case "setGap": return "gap:8px";
            case "setGridTemplateColumns": return "gridTemplateColumns:repeat(3, 1fr)";
            case "setTransform": return "transform:rotate(0deg)";
            case "setFilter": return "filter:none";
            // CSS animations are emitted as `animation: <name> <dur> <ease>`
            case "animateFadeIn": return "animation:fadeIn 400ms ease";
            case "animateFadeOut": return "animation:fadeOut 400ms ease";
            case "animateSlideIn": return "animation:slideIn 400ms ease";
            case "animateSlideOut": return "animation:slideOut 400ms ease";
            case "animateBounce": return "animation:bounce 600ms ease-out";
            case "animatePulse": return "animation:pulse 800ms ease-in-out";
            case "animateRotate": return "animation:rotate 600ms linear";
            case "animateShake": return "animation:shake 400ms ease-in-out";
            case "transitionAll": return "transition:all 300ms ease";
            case "transitionColor": return "transition:color 300ms ease";
            case "transitionTransform": return "transition:transform 300ms ease";
            // ASD source blocks (filled by source dialog)
            case "asdHtml": case "asdCss": case "asdJs":
            case "asdHead": case "asdMeta":
                return "";
            default: return "";
        }
    }

    /**
     * Open the animation/transition customise dialog: duration, easing,
     * delay, iteration. Updates the block's params in place.
     */
    private void showAnimationCustomizeDialog(int blockIndex) {
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (blockIndex < 0 || blockIndex >= blocks.size()) return;
        LogicBlockManager.LogicBlock block = blocks.get(blockIndex);

        String[] parts = (block.params != null ? block.params : "").split("\\|", -1);
        String name = parts.length > 0 ? parts[0] : "";
        String duration = parts.length > 1 ? parts[1] : "400ms";
        String easing = parts.length > 2 ? parts[2] : "ease";
        String delay = parts.length > 3 ? parts[3] : "0ms";
        String iter = parts.length > 4 ? parts[4] : "1";

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), 0);

        TextInputLayout tilName = createTil("Name / property");
        if (tilName.getEditText() != null) tilName.getEditText().setText(name);
        layout.addView(tilName);

        TextInputLayout tilDur = createTil("Duration (e.g. 400ms)");
        if (tilDur.getEditText() != null) tilDur.getEditText().setText(duration);
        layout.addView(tilDur);

        TextInputLayout tilEase = createTil("Easing (ease, linear, ease-in, ease-out, ease-in-out, cubic-bezier(...))");
        if (tilEase.getEditText() != null) tilEase.getEditText().setText(easing);
        layout.addView(tilEase);

        TextInputLayout tilDelay = createTil("Delay (e.g. 0ms)");
        if (tilDelay.getEditText() != null) tilDelay.getEditText().setText(delay);
        layout.addView(tilDelay);

        TextInputLayout tilIter = createTil("Iterations (number or 'infinite')");
        if (tilIter.getEditText() != null) tilIter.getEditText().setText(iter);
        layout.addView(tilIter);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Customise " + (block.action != null ? block.action : "animation"))
            .setView(layout)
            .setPositiveButton("Save", (d, w) -> {
                saveUndoState();
                block.params = getText(tilName) + "|" + getText(tilDur) + "|"
                    + getText(tilEase) + "|" + getText(tilDelay) + "|" + getText(tilIter);
                refreshWorkspace();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void createBlock(BlockDef eventDef, BlockDef actionDef, String value) {
        saveUndoState();

        String target = getTargetValue();
        String targetMode = getTargetMode();
        String eventKey = mapEventKey(eventDef.id);
        String actionKey = mapActionKey(actionDef.id);
        String params = mapParams(actionDef.id, value);

        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
        block.targetWidget = target;
        block.targetMode = targetMode;
        block.event = eventKey;
        block.action = actionKey;
        block.params = params;

        logicBlockManager.addBlock(block);
        refreshWorkspace();
        Toast.makeText(this, "Block added", Toast.LENGTH_SHORT).show();
    }

    private void showLogicBlockInput(BlockDef def) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        switch (def.id) {
            case "ifBlock":
            case "ifElseBlock": {
                TextInputLayout tilLeft = createTil("Left value");
                TextInputEditText inputLeft = (TextInputEditText) tilLeft.getEditText();
                layout.addView(tilLeft);

                TextInputLayout tilOp = createTil("Operator (==, !=, >, <)");
                TextInputEditText inputOp = (TextInputEditText) tilOp.getEditText();
                if (inputOp != null) inputOp.setText("==");
                layout.addView(tilOp);

                TextInputLayout tilRight = createTil("Right value");
                layout.addView(tilRight);

                TextInputLayout tilAction = createTil("Then action (JS)");
                layout.addView(tilAction);

                TextInputLayout tilElse = null;
                if ("ifElseBlock".equals(def.id)) {
                    tilElse = createTil("Else action (JS)");
                    layout.addView(tilElse);
                }

                final TextInputLayout finalTilElse = tilElse;
                new MaterialAlertDialogBuilder(this)
                    .setTitle(def.label)
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "logic";
                        block.event = "immediate";
                        block.action = def.id;
                        String left = getText(tilLeft);
                        String op = getText(tilOp);
                        String right = getText(tilRight);
                        String action = getText(tilAction);
                        String elseAction = finalTilElse != null ? getText(finalTilElse) : "";
                        block.params = left + "|" + op + "|" + right + "|" + action
                            + (elseAction.isEmpty() ? "" : "|" + elseAction);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            case "delay": {
                TextInputLayout tilMs = createTil("Delay (ms)");
                TextInputEditText inputMs = (TextInputEditText) tilMs.getEditText();
                if (inputMs != null) inputMs.setText("1000");
                layout.addView(tilMs);

                TextInputLayout tilAction = createTil("Action after delay (JS)");
                layout.addView(tilAction);

                new MaterialAlertDialogBuilder(this)
                    .setTitle("Delay")
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "logic";
                        block.event = "immediate";
                        block.action = "delay";
                        block.params = getText(tilMs) + "|" + getText(tilAction);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            case "loop": {
                TextInputLayout tilCount = createTil("Iterations");
                TextInputEditText inputCount = (TextInputEditText) tilCount.getEditText();
                if (inputCount != null) inputCount.setText("5");
                layout.addView(tilCount);

                TextInputLayout tilAction = createTil("Action per iteration (JS, use 'i' for index)");
                layout.addView(tilAction);

                new MaterialAlertDialogBuilder(this)
                    .setTitle("Loop")
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "logic";
                        block.event = "immediate";
                        block.action = "loop";
                        block.params = getText(tilCount) + "|" + getText(tilAction);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            default: {
                // Comparison blocks
                String op = "==";
                if ("compareNotEqual".equals(def.id)) op = "!=";
                else if ("compareGreater".equals(def.id)) op = ">";
                else if ("compareLess".equals(def.id)) op = "<";

                TextInputLayout tilLeft = createTil("Left value");
                layout.addView(tilLeft);
                TextInputLayout tilRight = createTil("Right value");
                layout.addView(tilRight);
                TextInputLayout tilAction = createTil("Action if true (JS)");
                layout.addView(tilAction);

                final String finalOp = op;
                new MaterialAlertDialogBuilder(this)
                    .setTitle(def.label)
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "logic";
                        block.event = "immediate";
                        block.action = "ifBlock";
                        block.params = getText(tilLeft) + "|" + finalOp + "|" + getText(tilRight) + "|" + getText(tilAction);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
        }
    }

    private void showVariableBlockInput(BlockDef def) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        switch (def.id) {
            case "createVar":
            case "createVarString":
            case "createVarNumber":
            case "createVarBoolean": {
                TextInputLayout tilName = createTil("Variable name");
                layout.addView(tilName);
                String type = "createVarString".equals(def.id) ? "string" :
                    "createVarNumber".equals(def.id) ? "number" :
                    "createVarBoolean".equals(def.id) ? "boolean" : "any";
                TextInputLayout tilValue = createTil("Initial value (" + type + ")");
                layout.addView(tilValue);

                new MaterialAlertDialogBuilder(this)
                    .setTitle(def.label)
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "variable";
                        block.event = "immediate";
                        block.action = "createVar";
                        block.params = getText(tilName) + "|" + type + "|" + getText(tilValue);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            case "setVar": {
                TextInputLayout tilName = createTil("Variable name");
                layout.addView(tilName);
                TextInputLayout tilValue = createTil("New value");
                layout.addView(tilValue);

                new MaterialAlertDialogBuilder(this)
                    .setTitle("Set Variable")
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "variable";
                        block.event = "immediate";
                        block.action = "setVar";
                        block.params = getText(tilName) + "|" + getText(tilValue);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
            case "getVar": {
                TextInputLayout tilName = createTil("Variable name");
                layout.addView(tilName);
                TextInputLayout tilTarget = createTil("Assign to element (selector, or empty for console.log)");
                layout.addView(tilTarget);

                new MaterialAlertDialogBuilder(this)
                    .setTitle("Get Variable")
                    .setView(layout)
                    .setPositiveButton("Add", (d, w) -> {
                        saveUndoState();
                        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
                        block.targetWidget = "";
                        block.targetMode = "variable";
                        block.event = "immediate";
                        block.action = "getVar";
                        block.params = getText(tilName) + "|" + getText(tilTarget);
                        logicBlockManager.addBlock(block);
                        refreshWorkspace();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
            }
        }
    }

    // ---- Workspace Rendering (Puzzle Blocks) ----

    private void refreshWorkspace() {
        blockWorkspace.removeAllViews();

        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (tvBlockCount != null) {
            tvBlockCount.setText(blocks.size() + " block" + (blocks.size() == 1 ? "" : "s") + " total");
        }
        
        // Style tab (mode 0) shows CSS-emitting blocks; Source tab (mode 1)
        // shows ASD raw-source blocks. Anything else is hidden in both tabs
        // so legacy data doesn't pollute the new workflow.
        boolean hasVisibleBlocks = false;
        for (LogicBlockManager.LogicBlock b : blocks) {
            if (isVisibleInCurrentMode(b)) {
                hasVisibleBlocks = true;
                break;
            }
        }

        if (!hasVisibleBlocks) {
            TextView empty = new TextView(this);
            empty.setText(currentMode == 0
                ? "Drag a CSS block here.\nUse a :hover / :active scope to wrap rules."
                : "Drag a Source block here.\nRaw HTML / CSS / JS goes verbatim into the page.");
            empty.setTextColor(Color.parseColor("#7A8B9C"));
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(32, 80, 32, 80);
            blockWorkspace.addView(empty);
            return;
        }

        String currentEvent = null;
        LinearLayout eventGroup = null;
        LinearLayout slot = null;

        int visibleCount = 0;
        for (int i = 0; i < blocks.size(); i++) {
            LogicBlockManager.LogicBlock block = blocks.get(i);
            String ev = block.event != null ? block.event : "immediate";

            if (!isVisibleInCurrentMode(block)) continue;

            visibleCount++;

            if (!ev.equals(currentEvent)) {
                currentEvent = ev;
                eventGroup = new LinearLayout(this);
                eventGroup.setOrientation(LinearLayout.VERTICAL);
                eventGroup.setBackground(getBlockBackground("C", false, COLOR_EVENT));
                // Do not explicitly set padding. The NinePatchDrawable will handle it.
                // If it's not a NinePatch, the user's PNG shouldn't have arbitrary padding forced.
                
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(dp(4), dp(8), dp(4), dp(8));
                eventGroup.setLayoutParams(lp);

                LinearLayout headerRow = new LinearLayout(this);
                headerRow.setOrientation(LinearLayout.HORIZONTAL);
                headerRow.setGravity(Gravity.CENTER_VERTICAL);
                
                TextView header = new TextView(this);
                header.setText(LogicBlockManager.getEventDisplayName(ev));
                header.setTextColor(Color.WHITE);
                header.setTypeface(null, Typeface.BOLD);
                header.setTextSize(13);
                header.setPadding(dp(12), dp(6), dp(8), dp(6));
                headerRow.addView(header);

                if (ev.startsWith("css:")) {
                    LogicBlockManager.LogicBlock containerBlock = null;
                    for (LogicBlockManager.LogicBlock b : blocks) {
                        if ("event_container".equals(b.action) && ev.equals(b.event)) {
                            containerBlock = b;
                            break;
                        }
                    }
                    if (containerBlock != null) {
                        String modePrefix = "id".equals(containerBlock.targetMode) ? "#"
                            : "class".equals(containerBlock.targetMode) ? "." : "";
                        String targetText = modePrefix + containerBlock.targetWidget;
                        TextView targetChip = createTargetChip(
                            (targetText.isEmpty() ? "select element" : targetText) + " \u25BC",
                            Color.parseColor("#3D5AFE"));
                        final LogicBlockManager.LogicBlock finalContainer = containerBlock;
                        targetChip.setOnClickListener(v -> showTargetPickerDialog(
                            finalContainer.targetMode, finalContainer.targetWidget,
                            (mode, value) -> {
                                saveUndoState();
                                finalContainer.targetMode = mode;
                                finalContainer.targetWidget = value;
                                for (LogicBlockManager.LogicBlock b : logicBlockManager.getBlocks()) {
                                    if (ev.equals(b.event)) {
                                        b.targetMode = mode;
                                        b.targetWidget = value;
                                    }
                                }
                                refreshWorkspace();
                            }));
                        headerRow.addView(targetChip);
                    }
                }
                
                eventGroup.addView(headerRow);

                // Make the event group draggable via its headerRow
                final String finalEv = ev;
                final LinearLayout finalGroup = eventGroup;
                headerRow.setOnLongClickListener(v -> {
                    finalGroup.setBackground(getBlockBackground("C", true, COLOR_EVENT));
                    // Hide inputs in the whole group
                    setBlockChildrenVisible(finalGroup, false);
                    
                    ClipData.Item item = new ClipData.Item("reorder_event|" + finalEv);
                    ClipData dragData = new ClipData("reorder_event", new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);
                    View.DragShadowBuilder shadow = new View.DragShadowBuilder(finalGroup);
                    finalGroup.startDragAndDrop(dragData, shadow, "event:" + finalEv, 0);
                    return true;
                });

                eventGroup.setOnDragListener((v, dragEvent) -> {
                    if (dragEvent.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                        finalGroup.setBackground(getBlockBackground("C", false, COLOR_EVENT));
                        setBlockChildrenVisible(finalGroup, true);
                    }
                    return false;
                });

                slot = new LinearLayout(this);
                slot.setOrientation(LinearLayout.VERTICAL);
                slot.setMinimumHeight(dp(36));
                LinearLayout.LayoutParams slotLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                slotLp.setMargins(dp(16), dp(4), 0, 0);
                slot.setLayoutParams(slotLp);
                attachSlotDropListener(slot, ev);
                eventGroup.addView(slot);

                blockWorkspace.addView(eventGroup);
            }

            if ("event_container".equals(block.action)) {
                // Empty container marker, do not render an action block
                continue;
            }

            View blockView = createWorkspacePuzzleBlock(block, i);
            blockView.setTag(i);
            if (slot != null) slot.addView(blockView);
        }
    }

    /**
     * Allow drops directly on an event group's slot. Drops here always
     * re-parent the block under {@code eventKey}, which is the fix for the
     * "dragging block in between parent like hover, active" bug — previously
     * the workspace listener decided the parent purely by Y-coordinate and
     * frequently snapped to the wrong group.
     */
    private void attachSlotDropListener(LinearLayout slot, String eventKey) {
        final android.graphics.drawable.Drawable original = slot.getBackground();
        slot.setOnDragListener((v, e) -> {
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return e.getLocalState() instanceof BlockDef
                        || e.getLocalState() instanceof Integer;
                case DragEvent.ACTION_DRAG_ENTERED: {
                    GradientDrawable hi = new GradientDrawable();
                    hi.setCornerRadius(dp(8));
                    hi.setColor(adjustAlpha(Color.WHITE, 60));
                    hi.setStroke(dp(2), COLOR_EVENT);
                    slot.setBackground(hi);
                    return true;
                }
                case DragEvent.ACTION_DRAG_EXITED:
                case DragEvent.ACTION_DRAG_ENDED:
                    slot.setBackground(original);
                    return true;
                case DragEvent.ACTION_DROP: {
                    slot.setBackground(original);
                    Object state = e.getLocalState();
                    if (state instanceof BlockDef) {
                        BlockDef def = (BlockDef) state;
                        if (CAT_VALUE.equals(def.category)) return false;
                        if ("C".equals(def.shape) || CAT_EVENT.equals(def.category)) {
                            // Pseudo-class scope blocks always create a new group.
                            addBlockFromDef(def);
                        } else {
                            insertActionAtSlot(def, eventKey, slot, e.getY());
                        }
                        return true;
                    } else if (state instanceof Integer) {
                        moveBlockIntoSlot((Integer) state, eventKey, slot, e.getY());
                        return true;
                    }
                    return false;
                }
            }
            return false;
        });
    }

    /**
     * Insert a fresh action block into {@code eventKey}'s group at the
     * position closest to {@code dropY} within {@code slot}.
     */
    private void insertActionAtSlot(BlockDef def, String eventKey, LinearLayout slot, float dropY) {
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();

        // Coerce to a valid event for the action's category.
        boolean isCssEvent = eventKey != null && eventKey.startsWith("css:");
        boolean isCssAction = CAT_CSS.equals(def.category);
        if (isCssAction && !isCssEvent) eventKey = "css:hover";
        else if (!isCssAction && isCssEvent) eventKey = "load";

        // Position within the global block list = position of the existing
        // group block we landed near, or the end of the group otherwise.
        int insertIdx = endOfGroup(eventKey);
        Integer nearIdx = findNearestBlockIndexInSlot(slot, dropY);
        if (nearIdx != null) insertIdx = nearIdx;

        saveUndoState();
        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
        block.targetWidget = "";
        block.targetMode = CAT_LOGIC.equals(def.category) ? "logic"
            : CAT_VARIABLE.equals(def.category) ? "variable"
            : CAT_ASD.equals(def.category) ? "source" : "id";
        block.event = CAT_ASD.equals(def.category) ? "asd" : eventKey;
        block.action = mapActionKey(def.id);
        block.params = defaultParamsFor(def);
        if (insertIdx < 0) insertIdx = 0;
        if (insertIdx > blocks.size()) insertIdx = blocks.size();
        blocks.add(insertIdx, block);

        // Drop the empty event_container marker for this group so the slot
        // shows the new action instead of staying empty.
        for (int i = blocks.size() - 1; i >= 0; i--) {
            LogicBlockManager.LogicBlock b = blocks.get(i);
            if ("event_container".equals(b.action) && eventKey.equals(b.event)) {
                blocks.remove(i);
                break;
            }
        }
        refreshWorkspace();
    }

    /**
     * Move the existing block at {@code fromIndex} into {@code eventKey}'s
     * group at the position closest to {@code dropY} within {@code slot}.
     * Inheriting the slot's event is what makes cross-parent drags work.
     */
    private void moveBlockIntoSlot(int fromIndex, String eventKey, LinearLayout slot, float dropY) {
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (fromIndex < 0 || fromIndex >= blocks.size()) return;

        LogicBlockManager.LogicBlock moving = blocks.get(fromIndex);
        BlockDef def = findBlockDef(moving.action);

        boolean isCssEvent = eventKey != null && eventKey.startsWith("css:");
        boolean isCssAction = def != null && CAT_CSS.equals(def.category);
        if (isCssAction && !isCssEvent) eventKey = "css:hover";
        else if (!isCssAction && isCssEvent) eventKey = "load";

        // Compute the destination index BEFORE we remove `moving` so the
        // collected anchor indexes are still valid; then adjust afterwards.
        Integer nearIdx = findNearestBlockIndexInSlot(slot, dropY);
        int destIdx = nearIdx != null ? nearIdx : endOfGroup(eventKey);

        saveUndoState();
        blocks.remove(fromIndex);
        if (destIdx > fromIndex) destIdx--;
        if (destIdx < 0) destIdx = 0;
        if (destIdx > blocks.size()) destIdx = blocks.size();

        moving.event = eventKey;
        blocks.add(destIdx, moving);

        // Clear empty-container markers that no longer represent an empty group.
        for (int i = blocks.size() - 1; i >= 0; i--) {
            LogicBlockManager.LogicBlock b = blocks.get(i);
            if ("event_container".equals(b.action) && eventKey.equals(b.event)) {
                blocks.remove(i);
                break;
            }
        }
        refreshWorkspace();
    }

    /**
     * Index just past the last block belonging to {@code eventKey}, or the
     * end of the list if no block belongs to that group.
     */
    private int endOfGroup(String eventKey) {
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        int last = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (eventKey != null && eventKey.equals(blocks.get(i).event)) last = i;
        }
        return last < 0 ? blocks.size() : last + 1;
    }

    /**
     * Walk the slot's children, return the global block index of the row
     * whose vertical midpoint is closest above {@code dropY}, or null when
     * the slot has no tagged rows.
     */
    private Integer findNearestBlockIndexInSlot(LinearLayout slot, float dropY) {
        Integer found = null;
        for (int i = 0; i < slot.getChildCount(); i++) {
            View child = slot.getChildAt(i);
            Object t = child.getTag();
            if (!(t instanceof Integer)) continue;
            float midY = child.getTop() + child.getHeight() / 2f;
            if (dropY < midY) return (Integer) t;
            found = ((Integer) t) + 1;
        }
        return found;
    }

    private void addEmptyEventContainer(String eventKey) {
        saveUndoState();
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (!blocks.isEmpty()) {
            LogicBlockManager.LogicBlock last = blocks.get(blocks.size() - 1);
            if ("event_container".equals(last.action) && eventKey.equals(last.event)) {
                return; // Already have an empty container for this event
            }
        }
        LogicBlockManager.LogicBlock container = new LogicBlockManager.LogicBlock();
        container.targetWidget = "";
        container.targetMode = "id";
        container.action = "event_container";
        container.event = eventKey;
        container.params = "";
        logicBlockManager.addBlock(container);
        refreshWorkspace();
    }

    private LinearLayout createEventSlot(String eventKey) {
        // This is used for nested drops - updating it to match the new C-shape style
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackground(getBlockBackground("C", false, COLOR_EVENT));
        // Removed explicit padding here as well for consistency

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(12), dp(4), 0, 0);
        wrapper.addView(inner);

        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapLp.setMargins(dp(4), dp(4), dp(4), dp(4));
        wrapper.setLayoutParams(wrapLp);

        inner.setOnDragListener((v, event) -> {

            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
                case DragEvent.ACTION_DRAG_ENTERED:
                    inner.setBackgroundColor(adjustAlpha(COLOR_EVENT, 40));
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    inner.setBackgroundColor(Color.TRANSPARENT);
                    return true;
                case DragEvent.ACTION_DROP:
                    inner.setBackgroundColor(Color.TRANSPARENT);
                    Object localState = event.getLocalState();
                    if (localState instanceof BlockDef) {
                        addBlockToEvent((BlockDef) localState, eventKey);
                    } else if (localState instanceof Integer) {
                        moveBlockToEvent((Integer) localState, eventKey);
                    }
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    inner.setBackgroundColor(Color.TRANSPARENT);
                    return true;
            }
            return false;
        });

        blockWorkspace.addView(wrapper);
        return inner;
    }

    private void addBlockToEvent(BlockDef actionDef, String eventKey) {
        // Coerce CSS-style actions onto a CSS event and JS-style actions onto a
        // JS event so the block always lands in a valid parent.
        boolean isCssEvent = eventKey != null && eventKey.startsWith("css:");
        boolean isCssAction = CAT_CSS.equals(actionDef.category);
        if (isCssAction && !isCssEvent) eventKey = "css:hover";
        else if (!isCssAction && isCssEvent) eventKey = activeEventKey != null
            && !activeEventKey.startsWith("css:") ? activeEventKey : "load";

        saveUndoState();
        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
        block.targetWidget = "";
        block.targetMode = CAT_LOGIC.equals(actionDef.category) ? "logic"
            : CAT_VARIABLE.equals(actionDef.category) ? "variable"
            : CAT_ASD.equals(actionDef.category) ? "source" : "id";
        block.event = CAT_ASD.equals(actionDef.category) ? "asd" : mapEventKey(eventKey);
        block.action = mapActionKey(actionDef.id);
        block.params = defaultParamsFor(actionDef);
        logicBlockManager.addBlock(block);
        refreshWorkspace();
    }

    private void moveBlockToEvent(int fromIndex, String eventKey) {
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (fromIndex < 0 || fromIndex >= blocks.size()) return;

        LogicBlockManager.LogicBlock block = blocks.get(fromIndex);
        BlockDef def = findBlockDef(block.action);
        boolean isCssEvent = eventKey != null && eventKey.startsWith("css:");
        boolean isCssAction = def != null && CAT_CSS.equals(def.category);
        if (isCssAction && !isCssEvent) eventKey = "css:hover";
        else if (!isCssAction && isCssEvent) eventKey = activeEventKey != null
            && !activeEventKey.startsWith("css:") ? activeEventKey : "load";

        saveUndoState();
        block.event = mapEventKey(eventKey);

        // Place at the end of the target group so the block sits with its peers.
        int targetPos = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (block.event.equals(blocks.get(i).event)) targetPos = i;
        }
        if (targetPos != -1 && targetPos != fromIndex) {
            blocks.remove(fromIndex);
            int insertAt = targetPos > fromIndex ? targetPos : targetPos + 1;
            if (insertAt > blocks.size()) insertAt = blocks.size();
            blocks.add(insertAt, block);
        }
        refreshWorkspace();
    }

    /**
     * Bottom cap of the C-shape. Uses loop.png (bottom part).
     */

    /**
     * Render a single block as a horizontal Sketchware-style "puzzle" row:
     *
     *   [target chip]  verb  [param chip]  verb  [param chip] ...
     *
     * Each block stacks immediately below the previous one with no margin
     * so the chain looks connected.
     */
    private void setBackgroundRetainingPadding(View v, android.graphics.drawable.Drawable d) {
        int pL = v.getPaddingLeft();
        int pT = v.getPaddingTop();
        int pR = v.getPaddingRight();
        int pB = v.getPaddingBottom();
        v.setBackground(d);
        v.setPadding(pL, pT, pR, pB);
    }

    private View createWorkspacePuzzleBlock(LogicBlockManager.LogicBlock block, int index) {
        // Defensive: render even when a block was loaded from a malformed file.
        if (block.targetMode == null) block.targetMode = LogicBlockManager.TARGET_MODE_ID;
        if (block.targetWidget == null) block.targetWidget = "";
        if (block.event == null) block.event = "immediate";
        if (block.action == null) block.action = "";
        if (block.params == null) block.params = "";

        BlockDef def = findBlockDef(block.action);
        String shape = def != null ? def.shape : "rect";
        String category = def != null ? def.category : CAT_CSS;

        int baseColor = getCategoryColor(category);
        int strokeColor = darken(baseColor);

        // Outer puzzle row (horizontal flow with wrap-around fallback if needed)
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(7), dp(10), dp(9));
        row.setBaselineAligned(false);

        setBackgroundRetainingPadding(row, getBlockBackground(shape, false, baseColor));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Zero margins for a connected "attached" effect like Sketchware blocks
        rowParams.setMargins(0, 0, 0, 0);
        row.setLayoutParams(rowParams);

        if (CAT_LOGIC.equals(category) || CAT_VARIABLE.equals(category)) {
            // Logic / Variable rows - leading category chip (NOT editable verb)
            // followed by editable param chips.
            String catLabel = CAT_LOGIC.equals(category) ? getLogicLabel(block.action) : getVarLabel(block.action);
            row.addView(createVerbChip(catLabel, darken(baseColor)));
            String[] parts = (block.params != null ? block.params : "").split("\\|");
            for (int p = 0; p < parts.length; p++) {
                if (parts[p] == null) continue;
                String trimmed = parts[p].trim();
                if (trimmed.isEmpty()) continue;
                final int paramIdx = p;
                TextView chip = createValueChip(trimmed + " \u25BC");
                chip.setOnClickListener(v -> editParamPart(index, paramIdx));
                attachValueDropToChip(chip, index, paramIdx);
                row.addView(chip);
            }

            if ("ifElseBlock".equals(block.action)) {
                LinearLayout column = new LinearLayout(this);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                android.graphics.drawable.Drawable eBg = androidx.core.content.ContextCompat.getDrawable(this, sketchweb.gl.R.drawable.if_else);
                if (eBg != null) {
                    eBg = androidx.core.graphics.drawable.DrawableCompat.wrap(eBg).mutate();
                    eBg.setColorFilter(new android.graphics.PorterDuffColorFilter(baseColor, android.graphics.PorterDuff.Mode.MULTIPLY));
                    setBackgroundRetainingPadding(column, eBg);
                }

                setBackgroundRetainingPadding(row, null);
                row.setPadding(dp(10), dp(4), dp(10), dp(4));
                column.addView(row);

                String[] parts = (block.params != null ? block.params : "").split("\\|", -1);
                String thenCode = parts.length > 3 ? parts[3] : "";
                String elseCode = parts.length > 4 ? parts[4] : "";

                column.addView(createIfElseSlot("then", thenCode, baseColor, index, 3));

                TextView elseTab = new TextView(this);
                elseTab.setText("else");
                elseTab.setTextColor(Color.WHITE);
                elseTab.setTextSize(13);
                elseTab.setTypeface(null, Typeface.BOLD);
                elseTab.setPadding(dp(14), dp(4), dp(14), dp(4));
                column.addView(elseTab);

                column.addView(createIfElseSlot("else", elseCode, baseColor, index, 4));

                return column;
            } else if ("ifBlock".equals(block.action)) {
                LinearLayout column = new LinearLayout(this);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                android.graphics.drawable.Drawable eBg = androidx.core.content.ContextCompat.getDrawable(this, sketchweb.gl.R.drawable.loop);
                if (eBg != null) {
                    eBg = androidx.core.graphics.drawable.DrawableCompat.wrap(eBg).mutate();
                    eBg.setColorFilter(new android.graphics.PorterDuffColorFilter(baseColor, android.graphics.PorterDuff.Mode.MULTIPLY));
                    setBackgroundRetainingPadding(column, eBg);
                }

                setBackgroundRetainingPadding(row, null);
                row.setPadding(dp(10), dp(4), dp(10), dp(4));
                column.addView(row);

                String[] parts = (block.params != null ? block.params : "").split("\\|", -1);
                String thenCode = parts.length > 3 ? parts[3] : "";
                column.addView(createIfElseSlot("then", thenCode, baseColor, index, 3));
                return column;
            }
        } else {
            // Element-targeted action row:  [target] verb [value] [extra value]
            String modePrefix = "id".equals(block.targetMode) ? "#"
                : "class".equals(block.targetMode) ? "." : "";
            String targetText = modePrefix + block.targetWidget;
            // Target chip is EDITABLE - tap opens id/class/tag picker.
            TextView targetChip = createTargetChip(
                (targetText.isEmpty() ? "page" : targetText) + " \u25BC",
                Color.parseColor("#3D5AFE"));
            targetChip.setOnClickListener(v -> showTargetPickerDialog(
                block.targetMode, block.targetWidget,
                (mode, value) -> {
                    saveUndoState();
                    block.targetMode = mode;
                    block.targetWidget = value;
                    refreshWorkspace();
                }));
            row.addView(targetChip);

            // Verb is NOT editable - just a label between chips.
            row.addView(createVerb(getActionVerb(block.action)));

            // Value chips are EDITABLE — tap opens the universal value+unit dialog.
            List<String> chips = extractValueChips(block);
            for (int ci = 0; ci < chips.size(); ci++) {
                String chipText = chips.get(ci);
                if (chipText == null || chipText.isEmpty()) continue;
                final int chipIdx = ci;
                TextView vChip = createValueChip(chipText);
                vChip.setOnClickListener(v -> editValueChip(index, chipIdx));
                attachValueDropToChip(vChip, index, chipIdx);
                row.addView(vChip);
            }
        }

        // Long press = Start Drag for reordering
        row.setOnLongClickListener(v -> {
            setBackgroundRetainingPadding(row, getBlockBackground(shape, true, baseColor));
            setBlockChildrenVisible(row, false);

            ClipData.Item item = new ClipData.Item("reorder|" + index);
            ClipData dragData = new ClipData("reorder", new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            v.startDragAndDrop(dragData, shadow, index, 0);
            return true;
        });

        row.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true; // receive subsequent events including DRAG_ENDED
                case DragEvent.ACTION_DRAG_ENDED:
                    // The drop may already have rebuilt the workspace and
                    // detached this row — restoring is harmless if so, but
                    // ensures cancelled drags revert visually.
                    setBackgroundRetainingPadding(row, getBlockBackground(shape, false, baseColor));
                    setBlockChildrenVisible(row, true);
                    row.post(() -> {
                        setBackgroundRetainingPadding(row, getBlockBackground(shape, false, baseColor));
                        setBlockChildrenVisible(row, true);
                    });
                    return true;
                default:
                    return false;
            }
        });

        // Short tap = edit value
        row.setOnClickListener(v -> showEditBlockDialog(index));

        return row;
    }

    /**
     * Render a labelled drop-slot inside an if / if-else block. The slot
     * displays the user's current code and accepts taps to edit, plus
     * value-token drops for quick var insertion.
     */
    private View createIfElseSlot(String label, String code, int baseColor,
                                  int blockIndex, int paramIndex) {
        LinearLayout slot = new LinearLayout(this);
        slot.setOrientation(LinearLayout.HORIZONTAL);
        slot.setGravity(Gravity.CENTER_VERTICAL);
        slot.setMinimumHeight(dp(36));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(28), dp(6), dp(20), dp(6));
        slot.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(6));
        bg.setColor(adjustAlpha(Color.WHITE, 220));
        bg.setStroke(dp(1), darken(baseColor));
        slot.setBackground(bg);
        slot.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView codeView = new TextView(this);
        String display = code == null || code.trim().isEmpty()
            ? label + "(...)"
            : code.trim();
        codeView.setText(display);
        codeView.setTextSize(12);
        codeView.setTextColor(code == null || code.trim().isEmpty()
            ? Color.parseColor("#90A4AE") : Color.parseColor("#212121"));
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setMinWidth(dp(140));
        slot.addView(codeView);

        slot.setOnClickListener(v -> editParamPart(blockIndex, paramIndex));

        // Accept value-token drops to splice a literal into this slot.
        slot.setOnDragListener((v, e) -> {
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return e.getLocalState() instanceof BlockDef;
                case DragEvent.ACTION_DRAG_ENTERED:
                    bg.setStroke(dp(2), baseColor);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                case DragEvent.ACTION_DRAG_ENDED:
                    bg.setStroke(dp(1), darken(baseColor));
                    return true;
                case DragEvent.ACTION_DROP:
                    bg.setStroke(dp(1), darken(baseColor));
                    Object state = e.getLocalState();
                    if (state instanceof BlockDef && CAT_VALUE.equals(((BlockDef) state).category)) {
                        spliceValueIntoParam(blockIndex, paramIndex, (BlockDef) state);
                        return true;
                    }
                    return false;
            }
            return false;
        });
        return slot;
    }

    /**
     * Replace the {@code paramIndex}-th |-separated piece of the block's
     * params with a default literal for the dropped value type. Strings get
     * single quotes; numbers and booleans go in raw.
     */
    private void spliceValueIntoParam(int blockIndex, int paramIndex, BlockDef valueDef) {
        List<LogicBlockManager.LogicBlock> all = logicBlockManager.getBlocks();
        if (blockIndex < 0 || blockIndex >= all.size()) return;
        LogicBlockManager.LogicBlock block = all.get(blockIndex);
        String[] parts = (block.params != null ? block.params : "").split("\\|", -1);
        if (paramIndex < 0) return;
        if (paramIndex >= parts.length) {
            String[] grown = new String[paramIndex + 1];
            System.arraycopy(parts, 0, grown, 0, parts.length);
            for (int i = parts.length; i < grown.length; i++) grown[i] = "";
            parts = grown;
        }
        String literal;
        switch (valueDef.id) {
            case "valueString": literal = "'text'"; break;
            case "valueNumber": literal = "0"; break;
            case "valueBoolean": literal = "true"; break;
            default: literal = "value"; break;
        }
        parts[paramIndex] = literal;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append("|");
            sb.append(parts[i]);
        }
        saveUndoState();
        block.params = sb.toString();
        refreshWorkspace();
    }

    /**
     * Edit a single value chip in a CSS / element-action block. Uses the
     * universal value+unit dialog when the action is sizing/color, otherwise
     * a plain text input.
     */
    private void editValueChip(int blockIndex, int chipIndex) {
        List<LogicBlockManager.LogicBlock> all = logicBlockManager.getBlocks();
        if (blockIndex < 0 || blockIndex >= all.size()) return;
        LogicBlockManager.LogicBlock block = all.get(blockIndex);

        boolean wantsUnit = isSizingActionForParams(block.action);
        boolean wantsColor = isColorActionForParams(block.action);

        // For "changeStyle" / "setAttribute" with two chips (property : value)
        // we only edit the chip the user tapped.
        List<String> chips = extractValueChips(block);
        if (chipIndex < 0 || chipIndex >= chips.size()) return;
        String currentValue = chips.get(chipIndex);

        showUniversalValueDialog(
            getActionVerb(block.action) + " - " + (chipIndex == 0 ? "property" : "value"),
            getValueHint(new BlockDef(block.action, "", "", CAT_CSS)),
            currentValue,
            wantsUnit && chipIndex > 0,
            wantsColor && chipIndex > 0,
            newValue -> {
                saveUndoState();
                chips.set(chipIndex, newValue);
                if (chips.size() == 2) {
                    block.params = chips.get(0) + ":" + chips.get(1);
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < chips.size(); i++) {
                        if (i > 0) sb.append("|");
                        sb.append(chips.get(i));
                    }
                    block.params = sb.toString();
                }
                refreshWorkspace();
            });
    }

    /**
     * Edit a single | -separated param of a logic / variable block.
     */
    private void editParamPart(int blockIndex, int partIndex) {
        List<LogicBlockManager.LogicBlock> all = logicBlockManager.getBlocks();
        if (blockIndex < 0 || blockIndex >= all.size()) return;
        LogicBlockManager.LogicBlock block = all.get(blockIndex);
        String[] parts = (block.params != null ? block.params : "").split("\\|", -1);
        if (partIndex < 0 || partIndex >= parts.length) return;
        String current = parts[partIndex];

        showUniversalValueDialog(
            "Edit value", "Value", current, false, false,
            newVal -> {
                saveUndoState();
                parts[partIndex] = newVal;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) sb.append("|");
                    sb.append(parts[i]);
                }
                block.params = sb.toString();
                refreshWorkspace();
            });
    }

    private boolean isSizingActionForParams(String action) {
        if (action == null) return false;
        // "changeStyle" carries property:value where value may need a unit
        return "changeStyle".equals(action);
    }

    private boolean isColorActionForParams(String action) {
        // changeStyle property "color", "background", "border" want color
        // hint - we surface that via the dialog the second chip when relevant.
        return "changeStyle".equals(action);
    }

    /** Read-only verb chip used as the leading badge for logic/var rows. */
    private TextView createVerbChip(String text, int color) {
        TextView chip = createTargetChip(text, color);
        chip.setClickable(false);
        chip.setFocusable(false);
        return chip;
    }

    /** Coloured rounded chip used for the leading "target" piece of a block. */
    private TextView createTargetChip(String text, int color) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(Color.WHITE);
        chip.setTypeface(null, Typeface.BOLD);
        chip.setTextSize(12);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(6));
        bg.setColor(color);
        chip.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dp(6), 0);
        chip.setLayoutParams(p);
        return chip;
    }

    /** White-on-blue inline verb (e.g. "set title", "show"). */
    private TextView createVerb(String text) {
        TextView verb = new TextView(this);
        verb.setText(text);
        verb.setTextColor(Color.WHITE);
        verb.setTextSize(13);
        verb.setPadding(0, 0, dp(6), 0);
        return verb;
    }

    /** White rectangular value pill (sketchware-style input look). */
    private TextView createValueChip(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(Color.parseColor("#424242"));
        chip.setTextSize(12);
        chip.setPadding(dp(12), dp(4), dp(12), dp(4));
        
        int resId = sketchweb.gl.R.drawable.block_string;
        if ("true".equals(text) || "false".equals(text)) {
            resId = sketchweb.gl.R.drawable.block_boolean;
        } else if (text.matches("-?\\d+(\\.\\d+)?")) {
            resId = sketchweb.gl.R.drawable.block_num;
        }

        android.graphics.drawable.Drawable bg = androidx.core.content.ContextCompat.getDrawable(this, resId);
        if (bg != null) {
            bg = androidx.core.graphics.drawable.DrawableCompat.wrap(bg).mutate();
            bg.setColorFilter(new android.graphics.PorterDuffColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.MULTIPLY));
            chip.setBackground(bg);
        }

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dp(6), 0);
        chip.setLayoutParams(p);
        return chip;
    }

    /**
     * Make a workspace value chip accept a dropped value-token block. The
     * dropped token is converted to a literal (string -> 'text', number -> 0,
     * boolean -> true) and replaces the chip's underlying param slice.
     */
    private void attachValueDropToChip(View chip, int blockIndex, int paramIndex) {
        chip.setOnDragListener((v, e) -> {
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return e.getLocalState() instanceof BlockDef
                        && CAT_VALUE.equals(((BlockDef) e.getLocalState()).category);
                case DragEvent.ACTION_DRAG_ENTERED:
                    chip.setAlpha(0.6f);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                case DragEvent.ACTION_DRAG_ENDED:
                    chip.setAlpha(1f);
                    return true;
                case DragEvent.ACTION_DROP:
                    chip.setAlpha(1f);
                    Object state = e.getLocalState();
                    if (state instanceof BlockDef && CAT_VALUE.equals(((BlockDef) state).category)) {
                        spliceValueIntoParam(blockIndex, paramIndex, (BlockDef) state);
                        return true;
                    }
                    return false;
            }
            return false;
        });
    }

    /** Slightly darker version of the colour for puzzle outline / chips. */
    private int darken(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.max(0f, hsv[2] * 0.78f);
        return Color.HSVToColor(hsv);
    }

    /**
     * Convert an action key into a Sketchware-style verb shown between chips.
     */
    private String getActionVerb(String action) {
        if (action == null) return "do";
        switch (action) {
            case "asdHtml": return "raw HTML";
            case "asdCss": return "raw CSS";
            case "asdJs": return "raw JS";
            case "asdHead": return "<head>";
            case "asdMeta": return "<meta>";
            case "changeStyle": return "set style";
            case "addClass": return "add class";
            case "removeClass": return "remove class";
            case "toggleClass": return "toggle class";
            case "setText": return "set text";
            case "setHTML": return "set html";
            case "showHide": return "visibility";
            case "navigate": return "go to url";
            case "goToPage": return "go to page";
            case "openPage": return "open page";
            case "alert": return "alert";
            case "consoleLog": return "console.log";
            case "setAttribute": return "set attribute";
            case "removeAttribute": return "remove attribute";
            case "setValue": return "set value";
            case "appendChild": return "append";
            case "prependChild": return "prepend";
            case "createElement": return "create element";
            case "removeElement": return "remove";
            case "scrollTo": return "scroll to";
            case "copyClipboard": return "copy clipboard";
            case "delay": return "delay";
            case "focusInput": return "focus";
            case "blurInput": return "blur";
            case "setHref": return "set href";
            case "fetchApi": return "fetch";
            case "localStorage": return "local storage";
            case "customJs": return "run js";
            case "animate": return "animate";
            default: return action;
        }
    }

    /**
     * Split a block's params into one-or-two visible value chips.
     * For "property:value" style params we render them as ["property", "value"]
     * for nicer Sketchware-like puzzle pieces.
     */
    private List<String> extractValueChips(LogicBlockManager.LogicBlock block) {
        List<String> out = new ArrayList<>();
        String params = block.params != null ? block.params : "";
        if (params.isEmpty()) return out;
        if ("changeStyle".equals(block.action) || "setAttribute".equals(block.action)) {
            String[] split = params.split(":", 2);
            for (String s : split) out.add(s.trim());
        } else {
            out.add(params);
        }
        return out;
    }

    private void showBlockActions(int index) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Block Actions")
            .setItems(new String[]{"Delete", "Move Up", "Move Down", "Duplicate"}, (dialog, which) -> {
                List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
                switch (which) {
                    case 0:
                        saveUndoState();
                        logicBlockManager.removeBlock(index);
                        refreshWorkspace();
                        break;
                    case 1:
                        if (index > 0) {
                            saveUndoState();
                            LogicBlockManager.LogicBlock temp = blocks.get(index);
                            blocks.set(index, blocks.get(index - 1));
                            blocks.set(index - 1, temp);
                            refreshWorkspace();
                        }
                        break;
                    case 2:
                        if (index < blocks.size() - 1) {
                            saveUndoState();
                            LogicBlockManager.LogicBlock temp2 = blocks.get(index);
                            blocks.set(index, blocks.get(index + 1));
                            blocks.set(index + 1, temp2);
                            refreshWorkspace();
                        }
                        break;
                    case 3:
                        saveUndoState();
                        LogicBlockManager.LogicBlock orig = blocks.get(index);
                        LogicBlockManager.LogicBlock copy = new LogicBlockManager.LogicBlock();
                        copy.targetWidget = orig.targetWidget;
                        copy.targetMode = orig.targetMode;
                        copy.event = orig.event;
                        copy.action = orig.action;
                        copy.params = orig.params;
                        logicBlockManager.addBlock(copy);
                        refreshWorkspace();
                        break;
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ---- UI Helpers ----

    private LinearLayout createBlockRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 4, 0, 4);
        return row;
    }

    private TextView createBadge(String text, int color) {
        TextView badge = new TextView(this);
        badge.setText(text);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(10);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(12, 4, 12, 4);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10);
        bg.setColor(color);
        badge.setBackground(bg);
        return badge;
    }

    private TextInputLayout createTil(String hint) {
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(hint);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxCornerRadii(dp(10), dp(10), dp(10), dp(10));
        TextInputEditText input = new TextInputEditText(this);
        input.setMinHeight(dp(44));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        til.addView(input);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 4, 0, 4);
        til.setLayoutParams(params);
        return til;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private String getText(TextInputLayout til) {
        if (til.getEditText() != null && til.getEditText().getText() != null) {
            return til.getEditText().getText().toString().trim();
        }
        return "";
    }

    private int adjustAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    // ---- Undo/Redo ----

    private void saveUndoState() {
        undoStack.add(logicBlockManager.toJson());
        if (undoStack.size() > MAX_UNDO) undoStack.remove(0);
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.size() <= 1) {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
            return;
        }
        redoStack.add(logicBlockManager.toJson());
        undoStack.remove(undoStack.size() - 1);
        logicBlockManager.fromJson(undoStack.get(undoStack.size() - 1));
        refreshWorkspace();
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "Nothing to redo", Toast.LENGTH_SHORT).show();
            return;
        }
        undoStack.add(logicBlockManager.toJson());
        String next = redoStack.remove(redoStack.size() - 1);
        logicBlockManager.fromJson(next);
        refreshWorkspace();
    }

    // ---- Target Picker (id / class / tag) ----

    /**
     * Show a Sketchware-style horizontal-chip picker for the target element.
     * The user picks Tag/Class/Id with chips, then types/picks a value with
     * autocomplete sourced from the page tree.
     */
    private void showTargetPickerDialog(String currentMode, String currentValue,
                                        TargetCommitListener listener) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), 0);

        TextView modeLabel = new TextView(this);
        modeLabel.setText("Selector type");
        modeLabel.setTextSize(12);
        modeLabel.setTextColor(Color.parseColor("#7A8B9C"));
        layout.addView(modeLabel);

        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(0, dp(6), 0, dp(8));

        final String[] modes = {"id", "class", "tag"};
        final String[] selectedMode = { currentMode != null ? currentMode : "id" };

        for (String mode : modes) {
            String label = "id".equals(mode) ? "# ID"
                         : "class".equals(mode) ? ". Class" : "<> Tag";
            TextView chip = makeUnitChip(label, mode.equals(selectedMode[0]));
            chip.setOnClickListener(v -> {
                selectedMode[0] = mode;
                for (int i = 0; i < chipRow.getChildCount(); i++) {
                    View child = chipRow.getChildAt(i);
                    if (child instanceof TextView) {
                        String t = ((TextView) child).getText().toString();
                        boolean isSelected =
                            ("id".equals(mode) && t.contains("ID"))
                            || ("class".equals(mode) && t.contains("Class"))
                            || ("tag".equals(mode) && t.contains("Tag"));
                        styleUnitChip((TextView) child, isSelected);
                    }
                }
            });
            chipRow.addView(chip);
        }
        layout.addView(chipRow);

        TextInputLayout til = new TextInputLayout(this);
        til.setHint("Selector value");
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxCornerRadii(dp(10), dp(10), dp(10), dp(10));

        android.widget.AutoCompleteTextView ac = new android.widget.AutoCompleteTextView(this);
        ac.setHint("Selector value");
        ac.setText(currentValue != null ? currentValue : "");
        ac.setThreshold(1);
        ac.setSingleLine(true);
        ac.setPadding(dp(12), dp(10), dp(12), dp(10));
        ac.setMinHeight(dp(44));
        ac.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_dropdown_item_1line, collectAutocompleteSuggestions()));

        til.addView(ac);
        layout.addView(til);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Pick target element")
            .setView(layout)
            .setPositiveButton("OK", (d, w) -> {
                String value = ac.getText() != null ? ac.getText().toString().trim() : "";
                listener.onCommit(selectedMode[0], value);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    interface TargetCommitListener { void onCommit(String mode, String value); }

    /**
     * Collect id/class/tag suggestions from the persisted page tree so the
     * autocomplete dropdown shows real elements from the user's page.
     */
    private List<String> collectAutocompleteSuggestions() {
        List<String> out = new ArrayList<>();
        try {
            File pageFile = new File(getFilesDir(), "projects/" + projectId + "_" + pageName + ".json");
            if (!pageFile.exists()) {
                pageFile = new File(getFilesDir(), "projects/" + projectId + ".json");
            }
            if (pageFile.exists()) {
                String json = FileUtil.readFile(pageFile.getAbsolutePath());
                List<java.util.Map<String, Object>> tree = new com.google.gson.Gson().fromJson(
                    json,
                    new com.google.gson.reflect.TypeToken<List<java.util.Map<String, Object>>>(){}.getType()
                );
                collectSelectorSuggestions(tree, out);
            }
        } catch (Exception e) {
            Log.w("LogicBlockActivity", "autocomplete: " + e.getMessage());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void collectSelectorSuggestions(List<java.util.Map<String, Object>> tree, List<String> out) {
        if (tree == null) return;
        for (java.util.Map<String, Object> node : tree) {
            Object fnObj = node.get("function");
            if (fnObj instanceof java.util.Map) {
                java.util.Map<String, Object> fn = (java.util.Map<String, Object>) fnObj;
                Object idObj = fn.get("id");
                if (idObj != null) {
                    String id = idObj.toString().trim();
                    if (!id.isEmpty()) out.add(id);
                }
                Object classObj = fn.get("class");
                if (classObj != null) {
                    String[] parts = classObj.toString().trim().split("\\s+");
                    for (String part : parts) {
                        if (!part.isEmpty()) out.add(part);
                    }
                }
            }
            Object childrenObj = node.get("children");
            if (childrenObj instanceof List) {
                collectSelectorSuggestions((List<java.util.Map<String, Object>>) childrenObj, out);
            }
        }
    }

    // ---- Edit existing block ----

    private void showEditBlockDialog(int index) {
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (index < 0 || index >= blocks.size()) return;
        LogicBlockManager.LogicBlock block = blocks.get(index);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), 0);

        TextInputLayout tilParams = createTil("Params (use | to separate)");
        TextInputEditText etParams = (TextInputEditText) tilParams.getEditText();
        if (etParams != null) etParams.setText(block.params);
        layout.addView(tilParams);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Edit " + getActionVerb(block.action))
            .setView(layout)
            .setPositiveButton("Save", (d, w) -> {
                saveUndoState();
                block.params = getText(tilParams);
                refreshWorkspace();
            })
            .setNeutralButton("Delete", (d, w) -> {
                saveUndoState();
                logicBlockManager.removeBlock(index);
                refreshWorkspace();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ---- Import / Export generated code ----

    private void showExportDialog() {
        String baseCss = logicBlockManager.generateBaseCssRules();
        String pseudoCss = logicBlockManager.generateCssPseudoRules();
        String js = logicBlockManager.generateJavaScript();
        String asdHtml = logicBlockManager.generateAsdSource("html");
        String asdCss = logicBlockManager.generateAsdSource("css");
        String asdJs = logicBlockManager.generateAsdSource("js");
        String asdHead = logicBlockManager.generateAsdSource("head");
        String asdMeta = logicBlockManager.generateAsdSource("meta");
        String json = logicBlockManager.toJson();

        StringBuilder code = new StringBuilder();
        if (asdMeta != null && !asdMeta.trim().isEmpty()) code.append(asdMeta).append("\n");
        if (asdHead != null && !asdHead.trim().isEmpty()) code.append(asdHead).append("\n");
        if (baseCss != null && !baseCss.trim().isEmpty()) {
            code.append("<style>\n").append(baseCss).append("</style>\n\n");
        }
        if (pseudoCss != null && !pseudoCss.trim().isEmpty()) {
            code.append("<style>\n").append(pseudoCss).append("</style>\n\n");
        }
        if (asdCss != null && !asdCss.trim().isEmpty()) {
            code.append("<style>\n").append(asdCss).append("\n</style>\n\n");
        }
        if (asdHtml != null && !asdHtml.trim().isEmpty()) {
            code.append(asdHtml).append("\n");
        }
        if ((js != null && !js.trim().isEmpty())
                || (asdJs != null && !asdJs.trim().isEmpty())) {
            code.append("<script>\n");
            if (js != null && !js.trim().isEmpty()) code.append(js);
            if (asdJs != null && !asdJs.trim().isEmpty()) {
                if (js != null && !js.trim().isEmpty()) code.append("\n");
                code.append(asdJs).append("\n");
            }
            code.append("</script>\n");
        }
        if (code.length() == 0) code.append("<!-- No logic blocks yet -->\n");

        new MaterialAlertDialogBuilder(this)
            .setTitle("Export")
            .setItems(new String[]{
                "Copy generated CSS + JS",
                "Copy blocks JSON",
                "Save to project export folder"
            }, (dialog, which) -> {
                switch (which) {
                    case 0: copyToClipboard("DragWeb code", code.toString()); break;
                    case 1: copyToClipboard("DragWeb blocks JSON", json); break;
                    case 2: saveToProjectExport(code.toString(), json); break;
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showImportDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), 0);

        TextView hint = new TextView(this);
        hint.setText("Paste blocks JSON exported from DragWeb. Existing blocks will be replaced.");
        hint.setTextSize(12);
        hint.setTextColor(Color.parseColor("#7A8B9C"));
        hint.setPadding(0, 0, 0, dp(8));
        layout.addView(hint);

        TextInputLayout til = createTil("Blocks JSON");
        TextInputEditText input = (TextInputEditText) til.getEditText();
        if (input != null) {
            input.setMinLines(6);
            input.setMaxLines(20);
            input.setGravity(Gravity.TOP | Gravity.START);
        }
        layout.addView(til);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Import")
            .setView(layout)
            .setPositiveButton("Import", (d, w) -> {
                String json = getText(til);
                if (json.isEmpty()) {
                    Toast.makeText(this, "Nothing to import", Toast.LENGTH_SHORT).show();
                    return;
                }
                int before = logicBlockManager.getBlocks().size();
                saveUndoState();
                logicBlockManager.fromJson(json);
                int after = logicBlockManager.getBlocks().size();
                refreshWorkspace();
                Toast.makeText(this, "Imported " + after + " block(s)" + (before > 0 ? " (replaced " + before + ")" : ""), Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("From clipboard", (d, w) -> {
                android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip()
                    && cm.getPrimaryClip() != null
                    && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                    if (text != null) {
                        saveUndoState();
                        logicBlockManager.fromJson(text.toString());
                        refreshWorkspace();
                        Toast.makeText(this, "Imported from clipboard", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void copyToClipboard(String label, String text) {
        android.content.ClipboardManager cm =
            (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text));
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToProjectExport(String code, String json) {
        try {
            String basePath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects/" + projectId + "/exports";
            File extDir = new File(basePath);
            if (!extDir.exists()) extDir.mkdirs();
            File codeFile = new File(extDir, pageName + "_logic.html");
            File jsonFile = new File(extDir, pageName + "_logic.json");
            FileUtil.writeFile(codeFile.getAbsolutePath(), code);
            FileUtil.writeFile(jsonFile.getAbsolutePath(), json);
            Toast.makeText(this, "Saved to: " + extDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---- JS Preview ----

    private void showJsPreview() {
        String baseCss = logicBlockManager.generateBaseCssRules();
        String pseudoCss = logicBlockManager.generateCssPseudoRules();
        String js = logicBlockManager.generateJavaScript();
        String asdHtml = logicBlockManager.generateAsdSource("html");
        String asdCss = logicBlockManager.generateAsdSource("css");
        String asdJs = logicBlockManager.generateAsdSource("js");
        String asdHead = logicBlockManager.generateAsdSource("head");
        String asdMeta = logicBlockManager.generateAsdSource("meta");

        StringBuilder combined = new StringBuilder();
        if (asdMeta != null && !asdMeta.trim().isEmpty()) {
            combined.append("<!-- ASD <meta> -->\n").append(asdMeta).append("\n\n");
        }
        if (asdHead != null && !asdHead.trim().isEmpty()) {
            combined.append("<!-- ASD <head> -->\n").append(asdHead).append("\n\n");
        }
        if (baseCss != null && !baseCss.trim().isEmpty()) {
            combined.append("<style>\n").append(baseCss).append("</style>\n\n");
        }
        if (pseudoCss != null && !pseudoCss.trim().isEmpty()) {
            combined.append("<style>\n").append(pseudoCss).append("</style>\n\n");
        }
        if (asdCss != null && !asdCss.trim().isEmpty()) {
            combined.append("<style>\n").append(asdCss).append("\n</style>\n\n");
        }
        if (asdHtml != null && !asdHtml.trim().isEmpty()) {
            combined.append("<!-- ASD HTML -->\n").append(asdHtml).append("\n\n");
        }
        if ((js != null && !js.trim().isEmpty())
                || (asdJs != null && !asdJs.trim().isEmpty())) {
            combined.append("<script>\n");
            if (js != null && !js.trim().isEmpty()) combined.append(js);
            if (asdJs != null && !asdJs.trim().isEmpty()) {
                if (js != null && !js.trim().isEmpty()) combined.append("\n");
                combined.append(asdJs).append("\n");
            }
            combined.append("</script>\n");
        }
        if (combined.length() == 0) combined.append("// No blocks yet");

        ScrollView sv = new ScrollView(this);
        sv.setPadding(24, 16, 24, 16);
        TextView tv = new TextView(this);
        tv.setText(combined.toString());
        tv.setTextSize(12);
        tv.setTextColor(Color.parseColor("#A5D6A7"));
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);

        GradientDrawable codeBg = new GradientDrawable();
        codeBg.setCornerRadius(12);
        codeBg.setColor(Color.parseColor("#0A0A14"));
        codeBg.setStroke(1, Color.parseColor("#333355"));
        tv.setBackground(codeBg);
        tv.setPadding(16, 12, 16, 12);
        sv.addView(tv);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Generated CSS / JS")
            .setView(sv)
            .setPositiveButton("Close", null)
            .show();
    }

    // ---- Save and Exit ----

    private void saveAndFinish() {
        File dir = new File(getFilesDir(), "projects");
        if (!dir.exists()) dir.mkdirs();
        String pageName = getIntent().getStringExtra("page_name");
        if (pageName == null || pageName.isEmpty()) pageName = "index";

        File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
        FileUtil.writeFile(logicFile.getAbsolutePath(), logicBlockManager.toJson());

        // Also save to external
        try {
            String basePath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.dragweb/projects/" + projectId;
            File extDir = new File(basePath);
            if (!extDir.exists()) extDir.mkdirs();
            FileUtil.writeFile(new File(extDir, pageName + "_logic.json").getAbsolutePath(), logicBlockManager.toJson());
        } catch (Exception e) {
            Log.w("LogicBlockActivity", "Could not save to external: " + e.getMessage());
        }

        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onBackPressed() {
        saveAndFinish();
    }

    // ---- Block Definitions ----

 private BlockDef[] getBlocksForCategory(String category) {
        List<BlockDef> filtered = new ArrayList<>();
        for (BlockDef def : allBlockDefs) {
            if (category.equals(def.category)) {
                filtered.add(def);
            }
    }
        return filtered.toArray(new BlockDef[0]);
}
    private BlockDef findBlockDef(String id) {
        for (BlockDef def : allBlockDefs) {
            if (id.equals(def.id)) return def;
        }
        return null;
    }

    /**
     * Style tab (mode 0) shows blocks that emit CSS — anything tagged with
     * the css category, every css:* event scope, and the empty event_container
     * markers used to display an empty pseudo-class group. Source tab (mode 1)
     * shows ASD blocks. Anything else is legacy noise and stays hidden.
     */
    private boolean isVisibleInCurrentMode(LogicBlockManager.LogicBlock block) {
        if (block == null) return false;
        String ev = block.event != null ? block.event : "";
        BlockDef d = findBlockDef(block.action);
        boolean isCss = (d != null && CAT_CSS.equals(d.category)) || ev.startsWith("css:");
        boolean isAsd = (d != null && CAT_ASD.equals(d.category)) || "asd".equals(ev);
        if (currentMode == 0) return isCss && !isAsd;
        return isAsd;
    }

    private int getCategoryColor(String category) {
        switch (category) {
            case CAT_EVENT: return COLOR_EVENT;
            case CAT_CSS: return COLOR_CSS;
            case CAT_HTML: return COLOR_HTML;
            case CAT_LOGIC: return COLOR_LOGIC;
            case CAT_VARIABLE: return COLOR_VARIABLE;
            case CAT_ANIMATION: return COLOR_ANIMATION;
            case CAT_ASD: return COLOR_ASD;
            case CAT_VALUE: return COLOR_VALUE;
            default: return COLOR_CSS;
        }
    }

    // ---- Mapping helpers ----

    private String mapEventKey(String id) {
        switch (id) {
            case "onClick": return "click";
            case "onHover": return "hover";
            case "onLoad": return "load";
            case "onInput": return "input";
            case "onSubmit": return "submit";
            case "onScroll": return "scroll";
            case "onKeyDown": return "keydown";
            case "onChange": return "change";
            default: return id;
        }
    }

    private String mapActionKey(String id) {
        switch (id) {
            // All CSS property setters share the changeStyle action so the
            // generator emits a single property:value pair per block.
            case "setDisplay": case "setPosition": case "setOverflow":
            case "setColor": case "setBackground": case "setBackgroundImage":
            case "setWidth": case "setHeight":
            case "setMaxWidth": case "setMaxHeight":
            case "setMinWidth": case "setMinHeight":
            case "setMargin": case "setPadding":
            case "setBorder": case "setRadius": case "setBoxShadow":
            case "setOpacity": case "setZIndex": case "setCursor":
            case "setFontSize": case "setFontFamily": case "setFontWeight":
            case "setFontStyle": case "setTextAlign": case "setTextDecoration":
            case "setLineHeight": case "setLetterSpacing":
            case "setFlexDirection": case "setJustifyContent": case "setAlignItems":
            case "setGap": case "setGridTemplateColumns":
            case "setTransform": case "setFilter":
                return "changeStyle";
            default: return id;
        }
    }

    private String mapParams(String actionId, String value) {
        switch (actionId) {
            case "setDisplay": return "display:" + value;
            case "setColor": return "color:" + value;
            case "setBackground": return "background:" + value;
            case "setWidth": return "width:" + value;
            case "setHeight": return "height:" + value;
            case "setMargin": return "margin:" + value;
            case "setPadding": return "padding:" + value;
            case "setBorder": return "border:" + value;
            case "setRadius": return "borderRadius:" + value;
            case "setOpacity": return "opacity:" + value;
            case "setFontSize": return "fontSize:" + value;
            case "showElement": return "show";
            case "hideElement": return "hide";
            case "toggleElement": return "toggle";
            case "focusInput": return "focus";
            default: return value;
        }
    }

    private String getValueHint(BlockDef def) {
        switch (def.id) {
            case "setDisplay": return "block, none, flex, grid";
            case "setColor": return "Color (e.g. #ff0000)";
            case "setBackground": return "Background color or gradient";
            case "setWidth": return "Width (e.g. 100px, 50%)";
            case "setHeight": return "Height (e.g. 200px)";
            case "setText": return "New text content";
            case "setHTML": return "HTML content";
            case "navigate": return "URL (e.g. https://example.com)";
            case "setHref": return "Href (e.g. #section, about.html, https://...)";
            case "goToPage": return "Page name (e.g. about)";
            case "alert": return "Alert message";
            case "addClass": case "removeClass": case "toggleClass": return "CSS class name";
            default: return "Value";
        }
    }

    private String getLogicLabel(String action) {
        if (action == null) return "LOGIC";
        switch (action) {
            case "ifBlock": return "IF";
            case "ifElseBlock": return "IF / ELSE";
            case "delay": return "DELAY";
            case "loop": return "LOOP";
            default: return "LOGIC";
        }
    }

    private String getVarLabel(String action) {
        if (action == null) return "VAR";
        switch (action) {
            case "createVar": return "VAR CREATE";
            case "setVar": return "VAR SET";
            case "getVar": return "VAR GET";
            default: return "VAR";
        }
    }

    private String formatLogicParams(LogicBlockManager.LogicBlock block) {
        if (block == null) return "";
        String params = block.params != null ? block.params : "";
        String action = block.action != null ? block.action : "";
        String[] parts = params.split("\\|");
        switch (action) {
            case "ifBlock":
            case "ifElseBlock":
                if (parts.length >= 4) {
                    String result = parts[0] + " " + parts[1] + " " + parts[2] + " -> " + parts[3];
                    if (parts.length >= 5) result += "\nELSE -> " + parts[4];
                    return result;
                }
                return params;
            case "delay":
                if (parts.length >= 2) return parts[0] + "ms -> " + parts[1];
                return params;
            case "loop":
                if (parts.length >= 2) return "x" + parts[0] + " -> " + parts[1];
                return params;
            default: return params;
        }
    }

    private boolean isHtmlAction(String action) {
        if (action == null) return false;
        switch (action) {
            case "setText": case "showHide": case "navigate":
            case "goToPage": case "scrollTo": case "alert":
            case "removeElement": case "setAttribute": case "setHref": case "focusInput":
                return true;
            default: return false;
        }
    }

    // ---- Block Definition ----

    private android.graphics.drawable.Drawable getBlockBackground(String shape, boolean selected, int color) {
        android.graphics.drawable.Drawable d = getBlockDrawable(shape);
        if (d != null) {
            d = androidx.core.graphics.drawable.DrawableCompat.wrap(d).mutate();
            if (selected) {
                d.setColorFilter(new android.graphics.PorterDuffColorFilter(darken(color), android.graphics.PorterDuff.Mode.MULTIPLY));
            } else {
                d.setColorFilter(new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.MULTIPLY));
            }
        }
        return d;
    }

    private android.graphics.drawable.Drawable getBlockDrawable(String shape) {
        int resId;
        if ("E".equals(shape)) {
            resId = sketchweb.gl.R.drawable.if_else;
        } else if ("C".equals(shape)) {
            resId = sketchweb.gl.R.drawable.loop;
        } else {
            resId = sketchweb.gl.R.drawable.block_ori;
        }
        
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeResource(getResources(), resId);
        if (bitmap == null) {
            return androidx.core.content.ContextCompat.getDrawable(this, resId);
        }
        
        byte[] chunk = bitmap.getNinePatchChunk();
        if (chunk != null && android.graphics.NinePatch.isNinePatchChunk(chunk)) {
            // The file is a valid 9-patch compiled by AAPT, but native 9-patch
            // stretches C and E shapes symmetrically, crushing the 'else' bar.
            // We use our custom Sketchware slicer to enforce strict vertical zones.
            return new BlockNineSliceDrawable(bitmap, shape);
        }
        
        return new BlockNineSliceDrawable(bitmap, shape);
    }

    static class BlockDef {
        String id;
        String label;
        String description;
        String category;
        String shape;
        String spec;

        BlockDef(String id, String label, String description, String category) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.category = category;
        }
    }

    private class BlockNineSliceDrawable extends android.graphics.drawable.Drawable {
        private android.graphics.Bitmap bitmap;
        private android.graphics.Paint paint;
        private android.graphics.Rect src = new android.graphics.Rect();
        private android.graphics.Rect dst = new android.graphics.Rect();
        private int leftPadding, topPadding, rightPadding, bottomPadding;
        private int srcX1, srcX2, srcY1, srcY2;

        public BlockNineSliceDrawable(android.graphics.Bitmap bitmap, String shape) {
            this.bitmap = bitmap;
            this.paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
            
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();

            // Sketchware logic: stretch horizontally right in the middle
            srcX1 = (int) (w * 0.45f);
            srcX2 = srcX1 + 1;

            if ("C".equals(shape)) {
                // Stretch at 35% height to safely stretch the spine without distorting headers
                srcY1 = (int) (h * 0.35f);
                srcY2 = srcY1 + 1;
                
                topPadding = dp(12);
                bottomPadding = dp(20);
                leftPadding = dp(16);
                rightPadding = dp(4);
            } else if ("E".equals(shape)) {
                // E-shape must stretch ABOVE the 'else' bar. The 'else' bar and footer remain fixed.
                srcY1 = (int) (h * 0.25f);
                srcY2 = srcY1 + 1;
                
                topPadding = dp(12);
                bottomPadding = dp(40); // Massive bottom padding to cover else bar + footer
                leftPadding = dp(16);
                rightPadding = dp(4);
            } else {
                // Standard action blocks stretch in the middle
                srcY1 = (int) (h * 0.5f);
                srcY2 = srcY1 + 1;
                
                topPadding = dp(6);
                bottomPadding = dp(6);
                leftPadding = dp(8);
                rightPadding = dp(4);
            }
        }

        @Override
        public boolean getPadding(android.graphics.Rect padding) {
            padding.set(leftPadding, topPadding, rightPadding, bottomPadding);
            return true;
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();

            int dstX1 = bounds.left + srcX1;
            int dstX2 = bounds.right - (w - srcX2);
            if (dstX2 < dstX1) {
                int mid = (dstX1 + dstX2) / 2;
                dstX1 = mid; dstX2 = mid;
            }

            int dstY1 = bounds.top + srcY1;
            int dstY2 = bounds.bottom - (h - srcY2);
            if (dstY2 < dstY1) {
                int mid = (dstY1 + dstY2) / 2;
                dstY1 = mid; dstY2 = mid;
            }

            int[] sX = {0, srcX1, srcX2, w};
            int[] sY = {0, srcY1, srcY2, h};
            int[] dX = {bounds.left, dstX1, dstX2, bounds.right};
            int[] dY = {bounds.top, dstY1, dstY2, bounds.bottom};

            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (sX[i] == sX[i+1] || sY[j] == sY[j+1]) continue;
                    src.set(sX[i], sY[j], sX[i+1], sY[j+1]);
                    dst.set(dX[i], dY[j], dX[i+1], dY[j+1]);
                    canvas.drawBitmap(bitmap, src, dst, paint);
                }
            }
        }

        @Override
        public void setAlpha(int alpha) { paint.setAlpha(alpha); }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }

        @Override
        public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
