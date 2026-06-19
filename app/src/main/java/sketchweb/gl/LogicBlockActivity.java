package sketchweb.gl;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vertical, JSON-driven block editor activity.
 *
 * <p>The previous canvas-based editor is gone: blocks now stack top-to-bottom
 * in a {@link WorkspaceView}, drag/drop is delegated to
 * {@link BlockDragDropManager}, and inline values are edited via
 * {@link BlockChipFactory}. This file keeps the activity surface compatible
 * with {@code MainActivity} (intent extras, .logic file format) so launches
 * from elsewhere in the app still work unchanged.
 */
public class LogicBlockActivity extends AppCompatActivity implements BlockDragDropManager.Host {

    private static final String CAT_CSS = "css";
    private static final String CAT_VALUE = "value";
    private static final String CAT_LOGIC = "logic";
    private static final String CAT_ANIMATION = "animation";
    private static final String CAT_META = "meta";
    private static final String CAT_ASD = "asd";

    private LogicBlockManager logicBlockManager;
    private BlockParamTypeManager paramTypeManager;
    private BlockChipFactory chipFactory;
    private BlockDragDropManager dragDropManager;
    private ManageBlocksWidgets customBlockManager;

    private String projectId;
    private String pageName = "index";
    private int currentMode = 0;

    private MaterialToolbar toolbar;
    private DrawerLayout drawerLayout;
    private LinearLayout palettePanel;
    private LinearLayout categoryListContainer;
    private LinearLayout blockPaletteContainer;
    private WorkspaceView workspaceView;
    private FloatingActionButton fabBlockPalette;
    private View btnBlockDelete;
    private LinearLayout btnBlockDuplicate;
    private LinearLayout btnSaveToCollection;
    private LinearLayout collectionList;
    private TextView tvBlockCount;

    private String currentCategory = CAT_CSS;
    private final List<BlockDef> allBlockDefs = new ArrayList<>();

