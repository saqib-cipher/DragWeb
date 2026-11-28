package glab.dragweb.activities;

import glab.dragweb.R;
import glab.dragweb.logic.BlockParamTypeManager;
import glab.dragweb.ui.AppDialogs;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BlockParamManagerActivity extends AppCompatActivity {

    private BlockParamTypeManager manager;
    private RecyclerView recyclerView;
    private ParamTypeAdapter adapter;
    private View layoutEmptyState;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;
    private TextInputEditText etSearchParams;
    private String currentQuery = "";
    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<String> exportLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_block_param_manager);

        View root = findViewById(R.id.rootParamManager);
        ExtendedFloatingActionButton fab = findViewById(R.id.addFab);
        int baseFabBottomMargin = ((ViewGroup.MarginLayoutParams) fab.getLayoutParams()).bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, 0);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
            lp.bottomMargin = baseFabBottomMargin + sysBars.bottom;
            fab.setLayoutParams(lp);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_import) {
                importLauncher.launch(new String[]{"application/json", "text/*", "*/*"});
                return true;
            } else if (id == R.id.menu_export) {
                if (manager.getCustomParamTypes().isEmpty()) {
                    Toast.makeText(this, "No custom parameter types to export", Toast.LENGTH_SHORT).show();
                } else {
                    exportLauncher.launch("param.json");
                }
                return true;
            }
            return false;
        });

        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) handleImportUri(uri);
                }
        );

        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri != null) handleExportUri(uri);
                }
        );

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ParamTypeAdapter();
        recyclerView.setAdapter(adapter);

        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);
        etSearchParams = findViewById(R.id.etSearchParams);

        if (etSearchParams != null) {
            etSearchParams.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentQuery = s != null ? s.toString().trim().toLowerCase(Locale.US) : "";
                    refreshList();
                }
                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        fab.setOnClickListener(v -> showAddTypeDialog());

        manager = new BlockParamTypeManager(this);
        refreshList();
    }

    private void refreshList() {
        Map<String, List<String>> all = manager.getCustomParamTypes();
        List<Map.Entry<String, List<String>>> filtered = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : all.entrySet()) {
            if (currentQuery.isEmpty()) {
                filtered.add(entry);
            } else {
                String key = entry.getKey() != null ? entry.getKey().toLowerCase(Locale.US) : "";
                String joined = String.join(" ", entry.getValue()).toLowerCase(Locale.US);
                if (key.contains(currentQuery) || joined.contains(currentQuery)) {
                    filtered.add(entry);
                }
            }
        }
        adapter.setItems(filtered);

        boolean empty = filtered.isEmpty();
        layoutEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            if (!currentQuery.isEmpty()) {
                tvEmptyTitle.setText("No matching parameter types");
                tvEmptySubtitle.setText("Try a different search query or clear the filter.");
            } else {
                tvEmptyTitle.setText("No custom parameter types");
                tvEmptySubtitle.setText("Create reusable dropdown parameter types for your custom blocks.");
            }
        }
    }

    private void showAddTypeDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_param_type, null);
        TextInputLayout tilName = dialogView.findViewById(R.id.tilParamTypeName);
        TextInputEditText etName = dialogView.findViewById(R.id.etParamTypeName);
        TextInputEditText etInitialOptions = dialogView.findViewById(R.id.etInitialOptions);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Add Parameter Type")
                .setView(dialogView)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                if (name.isEmpty()) {
                    tilName.setError("Enter a parameter type name");
                    return;
                }
                if (manager.getCustomParamTypes().containsKey(name)) {
                    tilName.setError("This parameter type already exists");
                    return;
                }
                tilName.setError(null);

                List<String> initialList = new ArrayList<>();
                String rawOpts = etInitialOptions.getText() != null ? etInitialOptions.getText().toString() : "";
                if (!rawOpts.trim().isEmpty()) {
                    for (String part : rawOpts.split(",")) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty() && !initialList.contains(trimmed)) {
                            initialList.add(trimmed);
                        }
                    }
                }
                manager.setOptions(name, initialList);
                refreshList();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void showEditOptionsDialog(String typeName) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_param_options, null);
        TextInputLayout tilNewOption = dialogView.findViewById(R.id.tilNewOption);
        TextInputEditText etNewOption = dialogView.findViewById(R.id.etNewOption);
        MaterialButton btnAddInline = dialogView.findViewById(R.id.btnAddOptionInline);
        TextView tvNoOptions = dialogView.findViewById(R.id.tvNoOptions);
        RecyclerView rvOptions = dialogView.findViewById(R.id.rvOptions);

        List<String> options = new ArrayList<>(manager.getOptions(typeName));
        rvOptions.setLayoutManager(new LinearLayoutManager(this));

        Runnable updateEmptyOptions = () -> {
            boolean isEmpty = options.isEmpty();
            tvNoOptions.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            rvOptions.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        };

        OptionsAdapter optionsAdapter = new OptionsAdapter(typeName, options, updateEmptyOptions);
        rvOptions.setAdapter(optionsAdapter);
        updateEmptyOptions.run();

        Runnable addInlineAction = () -> {
            String val = etNewOption.getText() != null ? etNewOption.getText().toString().trim() : "";
            if (val.isEmpty()) {
                tilNewOption.setError("Enter an option value");
                return;
            }
            if (options.contains(val)) {
                tilNewOption.setError("Option already exists");
                return;
            }
            tilNewOption.setError(null);
            options.add(val);
            manager.setOptions(typeName, options);
            optionsAdapter.notifyItemInserted(options.size() - 1);
            updateEmptyOptions.run();
            etNewOption.setText("");
            refreshList();
        };

        btnAddInline.setOnClickListener(v -> addInlineAction.run());
        etNewOption.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addInlineAction.run();
                return true;
            }
            return false;
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle("Options: " + typeName)
                .setView(dialogView)
                .setPositiveButton("Done", (d, w) -> {
                    manager.setOptions(typeName, options);
                    refreshList();
                })
                .show();
    }

    private void confirmDeleteType(String typeName) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Parameter Type?")
                .setMessage("Delete '" + typeName + "' and all of its options?")
                .setPositiveButton("Delete", (d, w) -> {
                    manager.deleteCustomParamType(typeName);
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleExportUri(Uri uri) {
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) throw new IllegalStateException("Cannot open output stream");
            String json = manager.exportCustomParamTypesJson();
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.flush();
            Toast.makeText(this, "Exported parameter types", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleImportUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            Map<String, List<String>> parsed = new Gson().fromJson(
                    sb.toString(),
                    new TypeToken<Map<String, List<String>>>() {}.getType()
            );
            if (parsed == null || parsed.isEmpty()) {
                Toast.makeText(this, "No valid parameter types found in file", Toast.LENGTH_SHORT).show();
                return;
            }
            if (manager.getCustomParamTypes().isEmpty()) {
                int count = manager.importCustomParamTypes(parsed, false);
                refreshList();
                Toast.makeText(this, "Imported " + count + " parameter type(s)", Toast.LENGTH_SHORT).show();
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Import Parameter Types")
                        .setMessage("Found " + parsed.size() + " parameter type(s). Merge with existing types or replace all?")
                        .setPositiveButton("Merge", (d, w) -> {
                            int count = manager.importCustomParamTypes(parsed, false);
                            refreshList();
                            Toast.makeText(this, "Imported " + count + " parameter type(s)", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Replace All", (d, w) -> {
                            int count = manager.importCustomParamTypes(parsed, true);
                            refreshList();
                            Toast.makeText(this, "Replaced with " + count + " parameter type(s)", Toast.LENGTH_SHORT).show();
                        })
                        .setNeutralButton("Cancel", null)
                        .show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private class ParamTypeAdapter extends RecyclerView.Adapter<ParamTypeAdapter.VH> {
        private List<Map.Entry<String, List<String>>> items = new ArrayList<>();

        public void setItems(List<Map.Entry<String, List<String>>> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.param_list, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map.Entry<String, List<String>> item = items.get(position);
            String typeName = item.getKey();
            List<String> opts = item.getValue() != null ? item.getValue() : new ArrayList<>();

            holder.text1.setText(typeName);
            holder.tvOptionCount.setText(opts.size() == 1 ? "1 option" : opts.size() + " options");
            holder.text2.setText(opts.isEmpty() ? "No options added yet" : String.join(", ", opts));

            holder.itemView.setOnClickListener(v -> showEditOptionsDialog(typeName));
            holder.btnEditType.setOnClickListener(v -> showEditOptionsDialog(typeName));
            holder.btnDeleteType.setOnClickListener(v -> confirmDeleteType(typeName));
            holder.itemView.setOnLongClickListener(v -> {
                confirmDeleteType(typeName);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView text1, text2, tvOptionCount;
            MaterialButton btnEditType, btnDeleteType;

            VH(View v) {
                super(v);
                text1 = v.findViewById(R.id.text1);
                text2 = v.findViewById(R.id.text2);
                tvOptionCount = v.findViewById(R.id.tvOptionCount);
                btnEditType = v.findViewById(R.id.btnEditType);
                btnDeleteType = v.findViewById(R.id.btnDeleteType);
            }
        }
    }

    private class OptionsAdapter extends RecyclerView.Adapter<OptionsAdapter.VH> {
        private final String typeName;
        private final List<String> options;
        private final Runnable onListChanged;

        OptionsAdapter(String typeName, List<String> options, Runnable onListChanged) {
            this.typeName = typeName;
            this.options = options;
            this.onListChanged = onListChanged;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_param_option, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String opt = options.get(position);
            holder.tvOptionText.setText(opt);

            View.OnClickListener editClick = v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                String current = options.get(pos);
                AppDialogs.builder(BlockParamManagerActivity.this)
                        .setTitle("Edit Option")
                        .setHint("Option value")
                        .setInitialValue(current)
                        .showTextInput(newVal -> {
                            String trimmed = newVal.trim();
                            if (!trimmed.isEmpty()) {
                                options.set(pos, trimmed);
                                manager.setOptions(typeName, options);
                                notifyItemChanged(pos);
                                refreshList();
                            }
                        });
            };

            holder.itemView.setOnClickListener(editClick);
            holder.btnEditOption.setOnClickListener(editClick);
            holder.btnDeleteOption.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                options.remove(pos);
                manager.setOptions(typeName, options);
                notifyItemRemoved(pos);
                onListChanged.run();
                refreshList();
            });
        }

        @Override
        public int getItemCount() {
            return options.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvOptionText;
            MaterialButton btnEditOption, btnDeleteOption;

            VH(View v) {
                super(v);
                tvOptionText = v.findViewById(R.id.tvOptionText);
                btnEditOption = v.findViewById(R.id.btnEditOption);
                btnDeleteOption = v.findViewById(R.id.btnDeleteOption);
            }
        }
    }
}
