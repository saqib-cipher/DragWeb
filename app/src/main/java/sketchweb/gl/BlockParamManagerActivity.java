package sketchweb.gl;

import static androidx.activity.EdgeToEdge.*;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BlockParamManagerActivity extends AppCompatActivity {

    private BlockParamTypeManager manager;
    private RecyclerView recyclerView;
    private ParamTypeAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_block_param_manager);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ParamTypeAdapter();
        recyclerView.setAdapter(adapter);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        ExtendedFloatingActionButton fab = findViewById(R.id.addFab);
        fab.setOnClickListener(v -> showAddTypeDialog());

        // Ensure param.json exists in storage before loading manager
        java.io.File paramFile = new java.io.File(CustomStorageUtil.getCustomDir(this), "param.json");
        if (!paramFile.exists() || paramFile.length() == 0) {
            try (java.io.InputStream is = getAssets().open("param.json");
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(paramFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                fos.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        manager = new BlockParamTypeManager(this);
        refreshList();
    }

    private void refreshList() {
        Map<String, List<String>> all = manager.getAllParamTypes();
        adapter.setItems(new ArrayList<>(all.entrySet()));
    }

    private void showAddTypeDialog() {
        new UniversalM3Dialog(this)
            .setTitle("Add Parameter Type")
            .setHint("Type name (e.g. position)")
            .showTextInput(name -> {
                if (!name.trim().isEmpty()) {
                    manager.setOptions(name.trim(), new ArrayList<>());
                    refreshList();
                }
            });
    }

    private void showEditOptionsDialog(String typeName) {
        List<String> options = new ArrayList<>(manager.getOptions(typeName));
        
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        OptionsAdapter optionsAdapter = new OptionsAdapter(typeName, options);
        rv.setAdapter(optionsAdapter);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Edit Options for " + typeName)
            .setView(rv)
            .setPositiveButton("Add Option", (d, w) -> showAddOptionDialog(typeName))
            .setNeutralButton("Done", (d, w) -> {
                manager.setOptions(typeName, optionsAdapter.getOptions());
                refreshList();
            })
            .show();
    }

    private void showAddOptionDialog(String typeName) {
        new UniversalM3Dialog(this)
            .setTitle("Add Option to " + typeName)
            .setHint("Option value (e.g. relative)")
            .showTextInput(val -> {
                if (!val.trim().isEmpty()) {
                    manager.addOption(typeName, val.trim());
                    refreshList();
                    showEditOptionsDialog(typeName);
                }
            });
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
            holder.text1.setText(item.getKey());
            holder.text2.setText(String.join(", ", item.getValue()));
            holder.itemView.setOnClickListener(v -> showEditOptionsDialog(item.getKey()));
            holder.itemView.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(BlockParamManagerActivity.this)
                    .setTitle("Delete Type?")
                    .setMessage("Are you sure you want to delete '" + item.getKey() + "'?")
                    .setPositiveButton("Delete", (d, w) -> {
                        manager.getAllParamTypes().remove(item.getKey());
                        manager.save();
                        refreshList();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView text1, text2;
            VH(View v) {
                super(v);
                text1 = v.findViewById(R.id.text1);
                text2 = v.findViewById(R.id.text2);
            }
        }
    }

    private class OptionsAdapter extends RecyclerView.Adapter<OptionsAdapter.VH> {
        private String typeName;
        private List<String> options;

        OptionsAdapter(String typeName, List<String> options) {
            this.typeName = typeName;
            this.options = options;
        }

        public List<String> getOptions() { return options; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String opt = options.get(position);
            holder.text.setText(opt);
            holder.itemView.setOnClickListener(v -> {
                new UniversalM3Dialog(BlockParamManagerActivity.this)
                    .setTitle("Edit Option")
                    .setInitialValue(opt)
                    .showTextInput(newVal -> {
                        if (!newVal.trim().isEmpty()) {
                            options.set(position, newVal.trim());
                            manager.setOptions(typeName, options);
                            notifyDataSetChanged();
                            refreshList();
                        }
                    });
            });
            holder.itemView.setOnLongClickListener(v -> {
                options.remove(position);
                manager.setOptions(typeName, options);
                notifyDataSetChanged();
                refreshList();
                return true;
            });
        }

        @Override
        public int getItemCount() { return options.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView text;
            VH(View v) { super(v); text = v.findViewById(android.R.id.text1); }
        }
    }
}
