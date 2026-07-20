package sketchweb.gl;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

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

        loadWidgets();
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