    private final List<String> undoStack = new ArrayList<>();
    private final List<String> redoStack = new ArrayList<>();
    private static final int MAX_UNDO = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_logic_block);

        paramTypeManager = new BlockParamTypeManager();
        customBlockManager = new ManageBlocksWidgets(this);

        projectId = getIntent().getStringExtra("project_id");
        if (projectId == null) projectId = "";
        pageName = getIntent().getStringExtra("page_name");
        if (pageName == null || pageName.isEmpty()) pageName = "index";

        logicBlockManager = new LogicBlockManager(this);
        loadLogicFromDisk();

        loadBlockDefinitions();
        chipFactory = new BlockChipFactory(this, paramTypeManager, customBlockManager);
        dragDropManager = new BlockDragDropManager(this);

        initViews();
        setupToolbar();
        setupFab();
        setupCollectionDrawer();

        workspaceView.configure(logicBlockManager, allBlockDefs, chipFactory, dragDropManager);
        workspaceView.setOnBlockInteractionListener(() -> { saveUndoState(); refreshHud(); });
        
        dragDropManager.attachDeleteBar(btnBlockDelete);
        if (btnBlockDuplicate != null) dragDropManager.attachDuplicateBar(btnBlockDuplicate);
        if (btnSaveToCollection != null) dragDropManager.attachSaveBar(btnSaveToCollection);

        setupCategoryButtons();
        showCategory(CAT_CSS);
        seedDefaultBlockIfEmpty();
        workspaceView.rebuild();
        refreshCollectionList();
        refreshHud();
        saveUndoState();

        final int toolbarInitialTop = toolbar != null ? toolbar.getPaddingTop() : 0;
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (toolbar != null) {
                toolbar.setPadding(toolbar.getPaddingLeft(), toolbarInitialTop + systemBars.top,
                    toolbar.getPaddingRight(), toolbar.getPaddingBottom());
            }
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void loadLogicFromDisk() {
        try {
            File dir = new File(getFilesDir(), "projects");
            File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
            if (logicFile.exists()) {
                String json = FileUtil.readFile(logicFile.getAbsolutePath());
                if (json != null && !json.isEmpty()) logicBlockManager.fromJson(json);
            }
        } catch (Exception e) {
            Log.w("LogicBlockActivity", "Could not load logic blocks: " + e.getMessage());
        }
    }

    private void seedDefaultBlockIfEmpty() {
        if (!logicBlockManager.getBlocks().isEmpty()) return;
        BlockDef def = findDef("cssSelector");
        if (def == null) return;
        workspaceView.insertNewBlock(def, null, 0);
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbarLogic);
        drawerLayout = findViewById(R.id.drawerLogic);
        palettePanel = findViewById(R.id.palettePanel);
        categoryListContainer = findViewById(R.id.categoryListContainer);
        blockPaletteContainer = findViewById(R.id.blockPaletteContainer);
        workspaceView = findViewById(R.id.workspaceView);
        fabBlockPalette = findViewById(R.id.fabBlockPalette);
        btnBlockDelete = findViewById(R.id.btnBlockDelete);
        btnBlockDuplicate = (LinearLayout) findViewById(R.id.btnBlockDuplicate);
        btnSaveToCollection = (LinearLayout) findViewById(R.id.btnSaveToCollection);
        collectionList = findViewById(R.id.collectionList);
        tvBlockCount = findViewById(R.id.tvBlockCount);

        TabLayout tabLayoutMode = findViewById(R.id.tabLayoutMode);
        if (tabLayoutMode != null) {
            tabLayoutMode.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override public void onTabSelected(TabLayout.Tab tab) {
                    currentMode = tab.getPosition();
                    setupCategoryButtons();
                    showCategory(currentMode == 0 ? CAT_CSS : CAT_ASD);
                }
                @Override public void onTabUnselected(TabLayout.Tab tab) {}
                @Override public void onTabReselected(TabLayout.Tab tab) {}
            });
        }
    }

    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> saveAndFinish());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_undo) { undo(); return true; }
            if (id == R.id.action_redo) { redo(); return true; }
            if (id == R.id.action_view_code) { showCodePreview(); return true; }
            if (id == R.id.action_collections) {
                if (drawerLayout != null) {
                    if (drawerLayout.isDrawerOpen(GravityCompat.END)) drawerLayout.closeDrawer(GravityCompat.END);
                    else drawerLayout.openDrawer(GravityCompat.END);
                }
                return true;
            }
            return false;
        });
    }

    private void setupCategoryButtons() {
        if (categoryListContainer == null) return;
        categoryListContainer.removeAllViews();
        Set<String> available = new LinkedHashSet<>();
        if (currentMode == 0) {
            available.add(CAT_CSS);
            available.add(CAT_ANIMATION);
            available.add(CAT_LOGIC);
            available.add(CAT_VALUE);
            available.add(CAT_META);
        } else {
            available.add(CAT_ASD);
        }
        for (String cat : available) {
            categoryListContainer.addView(createCategoryButton(cat, prettyName(cat)));
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

        int color = BlockCategoryPalette.colorIntForCategory(category);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(color);
        bg.setStroke(dp(2), category.equals(currentCategory) ? Color.WHITE : BlockCategoryPalette.darken(color));
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

    private String prettyName(String category) {
        switch (category) {
            case CAT_CSS: return "CSS";
            case CAT_VALUE: return "Value";
            case CAT_LOGIC: return "Logic";
            case CAT_ANIMATION: return "Anim";
            case CAT_META: return "Group";
            case CAT_ASD: return "ASD";
            default: return category;
        }
    }

    private void setupFab() {
        if (fabBlockPalette != null && palettePanel != null) {
            fabBlockPalette.setOnClickListener(v -> {
                boolean visible = palettePanel.getVisibility() == View.VISIBLE;
                palettePanel.setVisibility(visible ? View.GONE : View.VISIBLE);
            });
        }
    }



    private LogicBlockManager.LogicBlock cloneBlock(LogicBlockManager.LogicBlock orig) {
        LogicBlockManager.LogicBlock copy = new LogicBlockManager.LogicBlock();
        copy.targetWidget = orig.targetWidget;
        copy.targetMode = orig.targetMode;
        copy.event = orig.event;
        copy.action = orig.action;
        copy.category = orig.category;
        copy.params = orig.params;
        copy.shape = orig.shape;
        copy.spec = orig.spec;
        copy.x = orig.x;
        copy.y = orig.y;
        copy.nextBlockId = orig.nextBlockId;
        copy.parentBlockId = orig.parentBlockId;
        copy.subStackId = orig.subStackId;
        copy.id = orig.id; // Keep old ID temporarily for remapping
        if (orig.paramValues != null) copy.paramValues = new ArrayList<>(orig.paramValues);
        return copy;
    }

    // ------------------------------------------------------------------
    // Palette rendering
    // ------------------------------------------------------------------

    private void loadBlockDefinitions() {
        try {
            StringBuilder sb = new StringBuilder();
            java.io.InputStream is = getAssets().open("blocks.json");
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            List<BlockDef> parsed = new Gson().fromJson(sb.toString(),
                new TypeToken<List<BlockDef>>(){}.getType());
            allBlockDefs.clear();
            if (parsed != null) allBlockDefs.addAll(parsed);
        } catch (Exception e) {
            Log.w("LogicBlockActivity", "Failed to load blocks.json: " + e.getMessage());
        }
    }

    private void showCategory(String category) {
        currentCategory = category;
        blockPaletteContainer.removeAllViews();
        for (BlockDef def : allBlockDefs) {
            if (category.equals(def.category)) {
                blockPaletteContainer.addView(createPaletteEntry(def));
            }
        }
    }

    private View createPaletteEntry(BlockDef def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(3), dp(4), dp(3));
        row.setLayoutParams(lp);

        int color = Color.parseColor(def.resolvedColor());
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(8));
        gd.setStroke(dp(2), BlockCategoryPalette.darken(color));
        row.setBackground(gd);

        TextView name = new TextView(this);
        name.setText(def.label != null ? def.label : def.id);
        name.setTextColor(Color.WHITE);
        name.setTextSize(13);
        name.setTypeface(null, Typeface.BOLD);
        row.addView(name);

        // Tap inserts at the bottom of the workspace; long-press starts a drag.
        row.setOnClickListener(v -> {
            saveUndoState();
            int siblingIndex = workspaceView != null
                ? logicBlockManager.getBlocks().size() : 0;
            workspaceView.insertNewBlock(def, null, siblingIndex);
            refreshHud();
        });
        dragDropManager.attachPaletteSource(row, def);
        return row;
    }

    // ------------------------------------------------------------------
    // BlockDragDropManager.Host implementation
    // ------------------------------------------------------------------

    @Override public WorkspaceView getWorkspace() { return workspaceView; }
    @Override public BlockDef findDef(String id) {
        if (id == null) return null;
        for (BlockDef d : allBlockDefs) if (id.equals(d.id)) return d;
        return null;
    }
    @Override public BlockChipFactory getChipFactory() { return chipFactory; }
    @Override public BlockView.OnBlockChanged getBlockChangedListener() {
        return block -> { saveUndoState(); refreshHud(); };
    }
    @Override public void onWorkspaceMutated() {
        saveUndoState();
        refreshHud();
    }

    @Override
    public void saveBlockToCollection(String blockId) {
        LogicBlockManager.LogicBlock root = logicBlockManager.findBlockById(blockId);
        if (root == null) return;
        List<LogicBlockManager.LogicBlock> chain = collectChain(root);
        List<LogicBlockManager.LogicBlock> clones = new ArrayList<>();
        for (LogicBlockManager.LogicBlock b : chain) clones.add(cloneBlock(b));
        // Remap IDs in the cloned chain
        remapChainIds(clones);
        showSaveCollectionDialog(clones);
    }

    @Override
    public void duplicateBlock(String blockId) {
        LogicBlockManager.LogicBlock root = logicBlockManager.findBlockById(blockId);
        if (root == null) return;
        List<LogicBlockManager.LogicBlock> chain = collectChain(root);
        List<LogicBlockManager.LogicBlock> clones = new ArrayList<>();
        for (LogicBlockManager.LogicBlock b : chain) clones.add(cloneBlock(b));
        remapChainIds(clones);
        
        // Insert clones into manager
        for (LogicBlockManager.LogicBlock cb : clones) {
            logicBlockManager.addBlock(cb);
        }
        
        // Snap the new root to the bottom of the workspace for visibility
        LogicBlockManager.LogicBlock newRoot = clones.get(0);
        newRoot.parentBlockId = null;
        newRoot.y += 100; // Offset slightly
        
        workspaceView.rebuild();
        onWorkspaceMutated();
    }

    private List<LogicBlockManager.LogicBlock> collectChain(LogicBlockManager.LogicBlock root) {
        List<LogicBlockManager.LogicBlock> out = new ArrayList<>();
        out.add(root);
        // Standard subtree collection: find all blocks that have a parent in our 'out' list
        for (int i = 0; i < out.size(); i++) {
            String pid = out.get(i).id;
            if (pid == null) continue;
            for (LogicBlockManager.LogicBlock b : logicBlockManager.getBlocks()) {
                if (pid.equals(b.parentBlockId) && !out.contains(b)) {
                    out.add(b);
                }
            }
        }
        return out;
    }

    private void remapChainIds(List<LogicBlockManager.LogicBlock> chain) {
        Map<String, String> idMap = new HashMap<>();
        long timestamp = System.currentTimeMillis();
        int counter = 0;
        
        // First pass: generate new unique IDs and store mapping
        for (LogicBlockManager.LogicBlock b : chain) {
            String oldId = b.id;
            String newId = "blk_" + timestamp + "_" + (counter++) + "_" + (int)(Math.random() * 1000);
            idMap.put(oldId, newId);
            b.id = newId;
        }
        
        // Second pass: update all structural pointers using the map
        for (LogicBlockManager.LogicBlock b : chain) {
            if (b.nextBlockId != null) {
                String remapped = idMap.get(b.nextBlockId);
                if (remapped != null) b.nextBlockId = remapped;
                else b.nextBlockId = null; // Break link to blocks outside the chain
            }
            if (b.parentBlockId != null) {
                String remapped = idMap.get(b.parentBlockId);
                if (remapped != null) b.parentBlockId = remapped;
                else b.parentBlockId = null; // Dragged root becomes independent
            }
            if (b.subStackId != null) {
                String remapped = idMap.get(b.subStackId);
                if (remapped != null) b.subStackId = remapped;
                else b.subStackId = null; // Should not happen if collectChain is complete
            }
        }
    }

    // ------------------------------------------------------------------
    // Collection drawer
    // ------------------------------------------------------------------

    private File getCollectionDir() {
        File dir = new File(android.os.Environment.getExternalStorageDirectory(), ".dragweb/collections");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }



    private void setupCollectionDrawer() {
        // No manual listeners needed here anymore. 
        // dragDropManager.attachSaveBar and dragDropManager.attachDuplicateBar 
        // handle everything in a unified way.
    }

    private void showSaveCollectionDialog(List<LogicBlockManager.LogicBlock> chain) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), 0);
        TextInputLayout til = createTil("Collection name");
        TextInputEditText input = (TextInputEditText) til.getEditText();
        if (input != null) input.setText("collection_" + System.currentTimeMillis());
        layout.addView(til);
        new MaterialAlertDialogBuilder(this)
            .setTitle("Save to Collection")
            .setView(layout)
            .setPositiveButton("Save", (d, w) -> {
                String name = getText(til);
                try {
                    File dir = getCollectionDir();
                    File file = new File(dir, name.replaceAll("[^a-zA-Z0-9_-]", "_") + ".json");
                    FileUtil.writeFile(file.getAbsolutePath(), new Gson().toJson(chain));
                    refreshCollectionList();
                } catch (Exception ignored) {}
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void refreshCollectionList() {
        if (collectionList == null) return;
        collectionList.removeAllViews();
        File dir = getCollectionDir();
        File[] files = dir.listFiles((f, n) -> n.endsWith(".json"));
        if (files == null || files.length == 0) return;
        for (File f : files) collectionList.addView(createCollectionRow(f));
    }

    private View createCollectionRow(File file) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        
        TextView name = new TextView(this);
        name.setText(file.getName().replace(".json", ""));
        name.setTextColor(Color.parseColor("#0D47A1"));
        name.setTextSize(14);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        name.setOnClickListener(v -> loadCollection(file));
        row.addView(name);

        ImageView deleteBtn = new ImageView(this);
        deleteBtn.setImageResource(R.drawable.icon_delete_round);
        int iconSize = dp(24);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(iconSize, iconSize);
        deleteBtn.setLayoutParams(dlp);
        deleteBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
        deleteBtn.setImageTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828")));
        deleteBtn.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Collection")
                .setMessage("Are you sure you want to delete '" + name.getText() + "'?")
                .setPositiveButton("Delete", (d, w) -> {
                    if (file.delete()) refreshCollectionList();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        row.addView(deleteBtn);
        
        return row;
    }

    private void loadCollection(File file) {
        try {
            String json = FileUtil.readFile(file.getAbsolutePath());
            java.lang.reflect.Type type = new TypeToken<List<LogicBlockManager.LogicBlock>>(){}.getType();
            List<LogicBlockManager.LogicBlock> chain = new Gson().fromJson(json, type);
            if (chain == null || chain.isEmpty()) return;

            saveUndoState();
            // Remap IDs so they are unique in the current session
            remapChainIds(chain);
            
            // Position the loaded chain
            LogicBlockManager.LogicBlock root = chain.get(0);
            root.parentBlockId = null;
            root.y += 100;

            for (LogicBlockManager.LogicBlock b : chain) {
                logicBlockManager.addBlock(b);
            }
            workspaceView.rebuild();
            refreshHud();
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------
    // Misc
    // ------------------------------------------------------------------

    private void refreshHud() {
        if (tvBlockCount != null) {
            tvBlockCount.setText(logicBlockManager.getBlocks().size() + " blocks");
        }
    }

    private TextInputLayout createTil(String hint) {
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(hint);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxCornerRadii(dp(8), dp(8), dp(8), dp(8));
        til.addView(new TextInputEditText(this));
        return til;
    }

    private String getText(TextInputLayout til) {
        return til.getEditText() != null ? til.getEditText().getText().toString().trim() : "";
    }

    private int dp(int px) {
        return (int) (px * getResources().getDisplayMetrics().density);
    }

    private void updateUndoRedoMenuState() {
        if (toolbar == null) return;
        android.view.Menu menu = toolbar.getMenu();
        if (menu == null) return;
        android.view.MenuItem undoItem = menu.findItem(R.id.action_undo);
        android.view.MenuItem redoItem = menu.findItem(R.id.action_redo);

        boolean canUndo = undoStack.size() > 1;
        boolean canRedo = !redoStack.isEmpty();

        int colorOnSurface = 0xFF000000;
        android.util.TypedValue typedValue = new android.util.TypedValue();
        if (getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
            colorOnSurface = typedValue.data;
        }

        int activeColor = colorOnSurface;
        int inactiveColor = (colorOnSurface & 0x00FFFFFF) | (76 << 24); // 30% alpha

        if (undoItem != null) {
            undoItem.setEnabled(canUndo);
            android.graphics.drawable.Drawable icon = undoItem.getIcon();
            if (icon != null) {
                icon = icon.mutate();
                icon.setTint(canUndo ? activeColor : inactiveColor);
                undoItem.setIcon(icon);
            }
        }

        if (redoItem != null) {
            redoItem.setEnabled(canRedo);
            android.graphics.drawable.Drawable icon = redoItem.getIcon();
            if (icon != null) {
                icon = icon.mutate();
                icon.setTint(canRedo ? activeColor : inactiveColor);
                redoItem.setIcon(icon);
            }
        }
    }

    private void saveUndoState() {
        if (undoStack.size() >= MAX_UNDO) undoStack.remove(0);
        undoStack.add(logicBlockManager.toJson());
        redoStack.clear();
        updateUndoRedoMenuState();
    }

    private void undo() {
        if (undoStack.size() <= 1) return;
        redoStack.add(undoStack.remove(undoStack.size() - 1));
        logicBlockManager.fromJson(undoStack.get(undoStack.size() - 1));
        workspaceView.rebuild();
        refreshHud();
        updateUndoRedoMenuState();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        String state = redoStack.remove(redoStack.size() - 1);
        undoStack.add(state);
        logicBlockManager.fromJson(state);
        workspaceView.rebuild();
        refreshHud();
        updateUndoRedoMenuState();
    }

    private void saveAndFinish() {
        try {
            File dir = new File(getFilesDir(), "projects");
            if (!dir.exists()) dir.mkdirs();
            File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
            FileUtil.writeFile(logicFile.getAbsolutePath(), logicBlockManager.toJson());
        } catch (Exception ignored) {}
        setResult(RESULT_OK);
        finish();
    }

    private void showCodePreview() {
        StringBuilder sb = new StringBuilder();
        String css = logicBlockManager.generateBaseCssRules();
        if (css != null && !css.isEmpty()) {
            sb.append("/* CSS */\n").append(css).append("\n");
        }
        String pseudo = logicBlockManager.generateCssPseudoRules();
        if (pseudo != null && !pseudo.isEmpty()) {
            sb.append("/* Pseudo */\n").append(pseudo).append("\n");
        }
        String js = logicBlockManager.generateJavaScript();
        if (js != null && !js.isEmpty()) sb.append(js);
        if (sb.length() == 0) sb.append("// No emittable blocks yet");
        new MaterialAlertDialogBuilder(this)
            .setTitle("Generated Code")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show();
    }

    @Override
    public void onBackPressed() {
        saveAndFinish();
    }
}
