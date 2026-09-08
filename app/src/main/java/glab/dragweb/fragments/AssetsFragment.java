package glab.dragweb.fragments;

import glab.dragweb.R;
import glab.dragweb.activities.*;
import glab.dragweb.fragments.*;
import glab.dragweb.engine.*;
import glab.dragweb.ui.*;
import glab.dragweb.logic.*;
import glab.dragweb.codegen.*;
import glab.dragweb.data.*;
import glab.dragweb.adapters.*;
import glab.dragweb.models.*;
import glab.dragweb.util.*;
import glab.dragweb.colorpicker.*;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class AssetsFragment extends Fragment {

    private static final String ARG_PROJECT_ID = "project_id";

    private String projectId = "";
    private FileExplorerAdapter fileExplorerAdapter;
    private ActivityResultLauncher<Intent> importAssetLauncher;

    private RecyclerView rvAssets;
    private TextView tvAssetsPath;
    private Button btnImportImage;
    private Button btnNewFolder;
    private Button btnRefresh;
    private Button btnToggleViewMode;
    private android.widget.ProgressBar progressAssets;

    public static AssetsFragment newInstance(String projectId) {
        AssetsFragment fragment = new AssetsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PROJECT_ID, projectId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            projectId = getArguments().getString(ARG_PROJECT_ID);
        }

        importAssetLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) saveAssetFile(uri);
                }
            }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_assets, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        rvAssets = view.findViewById(R.id.rvAssets);
        tvAssetsPath = view.findViewById(R.id.tvAssetsPath);
        btnImportImage = view.findViewById(R.id.btnImportImage);
        btnNewFolder = view.findViewById(R.id.btnNewFolder);
        btnRefresh = view.findViewById(R.id.btnRefresh);
        btnToggleViewMode = view.findViewById(R.id.btnToggleViewMode);
        progressAssets = view.findViewById(R.id.progressAssets);

        btnImportImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            importAssetLauncher.launch(intent);
        });

        btnNewFolder.setOnClickListener(v -> {
            if (getContext() != null) {
                new UniversalM3Dialog(getContext())
                    .setTitle("New Folder")
                    .setHint("Folder name")
                    .showTextInput(name -> {
                        if (!name.isEmpty()) createFolder(name);
                    });
            }
        });

        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> loadFilesWithProgress());
        }

        if (btnToggleViewMode != null) {
            btnToggleViewMode.setOnClickListener(v -> toggleViewMode());
        }

        setupFileExplorer();
        loadFilesWithProgress();
    }

    private boolean isTextFile(File file) {
        if (file == null || FileUtil.isDirectory(file)) return false;
        String name = file.getName().toLowerCase(java.util.Locale.US);
        return name.endsWith(".html") || name.endsWith(".htm") 
            || name.endsWith(".css") || name.endsWith(".js") 
            || name.endsWith(".json") || name.endsWith(".txt") 
            || name.endsWith(".svg") || name.endsWith(".xml");
    }

    private void openTextEditor(File file) {
        if (getContext() == null || file == null) return;
        Intent intent = new Intent(getContext(), TextEditorActivity.class);
        intent.putExtra("file_path", file.getAbsolutePath());
        intent.putExtra("project_id", projectId);
        boolean isLocked = fileExplorerAdapter != null && fileExplorerAdapter.isSystemFile(file);
        intent.putExtra("read_only", isLocked);
        String relPath = "";
        try {
            String assetsPath = FileUtil.getDragWebDir(getContext()).getAbsolutePath() + "/projects/" + projectId + "/assets";
            File rootDir = new File(assetsPath);
            String rootCanonical = rootDir.getCanonicalPath();
            String fileCanonical = file.getCanonicalPath();
            if (fileCanonical.startsWith(rootCanonical)) {
                relPath = fileCanonical.substring(rootCanonical.length());
                if (relPath.startsWith("/")) {
                    relPath = relPath.substring(1);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("AssetsFragment", "Failed to compute relative path: " + e.getMessage());
        }
        intent.putExtra("relative_path", relPath);
        startActivity(intent);
    }

    private void setupFileExplorer() {
        if (getContext() == null) return;

        String assetsPath = FileUtil.getDragWebDir(getContext()).getAbsolutePath() + "/projects/" + projectId + "/assets";
        FileUtil.makeDir(assetsPath);
        File assetsDir = new File(assetsPath);

        // Ensure default asset directories and files exist
        File cssDir = new File(assetsDir, "css");
        File jsDir = new File(assetsDir, "js");
        FileUtil.makeDir(cssDir.getAbsolutePath());
        FileUtil.makeDir(jsDir.getAbsolutePath());
        File styleCss = new File(cssDir, "style.css");
        File themeCss = new File(cssDir, "theme.css");
        if (!styleCss.exists() || !themeCss.exists()) {
            ProjectCodeGenerator.generateAndSaveAssets(getContext(), projectId, "index");
        }

        fileExplorerAdapter = new FileExplorerAdapter(getContext(), assetsDir);
        fileExplorerAdapter.setOnFileClickListener(file -> {
            if (file == null) {
                fileExplorerAdapter.goUp();
            } else if (FileUtil.isDirectory(file)) {
                fileExplorerAdapter.navigateTo(file);
            } else if (isTextFile(file)) {
                openTextEditor(file);
            }
            updateAssetsPath();
        });
        fileExplorerAdapter.setOnFileLongClickListener(this::showFileContextMenu);

        rvAssets.setAdapter(fileExplorerAdapter);
        
        // Restore layout manager mode state
        int mode = fileExplorerAdapter.getViewMode();
        if (mode == FileExplorerAdapter.VIEW_MODE_GRID) {
            rvAssets.setLayoutManager(new GridLayoutManager(getContext(), 3));
        } else {
            rvAssets.setLayoutManager(new LinearLayoutManager(getContext()));
        }

        if (btnToggleViewMode != null) {
            if (btnToggleViewMode instanceof com.google.android.material.button.MaterialButton) {
                ((com.google.android.material.button.MaterialButton) btnToggleViewMode).setIconResource(
                    mode == FileExplorerAdapter.VIEW_MODE_LIST ? R.drawable.layout_grid : R.drawable.ic_list_view
                );
            }
        }
    }

    private void toggleViewMode() {
        if (fileExplorerAdapter == null || getContext() == null) return;
        int currentMode = fileExplorerAdapter.getViewMode();
        if (currentMode == FileExplorerAdapter.VIEW_MODE_LIST) {
            fileExplorerAdapter.setViewMode(FileExplorerAdapter.VIEW_MODE_GRID);
            rvAssets.setLayoutManager(new GridLayoutManager(getContext(), 3));
            if (btnToggleViewMode != null) {
                if (btnToggleViewMode instanceof com.google.android.material.button.MaterialButton) {
                    ((com.google.android.material.button.MaterialButton) btnToggleViewMode).setIconResource(R.drawable.ic_list_view);
                }
            }
        } else {
            fileExplorerAdapter.setViewMode(FileExplorerAdapter.VIEW_MODE_LIST);
            rvAssets.setLayoutManager(new LinearLayoutManager(getContext()));
            if (btnToggleViewMode != null) {
                if (btnToggleViewMode instanceof com.google.android.material.button.MaterialButton) {
                    ((com.google.android.material.button.MaterialButton) btnToggleViewMode).setIconResource(R.drawable.layout_grid);
                }
            }
        }
    }

    private void updateAssetsPath() {
        if (tvAssetsPath != null && fileExplorerAdapter != null) {
            tvAssetsPath.setText(fileExplorerAdapter.getRelativePath());
        }
    }

    private void createFolder(String name) {
        if (fileExplorerAdapter == null) return;
        File current = fileExplorerAdapter.getCurrentDir();
        File newDir = new File(current, name.trim());
        FileUtil.makeDir(newDir.getAbsolutePath());
        if (fileExplorerAdapter != null) {
            fileExplorerAdapter.setFileType(newDir, "external");
            fileExplorerAdapter.navigateTo(current);
        }
        updateAssetsPath();
        Toast.makeText(getContext(), "Folder created: " + name, Toast.LENGTH_SHORT).show();
    }

    private void saveAssetFile(Uri uri) {
        if (getContext() == null) return;
        try {
            File targetDir = fileExplorerAdapter != null ?
                fileExplorerAdapter.getCurrentDir() : null;
            if (targetDir == null) {
                targetDir = new File(FileUtil.getDragWebDir(getContext()), "projects/" + projectId + "/assets");
            }
            FileUtil.makeDir(targetDir.getAbsolutePath());

            String name = getFileNameFromUri(uri);
            if (name == null) name = "asset_" + System.currentTimeMillis();

            File dest = new File(targetDir, name);
            InputStream is = getContext().getContentResolver().openInputStream(uri);
            FileOutputStream fos = new FileOutputStream(dest);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            is.close();
            fos.close();

            if (fileExplorerAdapter != null) {
                fileExplorerAdapter.setFileType(dest, "external");
                fileExplorerAdapter.navigateTo(fileExplorerAdapter.getCurrentDir());
                updateAssetsPath();
            }
            Toast.makeText(getContext(), "Imported: " + name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileNameFromUri(Uri uri) {
        if (getContext() == null) return null;
        String name = null;
        if ("content".equals(uri.getScheme())) {
            android.database.Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) name = cursor.getString(idx);
                }
                cursor.close();
            }
        }
        if (name == null) {
            name = uri.getPath();
            if (name != null) {
                int cut = name.lastIndexOf('/');
                if (cut != -1) name = name.substring(cut + 1);
            }
        }
        return name;
    }

    private void showFileContextMenu(File file) {
        if (getContext() == null || file == null) return;

        boolean isSys = false;
        if (fileExplorerAdapter != null) {
            isSys = fileExplorerAdapter.isSystemFile(file);
        }

        final java.util.List<String> optionsList = new java.util.ArrayList<>();
        if (FileUtil.isDirectory(file)) {
            optionsList.add("Open");
            if (!isSys) {
                optionsList.add("Rename");
                optionsList.add("Delete");
            }
        } else {
            if (isTextFile(file)) {
                optionsList.add("Edit Code");
            }
            if (!isSys) {
                optionsList.add("Rename");
                optionsList.add("Delete");
            }
        }

        if (optionsList.isEmpty()) return;

        final String[] options = optionsList.toArray(new String[0]);
        new MaterialAlertDialogBuilder(getContext())
            .setTitle(file.getName())
            .setItems(options, (dialog, which) -> {
                String option = options[which];
                if ("Open".equals(option)) {
                    fileExplorerAdapter.navigateTo(file);
                } else if ("Edit Code".equals(option)) {
                    openTextEditor(file);
                } else if ("Rename".equals(option)) {
                    showRenameFileDialog(file);
                } else if ("Delete".equals(option)) {
                    deleteFileWithConfirm(file);
                }
                updateAssetsPath();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showRenameFileDialog(File file) {
        if (getContext() == null) return;
        UniversalDialog.textInput(getContext(), "Rename File", "New Name", file.getName(), newName -> {
            if (newName == null || newName.trim().isEmpty()) {
                Toast.makeText(getContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            File parent = file.getParentFile();
            File target = new File(parent, newName.trim());
            if (target.exists()) {
                Toast.makeText(getContext(), "A file with this name already exists", Toast.LENGTH_SHORT).show();
                return;
            }
            if (file.renameTo(target)) {
                if (fileExplorerAdapter != null) {
                    fileExplorerAdapter.renameFileType(file, target);
                    fileExplorerAdapter.navigateTo(fileExplorerAdapter.getCurrentDir());
                }
                Toast.makeText(getContext(), "File renamed successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Rename failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteFileWithConfirm(File file) {
        if (getContext() == null) return;
        new MaterialAlertDialogBuilder(getContext())
            .setTitle("Delete " + (FileUtil.isDirectory(file) ? "Folder" : "File"))
            .setMessage("Are you sure you want to delete " + file.getName() + "? This cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
                if (fileExplorerAdapter != null) {
                    fileExplorerAdapter.removeFileType(file);
                }
                if (deleteRecursive(file)) {
                    Toast.makeText(getContext(), "Deleted successfully", Toast.LENGTH_SHORT).show();
                    if (fileExplorerAdapter != null) {
                        fileExplorerAdapter.navigateTo(fileExplorerAdapter.getCurrentDir());
                    }
                } else {
                    Toast.makeText(getContext(), "Delete failed", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory == null) return false;
        FileUtil.deleteFile(fileOrDirectory.getAbsolutePath());
        return !fileOrDirectory.exists();
    }

    public void refresh() {
        if (fileExplorerAdapter != null) {
            fileExplorerAdapter.navigateTo(fileExplorerAdapter.getCurrentDir());
            updateAssetsPath();
        }
    }

    public void loadFilesWithProgress() {
        if (fileExplorerAdapter != null) {
            fileExplorerAdapter.navigateTo(fileExplorerAdapter.getCurrentDir());
            updateAssetsPath();
        }
        if (progressAssets != null) {
            progressAssets.setVisibility(View.GONE);
        }
        if (rvAssets != null) {
            rvAssets.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }
}
