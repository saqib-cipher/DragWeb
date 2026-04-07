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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;
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
	private String pendingBackupProject = null;

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

	private void backupAllProjects() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
		String filename = "DragWeb_Backup_" + sdf.format(new Date()) + ".zip";
		backupLauncher.launch(filename);
	}

	private void backupSingleProject(String projectName) {
		pendingBackupProject = projectName;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
		String filename = "DragWeb_" + projectName + "_" + sdf.format(new Date()) + ".zip";
		backupSingleLauncher.launch(filename);
	}

	private void importProject() {
		importZipLauncher.launch(new String[]{"application/zip"});
	}

	private void performBackup(Uri uri) {
		File dir = new File(getFilesDir(), "projects");
		if (!dir.exists() || dir.listFiles() == null || dir.listFiles().length == 0) {
			Toast.makeText(this, "No projects to backup", Toast.LENGTH_SHORT).show();
			return;
		}

		try {
			java.io.OutputStream fos = getContentResolver().openOutputStream(uri);
			ZipOutputStream zos = new ZipOutputStream(new java.io.BufferedOutputStream(fos));

			File[] files = dir.listFiles();
			for (File file : files) {
				if (file.isFile()) {
					byte[] buffer = new byte[1024];
					FileInputStream fis = new FileInputStream(file);
					zos.putNextEntry(new ZipEntry(file.getName()));
					int length;
					while ((length = fis.read(buffer)) > 0) {
						zos.write(buffer, 0, length);
					}
					zos.closeEntry();
					fis.close();
				}
			}

			// Also include external assets
			String extPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/.dragweb/projects";
			File extDir = new File(extPath);
			if (extDir.exists()) {
				addDirectoryToZip(zos, extDir, "external/");
			}

			zos.close();
			fos.close();
			Toast.makeText(this, "Backup successful", Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void performSingleProjectBackup(Uri uri, String projectName) {
		try {
			java.io.OutputStream fos = getContentResolver().openOutputStream(uri);
			ZipOutputStream zos = new ZipOutputStream(new java.io.BufferedOutputStream(fos));

			// Backup internal project files
			File dir = new File(getFilesDir(), "projects");
			String[] extensions = {".json", ".meta", ".theme", ".logic"};
			for (String ext : extensions) {
				File file = new File(dir, projectName + ext);
				if (file.exists()) {
					byte[] buffer = new byte[1024];
					FileInputStream fis = new FileInputStream(file);
					zos.putNextEntry(new ZipEntry(file.getName()));
					int length;
					while ((length = fis.read(buffer)) > 0) {
						zos.write(buffer, 0, length);
					}
					zos.closeEntry();
					fis.close();
				}
			}

			// Backup external assets
			String extPath = Environment.getExternalStorageDirectory().getAbsolutePath()
				+ "/.dragweb/projects/" + projectName;
			File extDir = new File(extPath);
			if (extDir.exists()) {
				addDirectoryToZip(zos, extDir, "assets/");
			}

			zos.close();
			fos.close();
			Toast.makeText(this, "Project backup successful", Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}

	private void addDirectoryToZip(ZipOutputStream zos, File dir, String prefix) throws Exception {
		File[] files = dir.listFiles();
		if (files == null) return;
		for (File file : files) {
			if (file.isDirectory()) {
				addDirectoryToZip(zos, file, prefix + file.getName() + "/");
			} else {
				byte[] buffer = new byte[1024];
				FileInputStream fis = new FileInputStream(file);
				zos.putNextEntry(new ZipEntry(prefix + file.getName()));
				int length;
				while ((length = fis.read(buffer)) > 0) {
					zos.write(buffer, 0, length);
				}
				zos.closeEntry();
				fis.close();
			}
		}
	}

	private void performImport(Uri uri) {
		File dir = new File(getFilesDir(), "projects");
		if (!dir.exists()) {
			dir.mkdirs();
		}

		try {
			String canonicalDirPath = dir.getCanonicalPath();
			java.io.InputStream fis = getContentResolver().openInputStream(uri);
			ZipInputStream zis = new ZipInputStream(new java.io.BufferedInputStream(fis));
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				File outFile = new File(dir, entry.getName());
				String canonicalFilePath = outFile.getCanonicalPath();

				if (!canonicalFilePath.startsWith(canonicalDirPath + File.separator)) {
					zis.closeEntry();
					continue;
				}

				if (entry.isDirectory()) {
					if (!outFile.exists()) outFile.mkdirs();
					zis.closeEntry();
					continue;
				} else {
					File parentFile = outFile.getParentFile();
					if (parentFile != null && !parentFile.exists()) {
						parentFile.mkdirs();
					}
				}

				FileOutputStream fos = new FileOutputStream(outFile);
				byte[] buffer = new byte[1024];
				int count;
				while ((count = zis.read(buffer)) != -1) {
					fos.write(buffer, 0, count);
				}
				fos.close();
				zis.closeEntry();
			}
			zis.close();
			fis.close();

			loadProjects();
			Toast.makeText(this, "Projects imported successfully", Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
								// ignore
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

		// Also load projects from external storage that aren't already loaded
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
					if (name.equals(existing.get("name"))) {
						alreadyLoaded = true;
						break;
					}
				}
				if (!alreadyLoaded) {
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

		// Also create external directory structure
		try {
			String extPath = Environment.getExternalStorageDirectory().getAbsolutePath()
				+ "/.dragweb/projects/" + name;
			new File(extPath).mkdirs();
			new File(extPath + "/assets").mkdirs();
		} catch (Exception e) {
			Log.w("HomeActivity", "Could not create external project dir");
		}

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
				// Delete all associated files
				String[] extensions = {".json", ".meta", ".theme", ".logic"};
				for (String ext : extensions) {
					File f = new File(dir, projectName + ext);
					if (f.exists()) f.delete();
				}

				// Also delete export files
				File exportDir = new File(getFilesDir(), "exports/" + projectName.replaceAll("[^a-zA-Z0-9._-]", "_"));
				if (exportDir.exists()) {
					FileUtil.deleteFile(exportDir.getAbsolutePath());
				}

				// Also delete external storage
				try {
					String extPath = Environment.getExternalStorageDirectory().getAbsolutePath()
						+ "/.dragweb/projects/" + projectName;
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

	private void showProjectOptions(String projectName) {
		String[] options = {"Open", "Backup Project", "Delete"};
		new MaterialAlertDialogBuilder(this)
			.setTitle(projectName)
			.setItems(options, (dialog, which) -> {
				switch (which) {
					case 0:
						openProject(projectName);
						break;
					case 1:
						backupSingleProject(projectName);
						break;
					case 2:
						deleteProject(projectName);
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
			holder.tvName.setText(project.get("name"));
			holder.tvDesc.setText(project.getOrDefault("description", ""));
			holder.tvDate.setText("Created: " + project.getOrDefault("created", ""));

			holder.itemView.setOnClickListener(v -> openProject(project.get("name")));
			holder.btnMenu.setOnClickListener(v -> showProjectOptions(project.get("name")));
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
