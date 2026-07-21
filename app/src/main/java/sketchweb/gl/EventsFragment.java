package sketchweb.gl;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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

    private RecyclerView rvEvents;
    private Button btnResetLogic;
    private Button btnImportCss;

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

        rvEvents = view.findViewById(R.id.rv_events);
        btnResetLogic = view.findViewById(R.id.btnResetLogic);
        btnImportCss = view.findViewById(R.id.btnImportCss);

        pageManager = new PageManager(getContext(), projectId);
        rvEvents.setLayoutManager(new LinearLayoutManager(getContext()));

        btnResetLogic.setOnClickListener(v -> {
            List<String> files = getCssAndJsFiles();
            String[] items = files.toArray(new String[0]);
            new MaterialAlertDialogBuilder(getContext())
                .setTitle("Select stylesheet/script to reset")
                .setItems(items, (dialog, which) -> {
                    String selected = items[which];
                    new MaterialAlertDialogBuilder(getContext())
                        .setTitle("Reset Logic")
                        .setMessage("Are you sure you want to delete all logic blocks for '" + selected + "'? This cannot be undone.")
                        .setPositiveButton("Reset", (d, w) -> {
                             File dir = new File(new File(getContext().getFilesDir(), "projects"), "logic");
                             String safeName = selected.replace("/", "_").replace(".", "_");
                             File logicFile = new File(dir, projectId + "_" + safeName + ".logic");
                            if (logicFile.exists()) {
                                logicFile.delete();
                            }
                            refreshLogicList();
                            Toast.makeText(getContext(), "Logic reset successfully", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
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

    private void refreshLogicList() {
        if (getContext() == null || rvEvents == null) return;
        List<String> files = getCssAndJsFiles();
        rvEvents.setAdapter(new CssFilesAdapter(files));
    }



    private List<String> getCssAndJsFiles() {
        List<String> files = new ArrayList<>();
        files.add("css/style.css");
        files.add("js/script.js");
        String path = Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/.dragweb/projects/" + projectId + "/assets";
        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            collectCssAndJsFilesRecursive(dir, dir, files);
        }
        return files;
    }

    private void collectCssAndJsFilesRecursive(File root, File current, List<String> filesList) {
        File[] files = current.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    collectCssAndJsFilesRecursive(root, f, filesList);
                } else if (f.isFile()) {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".css") || name.endsWith(".js")) {
                        String relative = f.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
                        relative = relative.replace("\\", "/");
                        if (!filesList.contains(relative)) {
                            filesList.add(relative);
                        }
                    }
                }
            }
        }
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
                    DesignDataManager.PageLogicData data = new Gson().fromJson(json, DesignDataManager.PageLogicData.class);
                    if (data != null && data.blocks != null) {
                        int count = 0;
                        for (java.util.ArrayList<?> list : data.blocks.values()) {
                            count += list.size();
                        }
                        return count;
                    }
                }
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    private void showImportCodeDialog() {
        if (getContext() == null) return;
        List<String> files = getCssAndJsFiles();
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
                List<Map<String, Object>> blockMaps;
                if (isJs) {
                    blockMaps = importer.importJsOnly(codeText);
                } else {
                    blockMaps = importer.importCssOnly(codeText);
                }

                List<LogicBlockManager.LogicBlock> importedBlocks = new Gson().fromJson(
                    new Gson().toJson(blockMaps),
                    new TypeToken<List<LogicBlockManager.LogicBlock>>(){}.getType()
                );
                if (importedBlocks == null || importedBlocks.isEmpty()) {
                    Toast.makeText(getContext(), "No " + codeType + " rules could be parsed into blocks.", Toast.LENGTH_LONG).show();
                    return;
                }

                File dir = new File(new File(getContext().getFilesDir(), "projects"), "logic");
                if (!dir.exists()) dir.mkdirs();
                String safeName = target.replace("/", "_").replace(".", "_");
                File logicFile = new File(dir, projectId + "_" + safeName + ".logic");

                List<LogicBlockManager.LogicBlock> currentBlocks = new ArrayList<>();
                if (logicFile.exists()) {
                    try {
                        String json = FileUtil.readFile(logicFile.getAbsolutePath());
                        List<LogicBlockManager.LogicBlock> parsed = new Gson().fromJson(json,
                            new TypeToken<List<LogicBlockManager.LogicBlock>>(){}.getType());
                        if (parsed != null) {
                            currentBlocks.addAll(parsed);
                        }
                    } catch (Exception e) { android.util.Log.e("EventsFragment", "Error", e); }
                }

                currentBlocks.addAll(importedBlocks);
                FileUtil.writeFile(logicFile.getAbsolutePath(), new Gson().toJson(currentBlocks));
                refreshLogicList();
                Toast.makeText(getContext(), "Successfully imported " + importedBlocks.size() + " " + codeType + " blocks into " + target, Toast.LENGTH_LONG).show();
            });
        });
    }



    class CssFilesAdapter extends RecyclerView.Adapter<CssFilesAdapter.ViewHolder> {
        private final List<String> items;

        CssFilesAdapter(List<String> items) {
            this.items = items;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvPageName, tvLinkedStyleName, tvPreview, tvBlocksCount;
            View cardView, actionContainer;

            ViewHolder(View view) {
                super(view);
                tvPageName = view.findViewById(R.id.tv_pagename);
                tvLinkedStyleName = view.findViewById(R.id.tv_linked_stylename);
                tvPreview = view.findViewById(R.id.tv_preview);
                tvBlocksCount = view.findViewById(R.id.blockscount);
                cardView = view.findViewById(R.id.cardView);
                actionContainer = view.findViewById(R.id.action_container);
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
            String cssPath = items.get(position);
            String displayName = cssPath.substring(cssPath.lastIndexOf('/') + 1);
            holder.tvPageName.setText(displayName);
            
            int blockCount = getBlockCountForCss(cssPath);
            holder.tvLinkedStyleName.setText(cssPath);

            if (holder.tvBlocksCount != null) {
                holder.tvBlocksCount.setText(String.valueOf(blockCount));
            }

            if (holder.tvPreview != null) {
                holder.tvPreview.setText(cssPath.endsWith(".js") ? "JS" : "CSS");
            }

            // Set rounded backgrounds with margin between items
            if (holder.cardView != null) {
                holder.cardView.setBackgroundResource(R.drawable.item_single_bg);
                if (holder.cardView instanceof com.google.android.material.card.MaterialCardView) {
                    com.google.android.material.card.MaterialCardView mcv = (com.google.android.material.card.MaterialCardView) holder.cardView;
                    mcv.setStrokeWidth(0);
                    mcv.setCardElevation(0);
                }
            }

            // Adjust vertical margins dynamically so they have space between them
            ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) layoutParams;
                int marginHorizontal = dp(16);
                int marginTop = (position == 0) ? dp(12) : dp(6);
                int marginBottom = (position == items.size() - 1) ? dp(12) : dp(6);
                lp.setMargins(marginHorizontal, marginTop, marginHorizontal, marginBottom);
                holder.itemView.setLayoutParams(lp);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), LogicBlockActivity.class);
                intent.putExtra("project_id", projectId);
                intent.putExtra("page_name", cssPath);
                intent.putExtra("id", "onCreate");
                intent.putExtra("event", "initializeLogic");
                intent.putExtra("filename", cssPath);
                intent.putExtra("event_text", "CSS Initialization");
                logicBlockLauncher.launch(intent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private int dp(int value) {
        if (getContext() == null) return value;
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
