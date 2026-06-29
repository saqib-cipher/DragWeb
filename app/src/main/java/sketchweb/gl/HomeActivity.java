package sketchweb.gl;

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

	private MaterialToolbar toolbar;
	private DrawerLayout drawer;
	private RecyclerView rvProjects;
	private LinearLayout layoutEmptyState;
	private TextView tvEmptyState;
	private ExtendedFloatingActionButton fabNewProject;
	private ArrayList<Map<String, String>> projectList = new ArrayList<>();
	private ProjectListAdapter adapter;

	private ActivityResultLauncher<String> backupLauncher;
	private ActivityResultLauncher<String[]> importZipLauncher;
	private ActivityResultLauncher<String> backupSingleLauncher;
	private ActivityResultLauncher<String[]> htmlFileLauncher;
	private ActivityResultLauncher<String[]> cssFileLauncher;
	private ActivityResultLauncher<String[]> jsFileLauncher;
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
		layoutEmptyState = findViewById(R.id.layoutEmptyState);
		tvEmptyState = findViewById(R.id.tvEmptyState);
		fabNewProject = findViewById(R.id.fabNewProject);

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

	private void performBackup(Uri uri) {
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
		projectList.clear();
		File dir = new File(getFilesDir(), "projects");
		if (dir.exists() && dir.isDirectory()) {
			File[] files = dir.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.getName().endsWith(".json")) {
						String fileId = file.getName().replace(".json", "");
						if (fileId.equals("widgets") || fileId.equals("params")) {
							continue;
						}
						// Check if corresponding external directory exists. If not, this project was deleted from storage.
						String extPath = Environment.getExternalStorageDirectory().getAbsolutePath()
							+ "/.dragweb/projects/" + fileId;
						File extDir = new File(extPath);
						if (!extDir.exists()) {
							// Delete internal project files so they don't remain in list
							file.delete();
							new File(dir, fileId + ".meta").delete();
							new File(dir, fileId + ".theme").delete();
							new File(dir, fileId + ".icons").delete();
							new File(dir, fileId + ".components.json").delete();
							new File(dir, fileId + ".animations").delete();
							new File(dir, fileId + ".breakpoints.json").delete();
							File logicDir = new File(dir, "logic");
							File[] logicFiles = logicDir.listFiles();
							if (logicFiles != null) {
								for (File lf : logicFiles) {
									if (lf.getName().startsWith(fileId + "_") || lf.getName().startsWith(fileId + ".")) {
										lf.delete();
									}
								}
							}
							continue;
						}
						int lastUnderscore = fileId.lastIndexOf('_');
						if (lastUnderscore > 0) {
							String possibleProjectId = fileId.substring(0, lastUnderscore);
							File possibleProjectFile = new File(dir, possibleProjectId + ".json");
							if (possibleProjectFile.exists()) {
								continue;
							}
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
									if (meta.containsKey("name")) project.put("name", meta.get("name"));
									if (meta.containsKey("description")) project.put("description", meta.get("description"));
									if (meta.containsKey("created")) project.put("created", meta.get("created"));
									if (meta.containsKey("id")) project.put("id", meta.get("id"));
									if (meta.containsKey("logoPath")) project.put("logoPath", meta.get("logoPath"));
								}
							} catch (Exception e) {
								// ignore
							}
						}

						if (!project.containsKey("name")) project.put("name", fileId);
						if (!project.containsKey("description")) project.put("description", "Website project");
						if (!project.containsKey("created")) {
							SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
							project.put("created", sdf.format(new Date(file.lastModified())));
						}

						// Last modified time
						SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
						project.put("lastModified", sdf.format(new Date(file.lastModified())));

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

		try {
			String extPath = Environment.getExternalStorageDirectory().getAbsolutePath()
				+ "/.dragweb/projects/" + projectId;
			new File(extPath).mkdirs();
			File assetsDir = new File(extPath + "/assets");
			assetsDir.mkdirs();

			File configFile = new File(extPath, "project.config.json");
			Map<String, String> config = new HashMap<>();
			config.put("id", projectId);
			config.put("name", name);
			config.put("description", description.isEmpty() ? "Website project" : description);
			FileUtil.writeFile(configFile.getAbsolutePath(), new Gson().toJson(config));
		} catch (Exception e) {
			Log.w("HomeActivity", "Could not create external project dir");
		}
		FileUtil.writeFile(metaFile.getAbsolutePath(), new Gson().toJson(meta));

		openProject(projectId, name);
	}

	/**
	 * Create a project from an imported HTML/CSS widget tree and logic blocks.
	 */
	private void createProjectFromImport(String name, List<Map<String, Object>> widgetTree, List<Map<String, Object>> logicBlocks, List<Map<String, Object>> cssLogicBlocks, String jsContent, List<String> enabledLibraries) {
		String projectId = generateProjectId();
		File dir = new File(getFilesDir(), "projects");
		if (!dir.exists()) dir.mkdirs();

		File logicDir = new File(dir, "logic");
		if (!logicDir.exists()) logicDir.mkdirs();

		// Save widget tree as project JSON
		File projectFile = new File(dir, projectId + ".json");
		FileUtil.writeFile(projectFile.getAbsolutePath(), new Gson().toJson(widgetTree));

		// Save logic blocks if any
		if (logicBlocks != null && !logicBlocks.isEmpty()) {
			File logicFile = new File(logicDir, projectId + "_index.logic");
			FileUtil.writeFile(logicFile.getAbsolutePath(), new Gson().toJson(logicBlocks));
		}

		// Save global CSS logic blocks if any
		if (cssLogicBlocks != null && !cssLogicBlocks.isEmpty()) {
			String globalCssPath = "css/style.css";
			String safeCssName = globalCssPath.replace("/", "_").replace(".", "_");
			File cssLogicFile = new File(logicDir, projectId + "_" + safeCssName + ".logic");
			FileUtil.writeFile(cssLogicFile.getAbsolutePath(), new Gson().toJson(cssLogicBlocks));
		}

		// Save metadata
		File metaFile = new File(dir, projectId + ".meta");
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
		File themeFile = new File(dir, projectId + ".theme");
		FileUtil.writeFile(themeFile.getAbsolutePath(), tm.toJson());

		// Enable detected standard icon libraries
		if (enabledLibraries != null && !enabledLibraries.isEmpty()) {
			IconLibraryManager ilm = new IconLibraryManager(this, projectId);
			for (String libId : enabledLibraries) {
				ilm.enable(libId);
			}
		}

		// Create external directory
		try {
			String extPath = Environment.getExternalStorageDirectory().getAbsolutePath()
				+ "/.dragweb/projects/" + projectId;
			new File(extPath).mkdirs();
			new File(extPath + "/assets").mkdirs();

			File configFile = new File(extPath, "project.config.json");
			Map<String, String> config = new HashMap<>();
			config.put("id", projectId);
			config.put("name", name.isEmpty() ? "Imported Website" : name);
			config.put("description", "Imported from HTML/CSS");
			FileUtil.writeFile(configFile.getAbsolutePath(), new Gson().toJson(config));

			// If global CSS blocks were imported, compile and write to external assets/css/style.css
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

					File targetStyleFile = new File(extPath, "assets/css/style.css");
					targetStyleFile.getParentFile().mkdirs();
					FileUtil.writeFile(targetStyleFile.getAbsolutePath(), compiledCss.toString());
				} catch (Exception e) {
					Log.w("HomeActivity", "Failed to compile/write style.css to assets: " + e.getMessage());
				}
			}

			// If JS content was imported, write to external assets/js/script.js and create its logic block
			if (jsContent != null && !jsContent.trim().isEmpty()) {
				try {
					File targetJsFile = new File(extPath, "assets/js/script.js");
					targetJsFile.getParentFile().mkdirs();
					FileUtil.writeFile(targetJsFile.getAbsolutePath(), jsContent);

					// Parse JavaScript content into visual logic blocks
					HtmlCssImporter importer = new HtmlCssImporter(HomeActivity.this);
					List<Map<String, Object>> jsBlocksList = importer.importJsOnly(jsContent);

					File jsLogicFile = new File(logicDir, projectId + "_js_script_js.logic");
					FileUtil.writeFile(jsLogicFile.getAbsolutePath(), new Gson().toJson(jsBlocksList));
				} catch (Exception e) {
					Log.w("HomeActivity", "Failed to write script.js/logic to assets: " + e.getMessage());
				}
			}
		} catch (Exception e) {
			Log.w("HomeActivity", "Could not create external project dir for import");
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
				File dir = new File(getFilesDir(), "projects");
				String[] extensions = {".json", ".meta", ".theme", ".logic"};
				for (String ext : extensions) {
					File f = new File(dir, projectId + ext);
					if (f.exists()) f.delete();
				}

				// Delete page files (projectId_pageName.json)
				File[] files = dir.listFiles();
				if (files != null) {
					for (File f : files) {
						if (f.getName().startsWith(projectId + "_")) {
							f.delete();
						}
					}
				}

				// Delete page logic files inside projects/logic/
				File logicDir = new File(dir, "logic");
				File[] logicFiles = logicDir.listFiles();
				if (logicFiles != null) {
					for (File lf : logicFiles) {
						if (lf.getName().startsWith(projectId + "_") || lf.getName().startsWith(projectId + ".")) {
							lf.delete();
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
				File dir = new File(getFilesDir(), "projects");
				File metaFile = new File(dir, projectId + ".meta");
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

				// Update external config
				try {
					String extPath = Environment.getExternalStorageDirectory().getAbsolutePath()
						+ "/.dragweb/projects/" + projectId + "/project.config.json";
					File configFile = new File(extPath);
					if (configFile.exists()) {
						String configJson = FileUtil.readFile(extPath);
						Map<String, String> config = new Gson().fromJson(configJson,
							new TypeToken<Map<String, String>>(){}.getType());
						if (config != null) {
							config.put("name", newName);
							if (!newDesc.isEmpty()) config.put("description", newDesc);
							FileUtil.writeFile(extPath, new Gson().toJson(config));
						}
					}
				} catch (Exception e) {
					// ignore
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
