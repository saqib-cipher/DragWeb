package sketchweb.gl;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import java.io.InputStream;
import java.io.OutputStream;

import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class HomeActivity extends AppCompatActivity {

	private MaterialToolbar toolbar;
	private DrawerLayout drawer;
	private RecyclerView rvProjects;
	private TextView tvEmptyState;
	private FloatingActionButton fabNewProject;
	private ArrayList<Map<String, String>> projectList = new ArrayList<>();
	private ProjectListAdapter adapter;

	private ActivityResultLauncher<String> backupLauncher;
	private ActivityResultLauncher<String[]> importZipLauncher;
	private ActivityResultLauncher<String> backupSingleLauncher;

	private ActivityResultLauncher<Intent> logoPickerLauncher;
	private String pendingBackupProject = null;
	private String pendingLogoProjectId = null;
	private String pendingLogoProjectName = null;
	private String pendingLogoProjectDesc = null;
	private ActivityResultLauncher<String[]> projectLogoLauncher;
	private String pendingBackupProject = null;
	private Uri pendingProjectLogoUri = null;
	private TextView pendingProjectLogoLabel = null;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.home);
		initViews();
		setupToolbar();
		ensureExternalDirectories();
		loadProjects();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadProjects();
	}

	private void ensureExternalDirectories() {
		try {
			String basePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dragweb";
			new File(basePath + "/projects").mkdirs();
			new File(basePath + "/custom").mkdirs();
		} catch (Exception e) {
			Log.w("HomeActivity", "Could not create external dirs: " + e.getMessage());
		}
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

		backupLauncher = registerForActivityResult(
			new ActivityResultContracts.CreateDocument("application/zip"),
			uri -> {
				if (uri != null) {
					performBackup(uri);
				}
			}
		);

		backupSingleLauncher = registerForActivityResult(
			new ActivityResultContracts.CreateDocument("application/zip"),
			uri -> {
				if (uri != null && pendingBackupProject != null) {
					performSingleProjectBackup(uri, pendingBackupProject);
					pendingBackupProject = null;
				}
			}
		);

		importZipLauncher = registerForActivityResult(
			new ActivityResultContracts.OpenDocument(),
			uri -> {
				if (uri != null) {
					performImport(uri);
				}
			}
		);


		logoPickerLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == RESULT_OK && result.getData() != null) {
					Uri logoUri = result.getData().getData();
					if (logoUri != null && pendingLogoProjectId != null) {
						saveLogoAndCreateProject(pendingLogoProjectId, pendingLogoProjectName,
							pendingLogoProjectDesc, logoUri);
					}
				} else if (pendingLogoProjectId != null) {
					// User skipped logo selection, create without logo
					createProjectInternal(pendingLogoProjectId, pendingLogoProjectName,
						pendingLogoProjectDesc);
				}
				pendingLogoProjectId = null;
				pendingLogoProjectName = null;
				pendingLogoProjectDesc = null;

		projectLogoLauncher = registerForActivityResult(
			new ActivityResultContracts.OpenDocument(),
			uri -> {
				if (uri != null) {
					pendingProjectLogoUri = uri;
					if (pendingProjectLogoLabel != null) {
						pendingProjectLogoLabel.setText("Logo selected");
					}
				}

			}
		);

		LinearLayout menuMyProjects = findViewById(R.id.menuMyProjects);
		LinearLayout menuAbout = findViewById(R.id.menuAbout);
		LinearLayout menuBackup = findViewById(R.id.menuBackup);
		LinearLayout menuImport = findViewById(R.id.menuImport);

		if (menuMyProjects != null) {
			menuMyProjects.setOnClickListener(v -> {
				drawer.closeDrawer(GravityCompat.START);
			});
		}
		if (menuAbout != null) {
			menuAbout.setOnClickListener(v -> {
				drawer.closeDrawer(GravityCompat.START);
				showAboutDialog();
			});
		}
		if (menuBackup != null) {
			menuBackup.setOnClickListener(v -> {
				drawer.closeDrawer(GravityCompat.START);
				backupAllProjects();
			});
		}
		if (menuImport != null) {
			menuImport.setOnClickListener(v -> {
				drawer.closeDrawer(GravityCompat.START);
				importProject();
			});
		}
	}

	private String generateProjectId() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private void backupAllProjects() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
		String filename = "DragWeb_Backup_" + sdf.format(new Date()) + ".zip";
		backupLauncher.launch(filename);
	}

	private void backupSingleProject(String projectId) {
		pendingBackupProject = projectId;
		String projectName = projectId;
		for (Map<String, String> p : projectList) {
			if (projectId.equals(p.get("id"))) {
				projectName = p.getOrDefault("name", projectId);
				break;
			}
		}
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
		String filename = "DragWeb_" + projectName + "_" + sdf.format(new Date()) + ".zip";
		backupSingleLauncher.launch(filename);
	}

	private void importProject() {
		importZipLauncher.launch(new String[]{"application/zip"});
	}

	private void performBackup(Uri uri) {
		ProjectDataManager pdm = new ProjectDataManager(this);
		boolean success = pdm.exportAllProjectsAsZip(uri);
		if (success) {
			Toast.makeText(this, "Backup successful", Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(this, "Backup failed", Toast.LENGTH_LONG).show();
		}
		File dir = new File(getFilesDir(), "projects");
		if (!dir.exists() || dir.listFiles() == null || dir.listFiles().length == 0) {
			Toast.makeText(this, "No projects to backup", Toast.LENGTH_SHORT).show();
			return;
		}
		ProjectDataManager pdm = new ProjectDataManager(this);
		boolean ok = pdm.exportAllProjectsAsZip(uri);
		Toast.makeText(this, ok ? "Backup successful" : "Backup failed", Toast.LENGTH_SHORT).show();
	}

	private void performSingleProjectBackup(Uri uri, String projectId) {
		ProjectDataManager pdm = new ProjectDataManager(this);
		boolean success = pdm.exportProjectAsZip(uri, projectId);
		if (success) {
			Toast.makeText(this, "Project backup successful", Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(this, "Backup failed", Toast.LENGTH_LONG).show();
		}
		boolean ok = pdm.exportSingleProjectAsZip(projectId, uri);
		Toast.makeText(this, ok ? "Project backup successful" : "Backup failed", Toast.LENGTH_SHORT).show();
	}

	private void performImport(Uri uri) {
		ProjectDataManager pdm = new ProjectDataManager(this);
		List<String> importedIds = pdm.importProjectsFromZip(uri);

		if (!importedIds.isEmpty()) {
			loadProjects();
			Toast.makeText(this, importedIds.size() + " project(s) imported successfully",
				Toast.LENGTH_SHORT).show();

			// Auto-open the first imported project if only one
			if (importedIds.size() == 1) {
				String pid = importedIds.get(0);
				String pName = pid;
				for (Map<String, String> p : projectList) {
					if (pid.equals(p.get("id"))) {
						pName = p.getOrDefault("name", pid);
						break;
					}
				}
				openProject(pid, pName);
			}
		} else {
			// Fallback: reload projects in case the ZIP had a legacy format
			loadProjects();
			Toast.makeText(this, "Projects imported", Toast.LENGTH_SHORT).show();
		ProjectDataManager.ImportResult result = pdm.importProjectsFromZip(uri);
		if (!result.success) {
			Toast.makeText(this, "Import failed: " + result.message, Toast.LENGTH_LONG).show();
			return;
		}

		loadProjects();
		Toast.makeText(this, "Projects imported successfully", Toast.LENGTH_SHORT).show();

		// Auto-load after import when there is a clear target project.
		if (result.importedProjectIds.size() == 1) {
			String projectId = result.importedProjectIds.iterator().next();
			String projectName = projectId;
			for (Map<String, String> p : projectList) {
				if (projectId.equals(p.get("id"))) {
					projectName = p.getOrDefault("name", projectId);
					break;
				}
			}
			openProject(projectId, projectName);
		}
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
						String fileId = file.getName().replace(".json", "");
						if (fileId.contains("_")) {
							continue;
						}
						Map<String, String> project = new HashMap<>();
						project.put("id", fileId);
						project.put("path", file.getAbsolutePath());

						File metaFile = new File(dir, fileId + ".meta");
						if (metaFile.exists()) {
							try {
								String metaJson = FileUtil.readFile(metaFile.getAbsolutePath());
								Map<String, String> meta = new Gson().fromJson(metaJson,
									new TypeToken<Map<String, String>>(){}.getType());
								if (meta != null) {
									if (meta.containsKey("name")) {
										project.put("name", meta.get("name"));
									}
									if (meta.containsKey("description")) {
										project.put("description", meta.get("description"));
									}
									if (meta.containsKey("created")) {
										project.put("created", meta.get("created"));
									}
									if (meta.containsKey("id")) {
										project.put("id", meta.get("id"));
									}
									if (meta.containsKey("logoPath")) {
										project.put("logoPath", meta.get("logoPath"));
									}
								}
							} catch (Exception e) {
								// ignore
							}
						}

						if (!project.containsKey("name")) {
							project.put("name", fileId);
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

		loadExternalProjects();
		updateEmptyState();
		adapter.notifyDataSetChanged();
	}

	private void loadExternalProjects() {
		try {
			ProjectDataManager pdm = new ProjectDataManager(this);
			java.util.List<Map<String, String>> extProjects = pdm.loadAllProjectsFromExternal();
			for (Map<String, String> extProject : extProjects) {
				String name = extProject.get("name");
				boolean alreadyLoaded = false;
				for (Map<String, String> existing : projectList) {
					if (name.equals(existing.get("name")) || name.equals(existing.get("id"))) {
						alreadyLoaded = true;
						break;
					}
				}
				if (!alreadyLoaded) {
					extProject.put("id", extProject.getOrDefault("id", name));
					extProject.put("description", "External project");
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
					extProject.put("created", sdf.format(new Date()));
					projectList.add(extProject);
				}
			}
		} catch (Exception e) {
			Log.w("HomeActivity", "Could not load external projects: " + e.getMessage());
		}
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
		TextView tvId = dialogView.findViewById(R.id.tvProjectId);
		TextView tvLogoPath = dialogView.findViewById(R.id.tvLogoPath);
		View btnSelectLogo = dialogView.findViewById(R.id.btnSelectLogo);

		String newId = generateProjectId();
		tvId.setText("ID: " + newId);
		pendingProjectLogoUri = null;
		pendingProjectLogoLabel = tvLogoPath;
		if (tvLogoPath != null) {
			tvLogoPath.setText("No logo selected");
		}
		if (btnSelectLogo != null) {
			btnSelectLogo.setOnClickListener(v -> projectLogoLauncher.launch(new String[]{"image/*"}));
		}

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

				// Offer logo selection
				showLogoSelectionChoice(newId, name, desc);
				createProject(newId, name, desc, pendingProjectLogoUri);
				pendingProjectLogoUri = null;
				pendingProjectLogoLabel = null;
			})
			.setNegativeButton("Cancel", (dialog, which) -> {
				pendingProjectLogoUri = null;
				pendingProjectLogoLabel = null;
			})
			.show();
	}

	/**
	 * Ask user if they want to select a logo for the project.
	 */
	private void showLogoSelectionChoice(String projectId, String name, String desc) {
		new MaterialAlertDialogBuilder(this)
			.setTitle("Project Logo")
			.setMessage("Would you like to select a logo image for \"" + name + "\"?")
			.setPositiveButton("Select Logo", (d, w) -> {
				pendingLogoProjectId = projectId;
				pendingLogoProjectName = name;
				pendingLogoProjectDesc = desc;
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
				intent.setType("image/*");
				logoPickerLauncher.launch(intent);
			})
			.setNegativeButton("Skip", (d, w) -> {
				createProjectInternal(projectId, name, desc);
			})
			.show();
	}

	/**
	 * Save the selected logo to the project assets directory and then create the project.
	 */
	private void saveLogoAndCreateProject(String projectId, String name, String desc, Uri logoUri) {
		try {
			// Create assets directory
			String assetsPath = Environment.getExternalStorageDirectory().getAbsolutePath()
				+ "/.dragweb/projects/" + projectId + "/assets";
			File assetsDir = new File(assetsPath);
			if (!assetsDir.exists()) assetsDir.mkdirs();

			// Determine file extension from MIME type
			String mimeType = getContentResolver().getType(logoUri);
			String ext = ".png";
			if (mimeType != null) {
				if (mimeType.contains("jpeg") || mimeType.contains("jpg")) ext = ".jpg";
				else if (mimeType.contains("webp")) ext = ".webp";
				else if (mimeType.contains("svg")) ext = ".svg";
			}

			File logoFile = new File(assetsDir, "logo" + ext);

			// Copy the image to assets
			InputStream is = getContentResolver().openInputStream(logoUri);
			if (is != null) {
				FileOutputStream fos = new FileOutputStream(logoFile);
				byte[] buffer = new byte[4096];
				int len;
				while ((len = is.read(buffer)) > 0) {
					fos.write(buffer, 0, len);
				}
				fos.close();
				is.close();
			}

			// Create the project with logo path in metadata
			createProjectInternal(projectId, name, desc, logoFile.getAbsolutePath());
		} catch (Exception e) {
			Log.w("HomeActivity", "Could not save logo: " + e.getMessage());
			createProjectInternal(projectId, name, desc);
		}
	}

	private void createProjectInternal(String projectId, String name, String description) {
		createProjectInternal(projectId, name, description, "");
	}

	private void createProjectInternal(String projectId, String name, String description, String logoPath) {
	private void createProject(String projectId, String name, String description, Uri logoUri) {
		File dir = new File(getFilesDir(), "projects");
		if (!dir.exists()) dir.mkdirs();

		File projectFile = new File(dir, projectId + ".json");
		FileUtil.writeFile(projectFile.getAbsolutePath(), "[]");

		File metaFile = new File(dir, projectId + ".meta");
		Map<String, String> meta = new HashMap<>();
		meta.put("id", projectId);
		meta.put("name", name);
		meta.put("description", description.isEmpty() ? "Website project" : description);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
		meta.put("created", sdf.format(new Date()));
		if (logoPath != null && !logoPath.isEmpty()) {
			meta.put("logoPath", logoPath);
		}
		FileUtil.writeFile(metaFile.getAbsolutePath(), new Gson().toJson(meta));
		String logoRelPath = "";

		// Create external directory structure
		try {
			String extPath = Environment.getExternalStorageDirectory().getAbsolutePath()
				+ "/.dragweb/projects/" + projectId;
			new File(extPath).mkdirs();
			File assetsDir = new File(extPath + "/assets");
			assetsDir.mkdirs();
			if (logoUri != null) {
				String fileName = resolveLogoFileName(logoUri);
				File logoFile = new File(assetsDir, fileName);
				if (copyUriToFile(logoUri, logoFile)) {
					logoRelPath = "assets/" + fileName;
				}
			}

			File configFile = new File(extPath, "project.config.json");
			Map<String, String> config = new HashMap<>();
			config.put("id", projectId);
			config.put("name", name);
			config.put("description", description.isEmpty() ? "Website project" : description);
			config.put("logoPath", logoRelPath);
			FileUtil.writeFile(configFile.getAbsolutePath(), new Gson().toJson(config));
		} catch (Exception e) {
			Log.w("HomeActivity", "Could not create external project dir");
		}
		meta.put("logoPath", logoRelPath);
		FileUtil.writeFile(metaFile.getAbsolutePath(), new Gson().toJson(meta));

		openProject(projectId, name);
	}

	private String resolveLogoFileName(Uri logoUri) {
		try {
			String name = null;
			android.database.Cursor c = getContentResolver().query(
				logoUri,
				new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
				null, null, null
			);
			if (c != null) {
				if (c.moveToFirst()) {
					int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
					if (idx >= 0) name = c.getString(idx);
				}
				c.close();
			}
			if (name != null && !name.isEmpty() && name.contains(".")) {
				String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
				if (ext.matches("png|jpg|jpeg|webp|gif|svg")) {
					return "logo." + ext;
				}
			}
		} catch (Exception e) {
			// ignore
		}
		return "logo.png";
	}

	private boolean copyUriToFile(Uri uri, File dest) {
		try (InputStream in = getContentResolver().openInputStream(uri);
		     OutputStream out = new java.io.FileOutputStream(dest)) {
			byte[] buffer = new byte[4096];
			int len;
			while ((len = in.read(buffer)) != -1) {
				out.write(buffer, 0, len);
			}
			return true;
		} catch (Exception e) {
			Log.w("HomeActivity", "Could not copy logo file: " + e.getMessage());
			return false;
		}
	}

	private void openProject(String projectId, String projectName) {
		Intent intent = new Intent(this, MainActivity.class);
		intent.putExtra("project_id", projectId);
		intent.putExtra("project_name", projectName);
		startActivity(intent);
	}

	private void deleteProject(String projectId) {
		String displayName = projectId;
		for (Map<String, String> p : projectList) {
			if (projectId.equals(p.get("id"))) {
				displayName = p.getOrDefault("name", projectId);
				break;
			}
		}

		final String finalName = displayName;
		new MaterialAlertDialogBuilder(this)
			.setTitle("Delete Project")
			.setMessage("Are you sure you want to delete \"" + finalName + "\"? This cannot be undone.")
			.setPositiveButton("Delete", (dialog, which) -> {
				File dir = new File(getFilesDir(), "projects");

				// Delete all project files matching this ID
				File[] allFiles = dir.listFiles();
				if (allFiles != null) {
					for (File f : allFiles) {
						String name = f.getName();
						if (name.startsWith(projectId + ".") || name.startsWith(projectId + "_")) {
							f.delete();
						}
					}
				}

				File exportDir = new File(getFilesDir(), "exports/" + projectId);
				if (exportDir.exists()) {
					FileUtil.deleteFile(exportDir.getAbsolutePath());
				}

				try {
					String extPath = Environment.getExternalStorageDirectory().getAbsolutePath()
						+ "/.dragweb/projects/" + projectId;
					File extDir = new File(extPath);
					if (extDir.exists()) {
						FileUtil.deleteFile(extDir.getAbsolutePath());
					}
				} catch (Exception e) {
					// ignore
				}

				loadProjects();
				Toast.makeText(this, "Project deleted", Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void showProjectOptions(String projectId, String projectName) {
		String[] options = {"Open", "Backup Project", "Delete"};
		new MaterialAlertDialogBuilder(this)
			.setTitle(projectName)
			.setItems(options, (dialog, which) -> {
				switch (which) {
					case 0:
						openProject(projectId, projectName);
						break;
					case 1:
						backupSingleProject(projectId);
						break;
					case 2:
						deleteProject(projectId);
						break;
				}
			})
			.show();
	}

	private void showAboutDialog() {
		View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_about, null);

		new MaterialAlertDialogBuilder(this)
			.setView(dialogView)
			.setPositiveButton("Close", null)
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
			String projectId = project.getOrDefault("id", "");
			String projectName = project.getOrDefault("name", projectId);

			holder.tvName.setText(projectName);
			holder.tvId.setText("ID: " + projectId);
			holder.tvDesc.setText(project.getOrDefault("description", ""));
			holder.tvDate.setText("Created: " + project.getOrDefault("created", ""));

			holder.itemView.setOnClickListener(v -> openProject(projectId, projectName));
			holder.btnMenu.setOnClickListener(v -> showProjectOptions(projectId, projectName));
			holder.btnBackup.setOnClickListener(v -> backupSingleProject(projectId));
		}

		@Override
		public int getItemCount() {
			return projectList.size();
		}

		class VH extends RecyclerView.ViewHolder {
			TextView tvName, tvId, tvDesc, tvDate;
			ImageView btnMenu, btnBackup;

			VH(View v) {
				super(v);
				tvName = v.findViewById(R.id.tvProjectName);
				tvId = v.findViewById(R.id.tvProjectId);
				tvDesc = v.findViewById(R.id.tvProjectDescription);
				tvDate = v.findViewById(R.id.tvProjectDate);
				btnMenu = v.findViewById(R.id.btnProjectMenu);
				btnBackup = v.findViewById(R.id.btnProjectBackup);
			}
		}
	}
}
