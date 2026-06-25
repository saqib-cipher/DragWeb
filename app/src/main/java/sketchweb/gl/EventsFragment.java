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
            List<String> files = getCssFiles();
            String[] items = files.toArray(new String[0]);
            new MaterialAlertDialogBuilder(getContext())
                .setTitle("Select stylesheet to reset")
                .setItems(items, (dialog, which) -> {
                    String selected = items[which];
                    new MaterialAlertDialogBuilder(getContext())
                        .setTitle("Reset Logic")
                        .setMessage("Are you sure you want to delete all logic blocks for '" + selected + "'? This cannot be undone.")
                        .setPositiveButton("Reset", (d, w) -> {
                            File dir = new File(getContext().getFilesDir(), "projects");
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
            btnImportCss.setOnClickListener(v -> showImportCssDialog());
        }

        refreshLogicList();
    }

    public void refreshLogicList(String page) {
        this.currentPage = page;
        refreshLogicList();
    }

    private void refreshLogicList() {
        if (getContext() == null || rvEvents == null) return;
        List<String> cssFiles = getCssFiles();
        rvEvents.setAdapter(new CssFilesAdapter(cssFiles));
    }

    private List<String> getCssFiles() {
        List<String> cssFiles = new ArrayList<>();
        cssFiles.add("css/style.css");
        String path = Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/.dragweb/projects/" + projectId + "/assets";
        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            collectCssFilesRecursive(dir, dir, cssFiles);
        }
        return cssFiles;
    }

    private void collectCssFilesRecursive(File root, File current, List<String> cssFiles) {
        File[] files = current.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    collectCssFilesRecursive(root, f, cssFiles);
                } else if (f.isFile() && f.getName().toLowerCase().endsWith(".css")) {
                    String relative = f.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
                    relative = relative.replace("\\", "/");
                    if (!cssFiles.contains(relative)) {
                        cssFiles.add(relative);
                    }
                }
            }
        }
    }

    private int getBlockCountForCss(String cssPath) {
        File dir = new File(getContext().getFilesDir(), "projects");
        String safeName = cssPath.replace("/", "_").replace(".", "_");
        File logicFile = new File(dir, projectId + "_" + safeName + ".logic");
        if (logicFile.exists()) {
            try {
                String json = FileUtil.readFile(logicFile.getAbsolutePath());
                List<Map<String, Object>> parsed = new Gson().fromJson(json,
                    new TypeToken<List<Map<String, Object>>>(){}.getType());
                return parsed != null ? parsed.size() : 0;
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    private void showImportCssDialog() {
        if (getContext() == null) return;
        List<String> files = getCssFiles();
        if (files.isEmpty()) return;

        String[] items = files.toArray(new String[0]);
        UniversalDialog.singleChoice(getContext(), "Select Target Stylesheet", items, (index, target) -> {
            UniversalDialog.multilineInput(getContext(), "Import CSS to " + target.substring(target.lastIndexOf('/') + 1), "Paste CSS code here", "", cssText -> {
                if (cssText.trim().isEmpty()) {
                    Toast.makeText(getContext(), "Pasted CSS is empty", Toast.LENGTH_SHORT).show();
                    return;
                }

                HtmlCssImporter importer = new HtmlCssImporter();
                List<Map<String, Object>> blockMaps = importer.importCssOnly(cssText);
                List<LogicBlockManager.LogicBlock> importedBlocks = new Gson().fromJson(
                    new Gson().toJson(blockMaps),
                    new TypeToken<List<LogicBlockManager.LogicBlock>>(){}.getType()
                );
                if (importedBlocks == null || importedBlocks.isEmpty()) {
                    Toast.makeText(getContext(), "No CSS rules could be parsed into blocks.", Toast.LENGTH_LONG).show();
                    return;
                }

                File dir = new File(getContext().getFilesDir(), "projects");
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
                    } catch (Exception e) {}
                }

                currentBlocks.addAll(importedBlocks);
                FileUtil.writeFile(logicFile.getAbsolutePath(), new Gson().toJson(currentBlocks));
                refreshLogicList();
                Toast.makeText(getContext(), "Successfully imported " + importedBlocks.size() + " CSS blocks into " + target, Toast.LENGTH_LONG).show();
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
                holder.tvPreview.setText("CSS");
            }


            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), LogicBlockActivity.class);
                intent.putExtra("project_id", projectId);
                intent.putExtra("page_name", cssPath);
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
