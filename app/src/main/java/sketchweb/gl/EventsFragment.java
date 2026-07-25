package sketchweb.gl;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import androidx.viewpager2.widget.ViewPager2;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import com.google.android.material.listitem.ListItemCardView;
import com.google.android.material.listitem.ListItemViewHolder;
import androidx.fragment.app.Fragment;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigationrail.NavigationRailView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class EventsFragment extends Fragment {

    private static final String ARG_PROJECT_ID = "project_id";
    private static final String ARG_CURRENT_PAGE = "current_page";

    private String projectId = "";
    private String currentPage = "";

    private PageManager pageManager;
    private ActivityResultLauncher<Intent> logicBlockLauncher;

    private NavigationRailView navigationRail;
    private TextView tvSectionTitle;
    private RecyclerView rvEvents;
    private Button btnResetLogic;
    private Button btnImportCss;
    private ImageView clear;
    private CardView suggestCard;

    private int currentTab = 0; // 0: CSS, 1: JS, 2: HTML, 3: Functions (MoreBlocks)

    public static class FunctionItem {
        public String name;
        public String spec;
        public String linkedFile;
    }

    public static class EventListItem {
        public boolean isFunction;
        public String title;
        public String subtitle;
        public String tag;
        public String targetPath;
        public String funcName;
        public int blockCount;
    }

    public static EventsFragment newInstance(String projectId, String currentPage) {
        EventsFragment fragment = new EventsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PROJECT_ID, projectId);
        args.putString(ARG_CURRENT_PAGE, currentPage);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            projectId = getArguments().getString(ARG_PROJECT_ID);
            currentPage = getArguments().getString(ARG_CURRENT_PAGE);
        }

        logicBlockLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    refreshLogicList();
                }
            }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_events, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        navigationRail = view.findViewById(R.id.navigation_rail);
        tvSectionTitle = view.findViewById(R.id.tvSectionTitle);
        rvEvents = view.findViewById(R.id.rv_events);
        btnResetLogic = view.findViewById(R.id.btnResetLogic);
        btnImportCss = view.findViewById(R.id.btnImportCss);
        clear = view.findViewById(R.id.clear);
        suggestCard = view.findViewById(R.id.suggestCard);

        clear.setOnClickListener(v -> suggestCard.setVisibility(View.GONE));

        pageManager = new PageManager(getContext(), projectId);
        rvEvents.setLayoutManager(new LinearLayoutManager(getContext()));

        if (navigationRail != null) {
            navigationRail.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_rail_css) {
                    currentTab = 0;
                    if (tvSectionTitle != null) tvSectionTitle.setText("CSS Stylesheets");
                } else if (id == R.id.nav_rail_js) {
                    currentTab = 1;
                    if (tvSectionTitle != null) tvSectionTitle.setText("JavaScript Scripts");
                } else if (id == R.id.nav_rail_html) {
                    currentTab = 2;
                    if (tvSectionTitle != null) tvSectionTitle.setText("HTML Pages");
                } else if (id == R.id.nav_rail_functions) {
                    currentTab = 3;
                    if (tvSectionTitle != null) tvSectionTitle.setText("JS Functions (MoreBlocks)");
                }
                refreshLogicList();
                return true;
            });
        }

        btnResetLogic.setOnClickListener(v -> {
            if (currentTab == 3) {
                final ArrayList<DesignDataManager.MoreBlockData> funcs = DesignDataManager.getProjectMoreBlocks(projectId);
                if (funcs == null || funcs.isEmpty()) {
                    Toast.makeText(getContext(), "No functions to reset", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] funcNames = new String[funcs.size()];
                for (int i = 0; i < funcs.size(); i++) {
                    funcNames[i] = funcs.get(i).name != null ? funcs.get(i).name : funcs.get(i).spec;
                }
                new MaterialAlertDialogBuilder(getContext())
                    .setTitle("Select function to reset")
                    .setItems(funcNames, (dialog, which) -> {
                        final DesignDataManager.MoreBlockData selectedMb = funcs.get(which);
                        final String selectedName = selectedMb.name != null ? selectedMb.name : selectedMb.spec;
                        new MaterialAlertDialogBuilder(getContext())
                            .setTitle("Reset Function")
                            .setMessage("Are you sure you want to delete all logic blocks for function '" + selectedName + "'?")
                            .setPositiveButton("Reset", (d, w) -> {
                                String linkedFile = selectedMb.linkedFile != null ? selectedMb.linkedFile : "js/script.js";
                                String cleanPage = DesignDataManager.getCleanPageName(linkedFile);
                                File logicFile = new File(android.os.Environment.getExternalStorageDirectory(),
                                    ".dragweb/projects/" + projectId + "/" + cleanPage + "_logic.json");
                                if (logicFile.exists()) {
                                    try {
                                        String json = FileUtil.readFile(logicFile.getAbsolutePath());
                                        if (json != null && !json.trim().isEmpty()) {
                                            DesignDataManager.PageLogicData data = new Gson().fromJson(json, DesignDataManager.PageLogicData.class);
                                            if (data != null && data.blocks != null) {
                                                data.blocks.remove("func_" + selectedName);
                                                data.blocks.remove(selectedName + "_moreBlock");
                                                String updatedJson = new GsonBuilder().setPrettyPrinting().create().toJson(data);
                                                FileUtil.writeFile(logicFile.getAbsolutePath(), updatedJson);
                                            }
                                        }
                                    } catch (Exception e) {
                                        Log.e("EventsFragment", "Error updating logic file", e);
                                    }
                                }

                                // Delete function's own separate logic file if exists
                                File funcLogicFile = new File(android.os.Environment.getExternalStorageDirectory(),
                                    ".dragweb/projects/" + projectId + "/" + cleanPage + "_func_" + selectedName + "_logic.json");
                                if (funcLogicFile.exists()) {
                                    funcLogicFile.delete();
                                }
                                
                                if (DesignDataManager.mapBlocks != null) {
                                    for (HashMap<String, ArrayList<BlockBean>> pageBlocks : DesignDataManager.mapBlocks.values()) {
                                        if (pageBlocks != null) {
                                            pageBlocks.remove("func_" + selectedName);
                                            pageBlocks.remove(selectedName + "_moreBlock");
                                        }
                                    }
                                }
                                refreshLogicList();
                                Toast.makeText(getContext(), "Function logic reset successfully", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            } else {
                List<String> files = getProjectFiles();
                String[] items = files.toArray(new String[0]);
                new MaterialAlertDialogBuilder(getContext())
                    .setTitle("Select stylesheet/script to reset")
                    .setItems(items, (dialog, which) -> {
                        String selected = items[which];
                        new MaterialAlertDialogBuilder(getContext())
                            .setTitle("Reset Logic")
                            .setMessage("Are you sure you want to delete all logic blocks for '" + selected + "'? This cannot be undone.")
                            .setPositiveButton("Reset", (d, w) -> {
                                String cleanName = DesignDataManager.getCleanPageName(selected);
                                File logicFile = new File(android.os.Environment.getExternalStorageDirectory(),
                                    ".dragweb/projects/" + projectId + "/" + cleanName + "_logic.json");
                                if (logicFile.exists()) {
                                    logicFile.delete();
                                }
                                DesignDataManager.mapBlocks.remove(cleanName);
                                refreshLogicList();
                                Toast.makeText(getContext(), "Logic reset successfully", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            }
        });

        if (btnImportCss != null) {
            btnImportCss.setText("Import Code");
            btnImportCss.setOnClickListener(v -> showImportCodeDialog());
        }

        refreshLogicList();
    }

    public void refreshLogicList(String page) {
        this.currentPage = page;
        refreshLogicList();
    }

    private String getCleanBlockName(String text) {
        if (text == null) return "";
        String clean = text.replaceAll("%[sdb]|%m\\.[a-zA-Z0-9_.]+", "").replaceAll("\\s+", " ").trim();
        return clean.isEmpty() ? text : clean;
    }

    private void refreshLogicList() {
        if (getContext() == null || rvEvents == null) return;

        List<EventListItem> items = new ArrayList<>();

        if (currentTab == 3) {
            List<FunctionItem> functions = getAllProjectFunctions();
            for (FunctionItem f : functions) {
                EventListItem item = new EventListItem();
                item.isFunction = true;
                item.title = getCleanBlockName(f.name != null && !f.name.isEmpty() ? f.name : f.spec);
                item.subtitle = "Linked to: " + f.linkedFile;
                item.tag = "FUNC";
                item.targetPath = f.linkedFile;
                item.funcName = f.name;
                item.blockCount = getBlockCountForMoreBlock(f.linkedFile, f.name);
                items.add(item);
            }
        } else {
            List<String> filteredFiles = getFilteredFiles(currentTab);
            for (String f : filteredFiles) {
                EventListItem item = new EventListItem();
                item.isFunction = false;
                String displayName = f.substring(f.lastIndexOf('/') + 1);
                item.title = displayName;
                item.subtitle = f;
                item.tag = f.endsWith(".js") ? "JS" : f.endsWith(".html") ? "HTML" : "CSS";
                item.targetPath = f;
                item.blockCount = getBlockCountForCss(f);
                items.add(item);
            }
        }

        rvEvents.setAdapter(new CssFilesAdapter(items));
    }

    private List<String> getFilteredFiles(int tab) {
        List<String> allFiles = getProjectFiles();
        List<String> result = new ArrayList<>();
        for (String f : allFiles) {
            String lower = f.toLowerCase();
            if (tab == 0 && lower.endsWith(".css")) {
                result.add(f);
            } else if (tab == 1 && lower.endsWith(".js")) {
                result.add(f);
            } else if (tab == 2 && (lower.endsWith(".html") || lower.endsWith(".htm"))) {
                result.add(f);
            }
        }
        return result;
    }

    private List<String> getProjectFiles() {
        List<String> files = new ArrayList<>();
        files.add("css/style.css");
        files.add("js/script.js");
        files.add("index.html");

        if (pageManager != null) {
            for (String page : pageManager.getPages()) {
                String htmlName = page.endsWith(".html") ? page : page + ".html";
                if (!files.contains(htmlName)) {
                    files.add(htmlName);
                }
            }
        }

        String path = Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/.dragweb/projects/" + projectId + "/assets";
        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            collectFilesRecursive(dir, dir, files);
        }
        return files;
    }

    private void collectFilesRecursive(File root, File current, List<String> filesList) {
        File[] files = current.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    collectFilesRecursive(root, f, filesList);
                } else if (f.isFile()) {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".css") || name.endsWith(".js") || name.endsWith(".html")) {
                        String relative = f.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
                        if (name.contains("theme.css") || relative.contains("theme.css")) {
                            continue; // Lock/hide theme.css
                        }
                        if (!filesList.contains(relative)) {
                            filesList.add(relative);
                        }
                    }
                }
            }
        }
    }

    private List<FunctionItem> getAllProjectFunctions() {
        List<FunctionItem> result = new ArrayList<>();
        ArrayList<DesignDataManager.MoreBlockData> storedBlocks = DesignDataManager.getProjectMoreBlocks(projectId);
        if (storedBlocks != null) {
            for (DesignDataManager.MoreBlockData mb : storedBlocks) {
                if (mb != null) {
                    FunctionItem fi = new FunctionItem();
                    fi.name = mb.name;
                    fi.spec = mb.spec;
                    fi.linkedFile = mb.linkedFile != null ? mb.linkedFile : "js/script.js";
                    result.add(fi);
                }
            }
        }
        return result;
    }

    private int countBlocksExcludingDefine(ArrayList<BlockBean> list) {
        if (list == null) return 0;
        int count = 0;
        for (BlockBean b : list) {
            if (b != null && !"definedFunc".equals(b.opCode)) {
                count++;
            }
        }
        return count;
    }

    private int getBlockCountForMoreBlock(String linkedFile, String funcName) {
        if (getContext() == null || funcName == null || funcName.isEmpty()) return 0;
        
        String funcKey1 = "func_" + funcName;
        String funcKey2 = funcName + "_moreBlock";
        
        if (DesignDataManager.mapBlocks != null) {
            for (HashMap<String, ArrayList<BlockBean>> map : DesignDataManager.mapBlocks.values()) {
                if (map != null) {
                    if (map.containsKey(funcKey1) && map.get(funcKey1) != null) {
                        return countBlocksExcludingDefine(map.get(funcKey1));
                    } else if (map.containsKey(funcKey2) && map.get(funcKey2) != null) {
                        return countBlocksExcludingDefine(map.get(funcKey2));
                    }
                }
            }
        }

        String cleanPage = DesignDataManager.getCleanPageName(linkedFile);
        String[] pagesToCheck = new String[]{cleanPage, "script", "js_script", "index"};
        for (String pKey : pagesToCheck) {
            File logicFile = new File(android.os.Environment.getExternalStorageDirectory(),
                ".dragweb/projects/" + projectId + "/" + pKey + "_logic.json");
            if (logicFile.exists()) {
                try {
                    String json = FileUtil.readFile(logicFile.getAbsolutePath());
                    if (json != null && !json.trim().isEmpty() && !json.trim().equals("{}")) {
                        DesignDataManager.PageLogicData data = DesignDataManager.deserializePageLogicData(json, pKey);
                        if (data != null && data.blocks != null) {
                            if (data.blocks.containsKey(funcKey1) && data.blocks.get(funcKey1) != null) {
                                return countBlocksExcludingDefine(data.blocks.get(funcKey1));
                            }
                            if (data.blocks.containsKey(funcKey2) && data.blocks.get(funcKey2) != null) {
                                return countBlocksExcludingDefine(data.blocks.get(funcKey2));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private int getBlockCountForCss(String cssPath) {
        if (getContext() == null) return 0;
        String cleanName = DesignDataManager.getCleanPageName(cssPath);
        File logicFile = new File(android.os.Environment.getExternalStorageDirectory(),
            ".dragweb/projects/" + projectId + "/" + cleanName + "_logic.json");
        if (logicFile.exists()) {
            try {
                String json = FileUtil.readFile(logicFile.getAbsolutePath());
                if (json != null && !json.trim().isEmpty() && !json.trim().equals("{}")) {
                    DesignDataManager.PageLogicData data = DesignDataManager.deserializePageLogicData(json, cssPath);
                    if (data != null && data.blocks != null) {
                        int count = 0;
                        for (Map.Entry<String, ArrayList<BlockBean>> entry : data.blocks.entrySet()) {
                            if (!entry.getKey().startsWith("func_") && entry.getValue() != null) {
                                count += entry.getValue().size();
                            }
                        }
                        return count;
                    }
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private void showImportCodeDialog() {
        if (getContext() == null) return;

        if (currentTab == 3) {
            UniversalDialog.multilineInput(getContext(), "Import JS as MoreBlock", "Paste JS code here", "", codeText -> {
                if (codeText.trim().isEmpty()) return;
                HtmlCssImporter importer = new HtmlCssImporter(getContext());
                ArrayList<BlockBean> importedBeans = importer.importJsToBeans(codeText);
                if (importedBeans == null || importedBeans.isEmpty()) {
                    Toast.makeText(getContext(), "No JS blocks could be parsed.", Toast.LENGTH_LONG).show();
                    return;
                }
                ArrayList<DesignDataManager.MoreBlockData> funcs = DesignDataManager.getProjectMoreBlocks(projectId);
                if (funcs == null || funcs.isEmpty()) {
                    Toast.makeText(getContext(), "No existing functions to import into", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] names = new String[funcs.size()];
                for (int i = 0; i < funcs.size(); i++) {
                    names[i] = funcs.get(i).name != null ? funcs.get(i).name : funcs.get(i).spec;
                }
                UniversalDialog.singleChoice(getContext(), "Select Function", names, (idx3, selectedName) -> {
                    saveImportedBlocksAsFunction(selectedName, importedBeans);
                });
            });
            return;
        }

        List<String> files = getProjectFiles();
        if (files.isEmpty()) return;

        String[] items = files.toArray(new String[0]);
        UniversalDialog.singleChoice(getContext(), "Select Target File", items, (index, target) -> {
            boolean isJs = target.toLowerCase().endsWith(".js");
            String title = isJs ? "Import JS to " + target.substring(target.lastIndexOf('/') + 1) : "Import CSS to " + target.substring(target.lastIndexOf('/') + 1);
            String hint = isJs ? "Paste JS code here" : "Paste CSS code here";
            String codeType = isJs ? "JS" : "CSS";

            UniversalDialog.multilineInput(getContext(), title, hint, "", codeText -> {
                if (codeText.trim().isEmpty()) {
                    Toast.makeText(getContext(), "Pasted " + codeType + " is empty", Toast.LENGTH_SHORT).show();
                    return;
                }

                HtmlCssImporter importer = new HtmlCssImporter(getContext());
                ArrayList<BlockBean> importedBeans;
                if (isJs) {
                    importedBeans = importer.importJsToBeans(codeText);
                } else {
                    importedBeans = importer.importCssToBeans(codeText);
                }

                if (importedBeans == null || importedBeans.isEmpty()) {
                    Toast.makeText(getContext(), "No " + codeType + " rules could be parsed into blocks.", Toast.LENGTH_LONG).show();
                    return;
                }

                String cleanPage = DesignDataManager.getCleanPageName(target);
                File extDir = new File(android.os.Environment.getExternalStorageDirectory(), ".dragweb/projects/" + projectId);
                if (!extDir.exists()) extDir.mkdirs();
                File logicFile = new File(extDir, cleanPage + "_logic.json");

                DesignDataManager.PageLogicData data = null;
                if (logicFile.exists()) {
                    try {
                        String json = FileUtil.readFile(logicFile.getAbsolutePath());
                        if (json != null && !json.trim().isEmpty()) {
                            data = new Gson().fromJson(json, DesignDataManager.PageLogicData.class);
                        }
                    } catch (Exception e) {
                        Log.e("EventsFragment", "Error reading logic file", e);
                    }
                }
                if (data == null) {
                    data = new DesignDataManager.PageLogicData();
                }

                String eventKey = "onCreate_initializeLogic";
                ArrayList<BlockBean> currentBlocks = data.blocks.get(eventKey);
                if (currentBlocks == null) {
                    currentBlocks = new ArrayList<>();
                    data.blocks.put(eventKey, currentBlocks);
                }

                Map<Integer, Integer> idMapping = new HashMap<>();
                int maxId = 0;
                for (BlockBean b : currentBlocks) {
                    try {
                        int idNum = Integer.parseInt(b.id);
                        if (idNum > maxId) maxId = idNum;
                    } catch (Exception ignored) {}
                }

                for (BlockBean bean : importedBeans) {
                    try {
                        int oldId = Integer.parseInt(bean.id);
                        maxId++;
                        idMapping.put(oldId, maxId);
                        bean.id = String.valueOf(maxId);
                    } catch (Exception e) {
                        maxId++;
                        bean.id = String.valueOf(maxId);
                    }
                }

                for (BlockBean bean : importedBeans) {
                    if (bean.subStack1 >= 0 && idMapping.containsKey(bean.subStack1)) {
                        bean.subStack1 = idMapping.get(bean.subStack1);
                    } else if (bean.subStack1 >= 0 && !idMapping.containsValue(bean.subStack1)) {
                        bean.subStack1 = -1;
                    }

                    if (bean.subStack2 >= 0 && idMapping.containsKey(bean.subStack2)) {
                        bean.subStack2 = idMapping.get(bean.subStack2);
                    } else if (bean.subStack2 >= 0 && !idMapping.containsValue(bean.subStack2)) {
                        bean.subStack2 = -1;
                    }

                    if (bean.nextBlock >= 0 && idMapping.containsKey(bean.nextBlock)) {
                        bean.nextBlock = idMapping.get(bean.nextBlock);
                    } else if (bean.nextBlock >= 0 && !idMapping.containsValue(bean.nextBlock)) {
                        bean.nextBlock = -1;
                    }
                }

                // Link the imported blocks chain to the bottom of the existing block chain in workspace
                BlockBean importedRootBlock = null;
                if (!importedBeans.isEmpty()) {
                    java.util.Set<String> childIds = new java.util.HashSet<>();
                    for (BlockBean b : importedBeans) {
                        if (b.nextBlock >= 0) childIds.add(String.valueOf(b.nextBlock));
                        if (b.subStack1 >= 0) childIds.add(String.valueOf(b.subStack1));
                        if (b.subStack2 >= 0) childIds.add(String.valueOf(b.subStack2));
                    }
                    for (BlockBean b : importedBeans) {
                        if (!childIds.contains(b.id)) {
                            importedRootBlock = b;
                            break;
                        }
                    }
                    if (importedRootBlock == null) {
                        importedRootBlock = importedBeans.get(0);
                    }
                }

                BlockBean currentRootBlock = null;
                if (!currentBlocks.isEmpty()) {
                    java.util.Set<String> childIds = new java.util.HashSet<>();
                    for (BlockBean b : currentBlocks) {
                        if (b.nextBlock >= 0) childIds.add(String.valueOf(b.nextBlock));
                        if (b.subStack1 >= 0) childIds.add(String.valueOf(b.subStack1));
                        if (b.subStack2 >= 0) childIds.add(String.valueOf(b.subStack2));
                    }
                    for (BlockBean b : currentBlocks) {
                        if (!childIds.contains(b.id)) {
                            currentRootBlock = b;
                            break;
                        }
                    }
                    if (currentRootBlock == null) {
                        currentRootBlock = currentBlocks.get(0);
                    }
                }

                BlockBean currentBottomBlock = currentRootBlock;
                if (currentBottomBlock != null) {
                    while (currentBottomBlock.nextBlock >= 0) {
                        BlockBean next = null;
                        for (BlockBean b : currentBlocks) {
                            if (b.id.equals(String.valueOf(currentBottomBlock.nextBlock))) {
                                next = b;
                                break;
                            }
                        }
                        if (next == null) break;
                        currentBottomBlock = next;
                    }
                }

                if (currentBottomBlock != null && importedRootBlock != null) {
                    currentBottomBlock.nextBlock = Integer.parseInt(importedRootBlock.id);
                }

                int maxStackIndex = currentBlocks.size();
                for (BlockBean bean : importedBeans) {
                    bean.stackIndex = maxStackIndex++;
                    currentBlocks.add(bean);
                }

                String updatedJson = new GsonBuilder().setPrettyPrinting().create().toJson(data);
                FileUtil.writeFile(logicFile.getAbsolutePath(), updatedJson);

                HashMap<String, ArrayList<BlockBean>> pageBlocks = DesignDataManager.mapBlocks.get(cleanPage);
                if (pageBlocks == null) {
                    pageBlocks = new HashMap<>();
                    DesignDataManager.mapBlocks.put(cleanPage, pageBlocks);
                }
                pageBlocks.put(eventKey, currentBlocks);

                refreshLogicList();
                Toast.makeText(getContext(), "Successfully imported " + importedBeans.size() + " " + codeType + " blocks into " + target, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void saveImportedBlocksAsFunction(String funcName, ArrayList<BlockBean> importedBeans) {
        String funcKey = "func_" + funcName;
        String linkedFile = "js/script.js";
        String cleanPage = DesignDataManager.getCleanPageName(linkedFile);
        File extDir = new File(android.os.Environment.getExternalStorageDirectory(), ".dragweb/projects/" + projectId);
        if (!extDir.exists()) extDir.mkdirs();
        File logicFile = new File(extDir, cleanPage + "_logic.json");

        DesignDataManager.PageLogicData data = null;
        if (logicFile.exists()) {
            try {
                String json = FileUtil.readFile(logicFile.getAbsolutePath());
                if (json != null && !json.trim().isEmpty()) {
                    data = new Gson().fromJson(json, DesignDataManager.PageLogicData.class);
                }
            } catch (Exception e) {
                Log.e("EventsFragment", "Error reading logic file", e);
            }
        }
        if (data == null) {
            data = new DesignDataManager.PageLogicData();
        }

        Map<Integer, Integer> idMapping = new HashMap<>();
        int maxId = 0;
        ArrayList<BlockBean> existing = data.blocks.get(funcKey);
        if (existing != null) {
            for (BlockBean b : existing) {
                try { int idNum = Integer.parseInt(b.id); if (idNum > maxId) maxId = idNum; } catch (Exception ignored) {}
            }
        }
        for (BlockBean bean : importedBeans) {
            try {
                int oldId = Integer.parseInt(bean.id);
                maxId++;
                idMapping.put(oldId, maxId);
                bean.id = String.valueOf(maxId);
            } catch (Exception e) {
                maxId++;
                bean.id = String.valueOf(maxId);
            }
        }
        for (BlockBean bean : importedBeans) {
            if (bean.subStack1 >= 0 && idMapping.containsKey(bean.subStack1)) bean.subStack1 = idMapping.get(bean.subStack1);
            else if (bean.subStack1 >= 0 && !idMapping.containsValue(bean.subStack1)) bean.subStack1 = -1;
            if (bean.subStack2 >= 0 && idMapping.containsKey(bean.subStack2)) bean.subStack2 = idMapping.get(bean.subStack2);
            else if (bean.subStack2 >= 0 && !idMapping.containsValue(bean.subStack2)) bean.subStack2 = -1;
            if (bean.nextBlock >= 0 && idMapping.containsKey(bean.nextBlock)) bean.nextBlock = idMapping.get(bean.nextBlock);
            else if (bean.nextBlock >= 0 && !idMapping.containsValue(bean.nextBlock)) bean.nextBlock = -1;
        }

        // Find root of imported blocks
        BlockBean importedRootBlock = null;
        if (!importedBeans.isEmpty()) {
            java.util.Set<String> childIds = new java.util.HashSet<>();
            for (BlockBean b : importedBeans) {
                if (b.nextBlock >= 0) childIds.add(String.valueOf(b.nextBlock));
                if (b.subStack1 >= 0) childIds.add(String.valueOf(b.subStack1));
                if (b.subStack2 >= 0) childIds.add(String.valueOf(b.subStack2));
            }
            for (BlockBean b : importedBeans) {
                if (!childIds.contains(b.id)) {
                    importedRootBlock = b;
                    break;
                }
            }
            if (importedRootBlock == null) {
                importedRootBlock = importedBeans.get(0);
            }
        }

        ArrayList<BlockBean> funcBlocks;
        if (existing != null && !existing.isEmpty()) {
            funcBlocks = existing;
            
            // Find root of existing blocks
            BlockBean currentRootBlock = null;
            java.util.Set<String> childIds = new java.util.HashSet<>();
            for (BlockBean b : funcBlocks) {
                if (b.nextBlock >= 0) childIds.add(String.valueOf(b.nextBlock));
                if (b.subStack1 >= 0) childIds.add(String.valueOf(b.subStack1));
                if (b.subStack2 >= 0) childIds.add(String.valueOf(b.subStack2));
            }
            for (BlockBean b : funcBlocks) {
                if (!childIds.contains(b.id)) {
                    currentRootBlock = b;
                    break;
                }
            }
            if (currentRootBlock == null) {
                currentRootBlock = funcBlocks.get(0);
            }

            BlockBean currentBottomBlock = currentRootBlock;
            if (currentBottomBlock != null) {
                while (currentBottomBlock.nextBlock >= 0) {
                    BlockBean next = null;
                    for (BlockBean b : funcBlocks) {
                        if (b.id.equals(String.valueOf(currentBottomBlock.nextBlock))) {
                            next = b;
                            break;
                        }
                    }
                    if (next == null) break;
                    currentBottomBlock = next;
                }
            }

            if (currentBottomBlock != null && importedRootBlock != null) {
                currentBottomBlock.nextBlock = Integer.parseInt(importedRootBlock.id);
            }

            int maxStackIndex = funcBlocks.size();
            for (BlockBean bean : importedBeans) {
                bean.stackIndex = maxStackIndex++;
                funcBlocks.add(bean);
            }
        } else {
            funcBlocks = new ArrayList<>();
            
            // Create definedFunc block at the start
            BlockBean defBlock = new BlockBean();
            maxId++;
            defBlock.id = String.valueOf(maxId);
            defBlock.opCode = "definedFunc";
            defBlock.spec = funcName;
            defBlock.type = " ";
            defBlock.category = "";
            defBlock.nextBlock = -1;
            
            if (importedRootBlock != null) {
                defBlock.nextBlock = Integer.parseInt(importedRootBlock.id);
            }
            
            defBlock.stackIndex = 0;
            funcBlocks.add(defBlock);

            int si = 1;
            for (BlockBean bean : importedBeans) {
                bean.stackIndex = si++;
                funcBlocks.add(bean);
            }
        }
        data.blocks.put(funcKey, funcBlocks);

        String updatedJson = new GsonBuilder().setPrettyPrinting().create().toJson(data);
        FileUtil.writeFile(logicFile.getAbsolutePath(), updatedJson);

        HashMap<String, ArrayList<BlockBean>> pageBlocks = DesignDataManager.mapBlocks.get(cleanPage);
        if (pageBlocks == null) {
            pageBlocks = new HashMap<>();
            DesignDataManager.mapBlocks.put(cleanPage, pageBlocks);
        }
        pageBlocks.put(funcKey, funcBlocks);

        refreshLogicList();
        Toast.makeText(getContext(), "Imported " + importedBeans.size() + " blocks into function: " + funcName, Toast.LENGTH_LONG).show();
    }

    class CssFilesAdapter extends RecyclerView.Adapter<CssFilesAdapter.ViewHolder> {
        private final List<EventListItem> items;

        CssFilesAdapter(List<EventListItem> items) {
            this.items = items;
        }

        class ViewHolder extends ListItemViewHolder {
            TextView tvPageName, tvLinkedStyleName, tvPreview, tvBlocksCount;
            View actionContainer;
            ListItemCardView cardView;
            Button btnDeleteItem;

            ViewHolder(View view) {
                super(view);
                tvPageName = view.findViewById(R.id.tv_pagename);
                tvLinkedStyleName = view.findViewById(R.id.tv_linked_stylename);
                tvPreview = view.findViewById(R.id.tv_preview);
                tvBlocksCount = view.findViewById(R.id.blockscount);
                cardView = view.findViewById(R.id.cardView);
                actionContainer = view.findViewById(R.id.action_container);
                btnDeleteItem = view.findViewById(R.id.btn_delete_item);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event_css, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            EventListItem item = items.get(position);
            holder.bind(position, items.size());
            holder.tvPageName.setText(item.title);
            holder.tvLinkedStyleName.setText(item.subtitle);

            if (item.isFunction) {
                int primaryColor = MaterialColors.getColor(holder.itemView, android.R.attr.colorPrimary, Color.BLUE);
                holder.tvLinkedStyleName.setTextColor(primaryColor);
                holder.tvLinkedStyleName.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                int defaultColor = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
                holder.tvLinkedStyleName.setTextColor(defaultColor);
                holder.tvLinkedStyleName.setTypeface(null, android.graphics.Typeface.NORMAL);
            }

            if (holder.tvBlocksCount != null) {
                holder.tvBlocksCount.setText(String.valueOf(item.blockCount));
            }

            if (holder.tvPreview != null) {
                holder.tvPreview.setText(item.tag);
            }

            android.view.View.OnClickListener clickListener = v -> openLogicEditor(item);
            holder.itemView.setOnClickListener(clickListener);
            if (holder.cardView != null) {
                holder.cardView.setOnClickListener(clickListener);
            }

            if (holder.btnDeleteItem != null) {
                holder.btnDeleteItem.setOnClickListener(v -> {
                    if (item.isFunction) {
                        String targetFunc = item.funcName != null ? item.funcName : item.title;
                        if (DesignDataManager.isMoreBlockUsedInProject(targetFunc, targetFunc)) {
                            new MaterialAlertDialogBuilder(getContext())
                                .setTitle("Cannot Delete")
                                .setMessage("MoreBlock '" + item.title + "' is already in use by one or more JS events. Remove all references before deleting.")
                                .setPositiveButton("OK", null)
                                .show();
                        } else {
                            new MaterialAlertDialogBuilder(getContext())
                                .setTitle("Delete MoreBlock")
                                .setMessage("Are you sure you want to delete MoreBlock '" + item.title + "'?")
                                .setPositiveButton("Delete", (d, w) -> {
                                    DesignDataManager.deleteProjectMoreBlock(projectId, targetFunc, item.targetPath);
                                    refreshLogicList();
                                    Toast.makeText(getContext(), "Deleted MoreBlock: " + item.title, Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        }
                    } else {
                        new MaterialAlertDialogBuilder(getContext())
                            .setTitle("Reset Logic")
                            .setMessage("Are you sure you want to delete all logic blocks for '" + item.title + "'? This cannot be undone.")
                            .setPositiveButton("Reset", (d, w) -> {
                                String cleanName = DesignDataManager.getCleanPageName(item.targetPath);
                                File logicFile = new File(android.os.Environment.getExternalStorageDirectory(),
                                    ".dragweb/projects/" + projectId + "/" + cleanName + "_logic.json");
                                if (logicFile.exists()) {
                                    logicFile.delete();
                                }
                                DesignDataManager.mapBlocks.remove(cleanName);
                                refreshLogicList();
                                Toast.makeText(getContext(), "Logic reset successfully", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    }
                });
            }
        }

        private void openLogicEditor(EventListItem item) {
            Intent intent = new Intent(getContext(), LogicBlockActivity.class);
            intent.putExtra("project_id", projectId);
            intent.putExtra("page_name", item.targetPath);
            if (item.isFunction) {
                intent.putExtra("id", "func");
                intent.putExtra("event", item.funcName != null ? item.funcName : item.title);
                intent.putExtra("filename", item.targetPath);
                intent.putExtra("event_text", "Function: " + item.title);
            } else {
                intent.putExtra("id", "onPageLoad");
                intent.putExtra("event", "onPageLoad");
                intent.putExtra("filename", item.targetPath);
                intent.putExtra("event_text", item.title + " Logic");
            }
            logicBlockLauncher.launch(intent);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private void disallowAllParentsIntercept(View v, boolean disallow) {
        if (v == null) return;
        ViewParent p = v.getParent();
        while (p != null) {
            if (!(p instanceof com.google.android.material.listitem.ListItemLayout)) {
                p.requestDisallowInterceptTouchEvent(disallow);
            }
            p = p.getParent();
        }
        if (getActivity() instanceof MainActivity) {
            ViewPager2 vp = ((MainActivity) getActivity()).getViewPager();
            if (vp != null) {
                vp.setUserInputEnabled(!disallow);
            }
        }
    }

    private int dp(int value) {
        if (getContext() == null) return value;
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
