package sketchweb.gl;

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

        setupFileExplorer();
    }

    private void setupFileExplorer() {
        if (getContext() == null) return;

        String assetsPath = Environment.getExternalStorageDirectory().getAbsolutePath()
            + "/.dragweb/projects/" + projectId + "/assets";
        File assetsDir = new File(assetsPath);
        if (!assetsDir.exists()) assetsDir.mkdirs();

        fileExplorerAdapter = new FileExplorerAdapter(getContext(), assetsDir);
        fileExplorerAdapter.setOnFileClickListener(file -> {
            if (file == null) {
                fileExplorerAdapter.goUp();
            } else if (file.isDirectory()) {
                fileExplorerAdapter.navigateTo(file);
            }
            updateAssetsPath();
        });
        fileExplorerAdapter.setOnFileLongClickListener(this::showFileContextMenu);

        rvAssets.setAdapter(fileExplorerAdapter);
        rvAssets.setLayoutManager(new LinearLayoutManager(getContext()));
        fileExplorerAdapter.navigateTo(assetsDir);
        updateAssetsPath();
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
        if (newDir.exists()) {
            Toast.makeText(getContext(), "Directory already exists", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newDir.mkdirs()) {
            fileExplorerAdapter.navigateTo(current);
            updateAssetsPath();
            Toast.makeText(getContext(), "Folder created: " + name, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "Failed to create folder", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAssetFile(Uri uri) {
        if (getContext() == null) return;
        try {
            File targetDir = fileExplorerAdapter != null ?
                fileExplorerAdapter.getCurrentDir() : null;
            if (targetDir == null) {
                targetDir = new File(Environment.getExternalStorageDirectory(), "/.dragweb/projects/" + projectId + "/assets");
            }
            targetDir.mkdirs();

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
        String[] options = file.isDirectory() ?
            new String[]{"Open", "Rename", "Delete"} :
            new String[]{"Use as Image Source", "Rename", "Delete"};

        new MaterialAlertDialogBuilder(getContext())
            .setTitle(file.getName())
            .setItems(options, (dialog, which) -> {
                if (file.isDirectory()) {
                    if (which == 0) fileExplorerAdapter.navigateTo(file);
                    else if (which == 1) showRenameFileDialog(file);
                    else deleteFileWithConfirm(file);
                } else {
                    if (which == 0) useFileAsImageSource(file);
                    else if (which == 1) showRenameFileDialog(file);
                    else deleteFileWithConfirm(file);
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
                Toast.makeText(getContext(), "File renamed successfully", Toast.LENGTH_SHORT).show();
                if (fileExplorerAdapter != null) {
                    fileExplorerAdapter.navigateTo(fileExplorerAdapter.getCurrentDir());
                }
            } else {
                Toast.makeText(getContext(), "Rename failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteFileWithConfirm(File file) {
        if (getContext() == null) return;
        new MaterialAlertDialogBuilder(getContext())
            .setTitle("Delete " + (file.isDirectory() ? "Folder" : "File"))
            .setMessage("Are you sure you want to delete " + file.getName() + "? This cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
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
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }
        return fileOrDirectory.delete();
    }

    private void useFileAsImageSource(File file) {
        Activity act = getActivity();
        if (act instanceof MainActivity) {
            ((MainActivity) act).useFileAsImageSource(file);
        }
    }

    public void refresh() {
        if (fileExplorerAdapter != null) {
            fileExplorerAdapter.navigateTo(fileExplorerAdapter.getCurrentDir());
            updateAssetsPath();
        }
    }
}
