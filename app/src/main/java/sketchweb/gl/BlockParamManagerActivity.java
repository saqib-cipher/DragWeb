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
        manager = new BlockParamTypeManager();
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new ParamTypeAdapter();
        recyclerView.setAdapter(adapter);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        
        ExtendedFloatingActionButton fab = findViewById(R.id.addFab);
        fab.setOnClickListener(v -> showAddTypeDialog());

        refreshList();
    }

    private void refreshList() {
        Map<String, List<String>> all = manager.getAllParamTypes();
        adapter.setItems(new ArrayList<>(all.entrySet()));
    }

    private void showAddTypeDialog() {
        EditText input = new EditText(this);
        input.setHint("Type name (e.g. position)");
        
        new MaterialAlertDialogBuilder(this)
            .setTitle("Add Parameter Type")
            .setView(input)
            .setPositiveButton("Add", (d, w) -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) {
                    manager.addOption(name, ""); // Initialize type
                    manager.removeOption(name, ""); // Clean up placeholder
                    refreshList();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showEditOptionsDialog(String typeName) {
        List<String> options = new ArrayList<>(manager.getOptions(typeName));
        
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        OptionsAdapter optionsAdapter = new OptionsAdapter(options);
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
        EditText input = new EditText(this);
        input.setHint("Option value (e.g. relative)");

        new MaterialAlertDialogBuilder(this)
            .setTitle("Add Option to " + typeName)
            .setView(input)
            .setPositiveButton("Add", (d, w) -> {
                String val = input.getText().toString().trim();
                if (!val.isEmpty()) {
                    manager.addOption(typeName, val);
                    showEditOptionsDialog(typeName); // Re-open edit dialog
                }
            })
            .setNegativeButton("Cancel", (d, w) -> showEditOptionsDialog(typeName))
            .show();
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
        private List<String> options;

        OptionsAdapter(List<String> options) {
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
                EditText input = new EditText(BlockParamManagerActivity.this);
                input.setText(opt);
                new MaterialAlertDialogBuilder(BlockParamManagerActivity.this)
                    .setTitle("Edit Option")
                    .setView(input)
                    .setPositiveButton("Save", (d, w) -> {
                        options.set(position, input.getText().toString().trim());
                        notifyDataSetChanged();
                    })
                    .setNegativeButton("Delete", (d, w) -> {
                        options.remove(position);
                        notifyDataSetChanged();
                    })
                    .show();
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
