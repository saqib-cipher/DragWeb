package sketchweb.gl;

import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class LogicBlockActivity extends AppCompatActivity {

    private LogicBlockManager logicBlockManager;
    private BlockDragDropManager blockDragDropManager;
    private LinearLayout blockEditorContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logic_blocks);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        blockEditorContainer = findViewById(R.id.blockEditorContainer);

        // Load project logic blocks
        String projectId = getIntent().getStringExtra("project_id");
        if (projectId == null || projectId.isEmpty()) {
            projectId = "default";
        }

        logicBlockManager = new LogicBlockManager(this);
        // We will load the logic json from the file
        java.io.File dir = new java.io.File(getFilesDir(), "projects");
        java.io.File logicFile = new java.io.File(dir, projectId + ".logic");
        if (logicFile.exists()) {
            String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
            logicBlockManager.fromJson(logicJson);
        }

        blockDragDropManager = new BlockDragDropManager(this, logicBlockManager);
        blockDragDropManager.setOnBlocksChangedListener(() -> {
            // Save logic blocks to file
            String json = logicBlockManager.toJson();
            FileUtil.writeFile(logicFile.getAbsolutePath(), json);
        });

        blockEditorContainer.addView(blockDragDropManager.buildBlockEditorView());
    }
}
