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
import com.google.android.material.tabs.TabLayout;
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

    // Category colors
    private static final int COLOR_EVENT = Color.parseColor("#FF9800");
    private static final int COLOR_CSS = Color.parseColor("#2196F3");
    private static final int COLOR_HTML = Color.parseColor("#4CAF50");
    private static final int COLOR_LOGIC = Color.parseColor("#E91E63");
    private static final int COLOR_VARIABLE = Color.parseColor("#00BCD4");
    private static final int COLOR_ANIMATION = Color.parseColor("#9C27B0");

    private LogicBlockManager logicBlockManager;
    private String projectId;
    private String pageName = "index";

    // Views
    private MaterialToolbar toolbar;
    private DrawerLayout drawerLayout;
    private LinearLayout palettePanel;
    private TabLayout tabCategories;
    private LinearLayout blockPaletteContainer;
    private LinearLayout blockWorkspace;
    private FloatingActionButton fabBlockPalette;
    private Button btnBlockDelete, btnBlockDuplicate;
    private LinearLayout dropSaveCollection, dropDeleteCollection, dropDuplicateCollection;
    private LinearLayout collectionList;
    private TextView tvBlockCount;

    private String currentCategory = CAT_EVENT;
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
        setupCategoryTabs();
        setupQuickActionButtons();
        setupFab();
        setupCollectionDrawer();
        setupWorkspaceDragDrop();

        loadBlockDefinitions();
        showCategory(CAT_EVENT);
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
        tabCategories = findViewById(R.id.tabBlockCategories);
        blockPaletteContainer = findViewById(R.id.blockPaletteContainer);
        blockWorkspace = findViewById(R.id.blockWorkspace);
        fabBlockPalette = findViewById(R.id.fabBlockPalette);
        btnBlockDelete = findViewById(R.id.btnBlockDelete);
        btnBlockDuplicate = findViewById(R.id.btnBlockDuplicate);
        dropSaveCollection = findViewById(R.id.dropSaveCollection);
        dropDeleteCollection = findViewById(R.id.dropDeleteCollection);
        dropDuplicateCollection = findViewById(R.id.dropDuplicateCollection);
        collectionList = findViewById(R.id.collectionList);
        tvBlockCount = findViewById(R.id.tvBlockCount);
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

    private void setupCategoryTabs() {
        tabCategories.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0: showCategory(CAT_EVENT); break;
                    case 1: showCategory(CAT_CSS); break;
                    case 2: showCategory(CAT_LOGIC); break;
                    case 3: showCategory(CAT_VARIABLE); break;
                    case 4: showCategory(CAT_ANIMATION); break;
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupFab() {
        if (fabBlockPalette == null || palettePanel == null) return;
        fabBlockPalette.setOnClickListener(v -> {
            boolean visible = palettePanel.getVisibility() == View.VISIBLE;
            palettePanel.setVisibility(visible ? View.GONE : View.VISIBLE);
        });
    }

    private void setupQuickActionButtons() {
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

    private void setupCollectionDrawer() {
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
                case DragEvent.ACTION_DRAG_ENTERED:
                    setWorkspaceHighlight(true, event.getLocalState());
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    setWorkspaceHighlight(false, null);
                    return true;
                case DragEvent.ACTION_DROP:
                    setWorkspaceHighlight(false, null);
                    Object localState = event.getLocalState();
                    if (localState instanceof BlockDef) {
                        addBlockFromDef((BlockDef) localState);
                    } else if (localState instanceof Integer) {
                        int fromIndex = (Integer) localState;
                        float dropY = event.getY();
                        reorderBlock(fromIndex, dropY);
                    }
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    setWorkspaceHighlight(false, null);
                    return true;
            }
            return false;
        });
    }

    private void reorderBlock(int fromIndex, float y) {
        saveUndoState();
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (fromIndex < 0 || fromIndex >= blocks.size()) return;

        LogicBlockManager.LogicBlock moving = blocks.remove(fromIndex);

        // Find new index based on Y coordinate
        int newIdx = 0;
        for (int i = 0; i < blockWorkspace.getChildCount(); i++) {
            View child = blockWorkspace.getChildAt(i);
            if (y > child.getTop() + child.getHeight() / 2) {
                Object tag = child.getTag();
                if (tag instanceof Integer) {
                    newIdx = (Integer) tag + 1;
                } else {
                    // It's a header or a cap, we can still use it for positioning
                    // but it doesn't give us a direct block index.
                }
            }
        }

        // The simple algorithm above handles basic repositioning.
        // If it was dropped above the first block's header, newIdx remains 0.

        if (newIdx > blocks.size()) newIdx = blocks.size();
        blocks.add(newIdx, moving);

        refreshWorkspace();
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
        // Sketchware-style compact palette pill: small colored chip with the
        // block label, drag/tap to add to workspace.
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(12), dp(8), dp(12), dp(10));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), dp(3), dp(4), dp(3));
        block.setLayoutParams(params);

        // Puzzle pill background based on shape
        GradientDrawable bg = new GradientDrawable();
        if ("C".equals(def.shape)) {
            bg.setCornerRadii(new float[]{dp(8), dp(8), dp(8), dp(8), dp(8), dp(8), 0, 0});
        } else if ("E".equals(def.shape)) {
            bg.setCornerRadii(new float[]{dp(2), dp(2), dp(8), dp(8), dp(8), dp(8), dp(2), dp(2)});
        } else {
            // "rect" or default
            bg.setCornerRadius(dp(4));
        }

        bg.setColor(baseColor);
        bg.setStroke(dp(1), darken(baseColor));
        block.setBackground(bg);
        block.setElevation(2);

        TextView nameText = new TextView(this);
        nameText.setText(def.label);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(12);
        nameText.setTypeface(null, Typeface.BOLD);
        block.addView(nameText);

        TextView descText = new TextView(this);
        descText.setText(def.description);
        descText.setTextColor(Color.parseColor("#E1F5FE"));
        descText.setTextSize(10);
        descText.setPadding(0, dp(2), 0, 0);
        block.addView(descText);

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

    /** Most recent event selected from the palette — new actions attach here. */
    private String activeEventKey = "load";

    /**
     * Create a block from a palette definition with sensible defaults — no
     * dialogs. Users tweak the block in the workspace by tapping its chips.
     * If an "onLoad" event header already exists, CSS/animation blocks
     * attach to it as a child instead of asking.
     */
    private void addBlockFromDef(BlockDef def) {
        if (CAT_EVENT.equals(def.category)) {
            // Events are implicit headers — switch the active event so the
            // next dropped action attaches to it. No standalone block.
            activeEventKey = mapEventKey(def.id);
            Toast.makeText(this,
                "Active event: " + LogicBlockManager.getEventDisplayName(activeEventKey),
                Toast.LENGTH_SHORT).show();
            return;
        }

        saveUndoState();
        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
        block.targetWidget = "";
        block.targetMode = "id";
        block.action = mapActionKey(def.id);
        block.params = defaultParamsFor(def);

        if (CAT_LOGIC.equals(def.category) || CAT_VARIABLE.equals(def.category)) {
            block.targetMode = CAT_LOGIC.equals(def.category) ? "logic" : "variable";
            block.event = "immediate";
            block.action = def.id;
        } else if (CAT_ANIMATION.equals(def.category)) {
            block.event = hasEvent("load") ? "load" : activeEventKey;
            block.action = def.id;
        } else {
            // CSS — if there's already an onLoad header in the workspace
            // attach to it as parent. Otherwise use the most-recently-set
            // event (defaults to "load" so CSS just becomes a load rule).
            block.event = hasEvent("load") ? "load" : activeEventKey;
        }

        logicBlockManager.addBlock(block);
        refreshWorkspace();

        if (CAT_ANIMATION.equals(def.category)) {
            int idx = logicBlockManager.getBlocks().size() - 1;
            showAnimationCustomizeDialog(idx);
        }
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
            case "setColor": return "color:#000000";
            case "setBackground": return "background:#FFFFFF";
            case "setWidth": return "width:100px";
            case "setHeight": return "height:100px";
            case "setMargin": return "margin:0px";
            case "setPadding": return "padding:8px";
            case "setBorder": return "border:1px solid #000000";
            case "setRadius": return "borderRadius:4px";
            case "setOpacity": return "opacity:1";
            case "setFontSize": return "fontSize:14px";
            case "addClass": case "removeClass": case "toggleClass": return "myClass";
            case "animateFadeIn": return "fadeIn|400ms|ease";
            case "animateFadeOut": return "fadeOut|400ms|ease";
            case "animateSlideIn": return "slideIn|400ms|ease";
            case "animateSlideOut": return "slideOut|400ms|ease";
            case "animateBounce": return "bounce|600ms|ease-out";
            case "animatePulse": return "pulse|800ms|ease-in-out";
            case "animateRotate": return "rotate|600ms|linear";
            case "animateShake": return "shake|400ms|ease-in-out";
            case "transitionAll": return "all|300ms|ease";
            case "transitionColor": return "color|300ms|ease";
            case "transitionSize": return "width,height|300ms|ease";
            case "transitionTransform": return "transform|300ms|ease";
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
            tvBlockCount.setText(blocks.size() + " block" + (blocks.size() == 1 ? "" : "s"));
        }
        if (blocks.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Drag a CSS or Logic block here.\nEvents are C-shapes that wrap actions.");
            empty.setTextColor(Color.parseColor("#7A8B9C"));
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(32, 80, 32, 80);
            blockWorkspace.addView(empty);
            return;
        }

        // Flat rendering with visual "C" shape markers.
        // This makes reordering trivial as each View corresponds to exactly one index.
        String currentEvent = null;
        for (int i = 0; i < blocks.size(); i++) {
            LogicBlockManager.LogicBlock block = blocks.get(i);
            String ev = block.event != null ? block.event : "immediate";

            if (!ev.equals(currentEvent)) {
                // Event Header
                View header = createEventHeaderBlock(ev);
                blockWorkspace.addView(header);
                currentEvent = ev;
            }

            // Indented block with left rail
            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.HORIZONTAL);
            wrapper.setPadding(dp(12), 0, 0, 0);

            View rail = new View(this);
            rail.setBackgroundColor(COLOR_EVENT);
            wrapper.addView(rail, new LinearLayout.LayoutParams(dp(6), ViewGroup.LayoutParams.MATCH_PARENT));

            View blockView = createWorkspacePuzzleBlock(block, i);
            blockView.setTag(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            lp.setMargins(dp(8), 0, 0, 0);
            wrapper.addView(blockView, lp);

            blockWorkspace.addView(wrapper);

            // Close C-shape if next block has a different event or it's the end
            boolean isLastOfEvent = (i == blocks.size() - 1) || !blocks.get(i + 1).event.equals(ev);
            if (isLastOfEvent) {
                blockWorkspace.addView(createEventBottomCap(ev));
            }
        }
    }

    /**
     * Vertical container with an orange left rail — visually completes the
     * "C" shape between the event header and bottom cap. Children are the
     * rectangular CSS / Logic action blocks.
     */
    private LinearLayout createEventSlot(String eventKey) {
        // Wrapper for the "C" shape vertical bar and the inner content
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);

        // Left rail (the vertical bar of the C)
        View rail = new View(this);
        GradientDrawable railBg = new GradientDrawable();
        railBg.setColor(COLOR_EVENT);
        rail.setBackground(railBg);
        LinearLayout.LayoutParams railLp = new LinearLayout.LayoutParams(dp(6),
            ViewGroup.LayoutParams.MATCH_PARENT);
        railLp.setMargins(dp(4), 0, 0, 0);
        rail.setLayoutParams(railLp);
        wrapper.addView(rail);

        // Inner stack where action blocks are placed
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(8), dp(2), dp(4), dp(2));
        LinearLayout.LayoutParams innerLp = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inner.setLayoutParams(innerLp);
        wrapper.addView(inner);

        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapLp.setMargins(dp(4), 0, dp(4), 0);
        wrapper.setLayoutParams(wrapLp);

        // Drag listener for dropping blocks into this event
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
        // No more dialog cascade — drop the action straight in with default
        // params under the chosen event. User edits chips inline in the
        // workspace.
        saveUndoState();
        LogicBlockManager.LogicBlock block = new LogicBlockManager.LogicBlock();
        block.targetWidget = "";
        block.targetMode = CAT_LOGIC.equals(actionDef.category) ? "logic"
            : CAT_VARIABLE.equals(actionDef.category) ? "variable" : "id";
        block.event = mapEventKey(eventKey);
        block.action = mapActionKey(actionDef.id);
        block.params = defaultParamsFor(actionDef);
        logicBlockManager.addBlock(block);
        refreshWorkspace();
    }

    private void moveBlockToEvent(int fromIndex, String eventKey) {
        saveUndoState();
        List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
        if (fromIndex >= 0 && fromIndex < blocks.size()) {
            LogicBlockManager.LogicBlock block = blocks.get(fromIndex);
            block.event = mapEventKey(eventKey);

            // Move it to be adjacent to other blocks of this event if possible
            int targetPos = -1;
            for (int i = 0; i < blocks.size(); i++) {
                if (block.event.equals(blocks.get(i).event)) {
                    targetPos = i;
                }
            }

            if (targetPos != -1 && targetPos != fromIndex) {
                blocks.remove(fromIndex);
                blocks.add(targetPos, block);
            }

            refreshWorkspace();
        }
    }

    /**
     * Bottom cap of the C-shape. Mirrors the orange header so the wrapping
     * around the slot looks closed.
     */
    private View createEventBottomCap(String eventKey) {
        View cap = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadii(new float[]{0, 0, dp(10), dp(10), dp(10), dp(10), 0, 0});
        bg.setColor(COLOR_EVENT);
        cap.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(60), dp(8));
        lp.setMargins(dp(4), 0, 0, dp(8));
        cap.setLayoutParams(lp);
        return cap;
    }

    /**
     * Renders a Sketchware-style orange header block, eg.
     * "On Click" / "On Page Load" / "Immediate".
     */
    private View createEventHeaderBlock(String eventKey) {
        String label = LogicBlockManager.getEventDisplayName(eventKey);

        TextView header = new TextView(this);
        header.setText(label);
        header.setTextColor(Color.WHITE);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextSize(13);
        header.setPadding(dp(14), dp(8), dp(14), dp(10));

        GradientDrawable bg = new GradientDrawable();
        // Tab on top-left, slight curve elsewhere — matches Sketchware "event" cap.
        bg.setCornerRadii(new float[]{dp(10), dp(10), dp(10), dp(10), dp(10), dp(10), 0, 0});
        bg.setColor(COLOR_EVENT);
        header.setBackground(bg);
        header.setElevation(2);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(4), dp(8), dp(4), 0);
        header.setLayoutParams(params);
        return header;
    }

    /**
     * Render a single block as a horizontal Sketchware-style "puzzle" row:
     *
     *   [target chip]  verb  [param chip]  verb  [param chip] ...
     *
     * Each block stacks immediately below the previous one with no margin
     * so the chain looks connected.
     */
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

        GradientDrawable bg = new GradientDrawable();
        if ("E".equals(shape)) {
            // Logic = E-shape: rounded but flatter on the left so it visually
            // pairs with the C-shape event around it.
            bg.setCornerRadii(new float[]{
                dp(2), dp(2),     // top-left
                dp(8), dp(8),     // top-right
                dp(8), dp(8),     // bottom-right
                dp(2), dp(2)      // bottom-left
            });
        } else {
            // "rect" or default
            bg.setCornerRadius(dp(4));
        }
        bg.setColor(baseColor);
        bg.setStroke(dp(1), strokeColor);
        row.setBackground(bg);
        row.setElevation(2);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(2), 0, dp(2));
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
                TextView chip = createValueChip(trimmed);
                chip.setOnClickListener(v -> editParamPart(index, paramIdx));
                row.addView(chip);
            }

            // If this is an if-else logic block, render a second slot ("else")
            // below it to give the block its E-shape silhouette. This is
            // visual only - the params already encode the else branch.
            if ("ifElseBlock".equals(block.action)) {
                LinearLayout column = new LinearLayout(this);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                column.addView(row);

                TextView elseTab = new TextView(this);
                elseTab.setText("else");
                elseTab.setTextColor(Color.WHITE);
                elseTab.setTextSize(11);
                elseTab.setTypeface(null, Typeface.BOLD);
                elseTab.setPadding(dp(12), dp(4), dp(12), dp(4));
                GradientDrawable elseBg = new GradientDrawable();
                elseBg.setCornerRadii(new float[]{0, 0, dp(6), dp(6), dp(6), dp(6), 0, 0});
                elseBg.setColor(darken(COLOR_LOGIC));
                elseTab.setBackground(elseBg);
                LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tabLp.setMargins(dp(20), dp(2), 0, dp(2));
                elseTab.setLayoutParams(tabLp);
                column.addView(elseTab);

                return column;
            }
        } else {
            // Element-targeted action row:  [target] verb [value] [extra value]
            String modePrefix = "id".equals(block.targetMode) ? "#"
                : "class".equals(block.targetMode) ? "." : "";
            String targetText = modePrefix + block.targetWidget;
            // Target chip is EDITABLE - tap opens id/class/tag picker.
            TextView targetChip = createTargetChip(
                targetText.isEmpty() ? "page" : targetText,
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
                row.addView(vChip);
            }
        }

        // Long press = Start Drag for reordering
        row.setOnLongClickListener(v -> {
            ClipData.Item item = new ClipData.Item("reorder|" + index);
            ClipData dragData = new ClipData("reorder", new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            // Pass the index as local state
            v.startDragAndDrop(dragData, shadow, index, 0);
            return true;
        });
        // Short tap = edit value
        row.setOnClickListener(v -> showEditBlockDialog(index));

        return row;
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
        chip.setTextColor(Color.parseColor("#0D47A1"));
        chip.setTextSize(12);
        chip.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(Color.WHITE);
        bg.setStroke(dp(1), Color.parseColor("#33000000"));
        chip.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dp(6), 0);
        chip.setLayoutParams(p);
        return chip;
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
        String json = logicBlockManager.toJson();

        StringBuilder code = new StringBuilder();
        if (baseCss != null && !baseCss.trim().isEmpty()) {
            code.append("<style>\n").append(baseCss).append("</style>\n\n");
        }
        if (pseudoCss != null && !pseudoCss.trim().isEmpty()) {
            code.append("<style>\n").append(pseudoCss).append("</style>\n\n");
        }
        if (js != null && !js.trim().isEmpty()) {
            code.append("<script>\n").append(js).append("</script>\n");
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

        StringBuilder combined = new StringBuilder();
        if (baseCss != null && !baseCss.trim().isEmpty()) {
            combined.append("/* CSS — applied at page load */\n<style>\n");
            combined.append(baseCss);
            combined.append("</style>\n\n");
        }
        if (pseudoCss != null && !pseudoCss.trim().isEmpty()) {
            combined.append("/* CSS pseudo-class rules */\n<style>\n");
            combined.append(pseudoCss);
            combined.append("</style>\n\n");
        }
        if (js != null && !js.trim().isEmpty()) {
            combined.append("/* JavaScript — for runtime events */\n<script>\n");
            combined.append(js);
            combined.append("</script>\n");
        }
        if (combined.length() == 0) combined.append("// No logic blocks yet");

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

    private int getCategoryColor(String category) {
        switch (category) {
            case CAT_EVENT: return COLOR_EVENT;
            case CAT_CSS: return COLOR_CSS;
            case CAT_HTML: return COLOR_HTML;
            case CAT_LOGIC: return COLOR_LOGIC;
            case CAT_VARIABLE: return COLOR_VARIABLE;
            case CAT_ANIMATION: return COLOR_ANIMATION;
            default: return COLOR_EVENT;
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
            case "setDisplay": case "setColor": case "setBackground":
            case "setWidth": case "setHeight": case "setMargin":
            case "setPadding": case "setBorder": case "setRadius":
            case "setOpacity": case "setFontSize":
                return "changeStyle";
            case "addClass": return "addClass";
            case "removeClass": return "removeClass";
            case "toggleClass": return "toggleClass";
            case "setText": return "setText";
            case "setHTML": return "setHTML";
            case "showElement": case "hideElement": case "toggleElement": return "showHide";
            case "navigate": return "navigate";
            case "goToPage": return "goToPage";
            case "alert": return "alert";
            case "scrollTo": return "scrollTo";
            case "focusInput": return "focusInput";
            case "setAttribute": return "setAttribute";
            case "setHref": return "setHref";
            case "removeElement": return "removeElement";
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
}
