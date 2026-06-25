package sketchweb.gl;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditorSettingsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private Spinner spinnerFontSize;
    private MaterialSwitch switchSuggestions;
    private Button btnExport;
    private Button btnImport;
    private EditText etDisplay;
    private EditText etInsert;
    private Button btnAdd;
    private RecyclerView rvSuggestions;

    private List<Map<String, String>> suggestionsList = new ArrayList<>();
    private SuggestionsAdapter adapter;
    private SharedPreferences prefs;
    private ActivityResultLauncher<Intent> importLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_editor_settings);

        prefs = getSharedPreferences("EditorSettings", MODE_PRIVATE);

        initViews();
        setupPreferences();
        loadSuggestions();
        setupImportExport();

        // Edge-to-edge window inset padding
        View rootLayout = findViewById(R.id.root_layout);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));
                return insets;
            });
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        spinnerFontSize = findViewById(R.id.spinnerFontSize);
        switchSuggestions = findViewById(R.id.switchSuggestions);
        btnExport = findViewById(R.id.btnExport);
        btnImport = findViewById(R.id.btnImport);
        etDisplay = findViewById(R.id.etDisplay);
        etInsert = findViewById(R.id.etInsert);
        btnAdd = findViewById(R.id.btnAdd);
        rvSuggestions = findViewById(R.id.rvSuggestions);

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        rvSuggestions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SuggestionsAdapter();
        rvSuggestions.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> addSuggestion());
    }

    private void setupPreferences() {
        String[] fontSizes = {"12sp", "14sp", "16sp", "18sp", "20sp", "22sp"};
        ArrayAdapter<String> fontAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, fontSizes);
        fontAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFontSize.setAdapter(fontAdapter);

        int savedSize = prefs.getInt("editor_font_size", 14);
        int selectedPosition = 1; // default to 14sp
        for (int i = 0; i < fontSizes.length; i++) {
            if (fontSizes[i].equals(savedSize + "sp")) {
                selectedPosition = i;
                break;
            }
        }
        spinnerFontSize.setSelection(selectedPosition);

        spinnerFontSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String val = fontSizes[position].replace("sp", "");
                try {
                    int size = Integer.parseInt(val);
                    prefs.edit().putInt("editor_font_size", size).apply();
                } catch (NumberFormatException ignored) {}
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        boolean showSuggestions = prefs.getBoolean("editor_show_suggestions", true);
        switchSuggestions.setChecked(showSuggestions);
        switchSuggestions.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("editor_show_suggestions", isChecked).apply();
        });
    }

    private void loadSuggestions() {
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dragweb/suggestions.json");
        if (!file.exists()) {
            writeDefaultSuggestions(file);
        }
        try {
            String json = FileUtil.readFile(file.getAbsolutePath());
            List<Map<String, String>> loaded = new Gson().fromJson(json,
                    new TypeToken<List<Map<String, String>>>(){}.getType());
            if (loaded != null) {
                suggestionsList.clear();
                suggestionsList.addAll(loaded);
                adapter.notifyDataSetChanged();
            }
        } catch (Exception e) {
            Log.e("EditorSettingsActivity", "Failed to load suggestions list", e);
        }
    }

    private void writeDefaultSuggestions(File file) {
        String defaultJson = "[\n" +
                "  { \"display\": \"<\", \"insert\": \"<\" },\n" +
                "  { \"display\": \">\", \"insert\": \">\" },\n" +
                "  { \"display\": \"/\", \"insert\": \"/\" },\n" +
                "  { \"display\": \"\\\"\", \"insert\": \"\\\"\" },\n" +
                "  { \"display\": \"'\", \"insert\": \"'\" },\n" +
                "  { \"display\": \"=\", \"insert\": \"=\" },\n" +
                "  { \"display\": \"{\", \"insert\": \"{\" },\n" +
                "  { \"display\": \"}\", \"insert\": \"}\" },\n" +
                "  { \"display\": \"(\", \"insert\": \"(\" },\n" +
                "  { \"display\": \")\", \"insert\": \")\" },\n" +
                "  { \"display\": \"[\", \"insert\": \"[\" },\n" +
                "  { \"display\": \"]\", \"insert\": \"]\" },\n" +
                "  { \"display\": \";\", \"insert\": \";\" },\n" +
                "  { \"display\": \".\", \"insert\": \".\" },\n" +
                "  { \"display\": \":\", \"insert\": \":\" },\n" +
                "  { \"display\": \"!\", \"insert\": \"!\" },\n" +
                "  { \"display\": \"-\", \"insert\": \"-\" },\n" +
                "  { \"display\": \"_\", \"insert\": \"_\" },\n" +
                "  { \"display\": \"+\", \"insert\": \"+\" }\n" +
                "]";
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileUtil.writeFile(file.getAbsolutePath(), defaultJson);
        } catch (Exception e) {
            Log.e("EditorSettingsActivity", "Failed to write default suggestions file", e);
        }
    }

    private void saveSuggestions() {
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dragweb/suggestions.json");
        try {
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(suggestionsList);
            FileUtil.writeFile(file.getAbsolutePath(), json);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save suggestions: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void addSuggestion() {
        String display = etDisplay.getText().toString().trim();
        String insert = etInsert.getText().toString();

        if (display.isEmpty()) {
            Toast.makeText(this, "Display label cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> item = new HashMap<>();
        item.put("display", display);
        item.put("insert", insert);

        suggestionsList.add(item);
        adapter.notifyItemInserted(suggestionsList.size() - 1);
        saveSuggestions();

        etDisplay.setText("");
        etInsert.setText("");
    }

    private void setupImportExport() {
        btnExport.setOnClickListener(v -> {
            try {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File exportFile = new File(downloadsDir, "dragweb_suggestions.json");
                String json = new GsonBuilder().setPrettyPrinting().create().toJson(suggestionsList);
                FileUtil.writeFile(exportFile.getAbsolutePath(), json);
                Toast.makeText(this, "Exported to: " + exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        importLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            try (InputStream is = getContentResolver().openInputStream(uri);
                                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line);
                                }
                                String json = sb.toString();
                                List<Map<String, String>> imported = new Gson().fromJson(json,
                                        new TypeToken<List<Map<String, String>>>(){}.getType());
                                if (imported != null) {
                                    suggestionsList.clear();
                                    suggestionsList.addAll(imported);
                                    adapter.notifyDataSetChanged();
                                    saveSuggestions();
                                    Toast.makeText(this, "Suggestions imported successfully", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, "Invalid JSON file", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );

        btnImport.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/json");
            importLauncher.launch(Intent.createChooser(intent, "Select suggestions JSON"));
        });
    }

    private class SuggestionsAdapter extends RecyclerView.Adapter<SuggestionsAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_suggestion_setting, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, String> item = suggestionsList.get(position);
            holder.tvDisplay.setText(item.get("display"));
            holder.tvInsert.setText(item.get("insert"));
            holder.btnDelete.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    suggestionsList.remove(pos);
                    notifyItemRemoved(pos);
                    saveSuggestions();
                }
            });
        }

        @Override
        public int getItemCount() {
            return suggestionsList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDisplay;
            TextView tvInsert;
            ImageButton btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                tvDisplay = itemView.findViewById(R.id.tvDisplay);
                tvInsert = itemView.findViewById(R.id.tvInsert);
                btnDelete = itemView.findViewById(R.id.btnDelete);
            }
        }
    }
}
