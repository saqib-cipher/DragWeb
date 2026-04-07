package sketchweb.gl;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

	private MaterialToolbar toolbar;
	private DrawerLayout drawer;
	private RecyclerView rvProjects;
	private TextView tvEmptyState;
	private FloatingActionButton fabNewProject;
	private ArrayList<Map<String, String>> projectList = new ArrayList<>();
	private ProjectListAdapter adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.home);
		initViews();
		setupToolbar();
		loadProjects();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadProjects();
	}

	private void initViews() {
		toolbar = findViewById(R.id._toolbar);
		drawer = findViewById(R.id._drawer);
		rvProjects = findViewById(R.id.rvProjects);
		tvEmptyState = findViewById(R.id.tvEmptyState);
		fabNewProject = findViewById(R.id.fabNewProject);

		rvProjects.setLayoutManager(new LinearLayoutManager(this));
		adapter = new ProjectListAdapter();
		rvProjects.setAdapter(adapter);

		fabNewProject.setOnClickListener(v -> showNewProjectDialog());
	}

	private void setupToolbar() {
		setSupportActionBar(toolbar);
		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			getSupportActionBar().setHomeButtonEnabled(true);
		}
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
			this, drawer, toolbar, R.string.app_name, R.string.app_name);
		drawer.addDrawerListener(toggle);
		toggle.syncState();
	}

	private void loadProjects() {
		projectList.clear();
		File dir = new File(getFilesDir(), "projects");
		if (dir.exists() && dir.isDirectory()) {
			File[] files = dir.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.getName().endsWith(".json")) {
						Map<String, String> project = new HashMap<>();
						String name = file.getName().replace(".json", "");
						project.put("name", name);
						project.put("path", file.getAbsolutePath());

						// Read project metadata
						File metaFile = new File(dir, name + ".meta");
						if (metaFile.exists()) {
							try {
								String metaJson = FileUtil.readFile(metaFile.getAbsolutePath());
								Map<String, String> meta = new Gson().fromJson(metaJson,
									new TypeToken<Map<String, String>>(){}.getType());
								if (meta != null) {
									if (meta.containsKey("description")) {
										project.put("description", meta.get("description"));
									}
									if (meta.containsKey("created")) {
										project.put("created", meta.get("created"));
									}
								}
							} catch (Exception e) {
								// ignore meta parse errors
							}
						}

						if (!project.containsKey("description")) {
							project.put("description", "Website project");
						}
						if (!project.containsKey("created")) {
							SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
							project.put("created", sdf.format(new Date(file.lastModified())));
						}
						projectList.add(project);
					}
				}
			}
		}

		updateEmptyState();
		adapter.notifyDataSetChanged();
	}

	private void updateEmptyState() {
		if (projectList.isEmpty()) {
			tvEmptyState.setVisibility(View.VISIBLE);
			rvProjects.setVisibility(View.GONE);
		} else {
			tvEmptyState.setVisibility(View.GONE);
			rvProjects.setVisibility(View.VISIBLE);
		}
	}

	private void showNewProjectDialog() {
		View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_project, null);
		TextInputEditText etName = dialogView.findViewById(R.id.etProjectName);
		TextInputEditText etDesc = dialogView.findViewById(R.id.etProjectDescription);

		new MaterialAlertDialogBuilder(this)
			.setTitle("New Project")
			.setView(dialogView)
			.setPositiveButton("Create", (dialog, which) -> {
				String name = etName.getText().toString().trim();
				String desc = etDesc.getText().toString().trim();

				if (name.isEmpty()) {
					Toast.makeText(this, "Please enter a project name", Toast.LENGTH_SHORT).show();
					return;
				}

				createProject(name, desc);
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void createProject(String name, String description) {
		File dir = new File(getFilesDir(), "projects");
		if (!dir.exists()) dir.mkdirs();

		// Save empty project file
		File projectFile = new File(dir, name + ".json");
		FileUtil.writeFile(projectFile.getAbsolutePath(), "[]");

		// Save metadata
		File metaFile = new File(dir, name + ".meta");
		Map<String, String> meta = new HashMap<>();
		meta.put("description", description.isEmpty() ? "Website project" : description);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
		meta.put("created", sdf.format(new Date()));
		FileUtil.writeFile(metaFile.getAbsolutePath(), new Gson().toJson(meta));

		openProject(name);
	}

	private void openProject(String projectName) {
		Intent intent = new Intent(this, MainActivity.class);
		intent.putExtra("project_name", projectName);
		startActivity(intent);
	}

	private void deleteProject(String projectName) {
		new MaterialAlertDialogBuilder(this)
			.setTitle("Delete Project")
			.setMessage("Are you sure you want to delete \"" + projectName + "\"? This cannot be undone.")
			.setPositiveButton("Delete", (dialog, which) -> {
				File dir = new File(getFilesDir(), "projects");
				File projectFile = new File(dir, projectName + ".json");
				File metaFile = new File(dir, projectName + ".meta");
				if (projectFile.exists()) projectFile.delete();
				if (metaFile.exists()) metaFile.delete();

				// Also delete export files
				File exportDir = new File(getFilesDir(), "exports/" + projectName.replaceAll("[^a-zA-Z0-9._-]", "_"));
				if (exportDir.exists()) {
					FileUtil.deleteFile(exportDir.getAbsolutePath());
				}

				loadProjects();
				Toast.makeText(this, "Project deleted", Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	@Override
	public void onBackPressed() {
		if (drawer.isDrawerOpen(GravityCompat.START)) {
			drawer.closeDrawer(GravityCompat.START);
		} else {
			super.onBackPressed();
		}
	}

	// ---- Adapter ----

	class ProjectListAdapter extends RecyclerView.Adapter<ProjectListAdapter.VH> {

		@Override
		public VH onCreateViewHolder(ViewGroup parent, int viewType) {
			View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_project, parent, false);
			return new VH(v);
		}

		@Override
		public void onBindViewHolder(VH holder, int position) {
			Map<String, String> project = projectList.get(position);
			holder.tvName.setText(project.get("name"));
			holder.tvDesc.setText(project.getOrDefault("description", ""));
			holder.tvDate.setText("Created: " + project.getOrDefault("created", ""));

			holder.itemView.setOnClickListener(v -> openProject(project.get("name")));
			holder.btnMenu.setOnClickListener(v -> deleteProject(project.get("name")));
		}

		@Override
		public int getItemCount() {
			return projectList.size();
		}

		class VH extends RecyclerView.ViewHolder {
			TextView tvName, tvDesc, tvDate;
			ImageView btnMenu;

			VH(View v) {
				super(v);
				tvName = v.findViewById(R.id.tvProjectName);
				tvDesc = v.findViewById(R.id.tvProjectDescription);
				tvDate = v.findViewById(R.id.tvProjectDate);
				btnMenu = v.findViewById(R.id.btnProjectMenu);
			}
		}
	}
}
