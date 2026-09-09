package glab.dragweb.activities;

import glab.dragweb.R;
import glab.dragweb.activities.*;
import glab.dragweb.fragments.*;
import glab.dragweb.engine.*;
import glab.dragweb.ui.*;
import glab.dragweb.logic.*;
import glab.dragweb.codegen.*;
import glab.dragweb.data.*;
import glab.dragweb.adapters.*;
import glab.dragweb.models.*;
import glab.dragweb.util.*;
import glab.dragweb.colorpicker.*;

import static androidx.core.view.ViewCompat.*;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import glab.dragweb.util.AppExecutors;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class HomeActivity extends AppCompatActivity {

	private static final Gson GSON = new Gson();
	private MaterialToolbar toolbar;
	private DrawerLayout drawer;
	private RecyclerView rvProjects;
	private LinearLayout layoutEmptyState;
	private TextView tvEmptyState;
	private ExtendedFloatingActionButton fabNewProject;
	private com.google.android.material.progressindicator.CircularProgressIndicator progressProjects;
	private ArrayList<Map<String, String>> projectList = new ArrayList<>();
	private ProjectListAdapter adapter;

	private ActivityResultLauncher<String> backupLauncher;
	private ActivityResultLauncher<String[]> importZipLauncher;
	private ActivityResultLauncher<String> backupSingleLauncher;
	private ActivityResultLauncher<String[]> htmlFileLauncher;
	private ActivityResultLauncher<String[]> cssFileLauncher;
	private ActivityResultLauncher<String[]> jsFileLauncher;
	private ActivityResultLauncher<Intent> storageFolderLauncher;
	private String pendingBackupProject = null;

	// HTML/CSS import state
	private Uri pendingHtmlUri = null;
	private Uri pendingCssUri = null;
	private Uri pendingJsUri = null;
	private TextView tvHtmlFileName = null;
	private TextView tvCssFileName = null;
	private TextView tvJsFileName = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (!SafStorageUtil.isStorageConfigured(this)) {
			Intent splashIntent = new Intent(this, SplashActivity.class);
			splashIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(splashIntent);
			finish();
			return;
		}
		EdgeToEdge.enable(this);
		setContentView(R.layout.home);
		initViews();
		setupToolbar();
		ensureExternalDirectories();
		loadProjects();

		final View appBar = findViewById(R.id._app_bar);
		final int appBarInitialTop = appBar != null ? appBar.getPaddingTop() : 0;
		final int fabInitialBottomMargin = fabNewProject != null ? ((ViewGroup.MarginLayoutParams) fabNewProject.getLayoutParams()).bottomMargin : dpToPx(16);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id._drawer), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			
			// Apply top padding to toolbar parent AppBarLayout
			if (appBar != null) {
				appBar.setPadding(appBar.getPaddingLeft(), appBarInitialTop + systemBars.top, appBar.getPaddingRight(), appBar.getPaddingBottom());
			}
			
			// Apply bottom margin to FAB to avoid overlap with navigation bar
			if (fabNewProject != null) {
				ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) fabNewProject.getLayoutParams();
				lp.bottomMargin = fabInitialBottomMargin + systemBars.bottom;
				fabNewProject.setLayoutParams(lp);
			}

			// Apply side paddings to the drawer content if needed for landscape
			v.setPadding(systemBars.left, 0, systemBars.right, 0);

			return insets;
		});
	}

	private int dpToPx(int dp) {
		float density = getResources().getDisplayMetrics().density;
		return Math.round(dp * density);
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!SafStorageUtil.isStorageConfigured(this)) {
			Intent splashIntent = new Intent(this, SplashActivity.class);
			splashIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(splashIntent);
			finish();
			return;
		}
		ensureExternalDirectories();
		loadProjects();
	}

	private void ensureExternalDirectories() {
		try {
			File dragWebDir = FileUtil.getDragWebDir(this);
			if (!dragWebDir.exists()) {
				dragWebDir.mkdirs();
			}
			File projectsDir = new File(dragWebDir, "projects");
			projectsDir.mkdirs();
			new File(dragWebDir, "custom").mkdirs();

			// Clean up any stray logic folder created under projects
			File strayLogicDir = new File(projectsDir, "logic");
			if (strayLogicDir.exists()) {
				FileUtil.deleteFile(strayLogicDir.getAbsolutePath());
			}
		} catch (Exception e) {
			Log.w("HomeActivity", "Could not create external dirs: " + e.getMessage());
		}
	}

	private void initViews() {
		toolbar = findViewById(R.id._toolbar);
		drawer = findViewById(R.id._drawer);
		rvProjects = findViewById(R.id.rvProjects);
		layoutEmptyState = findViewById(R.id.layoutEmptyState);
		tvEmptyState = findViewById(R.id.tvEmptyState);
		fabNewProject = findViewById(R.id.fabNewProject);
		progressProjects = findViewById(R.id.progressProjects);

		rvProjects.setLayoutManager(new LinearLayoutManager(this));
		adapter = new ProjectListAdapter();
		rvProjects.setAdapter(adapter);

		fabNewProject.setOnClickListener(v -> showNewProjectDialog());

		// Shrink/extend FAB on scroll
		rvProjects.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				if (dy > 0 && fabNewProject.isExtended()) {
					fabNewProject.shrink();
				} else if (dy < 0 && !fabNewProject.isExtended()) {
					fabNewProject.extend();
				}
			}
		});

		// Empty state import button
		MaterialButton btnEmptyImport = findViewById(R.id.btnEmptyImport);
		if (btnEmptyImport != null) {
			btnEmptyImport.setOnClickListener(v -> {
				Intent intent = new Intent(HomeActivity.this, ImportSiteActivity.class);
				startActivity(intent);
			});
		}

		// Register activity result launchers
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

		htmlFileLauncher = registerForActivityResult(
			new ActivityResultContracts.OpenDocument(),
			uri -> {
				if (uri != null) {
					pendingHtmlUri = uri;
					if (tvHtmlFileName != null) {
						String name = resolveFileName(uri);
						tvHtmlFileName.setText(name != null ? name : "HTML file selected");
					}
				}
			}
		);

		cssFileLauncher = registerForActivityResult(
			new ActivityResultContracts.OpenDocument(),
			uri -> {
				if (uri != null) {
					pendingCssUri = uri;
					if (tvCssFileName != null) {
						String name = resolveFileName(uri);
						tvCssFileName.setText(name != null ? name : "CSS file selected");
					}
				}
			}
		);

		jsFileLauncher = registerForActivityResult(
			new ActivityResultContracts.OpenDocument(),
			uri -> {
				if (uri != null) {
					pendingJsUri = uri;
					if (tvJsFileName != null) {
						String name = resolveFileName(uri);
						tvJsFileName.setText(name != null ? name : "JS file selected");
					}
				}
			}
		);

		storageFolderLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == RESULT_OK && result.getData() != null) {
					Uri uri = result.getData().getData();
					if (uri != null) {
						SafStorageUtil.ValidationResult validation = SafStorageUtil.validateFolderSelection(this, uri);
						if (validation.isValid) {
							SafStorageUtil.persistFolderSelection(this, uri);
							CustomStorageUtil.syncAssetsToStorage(this, null);
							loadProjects();
							Toast.makeText(this, "Storage location updated", Toast.LENGTH_SHORT).show();
						} else {
							showFolderMismatchDialog(validation.folderName);
						}
					}
				}
			}
		);

		// Drawer menu items
		LinearLayout menuMyProjects = findViewById(R.id.menuMyProjects);
		LinearLayout menuAbout = findViewById(R.id.menuAbout);
		LinearLayout menuBackup = findViewById(R.id.menuBackup);
		LinearLayout menuImport = findViewById(R.id.menuImport);
		LinearLayout menuImportWebsite = findViewById(R.id.menuImportWebsite);

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
		LinearLayout menuBlockParams = findViewById(R.id.menuBlockParams);
		if (menuBlockParams != null) {
			menuBlockParams.setOnClickListener(v -> {
				drawer.closeDrawer(GravityCompat.START);
				startActivity(new Intent(this, BlockParamManagerActivity.class));
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
		if (menuImportWebsite != null) {
			menuImportWebsite.setOnClickListener(v -> {
				drawer.closeDrawer(GravityCompat.START);
				Intent intent = new Intent(HomeActivity.this, ImportSiteActivity.class);
				startActivity(intent);
			});
		}

		LinearLayout menuCustomManager = findViewById(R.id.menuCustomManager);
		if (menuCustomManager != null) {
			menuCustomManager.setOnClickListener(v -> {
				drawer.closeDrawer(GravityCompat.START);
				startActivity(new Intent(this, ManageBlocksWidgetsActivity.class));
			});
		}

		LinearLayout menuEditorSettings = findViewById(R.id.menuEditorSettings);
		if (menuEditorSettings != null) {
			menuEditorSettings.setOnClickListener(v -> {
				drawer.closeDrawer(GravityCompat.START);
				startActivity(new Intent(this, EditorSettingsActivity.class));
			});
		}

		LinearLayout menuStorageLocation = findViewById(R.id.menuStorageLocation);
		if (menuStorageLocation != null) {
			menuStorageLocation.setOnClickListener(v -> {
				drawer.closeDrawer(GravityCompat.START);
				showStorageLocationDialog();
			});
		}
	}

	private String generateProjectId() {
		return ProjectDataManager.generateProjectId(this);
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

	private void showStorageLocationDialog() {
		String currentPath = FileUtil.getDragWebDir(this).getAbsolutePath();
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
			.setTitle("Storage Location")
			.setMessage("Current .dragweb workspace:\n\n" + currentPath + "\n\nAll website design projects, assets, custom blocks, and themes are stored in this directory.")
			.setPositiveButton("OK", null)
			.setNeutralButton("Change Folder", (dialog, which) -> {
				storageFolderLauncher.launch(SafStorageUtil.createFolderPickerIntent());
			})
			.show();
	}

	private void showFolderMismatchDialog(String selectedFolderName) {
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
			.setTitle("Folder Location Mismatch")
			.setMessage("You selected: \"" + (selectedFolderName != null ? selectedFolderName : "Unknown folder") + "\"\n\nDragWeb requires the '.dragweb' folder to store website projects and assets.\n\nPlease select the '.dragweb' folder.")
			.setPositiveButton("Select Again", (dialog, which) -> {
				storageFolderLauncher.launch(SafStorageUtil.createFolderPickerIntent());
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void performBackup(Uri uri) {
		File dir = new File(FileUtil.getDragWebDir(this), "projects");
		File[] projFiles = FileUtil.listFiles(dir);
		if (!dir.exists() || projFiles == null || projFiles.length == 0) {
			Toast.makeText(this, "No projects to backup", Toast.LENGTH_SHORT).show();
			return;
		}
		ProjectDataManager pdm = new ProjectDataManager(this);
		boolean ok = pdm.exportAllProjectsAsZip(uri);
		Toast.makeText(this, ok ? "Backup successful" : "Backup failed", Toast.LENGTH_SHORT).show();
	}

	private void performSingleProjectBackup(Uri uri, String projectId) {
		ProjectDataManager pdm = new ProjectDataManager(this);
		boolean ok = pdm.exportSingleProjectAsZip(projectId, uri);
		Toast.makeText(this, ok ? "Project backup successful" : "Backup failed", Toast.LENGTH_SHORT).show();
	}

	private void performImport(Uri uri) {
		ProjectDataManager pdm = new ProjectDataManager(this);
		ProjectDataManager.ImportResult result = pdm.importProjectsFromZip(uri);
		if (!result.success) {
			Toast.makeText(this, "Import failed: " + result.message, Toast.LENGTH_LONG).show();
			return;
		}

		loadProjects();
		Toast.makeText(this, "Projects imported successfully", Toast.LENGTH_SHORT).show();

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
		// Show loading spinner, hide list/empty state while scanning
		if (progressProjects != null) progressProjects.setVisibility(android.view.View.VISIBLE);
		if (rvProjects != null) rvProjects.setVisibility(android.view.View.GONE);
		if (layoutEmptyState != null) layoutEmptyState.setVisibility(android.view.View.GONE);

		final File dir = new File(FileUtil.getDragWebDir(this), "projects");

		AppExecutors.io().execute(() -> {
			if (!dir.exists()) dir.mkdirs();

			final ArrayList<Map<String, String>> loaded = new ArrayList<>();

			if (dir.isDirectory()) {
				File[] files = FileUtil.listFiles(dir);
				if (files != null) {
					// Safely remove any legacy loose root files if their project folder exists
					for (File f : files) {
						if (!f.isDirectory()) {
							String fname = f.getName();
							if (fname.endsWith(".json") || fname.endsWith(".meta") || fname.endsWith(".meta.txt") || fname.endsWith(".theme") || fname.endsWith(".cblocks") || fname.endsWith(".logic")) {
								String baseId = fname;
								int dotIdx = baseId.indexOf('.');
								if (dotIdx > 0) baseId = baseId.substring(0, dotIdx);
								int underIdx = baseId.indexOf('_');
								if (underIdx > 0) baseId = baseId.substring(0, underIdx);
								File projDir = new File(dir, baseId);
								if (projDir.exists() && projDir.isDirectory()) {
									try { f.delete(); } catch (Exception ignored) {}
								}
							}
						}
					}

					SimpleDateFormat sdfMeta = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
					SimpleDateFormat sdfDisplay = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

					for (File projectFolder : files) {
						if (!projectFolder.isDirectory()) continue;
						String fileId = projectFolder.getName();
						if (fileId == null || fileId.startsWith(".") || fileId.equalsIgnoreCase("logic") || fileId.equalsIgnoreCase("custom") || fileId.equalsIgnoreCase("templates")) {
							continue;
						}

						Map<String, String> project = new HashMap<>();
						project.put("id", fileId);
						project.put("path", projectFolder.getAbsolutePath());
						project.put("name", fileId);
						project.put("description", "Website Project");

						String metaJson = FileUtil.readFile(new File(projectFolder, "project.meta").getAbsolutePath());
						File legacyConfigFile = new File(projectFolder, "project.config.json");
						if (metaJson == null || metaJson.trim().isEmpty()) {
							if (legacyConfigFile.exists()) {
								metaJson = FileUtil.readFile(legacyConfigFile.getAbsolutePath());
							}
						}

						if (metaJson != null && !metaJson.trim().isEmpty()) {
							try {
								Map<String, String> meta = GSON.fromJson(metaJson, new TypeToken<Map<String, String>>(){}.getType());
								if (meta != null) {
									if (meta.containsKey("name")) project.put("name", meta.get("name"));
									if (meta.containsKey("description")) project.put("description", meta.get("description"));
									if (meta.containsKey("created")) project.put("created", meta.get("created"));
									if (meta.containsKey("id")) project.put("id", meta.get("id"));
									if (meta.containsKey("logoPath")) project.put("logoPath", meta.get("logoPath"));
								}
							} catch (Exception ignored) {}
						} else {
							Map<String, String> meta = new HashMap<>();
							meta.put("id", fileId);
							meta.put("name", fileId);
							meta.put("description", "Website Project");
							meta.put("created", sdfMeta.format(new Date(projectFolder.lastModified())));
							FileUtil.writeFile(new File(projectFolder, "project.meta").getAbsolutePath(), GSON.toJson(meta));
						}

						// Clean up legacy files if present
						if (legacyConfigFile.exists()) {
							FileUtil.deleteFile(legacyConfigFile.getAbsolutePath());
						}
						File legacyLayout = new File(projectFolder, "layout.json");
						if (legacyLayout.exists()) {
							File indexPage = new File(projectFolder, "pages/index.json");
							if (!indexPage.exists()) {
								String lJson = FileUtil.readFile(legacyLayout.getAbsolutePath());
								if (lJson != null && !lJson.isEmpty()) {
									FileUtil.writeFile(indexPage.getAbsolutePath(), lJson);
								}
							}
							FileUtil.deleteFile(legacyLayout.getAbsolutePath());
						}

						project.put("lastModified", sdfDisplay.format(new Date(projectFolder.lastModified())));
						loaded.add(project);
					}
				}
			}

			// Post results back to UI thread
			AppExecutors.mainThread(() -> {
				if (isDestroyed() || isFinishing()) return;
				projectList.clear();
				projectList.addAll(loaded);
				if (progressProjects != null) progressProjects.setVisibility(android.view.View.GONE);
				updateEmptyState();
				adapter.notifyDataSetChanged();
			});
		});
	}

	private void loadExternalProjects() {
		try {
			ProjectDataManager pdm = new ProjectDataManager(this);
			List<Map<String, String>> extProjects = pdm.loadAllProjectsFromExternal();
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
					extProject.put("lastModified", new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date()));
					projectList.add(extProject);
				}
			}
		} catch (Exception e) {
			Log.w("HomeActivity", "Could not load external projects: " + e.getMessage());
		}
	}

	private void updateEmptyState() {
		if (projectList.isEmpty()) {
			layoutEmptyState.setVisibility(View.VISIBLE);
			rvProjects.setVisibility(View.GONE);
		} else {
			layoutEmptyState.setVisibility(View.GONE);
			rvProjects.setVisibility(View.VISIBLE);
		}
	}

	private void showNewProjectDialog() {
		View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_project, null);
		TextInputEditText etName = dialogView.findViewById(R.id.etProjectName);
		TextInputEditText etDesc = dialogView.findViewById(R.id.etProjectDescription);
		TextView tvId = dialogView.findViewById(R.id.tvProjectId);

		String newId = generateProjectId();
		tvId.setText("ID: " + newId);

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

				createProject(newId, name, desc);
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void createProject(String projectId, String name, String description) {
		androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
			.setTitle("Creating Project")
			.setMessage("Setting up project workspace...")
			.setCancelable(false)
			.create();
		progressDialog.show();

		java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
			try {
				File dragWebDir = FileUtil.getDragWebDir(HomeActivity.this);
				File projectsDir = new File(dragWebDir, "projects");
				if (!projectsDir.exists()) FileUtil.makeDir(projectsDir.getAbsolutePath());

				File extDir = new File(projectsDir, projectId);
				if (!extDir.exists()) FileUtil.makeDir(extDir.getAbsolutePath());

				File pagesDir = new File(extDir, "pages");
				if (!pagesDir.exists()) FileUtil.makeDir(pagesDir.getAbsolutePath());

				File assetsDir = new File(extDir, "assets");
				if (!assetsDir.exists()) FileUtil.makeDir(assetsDir.getAbsolutePath());

				File cssDir = new File(assetsDir, "css");
				if (!cssDir.exists()) FileUtil.makeDir(cssDir.getAbsolutePath());

				File jsDir = new File(assetsDir, "js");
				if (!jsDir.exists()) FileUtil.makeDir(jsDir.getAbsolutePath());

				File imgDir = new File(assetsDir, "images");
				if (!imgDir.exists()) FileUtil.makeDir(imgDir.getAbsolutePath());

				File indexPage = new File(pagesDir, "index.json");
				FileUtil.writeFile(indexPage.getAbsolutePath(), "[]");
				FileUtil.writeFile(new File(extDir, "pages.json").getAbsolutePath(), "[\"index\"]");
				FileUtil.writeFile(new File(extDir, "index_logic.json").getAbsolutePath(), "{}");
				FileUtil.writeFile(new File(extDir, "theme.json").getAbsolutePath(), new ThemeManager().toJson());

				Map<String, String> meta = new HashMap<>();
				meta.put("id", projectId);
				meta.put("name", name);
				meta.put("description", description.isEmpty() ? "Website project" : description);
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
				meta.put("created", sdf.format(new Date()));
				FileUtil.writeFile(new File(extDir, "project.meta").getAbsolutePath(), new Gson().toJson(meta));

				// Generate initial assets (theme.css, style.css, script.js)
				ProjectCodeGenerator.generateAndSaveAssets(HomeActivity.this, projectId, "index");

				runOnUiThread(() -> {
					try { progressDialog.dismiss(); } catch (Exception ignored) {}
					openProject(projectId, name);
				});
			} catch (Exception e) {
				Log.e("HomeActivity", "Error creating project: " + e.getMessage(), e);
				runOnUiThread(() -> {
					try { progressDialog.dismiss(); } catch (Exception ignored) {}
					Toast.makeText(HomeActivity.this, "Failed to create project: " + e.getMessage(), Toast.LENGTH_LONG).show();
				});
			}
		});
	}

	/**
	 * Create a project from an imported HTML/CSS widget tree and logic blocks.
	 */
	private void createProjectFromImport(String name, List<Map<String, Object>> widgetTree, List<Map<String, Object>> logicBlocks, List<Map<String, Object>> cssLogicBlocks, String jsContent, List<String> enabledLibraries) {
		String projectId = generateProjectId();
		File dragWebDir = FileUtil.getDragWebDir(this);
		File dir = new File(dragWebDir, "projects");
		if (!dir.exists()) dir.mkdirs();

		File extDir = new File(dir, projectId);
		if (!extDir.exists()) extDir.mkdirs();

		// Save widget tree as pages/index.json
		String jsonStr = new Gson().toJson(widgetTree);
		File pagesDir = new File(extDir, "pages");
		if (!pagesDir.exists()) pagesDir.mkdirs();
		FileUtil.writeFile(new File(pagesDir, "index.json").getAbsolutePath(), jsonStr);
		FileUtil.writeFile(new File(extDir, "pages.json").getAbsolutePath(), "[\"index\"]");

		HtmlCssImporter importer = new HtmlCssImporter(this);

		// Save logic blocks if any
		if (logicBlocks != null && !logicBlocks.isEmpty()) {
			File logicFile = new File(extDir, "index_logic.json");
			ArrayList<BlockBean> beans = importer.convertRawMapsToBeans(logicBlocks);
			FileUtil.writeFile(logicFile.getAbsolutePath(), new Gson().toJson(beans));
		}

		// Save global CSS logic blocks if any
		if (cssLogicBlocks != null && !cssLogicBlocks.isEmpty()) {
			String cleanCssName = DesignDataManager.getCleanPageName("css/style.css");
			File cssLogicFile = new File(extDir, cleanCssName + "_logic.json");
			ArrayList<BlockBean> cssBeans = importer.convertRawMapsToBeans(cssLogicBlocks);
			FileUtil.writeFile(cssLogicFile.getAbsolutePath(), new Gson().toJson(cssBeans));
		}

		// Global JS content is saved directly to assets/js/script.js without creating redundant script_logic.json

		// Save metadata
		File metaFile = new File(extDir, "project.meta");
		Map<String, String> meta = new HashMap<>();
		meta.put("id", projectId);
		meta.put("name", name.isEmpty() ? "Imported Website" : name);
		meta.put("description", "Imported from HTML/CSS");
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
		meta.put("created", sdf.format(new Date()));
		FileUtil.writeFile(metaFile.getAbsolutePath(), new Gson().toJson(meta));

		// Save theme settings: disable default styles and inline styles since CSS is imported
		ThemeManager tm = new ThemeManager();
		tm.setUseInlineStyles(false);
		tm.setDisableDefaultStyles(true);
		File themeFile = new File(extDir, "theme.json");
		FileUtil.writeFile(themeFile.getAbsolutePath(), tm.toJson());

		// Enable detected standard icon libraries
		if (enabledLibraries != null && !enabledLibraries.isEmpty()) {
			IconLibraryManager ilm = new IconLibraryManager(this, projectId);
			for (String libId : enabledLibraries) {
				ilm.enable(libId);
			}
		}

		// Create project assets directory
		try {
			new File(extDir, "assets").mkdirs();

			// If global CSS blocks were imported, compile and write to assets/css/style.css
			if (cssLogicBlocks != null && !cssLogicBlocks.isEmpty()) {
				try {
					LogicBlockManager cssLogic = new LogicBlockManager(this);
					cssLogic.fromJson(new Gson().toJson(cssLogicBlocks));
					String baseRules = cssLogic.generateBaseCssRules();
					String pseudoRules = cssLogic.generateCssPseudoRules();
					String asdCss = cssLogic.generateAsdSource("css");
					StringBuilder compiledCss = new StringBuilder();
					compiledCss.append("/* Generated by DragWeb */\n\n");
					if (baseRules != null && !baseRules.trim().isEmpty()) {
						compiledCss.append(baseRules).append("\n");
					}
					if (pseudoRules != null && !pseudoRules.trim().isEmpty()) {
						compiledCss.append(pseudoRules).append("\n");
					}
					if (asdCss != null && !asdCss.trim().isEmpty()) {
						compiledCss.append(asdCss).append("\n");
					}

					File targetStyleFile = new File(extDir, "assets/css/style.css");
					targetStyleFile.getParentFile().mkdirs();
					FileUtil.writeFile(targetStyleFile.getAbsolutePath(), compiledCss.toString());
				} catch (Exception e) {
					Log.w("HomeActivity", "Failed to compile/write style.css to assets: " + e.getMessage());
				}
			}

			// If JS content was imported, write to assets/js/script.js
			if (jsContent != null && !jsContent.trim().isEmpty()) {
				try {
					File targetJsFile = new File(extDir, "assets/js/script.js");
					targetJsFile.getParentFile().mkdirs();
					FileUtil.writeFile(targetJsFile.getAbsolutePath(), jsContent);
				} catch (Exception e) {
					Log.w("HomeActivity", "Failed to write script.js to assets: " + e.getMessage());
				}
			}
		} catch (Exception e) {
			Log.w("HomeActivity", "Could not create project dir for import");
		}

		loadProjects();
		openProject(projectId, name.isEmpty() ? "Imported Website" : name);
	}

	private String resolveFileName(Uri uri) {
		try {
			android.database.Cursor c = getContentResolver().query(
				uri,
				new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
				null, null, null
			);
			if (c != null) {
				if (c.moveToFirst()) {
					int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
					if (idx >= 0) {
						String name = c.getString(idx);
						c.close();
						return name;
					}
				}
				c.close();
			}
		} catch (Exception e) {
			// ignore
		}
		return null;
	}

	private String readUriContent(Uri uri) {
		try (InputStream is = getContentResolver().openInputStream(uri);
			 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n");
			}
			return sb.toString();
		} catch (Exception e) {
			Log.e("HomeActivity", "Failed to read URI: " + e.getMessage());
			return null;
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
				File dir = new File(FileUtil.getDragWebDir(this), "projects");
				String[] extensions = {".json", ".meta", ".theme", ".logic"};
				for (String ext : extensions) {
					File f = new File(dir, projectId + ext);
					if (f.exists()) f.delete();
				}

				// Delete page files (projectId_pageName.json)
				File[] files = FileUtil.listFiles(dir);
				if (files != null) {
					for (File f : files) {
						if (f.getName().startsWith(projectId + "_")) {
							f.delete();
						}
					}
				}

				// Delete project subfolder
				File extDir = new File(dir, projectId);
				if (extDir.exists()) {
					FileUtil.deleteFile(extDir.getAbsolutePath());
				}

				File exportDir = new File(FileUtil.getDragWebDir(this), "exports/" + projectId);
				if (exportDir.exists()) {
					FileUtil.deleteFile(exportDir.getAbsolutePath());
				}

				// Reset all in-memory static maps to clear stale cached blocks
				DesignDataManager.initMaps();

				loadProjects();
				Toast.makeText(this, "Project deleted", Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void renameProject(String projectId, String currentName) {
		View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_project, null);
		TextInputEditText etName = dialogView.findViewById(R.id.etProjectName);
		TextInputEditText etDesc = dialogView.findViewById(R.id.etProjectDescription);
		TextView tvId = dialogView.findViewById(R.id.tvProjectId);

		// Pre-fill with current values
		etName.setText(currentName);
		tvId.setText("ID: " + projectId);

		new MaterialAlertDialogBuilder(this)
			.setTitle("Rename Project")
			.setView(dialogView)
			.setPositiveButton("Save", (dialog, which) -> {
				String newName = etName.getText().toString().trim();
				String newDesc = etDesc.getText().toString().trim();

				if (newName.isEmpty()) {
					Toast.makeText(this, "Please enter a project name", Toast.LENGTH_SHORT).show();
					return;
				}

				// Update metadata file
				File metaFile = new File(FileUtil.getDragWebDir(this), "projects/" + projectId + "/project.meta");
				if (!metaFile.getParentFile().exists()) metaFile.getParentFile().mkdirs();
				Map<String, String> meta = new HashMap<>();
				if (metaFile.exists()) {
					try {
						String metaJson = FileUtil.readFile(metaFile.getAbsolutePath());
						Map<String, String> existing = new Gson().fromJson(metaJson,
							new TypeToken<Map<String, String>>(){}.getType());
						if (existing != null) meta.putAll(existing);
					} catch (Exception e) {
						// ignore
					}
				}
				meta.put("id", projectId);
				meta.put("name", newName);
				if (!newDesc.isEmpty()) {
					meta.put("description", newDesc);
				}
				FileUtil.writeFile(metaFile.getAbsolutePath(), new Gson().toJson(meta));

				// Clean up legacy config if present
				File configFile = new File(FileUtil.getDragWebDir(this), "projects/" + projectId + "/project.config.json");
				if (configFile.exists()) {
					FileUtil.deleteFile(configFile.getAbsolutePath());
				}

				loadProjects();
				Toast.makeText(this, "Project renamed", Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void showProjectOptions(String projectId, String projectName) {
		String[] options = {"Open", "Rename", "Backup Project", "Delete"};
		new MaterialAlertDialogBuilder(this)
			.setTitle(projectName)
			.setItems(options, (dialog, which) -> {
				switch (which) {
					case 0:
						openProject(projectId, projectName);
						break;
					case 1:
						renameProject(projectId, projectName);
						break;
					case 2:
						backupSingleProject(projectId);
						break;
					case 3:
						deleteProject(projectId);
						break;
				}
			})
			.show();
	}

	// ---- Import Website (HTML/CSS) ----


private void showAboutDialog() {
    View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_about, null);
    final Button btnGit = dialogView.findViewById(R.id.btnGithub);

    new MaterialAlertDialogBuilder(this)
        .setView(dialogView)
        .setPositiveButton("Close", null)
        .show();

    btnGit.setOnClickListener(v -> {
        Intent in = new Intent(Intent.ACTION_VIEW);
        in.setData(Uri.parse("https://github.com/saqib-cipher/DragWeb"));
        startActivity(in);
    });
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
			String projectId = project.get("id");
			String projectName = project.get("name");

			holder.tvName.setText(projectName);
			holder.tvId.setText(projectId);
			holder.tvDesc.setText(project.getOrDefault("description", ""));

			String lastModified = project.getOrDefault("lastModified", "");
			if (!lastModified.isEmpty()) {
				holder.tvDate.setText(lastModified);
			} else {
				holder.tvDate.setText(project.getOrDefault("created", ""));
			}

			// Click to open
			holder.itemView.setOnClickListener(v -> openProject(projectId, projectName));

			// Long press for options
			holder.itemView.setOnLongClickListener(v -> {
				showProjectOptions(projectId, projectName);
				return true;
			});

			// Menu button
			holder.btnMenu.setOnClickListener(v -> showProjectOptions(projectId, projectName));
		}

		@Override
		public int getItemCount() {
			return projectList.size();
		}

		class VH extends RecyclerView.ViewHolder {
			TextView tvName, tvId, tvDesc, tvDate;
			ImageView btnMenu;

			VH(View v) {
				super(v);
				tvName = v.findViewById(R.id.tvProjectName);
				tvId = v.findViewById(R.id.tvProjectId);
				tvDesc = v.findViewById(R.id.tvProjectDescription);
				tvDate = v.findViewById(R.id.tvProjectDate);
				btnMenu = v.findViewById(R.id.btnProjectMenu);
			}
		}
	}
}
