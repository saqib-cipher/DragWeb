package sketchweb.gl;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ManageWidgetsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private RecyclerView rvWidgets;
    private TextView tvEmpty;
    private ExtendedFloatingActionButton fabAdd;
    private WidgetsAdapter adapter;
    private WidgetRegistry widgetRegistry;
    private final List<HashMap<String, Object>> widgets = new ArrayList<>();

    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<String> exportLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_blocks_widgets);

        widgetRegistry = new WidgetRegistry(this);

        View tabLayout = findViewById(R.id.tabLayoutManager);
        if (tabLayout != null) tabLayout.setVisibility(View.GONE);
        View rvBlocks = findViewById(R.id.rvBlocks);
        if (rvBlocks != null) rvBlocks.setVisibility(View.GONE);
        View tvEmptyBlocks = findViewById(R.id.tvEmptyBlocks);
        if (tvEmptyBlocks != null) tvEmptyBlocks.setVisibility(View.GONE);

        toolbar = findViewById(R.id.toolbarManager);
        if (toolbar != null) {
            toolbar.setTitle("Manage Widgets");
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        rvWidgets = findViewById(R.id.rvWidgets);
        tvEmpty = findViewById(R.id.tvEmptyWidgets);
        fabAdd = findViewById(R.id.fabAddCustom);

        if (rvWidgets != null) {
            rvWidgets.setVisibility(View.VISIBLE);
            rvWidgets.setLayoutManager(new LinearLayoutManager(this));
            adapter = new WidgetsAdapter();
            rvWidgets.setAdapter(adapter);
        }

        if (fabAdd != null) {
            fabAdd.setText("Add Widget");
            fabAdd.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, BlockWidgetEditorActivity.class);
                intent.putExtra("extra_type", "widget");
                intent.putExtra("type", "widget");
                startActivity(intent);
            });
        }

        importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) importWidgetsFromUri(uri);
            }
        );

        exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
                if (uri != null) exportWidgetsToUri(uri);
            }
        );

        loadWidgets();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_manage_import_export, menu);
        int m3ColorOnSurface = com.google.android.material.color.MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK);

        if (menu != null) {
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (item.getIcon() != null) {
                    item.getIcon().setTint(m3ColorOnSurface);
                }
            }
        }

        Toolbar toolbarView = findViewById(R.id.toolbar);
        if (toolbarView != null && toolbarView.getNavigationIcon() != null) {
            toolbarView.getNavigationIcon().setTint(m3ColorOnSurface);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_import) {
            showImportOptionsDialog();
            return true;
        } else if (item.getItemId() == R.id.menu_export) {
            showExportOptionsDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showImportOptionsDialog() {
        String[] options = new String[]{"Import from JSON File", "Import from Clipboard"};
        new MaterialAlertDialogBuilder(this)
            .setTitle("Import Widgets")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    importLauncher.launch(new String[]{"application/json", "text/*", "*/*"});
                } else if (which == 1) {
                    importFromClipboard();
                }
            })
            .show();
    }

    private androidx.appcompat.app.AlertDialog showProgressDialog(String message) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_progress_material, null);
        TextView tvMessage = dialogView.findViewById(R.id.progress_message);
        if (tvMessage != null) tvMessage.setText(message);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create();
        dialog.show();
        return dialog;
    }

    private String getUniqueWidgetName(String baseName, List<HashMap<String, Object>> list) {
        if (baseName == null || baseName.trim().isEmpty() || "null".equalsIgnoreCase(baseName.trim())) baseName = "custom_widget";
        String candidate = baseName.trim();
        int counter = 1;
        while (isWidgetNameExists(candidate, list)) {
            candidate = baseName.trim() + "_" + counter;
            counter++;
        }
        return candidate;
    }

    private boolean isWidgetNameExists(String name, List<HashMap<String, Object>> list) {
        if (name == null) return false;
        for (HashMap<String, Object> w : list) {
            String wName = String.valueOf(w.get("name"));
            if (wName != null && wName.equalsIgnoreCase(name.trim())) return true;
        }
        return false;
    }

    private void importFromClipboard() {
        androidx.appcompat.app.AlertDialog progress = showProgressDialog("Importing widgets...");
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (text != null && !text.toString().trim().isEmpty()) {
                    String json = text.toString().trim();
                    List<HashMap<String, Object>> imported = new Gson().fromJson(json, new TypeToken<List<HashMap<String, Object>>>(){}.getType());
                    if (imported != null && !imported.isEmpty()) {
                        for (HashMap<String, Object> w : imported) {
                            String name = String.valueOf(w.get("name"));
                            if (name != null && !name.isEmpty() && !"null".equalsIgnoreCase(name)) {
                                if (isWidgetNameExists(name, widgets)) {
                                    String uniqueName = getUniqueWidgetName(name, widgets);
                                    w.put("name", uniqueName);
                                }
                                widgetRegistry.saveWidget(w);
                            }
                        }
                        loadWidgets();
                        Toast.makeText(this, "Imported " + imported.size() + " widgets from clipboard", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Invalid widget JSON in clipboard", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (progress != null && progress.isShowing()) progress.dismiss();
        }
    }

    private void showExportOptionsDialog() {
        String[] options = new String[]{"Export to JSON File", "Copy All to Clipboard"};
        new MaterialAlertDialogBuilder(this)
            .setTitle("Export All Widgets")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    exportLauncher.launch("widgets.json");
                } else if (which == 1) {
                    String json = new GsonBuilder().setPrettyPrinting().create().toJson(widgets);
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Widgets JSON", json));
                    Toast.makeText(this, "Copied all widgets JSON to clipboard", Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private void importWidgetsFromUri(Uri uri) {
        androidx.appcompat.app.AlertDialog progress = showProgressDialog("Importing widgets...");
        try (InputStream is = getContentResolver().openInputStream(uri);
             InputStreamReader reader = new InputStreamReader(is, "UTF-8")) {
            List<HashMap<String, Object>> imported = new Gson().fromJson(reader, new TypeToken<List<HashMap<String, Object>>>(){}.getType());
            if (imported != null && !imported.isEmpty()) {
                for (HashMap<String, Object> w : imported) {
                    String name = String.valueOf(w.get("name"));
                    if (name != null && !name.isEmpty() && !"null".equalsIgnoreCase(name)) {
                        if (isWidgetNameExists(name, widgets)) {
                            String uniqueName = getUniqueWidgetName(name, widgets);
                            w.put("name", uniqueName);
                        }
                        widgetRegistry.saveWidget(w);
                    }
                }
                loadWidgets();
                Toast.makeText(this, "Imported " + imported.size() + " widgets", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (progress != null && progress.isShowing()) progress.dismiss();
        }
    }

    private void exportWidgetsToUri(Uri uri) {
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(widgets);
            os.write(json.getBytes("UTF-8"));
            Toast.makeText(this, "Export successful", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadWidgets();
    }

    private void loadWidgets() {
        widgets.clear();
        if (widgetRegistry != null) {
            widgets.addAll(widgetRegistry.loadOnlyCustomWidgets());
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (tvEmpty != null) {
            tvEmpty.setVisibility(widgets.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private class WidgetsAdapter extends RecyclerView.Adapter<WidgetsAdapter.ViewHolder> {

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_custom_widget_def, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final HashMap<String, Object> widget = widgets.get(position);
            String name = String.valueOf(widget.get("name"));
            String tag = String.valueOf(widget.get("tag"));
            String cat = String.valueOf(widget.get("category"));

            holder.tvName.setText(name);
            holder.tvDetails.setText("<" + tag + "> | Category: " + cat);

            holder.btnEdit.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(ManageWidgetsActivity.this, BlockWidgetEditorActivity.class);
                intent.putExtra("extra_type", "widget");
                intent.putExtra("extra_id", name);
                intent.putExtra("type", "widget");
                intent.putExtra("name", name);
                startActivity(intent);
            });

            holder.btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(ManageWidgetsActivity.this)
                    .setTitle("Delete Widget")
                    .setMessage("Delete widget '" + name + "'?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (widgetRegistry != null) {
                            widgetRegistry.deleteWidget(name);
                            loadWidgets();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }

        @Override
        public int getItemCount() {
            return widgets.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDetails;
            View btnEdit, btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_widget_name);
                tvDetails = itemView.findViewById(R.id.tv_widget_details);
                btnEdit = itemView.findViewById(R.id.btn_edit_widget);
                btnDelete = itemView.findViewById(R.id.btn_delete_widget);
            }
        }
    }
}
