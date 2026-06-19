package sketchweb.gl;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

	private WidgetBuilderEngine engine;
	private WidgetUpdater widgetUpdater;
	private PageCodeGenerator codeGenerator;
	private ProjectDataManager projectDataManager;
	private WidgetSelector selector;
	private DropZoneManager dropZoneManager;
	private UndoRedoManager undoRedoManager;
	private ThemeManager themeManager;
	private ExportManager exportManager;
	private WidgetRegistry widgetRegistry;
	private LogicBlockManager logicBlockManager;
	private ManageBlocksWidgets customBlockManager;
	private HierarchyTreeAdapter hierarchyAdapter;
	private PageManager pageManager;
	private ActivityResultLauncher<Intent> logicBlockLauncher;
	private FileExplorerAdapter fileExplorerAdapter;

	private ArrayList<HashMap<String, Object>> widgets = new ArrayList<>();
	private ArrayList<HashMap<String, Object>> filteredWidgets = new ArrayList<>();
	private ArrayList<HashMap<String, Object>> design = new ArrayList<>();

	// Clipboard for copy/paste
	private Map<String, Object> widgetClipboard = null;

	private String projectId = "";
	private String projectName = "Untitled";

	// Views
	private LinearLayout main;
	private LinearLayout topBar;
	private LinearLayout screen;
	private LinearLayout rightPanel;
	private LinearLayout bottomPanel;
	private LinearLayout eventPanel;
	private LinearLayout assetsPanel;
	private NestedScrollView vscroll2;
	private Button button5, button4, delete, btnImportWidgets, btnImportImage, btnImportSvg;
	private Button btnDrawer, btnBack, btnUndo, btnRedo, btnTheme, btnExport;
	private Button btnAddLogicBlock, btnViewAllBlocks, btnResetLogic;
	private LinearLayout blockEditorContainer;
	private Button btnNewFolder;
	private Button btnCopyWidget, btnAddPage;
	private TextView textview2, tvAssetsPath;
	private RecyclerView recyclerview3, recyclerview1, recyclerviewRightPanel, rvAssets;
	private RecyclerView rvDrawerWidgets;
	private android.widget.Spinner widgetSpinner, spnPageSelector;
	private TabLayout tabLayout;
	private Chip chipBasic, chipLayout;
	private ChipGroup chipGroupBottom;
	private TextInputEditText etSearchWidget, etSearchHierarchy;
	private ChipGroup chipGroupCategories;

	private androidx.drawerlayout.widget.DrawerLayout drawerLayout;

	private ActivityResultLauncher<android.content.Intent> importWidgetLauncher;
	private ActivityResultLauncher<android.content.Intent> importAssetLauncher;
	private ActivityResultLauncher<android.content.Intent> importSvgLauncher;
	private ActivityResultLauncher<android.content.Intent> importZipLauncher;

	// Track if a dialog is currently showing to avoid duplicates
	private boolean isDialogShowing = false;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Register logic block activity launcher before setContentView
		logicBlockLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == Activity.RESULT_OK) {
					// Reload logic blocks from file after returning
					if (logicBlockManager != null) {
						File dir = new File(getFilesDir(), "projects");
						String pageName = pageManager != null ? pageManager.getCurrentPage() : "index";
						File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
						if (logicFile.exists()) {
							String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
							logicBlockManager.fromJson(logicJson);
						} else {
							logicBlockManager.fromJson("[]");
						}
						refreshLogicBlocksUI();
					}
				}
			}
		);
		EdgeToEdge.enable(this);
		setContentView(R.layout.main);

		registerLaunchers();
 		initialize(savedInstanceState);
		initializeLogic();

		// Store initial paddings to avoid cumulative padding on re-application
		final int topBarInitialTop = topBar != null ? topBar.getPaddingTop() : 0;
		final int bottomPanelInitialBottom = bottomPanel != null ? bottomPanel.getPaddingBottom() : 0;
		final int mainInitialLeft = main != null ? main.getPaddingLeft() : 0;
		final int mainInitialRight = main != null ? main.getPaddingRight() : 0;

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id._main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			
			// Apply top padding to topBar
			if (topBar != null) {
				topBar.setPadding(topBar.getPaddingLeft(), topBarInitialTop + systemBars.top, topBar.getPaddingRight(), topBar.getPaddingBottom());
			}
			
			// Apply bottom padding to bottomPanel
			if (bottomPanel != null) {
				bottomPanel.setPadding(bottomPanel.getPaddingLeft(), bottomPanel.getPaddingTop(), bottomPanel.getPaddingRight(), bottomPanelInitialBottom + systemBars.bottom);
			}

			// Apply left/right insets to the main container for landscape support
			if (main != null) {
				main.setPadding(mainInitialLeft + systemBars.left, 0, mainInitialRight + systemBars.right, 0);
			}

			return insets;
		});
	}

	private void registerLaunchers() {
		importWidgetLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == RESULT_OK && result.getData() != null) {
					android.net.Uri uri = result.getData().getData();
					if (uri != null) {
						try {
							InputStream is = getContentResolver().openInputStream(uri);
							byte[] buffer = new byte[is.available()];
							is.read(buffer);
							is.close();
							String json = new String(buffer, "UTF-8");
							widgetRegistry.importCustomWidgets(json);
							refreshWidgetList();
							Toast.makeText(this, "Widgets imported!", Toast.LENGTH_SHORT).show();
						} catch (Exception e) {
							Toast.makeText(this, "Failed to import widgets.", Toast.LENGTH_SHORT).show();
						}
					}
				}
			}
		);

		importAssetLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == RESULT_OK && result.getData() != null) {
					android.net.Uri uri = result.getData().getData();
					if (uri != null) saveAssetFile(uri);
				}
			}
		);

		importZipLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == RESULT_OK && result.getData() != null) {
					android.net.Uri uri = result.getData().getData();
					if (uri != null) importProjectBackup(uri);
				}
			}
		);

		importSvgLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == RESULT_OK && result.getData() != null) {
					android.net.Uri uri = result.getData().getData();
					if (uri != null) {
						try {
							InputStream is = getContentResolver().openInputStream(uri);
							byte[] buffer = new byte[is.available()];
							is.read(buffer);
							is.close();
							String svgContent = new String(buffer, "UTF-8");
							widgetRegistry.importCustomWidgets(svgContent); 
							refreshWidgetList();
							Toast.makeText(this, "SVG imported!", Toast.LENGTH_SHORT).show();
						} catch (Exception e) {
							Toast.makeText(this, "Failed to import SVG.", Toast.LENGTH_SHORT).show();
						}
					}
				}
			}
		);
	}

	private void initialize(Bundle savedInstanceState) {
		main = findViewById(R.id.main);
		topBar = findViewById(R.id.topBar);
		screen = findViewById(R.id.screen);
		rightPanel = findViewById(R.id.rightPanel);
		bottomPanel = findViewById(R.id.bottomPanel);
		eventPanel = findViewById(R.id.eventPanel);
		assetsPanel = findViewById(R.id.assetsPanel);
		vscroll2 = findViewById(R.id.vscroll2);
		button5 = findViewById(R.id.button5);
		button4 = findViewById(R.id.button4);
		delete = findViewById(R.id.delete);
		btnImportWidgets = findViewById(R.id.btnImportWidgets);
		btnImportImage = findViewById(R.id.btnImportImage);
		btnImportSvg = findViewById(R.id.btnImportSvg);
		btnNewFolder = findViewById(R.id.btnNewFolder);
		textview2 = findViewById(R.id.textview2);
		recyclerview3 = findViewById(R.id.recyclerview3);
		recyclerview1 = findViewById(R.id.recyclerview1);
		recyclerviewRightPanel = findViewById(R.id.recyclerviewRightPanel);
		rvAssets = findViewById(R.id.rvAssets);
		btnResetLogic = findViewById(R.id.btnResetLogic);
		rvDrawerWidgets = findViewById(R.id.rvDrawerWidgets);
		widgetSpinner = findViewById(R.id.widgetSpinner);
		spnPageSelector = findViewById(R.id.spnPageSelector);
		btnAddPage = findViewById(R.id.btnAddPage);
		btnDrawer = findViewById(R.id.btnDrawer);
		btnBack = findViewById(R.id.btnBack);
		btnUndo = findViewById(R.id.btnUndo);
		btnRedo = findViewById(R.id.btnRedo);
		btnTheme = findViewById(R.id.btnTheme);
		btnExport = findViewById(R.id.btnExport);
		btnAddLogicBlock = findViewById(R.id.btnAddLogicBlock);
		btnCopyWidget = findViewById(R.id.btnCopyWidget);
		tvAssetsPath = findViewById(R.id.tvAssetsPath);
		tabLayout = findViewById(R.id.tabLayout);
		chipBasic = findViewById(R.id.chipBasic);
		chipLayout = findViewById(R.id.chipLayout);
		chipGroupBottom = findViewById(R.id.chipGroupBottom);
		etSearchWidget = findViewById(R.id.etSearchWidget);
		etSearchHierarchy = findViewById(R.id.etSearchHierarchy);
		chipGroupCategories = findViewById(R.id.chipGroupCategories);
		blockEditorContainer = findViewById(R.id.blockEditorContainer);

		// Get project info from intent
		if (getIntent().hasExtra("project_id")) {
			projectId = getIntent().getStringExtra("project_id");
		}
		if (getIntent().hasExtra("project_name")) {
			projectName = getIntent().getStringExtra("project_name");
		}
		if (projectId.isEmpty() && !projectName.isEmpty()) {
			projectId = projectName;
		}

		// Drawer button - store reference as field for reliability
		drawerLayout = findViewById(R.id._main);
		btnDrawer.setOnClickListener(v -> {
			try {
				if (drawerLayout != null) {
					View drawerContent = findViewById(R.id.drawerContent);
					if (drawerContent != null) {
						if (drawerLayout.isDrawerOpen(drawerContent)) {
							drawerLayout.closeDrawer(drawerContent);
						} else {
							drawerLayout.openDrawer(drawerContent);
						}
					}
				}
			} catch (Exception e) {
				Log.w("MainActivity", "Drawer toggle error: " + e.getMessage());
			}
		});

		button5.setOnClickListener(v -> showPreview());
		button4.setOnClickListener(v -> saveProject());

		// Delete selected widget
		delete.setOnClickListener(v -> {
			View selected = selector.getSelectedView();
			if (selected != null) {
				((ViewGroup) selected.getParent()).removeView(selected);
				selector.clearSelection();
				textview2.setText("No widget selected");
				delete.setEnabled(false);
				saveUndoState();
				refreshHierarchy();
				updateWidgetSpinnerFromTree();
			}
		});


		// Copy/Duplicate widget button
		if (btnCopyWidget != null) {
			btnCopyWidget.setOnClickListener(v -> showCopyPasteMenu());
		}

		// Import Asset Button (Images, JS, CSS, etc.)
		btnImportImage.setOnClickListener(v -> {
			android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
			intent.setType("*/*");
			importAssetLauncher.launch(intent);
		});

		// Import JSON Button
		if (btnImportWidgets != null) {
			btnImportWidgets.setOnClickListener(v -> {
				android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
				intent.setType("*/*");
				String[] mimetypes = {"application/json", "text/plain"};
				intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimetypes);
				importWidgetLauncher.launch(intent);
			});
		}

		if (btnImportSvg != null) {
			btnImportSvg.setOnClickListener(v -> {
				android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
				intent.setType("image/svg+xml");
				importSvgLauncher.launch(intent);
			});
		}

		btnBack.setOnClickListener(v -> {
			saveProject();
			finish();
		});

		btnUndo.setOnClickListener(v -> {
			List<Map<String, Object>> state = undoRedoManager.undo();
			if (state != null) {
				restoreState(state);
				refreshHierarchy();
			}
		});

		btnRedo.setOnClickListener(v -> {
			List<Map<String, Object>> state = undoRedoManager.redo();
			if (state != null) {
				restoreState(state);
				refreshHierarchy();
			}
		});

		btnTheme.setOnClickListener(v -> {
			android.widget.PopupMenu popup = new android.widget.PopupMenu(this, v);
			popup.getMenu().add("Themes");
			popup.getMenu().add("Font Settings");
			popup.getMenu().add("Icon Libraries");
			popup.getMenu().add("Animation Library");
			popup.setOnMenuItemClickListener(item -> {
				if (item.getTitle().equals("Themes")) {
					showThemeDialog();
				} else if (item.getTitle().equals("Font Settings")) {
					showFontSettingsDialog();
				} else if (item.getTitle().equals("Icon Libraries")) {
					showIconLibrariesDialog();
				} else if (item.getTitle().equals("Animation Library")) {
					showAnimationLibraryDialog();
				}
				return true;
			});
			popup.show();
		});

		btnExport.setOnClickListener(v -> showExportDialog());
		btnNewFolder.setOnClickListener(v -> {
			new UniversalM3Dialog(this)
				.setTitle("New Folder")
				.setHint("Folder name")
				.showTextInput(name -> {
					if (!name.isEmpty()) createFolder(name);
				});
		});

		btnAddLogicBlock.setOnClickListener(v -> {
			// Launch the dedicated Logic Block Activity
			Intent intent = new Intent(this, LogicBlockActivity.class);
			intent.putExtra("project_id", projectId);
			intent.putExtra("page_name", pageManager != null ? pageManager.getCurrentPage() : "index");
			logicBlockLauncher.launch(intent);
		});

		if (btnResetLogic != null) {
			btnResetLogic.setOnClickListener(v -> {
				new MaterialAlertDialogBuilder(this)
					.setTitle("Reset Logic")
					.setMessage("Are you sure you want to delete all logic blocks for this page? This cannot be undone.")
					.setPositiveButton("Reset", (d, w) -> {
						try {
							String page = pageManager != null ? pageManager.getCurrentPage() : "index";
							File dir = new File(getFilesDir(), "projects");
							File logicFile = new File(dir, projectId + "_" + page + ".logic");
							if (logicFile.exists()) {
								if (logicFile.delete()) {
									if (page.equals(pageManager != null ? pageManager.getCurrentPage() : "index")) {
										logicBlockManager.clear();
									}
									populateEventList();
									Toast.makeText(this, "Logic reset successfully", Toast.LENGTH_SHORT).show();
								}
							}
						} catch (Exception e) {
							Toast.makeText(this, "Reset failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
						}
					})
					.setNegativeButton("Cancel", null)
					.show();
			});
		}


		setupTabLayout();

		setupBottomChips();
		setupWidgetSearch();
		setupHierarchySearch();
		setupDrawerCategories();

	}

	private void setupDrawerCategories() {
		com.google.android.material.chip.ChipGroup chipGroup = findViewById(R.id.chipGroupDrawerCategories);
		if (chipGroup == null) return;
		chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
			String filter = "all";
			if (!checkedIds.isEmpty()) {
				int checkedId = checkedIds.get(0);
				if (checkedId == R.id.chipDrawerLayout) filter = "layout";
				else if (checkedId == R.id.chipDrawerBasic) filter = "basic";
				else if (checkedId == R.id.chipDrawerForm) filter = "form";
			}
			filteredWidgets.clear();
			for (HashMap<String, Object> w : widgets) {
				if ("all".equals(filter) || filter.equalsIgnoreCase(String.valueOf(w.get("category")))) {
					filteredWidgets.add(w);
				}
			}
			if (rvDrawerWidgets != null && rvDrawerWidgets.getAdapter() != null) {
				rvDrawerWidgets.getAdapter().notifyDataSetChanged();
			}
		});
	}

	private void initializeLogic() {
		engine = new WidgetBuilderEngine(this);
		widgetUpdater = new WidgetUpdater(this, engine);
		codeGenerator = new PageCodeGenerator();
		codeGenerator.setProjectInfo(projectName, getProjectLogoPath());
		projectDataManager = new ProjectDataManager(this);
		themeManager = new ThemeManager();
		exportManager = new ExportManager(this, themeManager);
		exportManager.setProjectId(projectId);
		// Wire the persisted IconLibraryManager so generated HTML and ZIP
		// exports pick up whichever icon CDNs the project has enabled.
		IconLibraryManager iconMgr = new IconLibraryManager(this, projectId);
		exportManager.setIconLibraryManager(iconMgr);
		codeGenerator.setIconLibraryManager(iconMgr);
		AnimationLibraryManager animMgr = new AnimationLibraryManager(this, projectId);
		exportManager.setAnimationLibraryManager(animMgr);
		codeGenerator.setAnimationLibraryManager(animMgr);
		logicBlockManager = new LogicBlockManager(this);
		customBlockManager = new ManageBlocksWidgets(this);
		pageManager = new PageManager(this, projectId);

		// Load logic blocks for the initial page
		File dir = new File(getFilesDir(), "projects");
		String pageName = pageManager != null ? pageManager.getCurrentPage() : "index";
		File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
		if (logicFile.exists()) {
			String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
			logicBlockManager.fromJson(logicJson);
		} else {
			// fallback for older projects, or clear
			File oldLogicFile = new File(dir, projectId + ".logic");
			if (oldLogicFile.exists()) {
				String logicJson = FileUtil.readFile(oldLogicFile.getAbsolutePath());
				logicBlockManager.fromJson(logicJson);
			}
		}

		// Set up block editor in event panel
		if (blockEditorContainer != null) {
			blockEditorContainer.removeAllViews();
		}

		// Undo/Redo
		undoRedoManager = new UndoRedoManager();
		undoRedoManager.setOnStateChangeListener((canUndo, canRedo) -> {
			btnUndo.setEnabled(canUndo);
			btnRedo.setEnabled(canRedo);
		});

		// Widget selector
		selector = new WidgetSelector(this);
		selector.setOnWidgetSelectedListener(widgetId -> {
			textview2.setText("Selected: " + widgetId);
			delete.setEnabled(true);
			updateWidgetSpinnerFromTree();
			if (hierarchyAdapter != null) {
				hierarchyAdapter.setSelectedView(selector.getSelectedView());
			}
			
			// Only build the list for the currently selected chip
			if (chipGroupBottom != null) {
				int checkedId = chipGroupBottom.getCheckedChipId();
				if (checkedId == R.id.chipLayout) {
					buildLayoutDesignList();
				} else {
					// Default to Basic
					buildDesignList();
				}
			} else {
				buildDesignList();
			}
		});
		selector.attachTo(screen);

		updateWidgetSpinnerFromTree();

		// Load widgets
		widgetRegistry = new WidgetRegistry(this);
		widgets = widgetRegistry.getAllWidgets();
		filteredWidgets = new ArrayList<>(widgets);

		autoLoadCustomConfigs();

		// Set up widget list in right panel (vertical)
		recyclerview1.setAdapter(new Recyclerview1Adapter(filteredWidgets));
		recyclerview1.setLayoutManager(new LinearLayoutManager(this));

		// Set up widget list in drawer (vertical - bigger)
		if (rvDrawerWidgets != null) {
			rvDrawerWidgets.setAdapter(new Recyclerview1Adapter(filteredWidgets));
			rvDrawerWidgets.setLayoutManager(new LinearLayoutManager(this));
		}

		// Setup hierarchy tree
		hierarchyAdapter = new HierarchyTreeAdapter(this);
		hierarchyAdapter.setOnItemClickListener(widgetView -> {
			selector.clearSelection();
			widgetView.performClick();
		});
		hierarchyAdapter.setOnItemLongClickListener(widgetView -> {
			showWidgetContextMenu(widgetView);
		});
		hierarchyAdapter.setOnReorderListener((movedView, newParent, newIndex) -> {
			saveUndoState();
			updateWidgetSpinnerFromTree();
		});
		recyclerviewRightPanel.setAdapter(hierarchyAdapter);
		recyclerviewRightPanel.setLayoutManager(new LinearLayoutManager(this));
		hierarchyAdapter.attachToRecyclerView(recyclerviewRightPanel);

		// Drop zone
		dropZoneManager = new DropZoneManager(this, screen, widgets, engine, selector);
		dropZoneManager.setOnTreeChangedListener(() -> {
			saveUndoState();
			updateWidgetSpinnerFromTree();
			refreshHierarchy();
		});
		setupCanvasDragListener();

		// File explorer for assets
		setupFileExplorer();

		// Page selector
		setupPageSelector();

		// Deselect on main click
		main.setOnClickListener(v -> {
			View selected = selector.getSelectedView();
			if (selected != null) {
				selected.setBackgroundColor(Color.TRANSPARENT);
				selector.clearSelection();
				textview2.setText("No widget selected");
				delete.setEnabled(false);
				if (hierarchyAdapter != null) {
					hierarchyAdapter.setSelectedView(null);
				}
			}
		});

		buildDesignList();
		loadProject();
		saveUndoState();
		refreshHierarchy();
	}

	// ---- Add Logic Block with target selector ----


	// ---- Widget Context Menu (long press on hierarchy) ----

	private void showWidgetContextMenu(View widgetView) {
		String[] options = {"Copy", "Duplicate", "Lock/Unlock", "Hide/Show", "Delete"};
		new MaterialAlertDialogBuilder(this)
			.setTitle("Widget Actions")
			.setItems(options, (dialog, which) -> {
				switch (which) {
					case 0: copyWidget(widgetView); break;
					case 1: duplicateWidget(widgetView); break;
					case 2: toggleLockWidget(widgetView); break;
					case 3: toggleHideWidget(widgetView); break;
					case 4: deleteWidget(widgetView); break;
				}
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void copyWidget(View widgetView) {
		Object tagObj = widgetView.getTag();
		if (tagObj instanceof Map) {
			widgetClipboard = new HashMap<>((Map<String, Object>) tagObj);
			// Serialize children if it's a container
			if (widgetView instanceof ViewGroup) {
				List<Map<String, Object>> children = serializeChildren((ViewGroup) widgetView);
				if (!children.isEmpty()) {
					widgetClipboard.put("children", children);
				}
			}
			Toast.makeText(this, "Widget copied", Toast.LENGTH_SHORT).show();
		}
	}

	private void pasteWidget() {
		if (widgetClipboard == null) {
			Toast.makeText(this, "Nothing to paste", Toast.LENGTH_SHORT).show();
			return;
		}
		rebuildView(new HashMap<>(widgetClipboard), screen);
		saveUndoState();
		refreshHierarchy();
		updateWidgetSpinnerFromTree();
		Toast.makeText(this, "Widget pasted", Toast.LENGTH_SHORT).show();
	}

	private void duplicateWidget(View widgetView) {
		if (!(widgetView.getParent() instanceof ViewGroup)) return;
		ViewGroup parent = (ViewGroup) widgetView.getParent();
		Object tagObj = widgetView.getTag();
		if (tagObj instanceof Map) {
			Map<String, Object> original = new HashMap<>((Map<String, Object>) tagObj);
			if (widgetView instanceof ViewGroup) {
				List<Map<String, Object>> children = serializeChildren((ViewGroup) widgetView);
				if (!children.isEmpty()) {
					original.put("children", children);
				}
			}
			int index = parent.indexOfChild(widgetView);
			rebuildViewAt(original, parent, index + 1);
			saveUndoState();
			refreshHierarchy();
			updateWidgetSpinnerFromTree();
			Toast.makeText(this, "Widget duplicated", Toast.LENGTH_SHORT).show();
		}
	}

	private void toggleLockWidget(View widgetView) {
		Object tagObj = widgetView.getTag();
		if (tagObj instanceof Map) {
			Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
			boolean locked = Boolean.TRUE.equals(widgetMap.get("locked"));
			widgetMap.put("locked", !locked);
			widgetView.setTag(widgetMap);
			refreshHierarchy();
			Toast.makeText(this, locked ? "Widget unlocked" : "Widget locked", Toast.LENGTH_SHORT).show();
		}
	}

	private void toggleHideWidget(View widgetView) {
		Object tagObj = widgetView.getTag();
		if (tagObj instanceof Map) {
			Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
			boolean hidden = Boolean.TRUE.equals(widgetMap.get("hidden"));
			widgetMap.put("hidden", !hidden);
			widgetView.setVisibility(hidden ? View.VISIBLE : View.GONE);
			widgetView.setTag(widgetMap);
			refreshHierarchy();
			Toast.makeText(this, hidden ? "Widget visible" : "Widget hidden", Toast.LENGTH_SHORT).show();
		}
	}

	private void deleteWidget(View widgetView) {
		if (widgetView.getParent() instanceof ViewGroup) {
			((ViewGroup) widgetView.getParent()).removeView(widgetView);
			selector.clearSelection();
			textview2.setText("No widget selected");
			delete.setEnabled(false);
			saveUndoState();
			refreshHierarchy();
			updateWidgetSpinnerFromTree();
		}
	}

	private List<Map<String, Object>> serializeChildren(ViewGroup parent) {
		List<Map<String, Object>> nodes = new ArrayList<>();
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			Object tagObj = child.getTag();
			if (tagObj instanceof Map) {
				Map<String, Object> widgetMap = new HashMap<>((Map<String, Object>) tagObj);
				if (child instanceof ViewGroup) {
					List<Map<String, Object>> children = serializeChildren((ViewGroup) child);
					if (!children.isEmpty()) {
						widgetMap.put("children", children);
					}
				}
				nodes.add(widgetMap);
			}
		}
		return nodes;
	}

	// ---- Copy/Paste Menu ----

	private void showCopyPasteMenu() {
		List<String> options = new ArrayList<>();
		options.add("Copy Selected Widget");
		options.add("Paste Widget");
		options.add("Duplicate Selected Widget");
		options.add("Duplicate Multiple…");

		new MaterialAlertDialogBuilder(this)
			.setTitle("Copy / Paste")
			.setItems(options.toArray(new String[0]), (dialog, which) -> {
				switch (which) {
					case 0:
						View selected = selector.getSelectedView();
						if (selected != null) copyWidget(selected);
						else Toast.makeText(this, "Select a widget first", Toast.LENGTH_SHORT).show();
						break;
					case 1:
						pasteWidget();
						break;
					case 2:
						View sel2 = selector.getSelectedView();
						if (sel2 != null) duplicateWidget(sel2);
						else Toast.makeText(this, "Select a widget first", Toast.LENGTH_SHORT).show();
						break;
					case 3:
						showDuplicateMultiPicker();
						break;
				}
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	/**
	 * Multi-select duplicate: gather every widget on the canvas, let the user
	 * tick the ones they want, then duplicate each in place. Avoids the
	 * select-one-tap-duplicate-repeat treadmill for users who need to clone
	 * several siblings at once.
	 */
	private void showDuplicateMultiPicker() {
		List<View> flat = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		collectWidgetsForPicker(screen, flat, labels, 0);
		if (flat.isEmpty()) {
			Toast.makeText(this, "Nothing to duplicate yet", Toast.LENGTH_SHORT).show();
			return;
		}
		boolean[] checked = new boolean[flat.size()];
		new MaterialAlertDialogBuilder(this)
			.setTitle("Pick widgets to duplicate")
			.setMultiChoiceItems(labels.toArray(new String[0]), checked,
				(d, which, isChecked) -> checked[which] = isChecked)
			.setPositiveButton("Duplicate", (d, w) -> {
				int count = 0;
				for (int i = 0; i < flat.size(); i++) {
					if (checked[i]) {
						duplicateWidget(flat.get(i));
						count++;
					}
				}
				Toast.makeText(this, count + " duplicated", Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	@SuppressWarnings("unchecked")
	private void collectWidgetsForPicker(ViewGroup parent, List<View> out, List<String> labels, int depth) {
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			Object tagObj = child.getTag();
			if (tagObj instanceof Map) {
				Map<String, Object> tagData = (Map<String, Object>) tagObj;
				String tag = tagData.containsKey("tag") ? tagData.get("tag").toString() : "view";
				String id = "";
				Map<String, Object> fn = (Map<String, Object>) tagData.get("function");
				if (fn != null && fn.containsKey("id")) id = fn.get("id").toString();
				StringBuilder pad = new StringBuilder();
				for (int d = 0; d < depth; d++) pad.append("  ");
				labels.add(pad + "<" + tag + ">" + (id.isEmpty() ? "" : " #" + id));
				out.add(child);
			}
			if (child instanceof ViewGroup) {
				collectWidgetsForPicker((ViewGroup) child, out, labels, depth + 1);
			}
		}
	}

	// ---- File Explorer ----

	private void setupFileExplorer() {
		String assetsPath = Environment.getExternalStorageDirectory().getAbsolutePath()
			+ "/.dragweb/projects/" + projectId + "/assets";
		File assetsDir = new File(assetsPath);
		if (!assetsDir.exists()) assetsDir.mkdirs();

		fileExplorerAdapter = new FileExplorerAdapter(this, assetsDir);
		fileExplorerAdapter.setOnFileClickListener(file -> {
			if (file.isDirectory()) {
				updateAssetsPath();
			}
		});
		fileExplorerAdapter.setOnFileLongClickListener(file -> {
			showFileContextMenu(file);
		});

		if (rvAssets != null) {
			rvAssets.setAdapter(fileExplorerAdapter);
			rvAssets.setLayoutManager(new LinearLayoutManager(this));
			fileExplorerAdapter.navigateTo(assetsDir);
			updateAssetsPath();
		}
	}

	private void updateAssetsPath() {
		if (tvAssetsPath != null && fileExplorerAdapter != null) {
			tvAssetsPath.setText(fileExplorerAdapter.getRelativePath());
		}
	}

	private void showFileContextMenu(File file) {
		String[] options = file.isDirectory() ?
			new String[]{"Open", "Delete"} :
			new String[]{"Use as Image Source", "Delete"};

		new MaterialAlertDialogBuilder(this)
			.setTitle(file.getName())
			.setItems(options, (dialog, which) -> {
				if (file.isDirectory()) {
					if (which == 0) fileExplorerAdapter.navigateTo(file);
					else deleteFileWithConfirm(file);
				} else {
					if (which == 0) useFileAsImageSource(file);
					else deleteFileWithConfirm(file);
				}
				updateAssetsPath();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void useFileAsImageSource(File file) {
		View selected = selector.getSelectedView();
		if (selected instanceof ImageView) {
			try {
				java.io.FileInputStream fis = new java.io.FileInputStream(file);
				java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
				byte[] buf = new byte[4096];
				int len;
				while ((len = fis.read(buf)) != -1) bos.write(buf, 0, len);
				fis.close();
				byte[] data = bos.toByteArray();
				String base64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
				String mimeType = "image/png";
				String name = file.getName().toLowerCase();
				if (name.endsWith(".jpg") || name.endsWith(".jpeg")) mimeType = "image/jpeg";
				else if (name.endsWith(".svg")) mimeType = "image/svg+xml";
				else if (name.endsWith(".gif")) mimeType = "image/gif";
				else if (name.endsWith(".webp")) mimeType = "image/webp";

				String src = "data:" + mimeType + ";base64," + base64;
				Map<String, Object> style = new HashMap<>();
				style.put("src", src);
				widgetUpdater.updateWidget(selected, "", style);
				saveUndoState();
				Toast.makeText(this, "Image source set", Toast.LENGTH_SHORT).show();
			} catch (Exception e) {
				Toast.makeText(this, "Could not load file", Toast.LENGTH_SHORT).show();
			}
		} else {
			Toast.makeText(this, "Select an Image widget first", Toast.LENGTH_SHORT).show();
		}
	}

	private List<String> getColorSuggestions() {
		List<String> list = new ArrayList<>();
		if (themeManager != null) {
			for (String key : themeManager.getAllStyles().keySet()) {
				if (key.toLowerCase().contains("color") || key.toLowerCase().contains("background")) {
					list.add("var(--" + ThemeManager.camelToKebab(key) + ")");
				}
			}
			for (String key : themeManager.getCustomCssVars().keySet()) {
				list.add("var(--" + (key.startsWith("--") ? key.substring(2) : key) + ")");
			}
		}
		return list;
	}

	private void setupPageSelector() {
		if (spnPageSelector == null || pageManager == null) return;
		updatePageSpinner();

		if (btnAddPage != null) {
			btnAddPage.setOnClickListener(v -> showAddPageDialog());
		}
	}

	private void updatePageSpinner() {
		List<String> pages = pageManager.getPages();
		android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
			this, android.R.layout.simple_spinner_item, pages);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spnPageSelector.setAdapter(adapter);

		int currentIdx = pages.indexOf(pageManager.getCurrentPage());
		if (currentIdx >= 0) spnPageSelector.setSelection(currentIdx);

		spnPageSelector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
				if (position < 0 || position >= pages.size()) return;
				String selectedPage = pages.get(position);
				if (!selectedPage.equals(pageManager.getCurrentPage())) {
					// Save current page layout with all nested children
					saveCurrentPageLayout();

					// Save current logic blocks for current page
					if (logicBlockManager != null) {
						File dir = new File(getFilesDir(), "projects");
						if (!dir.exists()) dir.mkdirs();
						String oldPageName = pageManager.getCurrentPage();
						File logicFile = new File(dir, projectId + "_" + oldPageName + ".logic");
						FileUtil.writeFile(logicFile.getAbsolutePath(), logicBlockManager.toJson());
					}

					// Switch to new page
					pageManager.setCurrentPage(selectedPage);

					// Load the new page layout
					loadCurrentPageLayout();

					// Load logic blocks for new page
					if (logicBlockManager != null) {
						File dir = new File(getFilesDir(), "projects");
						File logicFile = new File(dir, projectId + "_" + selectedPage + ".logic");
						if (logicFile.exists()) {
							String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
							logicBlockManager.fromJson(logicJson);
						} else {
							logicBlockManager.fromJson("[]"); // clear for new page
						}
						refreshLogicBlocksUI();
					}
				}
			}
			@Override
			public void onNothingSelected(android.widget.AdapterView<?> parent) {}
		});
	}

	private void showAddPageDialog() {
		new UniversalM3Dialog(this)
			.setTitle("New Page")
			.setHint("Page name (e.g. about)")
			.showTextInput(value -> {
				String pageName = value.trim().replaceAll("[^a-zA-Z0-9_-]", "");
				if (pageName.isEmpty()) {
					Toast.makeText(this, "Invalid page name", Toast.LENGTH_SHORT).show();
					return;
				}
				saveCurrentPageLayout();
				pageManager.addPage(pageName);
				pageManager.setCurrentPage(pageName);
				screen.removeAllViews();
				saveCurrentPageLayout();
				saveUndoState();
				refreshHierarchy();
				updatePageSpinner();
				Toast.makeText(this, "Page '" + pageName + "' created", Toast.LENGTH_SHORT).show();
			});
	}

	private void saveCurrentPageLayout() {
		List<Map<String, Object>> widgetTree = serializeChildren(screen);
		String json = new Gson().toJson(widgetTree);
		pageManager.savePageLayout(pageManager.getCurrentPage(), json);
	}

	private void loadCurrentPageLayout() {
		String json = pageManager.loadPageLayout(pageManager.getCurrentPage());
		screen.removeAllViews();
		try {
			List<Map<String, Object>> widgetTree = new Gson().fromJson(json,
				new TypeToken<List<Map<String, Object>>>(){}.getType());
			if (widgetTree != null) {
				for (Map<String, Object> nodeMap : widgetTree) {
					rebuildView(nodeMap, screen);
				}
			}
		} catch (Exception e) {
			Log.w("MainActivity", "Could not load page layout: " + e.getMessage());
		}
		saveCurrentPageLayout();
		selector.clearSelection();
		selector.attachTo(screen);
		textview2.setText("No widget selected");
		delete.setEnabled(false);
		undoRedoManager.clear();
		saveUndoState();
		refreshHierarchy();
		updateWidgetSpinnerFromTree();
	}

	// ---- Widget Search ----

	private void setupWidgetSearch() {
		if (etSearchWidget == null) return;
		etSearchWidget.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				filterWidgetsBySearch(s.toString());
			}
			@Override
			public void afterTextChanged(Editable s) {}
		});
	}

	private void filterWidgetsBySearch(String query) {
		filteredWidgets.clear();
		if (query.isEmpty()) {
			filteredWidgets.addAll(widgets);
		} else {
			String lowerQuery = query.toLowerCase();
			for (HashMap<String, Object> widget : widgets) {
				String name = widget.containsKey("name") ? widget.get("name").toString().toLowerCase() : "";
				String tag = widget.containsKey("tag") ? widget.get("tag").toString().toLowerCase() : "";
				String category = widget.containsKey("category") ? widget.get("category").toString().toLowerCase() : "";
				if (name.contains(lowerQuery) || tag.contains(lowerQuery) || category.contains(lowerQuery)) {
					filteredWidgets.add(widget);
				}
			}
		}
		notifyWidgetAdapters();
	}

	private void setupHierarchySearch() {
		if (etSearchHierarchy == null) return;
		etSearchHierarchy.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (hierarchyAdapter != null) {
					hierarchyAdapter.setFilter(s.toString());
				}
			}
			@Override
			public void afterTextChanged(Editable s) {}
		});
	}

	// ---- Tab Layout ----

	private void setupTabLayout() {
		tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			@Override
			public void onTabSelected(TabLayout.Tab tab) {
				switch (tab.getPosition()) {
					case 0: showViewMode(); break;
					case 1: showEventMode(); break;
					case 2: showAssetsMode(); break;
				}
			}
			@Override
			public void onTabUnselected(TabLayout.Tab tab) {}
			@Override
			public void onTabReselected(TabLayout.Tab tab) {}
		});
	}

	private void showViewMode() {
		vscroll2.setVisibility(View.VISIBLE);
		eventPanel.setVisibility(View.GONE);
		if (assetsPanel != null) assetsPanel.setVisibility(View.GONE);
		bottomPanel.setVisibility(View.VISIBLE);
		View rightPanelCard = findViewById(R.id.rightPanelCard);
		if (rightPanelCard != null) rightPanelCard.setVisibility(View.VISIBLE);
	}

	private void showEventMode() {
		vscroll2.setVisibility(View.GONE);
		eventPanel.setVisibility(View.VISIBLE);
		if (assetsPanel != null) assetsPanel.setVisibility(View.GONE);
		bottomPanel.setVisibility(View.GONE);
		View rightPanelCard = findViewById(R.id.rightPanelCard);
		if (rightPanelCard != null) rightPanelCard.setVisibility(View.GONE);
		refreshLogicBlocksUI();
	}

	private void showAssetsMode() {
		vscroll2.setVisibility(View.GONE);
		eventPanel.setVisibility(View.GONE);
		if (assetsPanel != null) assetsPanel.setVisibility(View.VISIBLE);
		bottomPanel.setVisibility(View.GONE);
		View rightPanelCard = findViewById(R.id.rightPanelCard);
		if (rightPanelCard != null) rightPanelCard.setVisibility(View.GONE);
		// Refresh file list
		if (fileExplorerAdapter != null) {
			fileExplorerAdapter.navigateTo(fileExplorerAdapter.getCurrentDir());
			updateAssetsPath();
		}
	}



	private void notifyWidgetAdapters() {
		if (recyclerview1 != null && recyclerview1.getAdapter() != null) {
			recyclerview1.getAdapter().notifyDataSetChanged();
		}
		if (rvDrawerWidgets != null && rvDrawerWidgets.getAdapter() != null) {
			rvDrawerWidgets.getAdapter().notifyDataSetChanged();
		}
	}

	private void setupBottomChips() {
		if (chipGroupBottom == null) return;
		chipGroupBottom.setOnCheckedStateChangeListener((group, checkedIds) -> {
			if (checkedIds.isEmpty()) return;
			int id = checkedIds.get(0);
			if (id == R.id.chipBasic) {
				buildDesignList();
			} else if (id == R.id.chipLayout) {
				buildLayoutDesignList();
			}
		});
	}

	private void refreshWidgetList() {
		widgets = widgetRegistry.getAllWidgets();
		filteredWidgets.clear();
		filteredWidgets.addAll(widgets);
		notifyWidgetAdapters();
	}

	private void refreshHierarchy() {
		if (hierarchyAdapter != null) {
			hierarchyAdapter.buildTree(screen);
		}
	}

	// ---- Widget Spinner ----

	private void updateWidgetSpinnerFromTree() {
		List<String> items = new ArrayList<>();
		items.add("body (screen)");
		collectWidgetNames(screen, items, 0);

		int selectedIndex = 0;
		View selectedView = selector.getSelectedView();
		if (selectedView != null) {
			selectedIndex = findViewIndexInTree(screen, selectedView, new int[]{1});
			if (selectedIndex < 0 || selectedIndex >= items.size()) {
				selectedIndex = 0;
			}
		}

		android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
			this, android.R.layout.simple_spinner_item, items);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		widgetSpinner.setAdapter(adapter);
		widgetSpinner.setSelection(selectedIndex);

		widgetSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
				if (position == 0) return;
				View targetView = findViewAtTreeIndex(screen, position, new int[]{1});
				if (targetView != null && targetView != selector.getSelectedView()) {
					selector.clearSelection();
					targetView.performClick();
				}
			}
			@Override
			public void onNothingSelected(android.widget.AdapterView<?> parent) {}
		});
	}

	private void collectWidgetNames(ViewGroup parent, List<String> items, int depth) {
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			Object tagObj = child.getTag();
			String prefix = "";
			for (int d = 0; d < depth; d++) prefix += "  ";

			if (tagObj instanceof Map) {
				Map<String, Object> tagData = (Map<String, Object>) tagObj;
				String tag = tagData.containsKey("tag") ? tagData.get("tag").toString() : "?";
				String id = "";
				Map<String, Object> fn = (Map<String, Object>) tagData.get("function");
				if (fn != null && fn.containsKey("id")) {
					id = fn.get("id").toString();
				}
				String text = "";
				if (fn != null && fn.containsKey("text")) {
					text = fn.get("text").toString();
					if (text.length() > 12) text = text.substring(0, 12) + "..";
				}
				String label = prefix + "<" + tag + ">";
				if (!id.isEmpty()) label += " #" + id;
				if (!text.isEmpty()) label += " \"" + text + "\"";
				items.add(label);
			} else {
				items.add(prefix + "view");
			}

			if (child instanceof ViewGroup) {
				collectWidgetNames((ViewGroup) child, items, depth + 1);
			}
		}
	}

	private int findViewIndexInTree(ViewGroup parent, View target, int[] counter) {
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			if (child == target) return counter[0];
			counter[0]++;
			if (child instanceof ViewGroup) {
				int found = findViewIndexInTree((ViewGroup) child, target, counter);
				if (found >= 0) return found;
			}
		}
		return -1;
	}

	private View findViewAtTreeIndex(ViewGroup parent, int targetIndex, int[] counter) {
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			if (counter[0] == targetIndex) return child;
			counter[0]++;
			if (child instanceof ViewGroup) {
				View found = findViewAtTreeIndex((ViewGroup) child, targetIndex, counter);
				if (found != null) return found;
			}
		}
		return null;
	}

	private void setupCanvasDragListener() {
		screen.setOnDragListener((v, event) -> {
			int action = event.getAction();
			switch (action) {
				case DragEvent.ACTION_DRAG_STARTED:
					return event.getClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN);
				case DragEvent.ACTION_DRAG_ENTERED:
					v.setBackgroundColor(Color.parseColor("#90A4AE"));
					return true;
				case DragEvent.ACTION_DRAG_EXITED:
					v.setBackgroundColor(Color.parseColor("#B0BEC5"));
					return true;
				case DragEvent.ACTION_DROP:
					v.setBackgroundColor(Color.parseColor("#B0BEC5"));
					ClipData data = event.getClipData();
					if (data != null && data.getItemCount() > 0) {
						try {
							String dragText = data.getItemAt(0).getText().toString();
							if (dragText.startsWith("reorder:")) {
								handleReorderDrop(dragText, event);
								return true;
							}
							int pos = Integer.parseInt(dragText);
							if (pos < 0 || pos >= filteredWidgets.size()) return false;
							Map<String, Object> widgetDefinition = filteredWidgets.get(pos);
							View newWidgetView = engine.createWidget(widgetDefinition.get("tag").toString());
							if (newWidgetView != null) {
								applyWidgetDefaults(newWidgetView, widgetDefinition);
								int targetIndex = findDropIndex(screen, event.getY());
								if (targetIndex >= 0 && targetIndex <= screen.getChildCount()) {
									screen.addView(newWidgetView, targetIndex);
								} else {
									screen.addView(newWidgetView);
								}
								selector.registerView(newWidgetView);
								setupWidgetReorderDrag(newWidgetView);
								newWidgetView.performClick();
								dropZoneManager.registerWidgetAsDropZoneIfContainer(newWidgetView);
								saveUndoState();
								refreshHierarchy();
								updateWidgetSpinnerFromTree();
							}
						} catch (NumberFormatException e) {
							Toast.makeText(this, "Error parsing widget position.", Toast.LENGTH_SHORT).show();
						} catch (Exception e) {
							Toast.makeText(this, "Error creating widget.", Toast.LENGTH_SHORT).show();
							Log.e("MainActivity", "Error: " + e.getMessage());
						}
					}
					return true;
				case DragEvent.ACTION_DRAG_ENDED:
					v.setBackgroundColor(Color.parseColor("#B0BEC5"));
					return true;
				case DragEvent.ACTION_DRAG_LOCATION:
					return true;
				default:
					return false;
			}
		});
	}

	private void applyWidgetDefaults(View newWidgetView, Map<String, Object> widgetDefinition) {
		Map<String, Object> newWidgetMap = (Map<String, Object>) newWidgetView.getTag();
		if (newWidgetMap != null) {
			Map<String, Object> defFunction = (Map<String, Object>) widgetDefinition.get("function");
			if (defFunction != null) {
				Map<String, Object> newFunction = (Map<String, Object>) newWidgetMap.get("function");
				if (newFunction == null) {
					newFunction = new HashMap<>();
					newWidgetMap.put("function", newFunction);
				}
				for (Map.Entry<String, Object> entry : defFunction.entrySet()) {
					if (!"style".equals(entry.getKey())) {
						newFunction.put(entry.getKey(), entry.getValue());
					}
				}
				Map<String, Object> defStyle = (Map<String, Object>) defFunction.get("style");
				if (defStyle != null) {
					Map<String, Object> newStyle = (Map<String, Object>) newFunction.get("style");
					if (newStyle == null) {
						newStyle = new HashMap<>();
						newFunction.put("style", newStyle);
					}
					newStyle.putAll(defStyle);
				}
			}
		}
		engine.applyPropertiesToView(newWidgetView, (Map<String, Object>) newWidgetView.getTag());
	}

	private int findDropIndex(ViewGroup parent, float dropY) {
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			float centerY = child.getY() + (child.getHeight() / 2f);
			if (dropY < centerY) {
				return i;
			}
		}
		return parent.getChildCount();
	}

	private void setupWidgetReorderDrag(View widget) {
		widget.setOnLongClickListener(v -> {
			// Check if widget is locked
			Object tagObj = v.getTag();
			if (tagObj instanceof Map) {
				Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
				if (Boolean.TRUE.equals(widgetMap.get("locked"))) {
					Toast.makeText(this, "Widget is locked", Toast.LENGTH_SHORT).show();
					return true;
				}
			}
			ClipData.Item item = new ClipData.Item("reorder:" + v.hashCode());
			ClipData dragData = new ClipData("reorder", new String[]{"text/plain"}, item);
			View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
			v.startDragAndDrop(dragData, shadow, v, 0);
			return true;
		});
	}

	private void handleReorderDrop(String dragText, DragEvent event) {
		int hash = Integer.parseInt(dragText.replace("reorder:", ""));
		View draggedView = findViewByHash(screen, hash);
		if (draggedView != null && draggedView.getParent() instanceof ViewGroup) {
			ViewGroup oldParent = (ViewGroup) draggedView.getParent();
			oldParent.removeView(draggedView);
			int newIndex = findDropIndex(screen, event.getY());
			screen.addView(draggedView, Math.min(newIndex, screen.getChildCount()));
			// Re-register for reorder drag and nested drop zones
			setupWidgetReorderDrag(draggedView);
			dropZoneManager.registerWidgetAsDropZoneIfContainer(draggedView);
			saveUndoState();
			refreshHierarchy();
			updateWidgetSpinnerFromTree();
		}
	}

	/** Get the project logo path from assets storage. */
	private String getProjectLogoPath() {
		String configuredPath = getLogoPathFromProjectConfig();
		if (!configuredPath.isEmpty()) {
			String abs = configuredPathToAbsolute(configuredPath);
			if (!abs.isEmpty()) {
				return abs;
			}
		}
		try {
			String assetsPath = Environment.getExternalStorageDirectory().getAbsolutePath()
				+ "/.dragweb/projects/" + projectId + "/assets";
			File assetsDir = new File(assetsPath);
			if (assetsDir.exists()) {
				// Look for a logo file (logo.png, logo.jpg, logo.svg)
				String[] logoNames = {"logo.png", "logo.jpg", "logo.jpeg", "logo.svg", "logo.webp"};
				for (String name : logoNames) {
					File logoFile = new File(assetsDir, name);
					if (logoFile.exists()) {
						return logoFile.getAbsolutePath();
					}
				}
			}
		} catch (Exception e) {
			Log.w("MainActivity", "Could not find project logo: " + e.getMessage());
		}
		return "";
	}

	private String getProjectLogoPathForExport() {
		String configuredPath = getLogoPathFromProjectConfig();
		if (!configuredPath.isEmpty()) {
			return configuredPath;
		}
		String abs = getProjectLogoPath();
		if (abs.contains("/assets/")) {
			return "assets/" + abs.substring(abs.lastIndexOf('/') + 1);
		}
		return "";
	}

	private String getLogoPathFromProjectConfig() {
		try {
			File metaFile = new File(getFilesDir(), "projects/" + projectId + ".meta");
			if (metaFile.exists()) {
				Map<String, String> meta = new Gson().fromJson(
					FileUtil.readFile(metaFile.getAbsolutePath()),
					new TypeToken<Map<String, String>>(){}.getType()
				);
				if (meta != null && meta.containsKey("logoPath")) {
					String rel = meta.get("logoPath");
					if (rel != null && !rel.trim().isEmpty()) return rel.trim();
				}
			}
		} catch (Exception e) {
			Log.w("MainActivity", "Could not load logo path from config: " + e.getMessage());
		}
		return "";
	}

	private String configuredPathToAbsolute(String configured) {
		if (configured == null || configured.isEmpty()) return "";
		if (configured.startsWith("/")) return configured;
		if (configured.startsWith("assets/")) {
			return Environment.getExternalStorageDirectory().getAbsolutePath()
				+ "/.dragweb/projects/" + projectId + "/" + configured;
		}
		return "";
	}

	private boolean hasDefaultHeaderWidget() {
		for (int i = 0; i < screen.getChildCount(); i++) {
			View child = screen.getChildAt(i);
			Object tagObj = child.getTag();
			if (!(tagObj instanceof Map)) continue;
			Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
			if (!"header".equals(String.valueOf(widgetMap.get("tag")))) continue;
			Map<String, Object> fn = (Map<String, Object>) widgetMap.get("function");
			if (fn == null) continue;
			String id = fn.containsKey("id") ? String.valueOf(fn.get("id")) : "";
			String cssClass = fn.containsKey("class") ? String.valueOf(fn.get("class")) : "";
			if ("dragweb-default-header".equals(id) || cssClass.contains("dragweb-default-header")) {
				return true;
			}
		}
		return false;
	}

	private View findViewByHash(ViewGroup parent, int hash) {
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			if (child.hashCode() == hash) return child;
			if (child instanceof ViewGroup) {
				View found = findViewByHash((ViewGroup) child, hash);
				if (found != null) return found;
			}
		}
		return null;
	}

	// ---- Preview ----

	private void showPreview() {
		// Save current page first so its layout is up to date
		saveCurrentPageLayout();

		// Save current logic blocks
		if (logicBlockManager != null) {
			File dir = new File(getFilesDir(), "projects");
			if (!dir.exists()) dir.mkdirs();
			String currentPageName = pageManager != null ? pageManager.getCurrentPage() : "index";
			File logicFile = new File(dir, projectId + "_" + currentPageName + ".logic");
			FileUtil.writeFile(logicFile.getAbsolutePath(), logicBlockManager.toJson());
		}

		PageCodeGenerator codeGen = new PageCodeGenerator();
		codeGen.setProjectInfo(projectName, getProjectLogoPathForExport());
		if (exportManager != null) {
			codeGen.setIconLibraryManager(new IconLibraryManager(this, projectId));
			codeGen.setAnimationLibraryManager(new AnimationLibraryManager(this, projectId));
		}
		ArrayList<String> pageNames = new ArrayList<>();
		ArrayList<String> pageCodes = new ArrayList<>();

		List<String> allPages = pageManager != null ? pageManager.getPages() : new ArrayList<>();
		if (allPages.isEmpty()) allPages.add("index");

		String currentPage = pageManager != null ? pageManager.getCurrentPage() : "index";

		for (String pageName : allPages) {
			pageNames.add(pageName);

			if (pageName.equals(currentPage)) {
				// Current page: generate from live screen
				String html = codeGen.generateFullCode(screen, themeManager, logicBlockManager);
				pageCodes.add(html);
			} else {
				// Other pages: generate from saved JSON data
				String pageJson = pageManager.loadPageLayout(pageName);
				LogicBlockManager pageLogic = new LogicBlockManager(this);
				File dir = new File(getFilesDir(), "projects");
				File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
				if (logicFile.exists()) {
					String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
					pageLogic.fromJson(logicJson);
				}

				try {
					List<Map<String, Object>> widgetTree = new Gson().fromJson(pageJson,
						new TypeToken<List<Map<String, Object>>>(){}.getType());
					String html = codeGen.generateFullCodeFromTree(widgetTree, themeManager, pageLogic);
					pageCodes.add(html);
				} catch (Exception e) {
					pageCodes.add("<html><body><p>Error loading page: " + pageName + "</p></body></html>");
				}
			}
		}

		// Find the index of the current page to start on that tab
		int startIndex = pageNames.indexOf(currentPage);
		if (startIndex < 0) startIndex = 0;

		Intent previewIntent = new Intent(this, PreviewActivity.class);
		previewIntent.putStringArrayListExtra("page_names", pageNames);
		previewIntent.putStringArrayListExtra("page_codes", pageCodes);
		previewIntent.putExtra("start_page_index", startIndex);
		// Pass the project id so PreviewActivity can locate /.dragweb/projects/<id>/assets
		// and the local HTTP server can serve images referenced as "assets/...".
		previewIntent.putExtra("project_id", projectId);
		startActivity(previewIntent);
	}


	private String generateCssFromStyle(Map<String, Object> widgetMap, Map<String, Object> style) {
		StringBuilder css = new StringBuilder();
		String tag = widgetMap.containsKey("tag") ? widgetMap.get("tag").toString() : "div";
		css.append(tag).append(" {\n");
		if (style != null) {
			for (Map.Entry<String, Object> entry : style.entrySet()) {
				String key = entry.getKey().replaceAll("([A-Z])", "-$1").toLowerCase();
				css.append("  ").append(key).append(": ").append(entry.getValue()).append(";\n");
			}
		}
		css.append("}");
		return css.toString();
	}

	private String formatJson(String json) {
		try {
			Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
			Object obj = gson.fromJson(json, Object.class);
			return gson.toJson(obj);
		} catch (Exception e) {
			return json;
		}
	}

	// ---- New Folder Dialog ----

	private void showNewFolderDialog() {
		new UniversalM3Dialog(this)
			.setTitle("Create New Folder")
			.setHint("Folder name")
			.showTextInput(value -> {
				try {
					File currentDir = fileExplorerAdapter != null ?
						fileExplorerAdapter.getCurrentDir() : null;
					String basePath;
					if (currentDir != null) {
						basePath = currentDir.getAbsolutePath() + "/" + value;
					} else {
						basePath = Environment.getExternalStorageDirectory().getAbsolutePath()
							+ "/.dragweb/projects/" + projectId + "/assets/" + value;
					}
					File dir = new File(basePath);
					if (dir.mkdirs()) {
						Toast.makeText(this, "Folder created: " + value, Toast.LENGTH_SHORT).show();
						if (fileExplorerAdapter != null) {
							fileExplorerAdapter.navigateTo(fileExplorerAdapter.getCurrentDir());
						}
					} else {
						Toast.makeText(this, "Could not create folder", Toast.LENGTH_SHORT).show();
					}
				} catch (Exception e) {
					Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
				}
			});
	}

	// ---- Custom JSON Support ----

	private void autoLoadCustomConfigs() {
		String widgetsPath = Environment.getExternalStorageDirectory().getAbsolutePath()
			+ "/.dragweb/custom/widgets.json";
		File customWidgetsFile = new File(widgetsPath);
		if (customWidgetsFile.exists()) {
			try {
				String json = FileUtil.readFile(widgetsPath);
				if (json != null && !json.isEmpty()) {
					widgetRegistry.importCustomWidgets(json);
				}
			} catch (Exception e) {
				Log.w("MainActivity", "Failed to load custom widgets: " + e.getMessage());
			}
		}

		// Custom-block library (templates that emit static HTML/CSS) is loaded
		// by ManageBlocksWidgets itself: it tries /.dragweb/custom/blocks.json,
		// falls back to assets/blocks.json, then to built-in defaults. We just
		// re-trigger that load so any external edits are picked up.
		if (customBlockManager != null) {
			try {
				customBlockManager.loadLibrary();
			} catch (Exception e) {
				Log.w("MainActivity", "Failed to load custom block library: " + e.getMessage());
			}
		}
	}

	private void loadCustomWidgetsFromDevice() {
		String widgetsPath = Environment.getExternalStorageDirectory().getAbsolutePath()
			+ "/.dragweb/custom/widgets.json";
		File file = new File(widgetsPath);
		if (file.exists()) {
			try {
				String json = FileUtil.readFile(widgetsPath);
				widgetRegistry.importCustomWidgets(json);
				refreshWidgetList();
				Toast.makeText(this, "Custom widgets loaded", Toast.LENGTH_SHORT).show();
			} catch (Exception e) {
				Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_SHORT).show();
			}
		} else {
			File dir = new File(Environment.getExternalStorageDirectory(), ".dragweb/custom");
			dir.mkdirs();
			Toast.makeText(this, "Place widgets.json in:\n" + widgetsPath, Toast.LENGTH_LONG).show();
		}
	}

	private void loadCustomBlocksFromDevice() {
		String blocksPath = Environment.getExternalStorageDirectory().getAbsolutePath()
			+ "/.dragweb/custom/blocks.json";
		File file = new File(blocksPath);
		if (file.exists()) {
			try {
				if (customBlockManager == null) customBlockManager = new ManageBlocksWidgets(this);
				customBlockManager.loadLibrary();
				int count = customBlockManager.getDefinitions().size();
				Toast.makeText(this, "Loaded " + count + " custom block templates", Toast.LENGTH_SHORT).show();
			} catch (Exception e) {
				Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_SHORT).show();
			}
		} else {
			File dir = new File(Environment.getExternalStorageDirectory(), ".dragweb/custom");
			dir.mkdirs();
			Toast.makeText(this, "Place blocks.json in:\n" + blocksPath, Toast.LENGTH_LONG).show();
		}
	}

	// ---- Assets Management ----

	private void createFolder(String name) {
		File current = fileExplorerAdapter != null ? fileExplorerAdapter.getCurrentDir() : null;
		if (current == null) return;
		File newFolder = new File(current, name);
		if (newFolder.mkdirs()) {
			if (fileExplorerAdapter != null) fileExplorerAdapter.navigateTo(current);
			updateAssetsPath();
		} else {
			Toast.makeText(this, "Failed to create folder", Toast.LENGTH_SHORT).show();
		}
	}

	private void saveAssetFile(android.net.Uri uri) {
		try {
			File targetDir = fileExplorerAdapter != null ? 
				fileExplorerAdapter.getCurrentDir() : null;
			if (targetDir == null) {
				targetDir = new File(Environment.getExternalStorageDirectory(), "/.dragweb/projects/" + projectId + "/assets");
			}
			targetDir.mkdirs();

			String name = getFileNameFromUri(uri);
			if (name == null) name = "asset_" + System.currentTimeMillis();
			
			File dest = new File(targetDir, name);
			InputStream is = getContentResolver().openInputStream(uri);
			FileOutputStream fos = new FileOutputStream(dest);
			byte[] buffer = new byte[8192];
			int len;
			while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
			is.close();
			fos.close();

			if (fileExplorerAdapter != null) {
				fileExplorerAdapter.navigateTo(fileExplorerAdapter.getCurrentDir());
				updateAssetsPath();
			}
			Toast.makeText(this, "Imported: " + name, Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}

	private String getFileNameFromUri(android.net.Uri uri) {
		String name = null;
		if ("content".equals(uri.getScheme())) {
			android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
			if (cursor != null) {
				if (cursor.moveToFirst()) {
					int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
					if (idx != -1) name = cursor.getString(idx);
				}
				cursor.close();
			}
		}
		if (name == null) {
			name = uri.getPath();
			if (name != null) {
				int cut = name.lastIndexOf('/');
				if (cut != -1) name = name.substring(cut + 1);
			}
		}
		return name;
	}

	private void importProjectBackup(android.net.Uri uri) {
		try {
			File tempZip = new File(getCacheDir(), "import.zip");
			InputStream is = getContentResolver().openInputStream(uri);
			FileOutputStream fos = new FileOutputStream(tempZip);
			byte[] buffer = new byte[8192];
			int len;
			while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
			is.close();
			fos.close();

			if (exportManager.restoreProjectFromZip(tempZip)) {
				Toast.makeText(this, "Project restored! Please reopen the project.", Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(this, "Restore failed. Check ZIP structure.", Toast.LENGTH_SHORT).show();
			}
			tempZip.delete();
		} catch (Exception e) {
			Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}

	private void deleteFileWithConfirm(File file) {
		new MaterialAlertDialogBuilder(this)
			.setTitle("Delete " + (file.isDirectory() ? "Folder" : "File"))
			.setMessage("Are you sure you want to delete " + file.getName() + "?")
			.setPositiveButton("Delete", (d, w) -> {
				if (recursiveDelete(file)) {
					if (fileExplorerAdapter != null) 
						fileExplorerAdapter.navigateTo(fileExplorerAdapter.getCurrentDir());
					updateAssetsPath();
				} else {
					Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
				}
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private boolean recursiveDelete(File file) {
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) recursiveDelete(child);
			}
		}
		return file.delete();
	}

	// ---- Logic Blocks UI ----

	private void refreshLogicBlocksUI() {

		// Populate the event list container with events from all pages
		populateEventList();
	}

	private void populateEventList() {
		LinearLayout eventListContainer = findViewById(R.id.eventListContainer);
		if (eventListContainer == null) return;
		eventListContainer.removeAllViews();


		List<String> allPages = pageManager != null ? pageManager.getPages() : new ArrayList<>();
		if (allPages.isEmpty()) allPages.add("index");

		// All event types to show
		String[][] eventTypes = {
			{LogicBlockManager.EVENT_PAGE_LOAD, "On Page Load", "#FF9800"},
			{LogicBlockManager.EVENT_VISIBLE, "On Visible", "#FF9800"},
			{LogicBlockManager.EVENT_HIDDEN, "On Hidden", "#FF9800"},
			{LogicBlockManager.EVENT_DESTROY, "On Destroy", "#FF9800"},
			{LogicBlockManager.EVENT_PAGE_SCROLL, "On Scroll", "#FF9800"},
			{LogicBlockManager.EVENT_PAGE_INPUT, "On Page Input", "#FF9800"},
			{LogicBlockManager.EVENT_CLICK, "On Click", "#4CAF50"},
			{LogicBlockManager.EVENT_HOVER, "On Hover", "#4CAF50"},
			{LogicBlockManager.EVENT_INPUT, "On Input", "#4CAF50"},
			{LogicBlockManager.EVENT_SUBMIT, "On Submit", "#4CAF50"},
			{LogicBlockManager.EVENT_SCROLL, "On Scroll", "#4CAF50"},
			{LogicBlockManager.EVENT_KEYDOWN, "On Key Down", "#4CAF50"},
			{LogicBlockManager.EVENT_CHANGE, "On Change", "#4CAF50"},
		};

		String currentPage = pageManager != null ? pageManager.getCurrentPage() : "index";
		File dir = new File(getFilesDir(), "projects");

		for (String pageName : allPages) {
			// Load logic blocks for this page
			LogicBlockManager pageLogic;
			if (pageName.equals(currentPage)) {
				pageLogic = logicBlockManager;
			} else {
				pageLogic = new LogicBlockManager(this);
				File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
				if (logicFile.exists()) {
					String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
					pageLogic.fromJson(logicJson);
				}
			}

			int totalBlocks = pageLogic.getBlocks().size();

			// Page header
			TextView pageHeader = new TextView(this);
			pageHeader.setText(pageName.toUpperCase() + " (" + totalBlocks + " blocks)");
			pageHeader.setTextColor(Color.parseColor("#6200EE"));
			pageHeader.setTextSize(14);
			pageHeader.setTypeface(null, android.graphics.Typeface.BOLD);
			pageHeader.setPadding(4, 16, 4, 8);
			eventListContainer.addView(pageHeader);

			if (totalBlocks == 0) {
				// Show empty state for this page
				TextView emptyText = new TextView(this);
				emptyText.setText("No logic blocks added. Tap + to add.");
				emptyText.setTextSize(12);
				emptyText.setPadding(8, 4, 8, 12);
				emptyText.setTextColor(Color.parseColor("#999999"));
				eventListContainer.addView(emptyText);

				// Add block button for empty page
				addEventButton(eventListContainer, pageName, "Add Event Block", "#6200EE");
			} else {
				// Show each event type that has blocks
				for (String[] eventInfo : eventTypes) {
					String eventKey = eventInfo[0];
					String eventLabel = eventInfo[1];
					String eventColor = eventInfo[2];

					int blockCount = pageLogic.getBlockCountForEvent(eventKey);
					if (blockCount > 0) {
						addEventCard(eventListContainer, pageName, eventKey, eventLabel, blockCount, eventColor);
					}
				}

				// Also show blocks with no standard event (immediate/custom)
				int otherCount = 0;
				for (LogicBlockManager.LogicBlock block : pageLogic.getBlocks()) {
					boolean matched = false;
					for (String[] eventInfo : eventTypes) {
						if (eventInfo[0].equals(block.event)) {
							matched = true;
							break;
						}
					}
					if (!matched) otherCount++;
				}
				if (otherCount > 0) {
					addEventCard(eventListContainer, pageName, "immediate", "Immediate / Custom", otherCount, "#9E9E9E");
				}

				// Add button
				addEventButton(eventListContainer, pageName, "Add More Blocks", "#6200EE");
			}

			// Divider
			View divider = new View(this);
			divider.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 1));
			divider.setBackgroundColor(Color.parseColor("#33FFFFFF"));
			eventListContainer.addView(divider);
		}
	}

	private void addEventCard(LinearLayout container, String pageName, String eventKey, String eventLabel, int blockCount, String colorHex) {
		LinearLayout card = new LinearLayout(this);
		card.setOrientation(LinearLayout.HORIZONTAL);
		card.setGravity(android.view.Gravity.CENTER_VERTICAL);
		card.setPadding(16, 12, 16, 12);

		android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
		bg.setCornerRadius(12);
		bg.setColor(Color.parseColor("#1A" + colorHex.substring(1)));
		bg.setStroke(1, Color.parseColor("#44" + colorHex.substring(1)));
		card.setBackground(bg);

		LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		cardParams.setMargins(0, 4, 0, 4);
		card.setLayoutParams(cardParams);

		// Color indicator dot
		View dot = new View(this);
		android.graphics.drawable.GradientDrawable dotBg = new android.graphics.drawable.GradientDrawable();
		dotBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
		dotBg.setColor(Color.parseColor(colorHex));
		dot.setBackground(dotBg);
		LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(12, 12);
		dotParams.setMargins(0, 0, 12, 0);
		dot.setLayoutParams(dotParams);
		card.addView(dot);

		// Event name
		TextView tvEvent = new TextView(this);
		tvEvent.setText(eventLabel);
		tvEvent.setTextSize(13);
		tvEvent.setTypeface(null, android.graphics.Typeface.BOLD);
		tvEvent.setTextColor(Color.parseColor(colorHex));
		LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
			0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
		tvEvent.setLayoutParams(tvParams);
		card.addView(tvEvent);

		// Block count badge
		TextView tvCount = new TextView(this);
		tvCount.setText(String.valueOf(blockCount));
		tvCount.setTextSize(12);
		tvCount.setTextColor(Color.WHITE);
		tvCount.setGravity(android.view.Gravity.CENTER);
		android.graphics.drawable.GradientDrawable countBg = new android.graphics.drawable.GradientDrawable();
		countBg.setCornerRadius(20);
		countBg.setColor(Color.parseColor(colorHex));
		tvCount.setBackground(countBg);
		tvCount.setPadding(16, 4, 16, 4);
		tvCount.setMinWidth(32);
		card.addView(tvCount);

		// Click to open LogicBlockActivity for this page
		card.setOnClickListener(v -> {
			Intent intent = new Intent(this, LogicBlockActivity.class);
			intent.putExtra("project_id", projectId);
			intent.putExtra("page_name", pageName);
			logicBlockLauncher.launch(intent);
		});

		container.addView(card);
	}

	private void addEventButton(LinearLayout container, String pageName, String label, String colorHex) {
		TextView btnAdd = new TextView(this);
		btnAdd.setText("+ " + label);
		btnAdd.setTextSize(12);
		btnAdd.setTextColor(Color.parseColor(colorHex));
		btnAdd.setGravity(android.view.Gravity.CENTER);
		btnAdd.setPadding(16, 10, 16, 10);

		android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
		btnBg.setCornerRadius(8);
		btnBg.setStroke(1, Color.parseColor("#44" + colorHex.substring(1)));
		btnAdd.setBackground(btnBg);

		LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		btnParams.setMargins(0, 4, 0, 8);
		btnAdd.setLayoutParams(btnParams);

		btnAdd.setOnClickListener(v -> {
			Intent intent = new Intent(this, LogicBlockActivity.class);
			intent.putExtra("project_id", projectId);
			intent.putExtra("page_name", pageName);
			logicBlockLauncher.launch(intent);
		});

		container.addView(btnAdd);
	}


	// ---- Project Save/Load ----

	private void saveProject() {
		// Save current page layout first
		saveCurrentPageLayout();

		// Save all cached pages to disk
		pageManager.saveAllPages();

		// Save main layout (index page for backwards compat)
		projectDataManager.saveProject(screen, projectId);

		File dir = new File(getFilesDir(), "projects");
		if (!dir.exists()) dir.mkdirs();
		File themeFile = new File(dir, projectId + ".theme");
		FileUtil.writeFile(themeFile.getAbsolutePath(), themeManager.toJson());

		String currentPageName = pageManager != null ? pageManager.getCurrentPage() : "index";
		File logicFile = new File(dir, projectId + "_" + currentPageName + ".logic");
		FileUtil.writeFile(logicFile.getAbsolutePath(), logicBlockManager.toJson());

		// Save logic blocks for all pages to ensure nothing is lost
		if (pageManager != null) {
			for (String page : pageManager.getPages()) {
				if (!page.equals(currentPageName)) {
					// Other pages' logic is already on disk, no need to re-save
				}
			}
		}

		saveProjectToExternal();
		Toast.makeText(this, "Project saved", Toast.LENGTH_SHORT).show();
	}

	private void saveProjectToExternal() {
		try {
			String basePath = Environment.getExternalStorageDirectory().getAbsolutePath()
				+ "/.dragweb/projects/" + projectId;
			File extDir = new File(basePath);
			if (!extDir.exists()) extDir.mkdirs();

			File internalDir = new File(getFilesDir(), "projects");
			File layoutFile = new File(internalDir, projectId + ".json");
			if (layoutFile.exists()) {
				String json = FileUtil.readFile(layoutFile.getAbsolutePath());
				FileUtil.writeFile(new File(extDir, "layout.json").getAbsolutePath(), json);
			}

			// Save theme
			FileUtil.writeFile(new File(extDir, "theme.json").getAbsolutePath(), themeManager.toJson());

			// Save logic blocks for all pages
			if (pageManager != null) {
				for (String page : pageManager.getPages()) {
					File logicFile = new File(internalDir, projectId + "_" + page + ".logic");
					if (logicFile.exists()) {
						String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
						FileUtil.writeFile(new File(extDir, page + "_logic.json").getAbsolutePath(), logicJson);
					}
				}
			} else {
				String currentPageName = "index";
				FileUtil.writeFile(new File(extDir, currentPageName + "_logic.json").getAbsolutePath(), logicBlockManager.toJson());
			}
		} catch (Exception e) {
			Log.w("MainActivity", "Could not save to external: " + e.getMessage());
		}
	}

	private void loadProject() {
		// Try loading the current page from PageManager first
		String pageJson = pageManager.loadPageLayout(pageManager.getCurrentPage());
		boolean loadedFromPage = false;

		if (pageJson != null && !"[]".equals(pageJson.trim())) {
			screen.removeAllViews();
			try {
				List<Map<String, Object>> widgetTree = new Gson().fromJson(pageJson,
					new TypeToken<List<Map<String, Object>>>(){}.getType());
				if (widgetTree != null && !widgetTree.isEmpty()) {
					for (Map<String, Object> nodeMap : widgetTree) {
						rebuildView(nodeMap, screen);
					}
					loadedFromPage = true;
				}
			} catch (Exception e) {
				Log.w("MainActivity", "Could not load page layout: " + e.getMessage());
			}
		}

		// Fall back to legacy project data if page data is empty
		if (!loadedFromPage) {
			projectDataManager.loadProject(screen, projectId, engine, selector, dropZoneManager);
		}



		// Register all loaded widgets for reorder drag
		registerAllWidgetsForDrag(screen);

		File dir = new File(getFilesDir(), "projects");
		File themeFile = new File(dir, projectId + ".theme");
		if (themeFile.exists()) {
			String themeJson = FileUtil.readFile(themeFile.getAbsolutePath());
			themeManager.fromJson(themeJson);
		}

		String currentPageName = pageManager != null ? pageManager.getCurrentPage() : "index";
		File logicFile = new File(dir, projectId + "_" + currentPageName + ".logic");
		if (logicFile.exists()) {
			String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
			logicBlockManager.fromJson(logicJson);
		} else {
			// Fallback for older projects
			File oldLogicFile = new File(dir, projectId + ".logic");
			if (oldLogicFile.exists()) {
				String logicJson = FileUtil.readFile(oldLogicFile.getAbsolutePath());
				logicBlockManager.fromJson(logicJson);
			}
		}

		// Save initial page layout so it's cached
		if (pageManager != null && screen.getChildCount() > 0) {
			saveCurrentPageLayout();
		}
	}

	private void registerAllWidgetsForDrag(ViewGroup parent) {
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			setupWidgetReorderDrag(child);
			if (child instanceof ViewGroup) {
				registerAllWidgetsForDrag((ViewGroup) child);
			}
		}
	}

	private void saveUndoState() {
		undoRedoManager.saveState(screen);
		refreshHierarchy();
	}

	private void restoreState(List<Map<String, Object>> state) {
		screen.removeAllViews();
		for (Map<String, Object> nodeMap : state) {
			rebuildView(nodeMap, screen);
		}
		selector.clearSelection();
		selector.attachTo(screen);
		textview2.setText("No widget selected");
		delete.setEnabled(false);
	}

	private void rebuildView(Map<String, Object> nodeMap, ViewGroup parent) {
		if (!nodeMap.containsKey("tag")) return;
		String tag = nodeMap.get("tag").toString();
		View newView = engine.createWidget(tag);
		if (newView != null) {
			Map<String, Object> newWidgetMap = new HashMap<>(nodeMap);
			newWidgetMap.remove("children");
			engine.applyPropertiesToView(newView, newWidgetMap);
			newView.setTag(newWidgetMap);
			parent.addView(newView);
			selector.registerView(newView);
			setupWidgetReorderDrag(newView);
			dropZoneManager.registerWidgetAsDropZoneIfContainer(newView);

			// Handle hidden state
			if (Boolean.TRUE.equals(newWidgetMap.get("hidden"))) {
				newView.setVisibility(View.GONE);
			}

			if (nodeMap.containsKey("children") && newView instanceof ViewGroup) {
				List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get("children");
				for (Map<String, Object> childMap : children) {
					rebuildView(childMap, (ViewGroup) newView);
				}
			}
		}
	}

	private void rebuildViewAt(Map<String, Object> nodeMap, ViewGroup parent, int index) {
		if (!nodeMap.containsKey("tag")) return;
		String tag = nodeMap.get("tag").toString();
		View newView = engine.createWidget(tag);
		if (newView != null) {
			Map<String, Object> newWidgetMap = new HashMap<>(nodeMap);
			newWidgetMap.remove("children");
			engine.applyPropertiesToView(newView, newWidgetMap);
			newView.setTag(newWidgetMap);
			parent.addView(newView, Math.min(index, parent.getChildCount()));
			selector.registerView(newView);
			setupWidgetReorderDrag(newView);
			dropZoneManager.registerWidgetAsDropZoneIfContainer(newView);

			if (nodeMap.containsKey("children") && newView instanceof ViewGroup) {
				List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get("children");
				for (Map<String, Object> childMap : children) {
					rebuildView(childMap, (ViewGroup) newView);
				}
			}
		}
	}

	// ---- Theme Dialog ----

	private void showThemeDialog() {
		View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_theme_settings, null);
		Button btnLight = dialogView.findViewById(R.id.btnLightTheme);
		Button btnDark = dialogView.findViewById(R.id.btnDarkTheme);
		TextInputEditText etPrimary = dialogView.findViewById(R.id.etPrimaryColor);
		TextInputEditText etSecondary = dialogView.findViewById(R.id.etSecondaryColor);
		TextInputEditText etAccent = dialogView.findViewById(R.id.etAccentColor);
		TextInputEditText etBackground = dialogView.findViewById(R.id.etBodyBackground);
		TextInputEditText etBodyColor = dialogView.findViewById(R.id.etBodyColor);
		TextInputEditText etLinkColor = dialogView.findViewById(R.id.etLinkColor);
		TextInputEditText etBorderColor = dialogView.findViewById(R.id.etBorderColor);
		LinearLayout customVarsContainer = dialogView.findViewById(R.id.customVarsContainer);
		Button btnAddCssVar = dialogView.findViewById(R.id.btnAddCssVar);
		com.google.android.material.switchmaterial.SwitchMaterial switchInlineStyles = dialogView.findViewById(R.id.switchInlineStyles);
		Button btnResetTheme = dialogView.findViewById(R.id.btnResetTheme);

		if (switchInlineStyles != null) {
			switchInlineStyles.setChecked(themeManager.isUseInlineStyles());
		}

		// Track which theme is being edited in dialog
		final String[] editingTheme = { themeManager.getCurrentTheme() };

		setupColorInputField(etPrimary);
		setupColorInputField(etSecondary);
		setupColorInputField(etAccent);
		setupColorInputField(etBackground);
		setupColorInputField(etBodyColor);
		setupColorInputField(etLinkColor);
		setupColorInputField(etBorderColor);

		// Helper to populate fields from a theme
		Runnable populateFields = () -> {
			String t = editingTheme[0];
			etPrimary.setText(themeManager.getStyleForTheme(t, "primaryColor"));
			etSecondary.setText(themeManager.getStyleForTheme(t, "secondaryColor"));
			etAccent.setText(themeManager.getStyleForTheme(t, "accentColor"));
			etBackground.setText(themeManager.getStyleForTheme(t, "bodyBackground"));
			etBodyColor.setText(themeManager.getStyleForTheme(t, "bodyColor"));
			etLinkColor.setText(themeManager.getStyleForTheme(t, "linkColor"));
			etBorderColor.setText(themeManager.getStyleForTheme(t, "borderColor"));
		};

		// Helper to save current field values into the editing theme
		Runnable saveFieldsToTheme = () -> {
			String t = editingTheme[0];
			String primary = etPrimary.getText().toString().trim();
			String secondary = etSecondary.getText().toString().trim();
			String accent = etAccent.getText().toString().trim();
			String bg = etBackground.getText().toString().trim();
			String bodyColor = etBodyColor.getText().toString().trim();
			String linkColor = etLinkColor.getText().toString().trim();
			String borderColor = etBorderColor.getText().toString().trim();
			if (!primary.isEmpty()) themeManager.setStyleForTheme(t, "primaryColor", primary);
			if (!secondary.isEmpty()) themeManager.setStyleForTheme(t, "secondaryColor", secondary);
			if (!accent.isEmpty()) themeManager.setStyleForTheme(t, "accentColor", accent);
			if (!bg.isEmpty()) themeManager.setStyleForTheme(t, "bodyBackground", bg);
			if (!bodyColor.isEmpty()) themeManager.setStyleForTheme(t, "bodyColor", bodyColor);
			if (!linkColor.isEmpty()) themeManager.setStyleForTheme(t, "linkColor", linkColor);
			if (!borderColor.isEmpty()) themeManager.setStyleForTheme(t, "borderColor", borderColor);
		};

		populateFields.run();

		if (ThemeManager.THEME_DARK.equals(editingTheme[0])) {
			btnDark.performClick();
		} else {
			btnLight.performClick();
		}

		btnLight.setOnClickListener(v -> {
			// Save current edits to the theme we were editing
			saveFieldsToTheme.run();
			editingTheme[0] = ThemeManager.THEME_LIGHT;
			themeManager.setTheme(ThemeManager.THEME_LIGHT);
			populateFields.run();
		});
		btnDark.setOnClickListener(v -> {
			saveFieldsToTheme.run();
			editingTheme[0] = ThemeManager.THEME_DARK;
			themeManager.setTheme(ThemeManager.THEME_DARK);
			populateFields.run();
		});

		Map<String, String> customVars = themeManager.getCustomCssVars();
		for (Map.Entry<String, String> entry : customVars.entrySet()) {
			addCssVarRow(customVarsContainer, entry.getKey(), entry.getValue());
		}

		if (btnAddCssVar != null) {
			btnAddCssVar.setOnClickListener(v -> addCssVarRow(customVarsContainer, "", ""));
		}

		if (btnResetTheme != null) {
			btnResetTheme.setOnClickListener(v -> {
				new MaterialAlertDialogBuilder(this)
					.setTitle("Reset Theme")
					.setMessage("Are you sure you want to reset theme settings to defaults?")
					.setPositiveButton("Reset", (d, w) -> {
						themeManager.resetToDefaults();
						populateFields.run();
						if (switchInlineStyles != null) {
							switchInlineStyles.setChecked(themeManager.isUseInlineStyles());
						}
						customVarsContainer.removeAllViews();
						Map<String, String> cv = themeManager.getCustomCssVars();
						for (Map.Entry<String, String> entry : cv.entrySet()) {
							addCssVarRow(customVarsContainer, entry.getKey(), entry.getValue());
						}
						if (ThemeManager.THEME_DARK.equals(themeManager.getCurrentTheme())) {
							btnDark.performClick();
						} else {
							btnLight.performClick();
						}
						Toast.makeText(this, "Theme reset to defaults", Toast.LENGTH_SHORT).show();
					})
					.setNegativeButton("Cancel", null)
					.show();
			});
		}

		new MaterialAlertDialogBuilder(this)
			.setTitle("Theme Settings")
			.setView(dialogView)
			.setPositiveButton("Apply", (dialog, which) -> {
				// Save final edits
				saveFieldsToTheme.run();

				if (switchInlineStyles != null) {
					themeManager.setUseInlineStyles(switchInlineStyles.isChecked());
				}

				Map<String, String> newVars = new LinkedHashMap<>();
				for (int i = 0; i < customVarsContainer.getChildCount(); i++) {
					View row = customVarsContainer.getChildAt(i);
					TextInputEditText etName = row.findViewWithTag("varName");
					TextInputEditText etValue = row.findViewWithTag("varValue");
					if (etName != null && etValue != null) {
						String name = etName.getText().toString().trim();
						String val = etValue.getText().toString().trim();
						if (!name.isEmpty() && !val.isEmpty()) {
							newVars.put(name, val);
						}
					}
				}
				themeManager.setCustomCssVars(newVars);

				Toast.makeText(this, "Theme updated (light + dark)", Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void showFontSettingsDialog() {
		String currentFont = themeManager.getStyleForTheme(themeManager.getCurrentTheme(), "fontFamily");
		if (currentFont == null || currentFont.isEmpty()) {
			currentFont = "sans-serif";
		}

		java.util.List<String> fontSuggestions = java.util.Arrays.asList(
			"sans-serif", "serif", "monospace", "system-ui",
			"Inter", "Outfit", "Roboto", "Poppins", "Montserrat", "Playfair Display"
		);

		UniversalDialog.autocompleteInput(this, "Font Settings", "Font Family", currentFont, fontSuggestions, font -> {
			if (!font.isEmpty()) {
				themeManager.setStyleForTheme(ThemeManager.THEME_LIGHT, "fontFamily", font);
				themeManager.setStyleForTheme(ThemeManager.THEME_DARK, "fontFamily", font);
				Toast.makeText(this, "Font family updated", Toast.LENGTH_SHORT).show();
			}
		});
	}

	private void setupColorInputField(TextInputEditText et) {
		et.setFocusable(false);
		et.setCursorVisible(false);
		et.setOnClickListener(v -> {
			String current = et.getText().toString();
			UniversalDialog.colorPicker(this, "Select Color", current, getColorSuggestions(), hex -> {
				et.setText(hex);
			});
		});

		et.addTextChangedListener(new android.text.TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
			@Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
			@Override public void afterTextChanged(android.text.Editable s) {
				String color = s.toString();
				try {
					int parsed = Color.parseColor(color.startsWith("#") ? color : "#000000");
					GradientDrawable gd = new GradientDrawable();
					gd.setShape(GradientDrawable.OVAL);
					gd.setColor(parsed);
					gd.setStroke((int)(1 * getResources().getDisplayMetrics().density), Color.LTGRAY);
					int size = (int)(20 * getResources().getDisplayMetrics().density);
					gd.setSize(size, size);
					et.setCompoundDrawablesWithIntrinsicBounds(gd, null, null, null);
					et.setCompoundDrawablePadding((int)(8 * getResources().getDisplayMetrics().density));
				} catch (Exception e) {}
			}
		});
		// Refresh swatch
		if (et.getText().length() > 0) {
			et.setText(et.getText());
		}
	}

	private void addCssVarRow(LinearLayout container, String name, String value) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(android.view.Gravity.CENTER_VERTICAL);
		row.setPadding(0, 8, 0, 8);

		// Name field
		TextInputLayout tilName = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
		tilName.setHint("Variable");
		tilName.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
		LinearLayout.LayoutParams lpName = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f);
		lpName.setMarginEnd(8);
		tilName.setLayoutParams(lpName);
		
		TextInputEditText etName = new TextInputEditText(tilName.getContext());
		etName.setText(name);
		etName.setTextSize(14);
		etName.setTag("varName");
		tilName.addView(etName);

		// Value field
		TextInputLayout tilValue = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
		tilValue.setHint("Value");
		tilValue.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
		LinearLayout.LayoutParams lpValue = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		tilValue.setLayoutParams(lpValue);

		TextInputEditText etValue = new TextInputEditText(tilValue.getContext());
		etValue.setText(value);
		etValue.setTextSize(14);
		etValue.setTag("varValue");
		tilValue.addView(etValue);
		
		setupColorInputField(etValue);

		// Remove button
		ImageButton btnRemove = new ImageButton(this, null, com.google.android.material.R.attr.materialIconButtonStyle);
		// Simple delete icon
		btnRemove.setImageResource(android.R.drawable.ic_menu_delete);
		btnRemove.setOnClickListener(v -> container.removeView(row));

		row.addView(tilName);
		row.addView(tilValue);
		row.addView(btnRemove);
		container.addView(row);
	}

	// ---- Libraries Dialog (animations + icon CDNs) ----

	/**
	 * Surface for the {@link AnimationLibrary} (read-only catalog of bundled
	 * keyframes) and {@link IconLibraryManager} (per-project list of enabled
	 * icon CDN packs). Without this hook neither was reachable from the UI,
	 * even though both already feed the export pipeline.
	 */
	private void showLibrariesDialog() {
		String[] options = { "Icon Libraries…", "Animation Library…" };
		new MaterialAlertDialogBuilder(this)
			.setTitle("Project libraries")
			.setItems(options, (d, which) -> {
				if (which == 0) showIconLibrariesDialog();
				else showAnimationLibraryDialog();
			})
			.setNegativeButton("Close", null)
			.show();
	}

	private void showIconLibrariesDialog() {
		IconLibraryManager mgr = new IconLibraryManager(this, projectId);
		java.util.List<IconLibraryManager.Library> all = mgr.allLibraries();
		String[] labels = new String[all.size()];
		boolean[] checked = new boolean[all.size()];
		for (int i = 0; i < all.size(); i++) {
			labels[i] = all.get(i).displayName + "  ·  " + all.get(i).version;
			checked[i] = mgr.isEnabled(all.get(i).id);
		}
		new MaterialAlertDialogBuilder(this)
			.setTitle("Icon libraries")
			.setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
				if (isChecked) mgr.enable(all.get(which).id);
				else mgr.disable(all.get(which).id);
			})
			.setPositiveButton("Done", (d, w) -> {
				if (exportManager != null) exportManager.setIconLibraryManager(mgr);
				if (codeGenerator != null) codeGenerator.setIconLibraryManager(mgr);
				Toast.makeText(this, "Icon libraries updated", Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void showAnimationLibraryDialog() {
		AnimationLibraryManager mgr = new AnimationLibraryManager(this, projectId);
		java.util.List<String> all = mgr.allLocalAnimations();
		all.add(0, AnimationLibraryManager.ANIMATE_CSS_ID);
		
		String[] labels = new String[all.size()];
		boolean[] checked = new boolean[all.size()];
		
		for (int i = 0; i < all.size(); i++) {
			String id = all.get(i);
			if (id.equals(AnimationLibraryManager.ANIMATE_CSS_ID)) {
				labels[i] = "Animate.css (External CDN)";
			} else {
				labels[i] = "Local: " + id;
			}
			checked[i] = mgr.isEnabled(id);
		}
		
		new MaterialAlertDialogBuilder(this)
			.setTitle("Animation Libraries")
			.setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
				if (isChecked) mgr.enable(all.get(which));
				else mgr.disable(all.get(which));
			})
			.setPositiveButton("Done", (d, w) -> {
				if (exportManager != null) exportManager.setAnimationLibraryManager(mgr);
				if (codeGenerator != null) codeGenerator.setAnimationLibraryManager(mgr);
				Toast.makeText(this, "Animation libraries updated", Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	// ---- Export Dialog ----

	private void showExportDialog() {
		final android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
		progress.setTitle("Exporting Project");
		progress.setMessage("Exporting ZIP and separate files...");
		progress.setCancelable(false);
		progress.show();

		new Thread(() -> {
			boolean folderOk = false;
			String folderPath = "";
			boolean zipOk = false;
			String zipPath = "";
			String errMsg = "";

			try {
				// 1. Generate separate files (Folder export)
				ExportManager.ExportResult result = exportManager.generateExportFiles(
						screen, projectName, logicBlockManager, customBlockManager);
				folderOk = result.success;
				if (folderOk && result.exportDir != null) {
					folderPath = result.exportDir.getAbsolutePath();
				} else {
					errMsg = result.message;
				}

				// 2. Generate ZIP archive
				File zipFile = exportManager.exportAsZip(
						screen, projectName, projectId, logicBlockManager, customBlockManager);
				zipOk = (zipFile != null && zipFile.exists());
				if (zipOk) {
					zipPath = zipFile.getAbsolutePath();
				}
			} catch (Exception e) {
				errMsg = e.getMessage();
			}

			final boolean finalFolderOk = folderOk;
			final String finalFolderPath = folderPath;
			final boolean finalZipOk = zipOk;
			final String finalZipPath = zipPath;
			final String finalErrMsg = errMsg;

			runOnUiThread(() -> {
				progress.dismiss();
				if (finalFolderOk && finalZipOk) {
					new MaterialAlertDialogBuilder(MainActivity.this)
						.setTitle("Export Successful")
						.setMessage("Exported separate files to:\n" + finalFolderPath + "\n\nExported ZIP file to:\n" + finalZipPath)
						.setPositiveButton("OK", null)
						.show();
				} else {
					Toast.makeText(MainActivity.this, "Export failed: " + finalErrMsg, Toast.LENGTH_LONG).show();
				}
			});
		}).start();
	}

	// ---- Design Property Lists (removed event items from view section) ----

	private void buildDesignList() {
		design.clear();
		View selected = selector.getSelectedView();
		Map<String, Object> currentStyle = getWidgetStyle(selected);
		Map<String, Object> currentFunction = getWidgetFunction(selected);

		List<String> items = new ArrayList<>();
		String tag = getSelectedTag();
		if ("p".equals(tag) || "h1".equals(tag) || "h2".equals(tag) || "h3".equals(tag) || "h4".equals(tag) || "h5".equals(tag) || "h6".equals(tag)
			|| "span".equals(tag) || "label".equals(tag) || "a".equals(tag) || "button".equals(tag) || "li".equals(tag)) {
			items.add(0, "Edittext");
		}

		if ("img".equals(tag)) {
			items.add("ImageSrc");
		}
		if ("a".equals(tag)) {
			items.add("SetHref");
			items.add("SetTarget");
		}
		if ("input".equals(tag) || "textarea".equals(tag)) {
			items.add("SetPlaceholder");
			items.add("SetType");
		}
		if ("select".equals(tag)) {
			items.add("ListItems");
		}
		if ("i".equals(tag)) {
			items.add("PickIcon");
		}
		
		items.add("SetId");
		items.add("SetClass");
		items.add("Color");
		items.add("Background");

		for (String item : items) {
			HashMap<String, Object> map = new HashMap<>();
			map.put("edit", item);
			String value = "";
			if (item.equals("Edittext")) value = currentFunction.containsKey("text") ? currentFunction.get("text").toString() : "";
			else if (item.equals("SetId")) value = currentFunction.containsKey("id") ? currentFunction.get("id").toString() : "";
			else if (item.equals("SetClass")) value = currentFunction.containsKey("class") ? currentFunction.get("class").toString() : "";
			else if (item.equals("SetHref")) value = currentFunction.containsKey("href") ? currentFunction.get("href").toString() : "";
			else if (item.equals("SetPlaceholder")) value = currentFunction.containsKey("placeholder") ? currentFunction.get("placeholder").toString() : "";
			else {
				String cssKey = getCssKeyFromItem(item);
				if (currentStyle.containsKey(cssKey)) value = currentStyle.get(cssKey).toString();
			}
			map.put("value", value);
			design.add(map);
		}
		recyclerview3.setAdapter(new Recyclerview3Adapter(design));
		recyclerview3.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
	}

	private void buildLayoutDesignList() {
		design.clear();
		View selected = selector.getSelectedView();
		Map<String, Object> currentStyle = getWidgetStyle(selected);

		String[] items = {
			"Display", "Position", "FlexDir", "FlexWrap",
			"JustifyContent", "AlignItems", "AlignSelf", "AlignContent",
			"Gap", "RowGap", "ColGap",
			"GridCols", "GridRows", "GridGap",
			"Float", "Clear",
			"Top", "Right", "Bottom", "Left",
			"MinWidth", "MaxWidth", "MinHeight", "MaxHeight",
			"ObjectFit", "AspectRatio"
		};
		for (String item : items) {
			HashMap<String, Object> map = new HashMap<>();
			map.put("edit", item);
			String cssKey = getCssKeyFromItem(item);
			String value = currentStyle.containsKey(cssKey) ? currentStyle.get(cssKey).toString() : "";
			map.put("value", value);
			design.add(map);
		}
		recyclerview3.setAdapter(new Recyclerview3Adapter(design));
		recyclerview3.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
	}

	private Map<String, Object> getWidgetStyle(View v) {
		if (v == null) return new HashMap<>();
		Object tag = v.getTag();
		if (tag instanceof Map) {
			Map<String, Object> wm = (Map<String, Object>) tag;
			Map<String, Object> fn = (Map<String, Object>) wm.get("function");
			if (fn != null) {
				Map<String, Object> st = (Map<String, Object>) fn.get("style");
				if (st != null) return st;
			}
		}
		return new HashMap<>();
	}

	private Map<String, Object> getWidgetFunction(View v) {
		if (v == null) return new HashMap<>();
		Object tag = v.getTag();
		if (tag instanceof Map) {
			Map<String, Object> wm = (Map<String, Object>) tag;
			Map<String, Object> fn = (Map<String, Object>) wm.get("function");
			if (fn != null) return fn;
		}
		return new HashMap<>();
	}

	private String getCssKeyFromItem(String item) {
		switch (item) {
			case "TextSize": return "fontSize";
			case "Color": return "color";
			case "Font": return "fontWeight";
			case "TextAlign": return "textAlign";
			case "Background": return "backgroundColor";
			case "BorderRadius": return "borderRadius";
			case "BorderWidth": return "borderWidth";
			case "BorderColor": return "borderColor";
			case "Padding": return "padding";
			case "Margin": return "margin";
			case "Width": return "width";
			case "Height": return "height";
			case "Display": return "display";
			case "Position": return "position";
			case "FlexDir": return "flexDirection";
			case "FlexWrap": return "flexWrap";
			case "JustifyContent": return "justifyContent";
			case "AlignItems": return "alignItems";
			case "AlignSelf": return "alignSelf";
			case "AlignContent": return "alignContent";
			case "Gap": return "gap";
			case "RowGap": return "rowGap";
			case "ColGap": return "columnGap";
			case "GridCols": return "gridTemplateColumns";
			case "GridRows": return "gridTemplateRows";
			case "GridGap": return "gridGap";
			case "Float": return "float";
			case "Clear": return "clear";
			case "Top": return "top";
			case "Right": return "right";
			case "Bottom": return "bottom";
			case "Left": return "left";
			case "MinWidth": return "minWidth";
			case "MaxWidth": return "maxWidth";
			case "MinHeight": return "minHeight";
			case "MaxHeight": return "maxHeight";
			case "ObjectFit": return "objectFit";
			case "AspectRatio": return "aspectRatio";
			case "Edittext": return "text";
			case "SetId": return "id";
			case "SetClass": return "class";
			case "SetHref": return "href";
			case "SetTarget": return "target";
			case "SetPlaceholder": return "placeholder";
			case "SetType": return "type";
			case "ImageSrc": return "src";
			case "ListItems": return "items";
			case "Elevation": return "elevation";
			case "Opacity": return "opacity";
			case "Rotation": return "rotation";
			case "Overflow": return "overflow";
			case "BoxShadow": return "boxShadow";
			case "TextDecor": return "textDecoration";
			case "LineHeight": return "lineHeight";
			case "LetterSpace": return "letterSpacing";
			case "ZIndex": return "zIndex";
			case "BorderTop": return "borderTop";
			case "BorderRight": return "borderRight";
			case "BorderBottom": return "borderBottom";
			case "BorderLeft": return "borderLeft";
			case "RadiusTL": return "borderTopLeftRadius";
			case "RadiusTR": return "borderTopRightRadius";
			case "RadiusBL": return "borderBottomLeftRadius";
			case "RadiusBR": return "borderBottomRightRadius";
			default: return item.toLowerCase();
		}
	}



	private String getSelectedTag() {
		View selected = selector.getSelectedView();
		if (selected != null) {
			Object tagObj = selected.getTag();
			if (tagObj instanceof Map) {
				Map<String, Object> wm = (Map<String, Object>) tagObj;
				if (wm.containsKey("tag")) return wm.get("tag").toString();
			}
		}
		return "div";
	}

	private java.util.List<String> harvestIds() {
		java.util.Set<String> set = new java.util.HashSet<>();
		harvestIdsRecursive(screen, set);
		return new java.util.ArrayList<>(set);
	}
	private void harvestIdsRecursive(View v, java.util.Set<String> set) {
		Object tag = v.getTag();
		if (tag instanceof Map) {
			Map<String, Object> wm = (Map<String, Object>) tag;
			Map<String, Object> fn = (Map<String, Object>) wm.get("function");
			if (fn != null && fn.containsKey("id")) {
				String s = String.valueOf(fn.get("id")).trim();
				if (!s.isEmpty()) set.add(s);
			}
		}
		if (v instanceof ViewGroup) {
			ViewGroup vg = (ViewGroup) v;
			for (int i = 0; i < vg.getChildCount(); i++) harvestIdsRecursive(vg.getChildAt(i), set);
		}
	}

	private java.util.List<String> harvestClasses() {
		java.util.Set<String> set = new java.util.HashSet<>();
		harvestClassesRecursive(screen, set);
		return new java.util.ArrayList<>(set);
	}
	private void harvestClassesRecursive(View v, java.util.Set<String> set) {
		Object tag = v.getTag();
		if (tag instanceof Map) {
			Map<String, Object> wm = (Map<String, Object>) tag;
			Map<String, Object> fn = (Map<String, Object>) wm.get("function");
			if (fn != null && fn.containsKey("class")) {
				String s = String.valueOf(fn.get("class")).trim();
				for (String c : s.split("\\s+")) {
					if (!c.isEmpty()) set.add(c);
				}
			}
		}
		if (v instanceof ViewGroup) {
			ViewGroup vg = (ViewGroup) v;
			for (int i = 0; i < vg.getChildCount(); i++) harvestClassesRecursive(vg.getChildAt(i), set);
		}
	}

	private java.util.List<String> harvestTags() {
		return java.util.Arrays.asList(
			"div", "span", "p", "h1", "h2", "h3", "h4", "h5", "h6",
			"a", "button", "input", "textarea", "select", "form",
			"ul", "ol", "li", "img", "header", "footer", "nav", "main", "section"
		);
	}

	private void syncProjectAssets() {
		ProjectAssetManager.getInstance().update(harvestIds(), harvestClasses(), harvestTags());
	}

	public void handleDesignItemClick(int position) {
		if (isDialogShowing) return; // Prevent duplicate dialogs
		View selected = selector.getSelectedView();
		if (selected == null) {
			Toast.makeText(this, "Select a widget first", Toast.LENGTH_SHORT).show();
			return;
		}

		String editType = design.get(position).get("edit").toString();
		String initialValue = design.get(position).containsKey("value") ? design.get(position).get("value").toString() : "";
		syncProjectAssets();
		UniversalM3Dialog dialog = new UniversalM3Dialog(this).setTitle(editType).setInitialValue(initialValue);

		switch (editType) {
			case "Edittext":
				dialog.setHint("Text content").showTextInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("text", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "SetId":
				dialog.setHint("my-element-id").showTextInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("id", value.replaceFirst("^#", ""));
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshHierarchy();
					buildDesignList();
				});
				break;
			case "SetClass":
				dialog.setHint("class1 class2").showTextInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("class", value.replaceFirst("^\\.", ""));
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshHierarchy();
					buildDesignList();
				});
				break;
			case "PickIcon":
				dialog.showIconPicker(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("class", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "SetHref":
				dialog.setHint("https://...").showTextInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("href", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "SetTarget":
				dialog.setOptions(new String[]{"_self", "_blank", "_parent", "_top"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("target", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "SetPlaceholder":
				dialog.setHint("Placeholder...").showTextInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("placeholder", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "SetType":
				dialog.setOptions(new String[]{"text", "password", "email", "number", "tel", "url", "date", "time", "color", "range", "file", "checkbox", "radio", "submit", "reset", "hidden"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("type", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "ImageSrc":
				dialog.setHint("URL or base64").showTextInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("src", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "ListItems":
				showListItemsDialog(selected);
				break;
			case "TextSize":
				dialog.setUnits(new String[]{"px", "rem", "em", "vh", "vw", "pt"}).showUnitInput("fontSize", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("fontSize", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "Color":
				dialog.setSuggestions(getColorSuggestions()).showColorInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("color", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "Font":
				dialog.setOptions(new String[]{"normal", "bold", "lighter", "100", "200", "300", "400", "500", "600", "700", "800", "900"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("fontWeight", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "TextAlign":
				dialog.setOptions(new String[]{"left", "center", "right", "justify"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("textAlign", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "Background":
				dialog.setSuggestions(getColorSuggestions()).showColorInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("backgroundColor", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "BorderRadius":
				dialog.setUnits(new String[]{"px", "rem", "%", "em"}).showUnitInput("borderRadius", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("borderRadius", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "BorderWidth":
				dialog.setUnits(new String[]{"px", "rem", "em"}).showUnitInput("borderWidth", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("borderWidth", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "BorderColor":
				dialog.setSuggestions(getColorSuggestions()).showColorInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("borderColor", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "Padding":
				dialog.setUnits(new String[]{"px", "rem", "%", "em"}).showFourValueInput("padding", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("padding", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "Margin":
				dialog.setUnits(new String[]{"px", "rem", "%", "em", "auto"}).showFourValueInput("margin", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("margin", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "Width":
				dialog.setUnits(new String[]{"px", "rem", "%", "em", "vw", "auto"}).showUnitInput("width", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("width", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "Height":
				dialog.setUnits(new String[]{"px", "rem", "%", "em", "vh", "auto"}).showUnitInput("height", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("height", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildDesignList();
				});
				break;
			case "Display":
				dialog.setOptions(new String[]{"block", "flex", "grid", "inline", "inline-block", "inline-flex", "none", "contents"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("display", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "Position":
				dialog.setOptions(new String[]{"static", "relative", "absolute", "fixed", "sticky"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("position", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "FlexDir":
				dialog.setOptions(new String[]{"row", "column", "row-reverse", "column-reverse"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("flexDirection", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "FlexWrap":
				dialog.setOptions(new String[]{"nowrap", "wrap", "wrap-reverse"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("flexWrap", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "JustifyContent":
				dialog.setOptions(new String[]{"flex-start", "center", "flex-end", "space-between", "space-around", "space-evenly"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("justifyContent", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "AlignItems":
				dialog.setOptions(new String[]{"flex-start", "center", "flex-end", "stretch", "baseline"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("alignItems", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "AlignSelf":
				dialog.setOptions(new String[]{"auto", "flex-start", "center", "flex-end", "stretch", "baseline"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("alignSelf", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "AlignContent":
				dialog.setOptions(new String[]{"flex-start", "center", "flex-end", "stretch", "space-between", "space-around"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("alignContent", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "Gap":
				dialog.setUnits(new String[]{"px", "rem", "em"}).showUnitInput("gap", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("gap", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "RowGap":
				dialog.setUnits(new String[]{"px", "rem", "em"}).showUnitInput("rowGap", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("rowGap", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "ColGap":
				dialog.setUnits(new String[]{"px", "rem", "em"}).showUnitInput("columnGap", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("columnGap", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "Top":
				dialog.setUnits(new String[]{"px", "%", "rem", "em"}).showUnitInput("top", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("top", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "Right":
				dialog.setUnits(new String[]{"px", "%", "rem", "em"}).showUnitInput("right", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("right", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "Bottom":
				dialog.setUnits(new String[]{"px", "%", "rem", "em"}).showUnitInput("bottom", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("bottom", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "Left":
				dialog.setUnits(new String[]{"px", "%", "rem", "em"}).showUnitInput("left", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("left", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "ObjectFit":
				dialog.setOptions(new String[]{"fill", "contain", "cover", "none", "scale-down"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("objectFit", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
			case "AspectRatio":
				dialog.setHint("16/9").showTextInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("aspectRatio", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					buildLayoutDesignList();
				});
				break;
		}
	}
	// ---- CSS Variable Dialog ----

	private void showCssVariableDialog(View targetView) {
		List<String> vars = new ArrayList<>();
		vars.add("var(--primary-color)");
		vars.add("var(--secondary-color)");
		vars.add("var(--accent-color)");
		vars.add("var(--body-background)");
		vars.add("var(--body-color)");
		vars.add("var(--link-color)");
		vars.add("var(--border-color)");
		vars.add("var(--card-background)");
		vars.add("var(--font-family)");

		Map<String, String> customVars = themeManager.getCustomCssVars();
		for (String key : customVars.keySet()) {
			String varName = key.startsWith("--") ? "var(" + key + ")" : "var(--" + key + ")";
			vars.add(varName);
		}

		String[] cssVars = vars.toArray(new String[0]);

		new MaterialAlertDialogBuilder(this)
			.setTitle("Apply CSS Variable")
			.setItems(cssVars, (dialog, which) -> {
				String varName = cssVars[which];
				String[] properties = {"color", "backgroundColor", "borderColor", "fontFamily", "background"};
				new MaterialAlertDialogBuilder(this)
					.setTitle("Apply to Property")
					.setItems(properties, (d2, w2) -> {
						Map<String, Object> style = new HashMap<>();
						style.put(properties[w2], varName);
						widgetUpdater.updateWidget(targetView, "", style);
						saveUndoState();
					})
					.show();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	interface OnStyleConfirmed {
		void onConfirmed(String value);
	}

	/**
	 * Dialog for editing the items array of a list-style widget (ul / ol /
	 * select). Items are entered one per line. The widget tag's "function.items"
	 * map gets replaced with the parsed array.
	 */
	/**
	 * Replace the children of a list widget (ul / ol / select) on the canvas
	 * with a TextView per item so the design surface visually reflects the
	 * configured items.
	 */
	private void rebuildListWidget(View selected, List<String> items) {
		if (!(selected instanceof ViewGroup)) return;
		ViewGroup vg = (ViewGroup) selected;
		vg.removeAllViews();
		boolean ordered = false;
		Object t = selected.getTag();
		if (t instanceof Map) {
			Object tag = ((Map<?, ?>) t).get("tag");
			ordered = "ol".equals(String.valueOf(tag));
		}
		for (int i = 0; i < items.size(); i++) {
			TextView tv = new TextView(this);
			String prefix = ordered ? (i + 1) + ". " : "• ";
			tv.setText(prefix + items.get(i));
			tv.setTextColor(Color.parseColor("#222222"));
			tv.setPadding(8, 4, 8, 4);
			vg.addView(tv);
		}
	}

	@SuppressWarnings("unchecked")
	private void showListItemsDialog(View selected) {
		Object tagObj = selected.getTag();
		if (!(tagObj instanceof Map)) return;
		Map<String, Object> wm = (Map<String, Object>) tagObj;
		Map<String, Object> fn = (Map<String, Object>) wm.get("function");
		if (fn == null) {
			fn = new HashMap<>();
			wm.put("function", fn);
		}

		Object existing = fn.get("items");
		StringBuilder seed = new StringBuilder();
		if (existing instanceof List) {
			for (Object o : (List<?>) existing) {
				if (o != null) seed.append(o.toString()).append("\n");
			}
		}

		LinearLayout container = new LinearLayout(this);
		container.setOrientation(LinearLayout.VERTICAL);
		container.setPadding(48, 16, 48, 0);

		TextView help = new TextView(this);
		help.setText("Enter one item per line:");
		help.setTextSize(12);
		container.addView(help);

		TextInputLayout til = new TextInputLayout(this);
		til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
		TextInputEditText input = new TextInputEditText(this);
		input.setText(seed.toString());
		input.setMinLines(5);
		input.setMaxLines(15);
		input.setGravity(Gravity.TOP | Gravity.START);
		til.addView(input);
		container.addView(til);

		final Map<String, Object> fnRef = fn;
		new MaterialAlertDialogBuilder(this)
			.setTitle("Edit list items")
			.setView(container)
			.setPositiveButton("Save", (d, w) -> {
				String raw = input.getText() != null ? input.getText().toString() : "";
				List<String> parsed = new ArrayList<>();
				for (String line : raw.split("\\r?\\n")) {
					String t = line.trim();
					if (!t.isEmpty()) parsed.add(t);
				}
				fnRef.put("items", parsed);
				selected.setTag(wm);
				rebuildListWidget(selected, parsed);
				saveUndoState();
				refreshHierarchy();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	@Override
	public void onBackPressed() {
		saveProject();
		super.onBackPressed();
	}

	// ---- Adapters ----

	public class Recyclerview3Adapter extends RecyclerView.Adapter<Recyclerview3Adapter.ViewHolder> {
		ArrayList<HashMap<String, Object>> data;

		public Recyclerview3Adapter(ArrayList<HashMap<String, Object>> arr) {
			data = arr;
		}

		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			View v = getLayoutInflater().inflate(R.layout.design, null);
			RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.setMargins(2, 0, 2, 0);
			v.setLayoutParams(lp);
			return new ViewHolder(v);
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, final int position) {
			View view = holder.itemView;
			com.google.android.material.card.MaterialCardView cardview1 = view.findViewById(R.id.cardview1);
			ImageView imageview1 = view.findViewById(R.id.imageview1);
			TextView textview1 = view.findViewById(R.id.textview1);

			String editType = design.get(position).get("edit").toString();
			String value = design.get(position).containsKey("value") ? design.get(position).get("value").toString() : "";
			textview1.setText(editType + (value.isEmpty() ? "" : ": " + value));
			int iconRes = getDesignIcon(editType);
			imageview1.setImageResource(iconRes);
			cardview1.setOnClickListener(v -> handleDesignItemClick(position));
		}

		@Override
		public int getItemCount() {
			return data.size();
		}

		public class ViewHolder extends RecyclerView.ViewHolder {
			public ViewHolder(View v) { super(v); }
		}
	}

	private int getDesignIcon(String editType) {
		switch (editType) {
			case "Edittext": return R.drawable.cursor_text;
			case "SetId": case "SetClass": return R.drawable.icon_design_services_round;
			case "SetHref": case "SetTarget": return R.drawable.icon_web_round;
			case "SetPlaceholder": case "SetType": return R.drawable.cursor_text;
			case "ImageSrc": return R.drawable.default_image;
			case "PickIcon": return R.drawable.emphasis;
			case "TextSize": return R.drawable.textsize;
			case "TextAlign": return R.drawable.focus_centered;
			case "Color": return R.drawable.textcolor;
			case "Font": return R.drawable.alphabet_latin;
			case "Background": return R.drawable.background;
			case "BorderRadius": case "RadiusTL": case "RadiusTR": case "RadiusBL": case "RadiusBR":
				return R.drawable.border_radius;
			case "BorderWidth": case "BorderTop": case "BorderRight": case "BorderBottom": case "BorderLeft":
				return R.drawable.border_style;
			case "BorderColor": return R.drawable.freezerowcolumn;
			case "Padding": return R.drawable.box_padding;
			case "Margin": return R.drawable.box_margin;
			case "Elevation": case "BoxShadow": case "ZIndex": return R.drawable.emphasis;
			case "Opacity": return R.drawable.droplet;
			case "Rotation": return R.drawable.rotate;
			case "Cursor": return R.drawable.cursor_text;
			case "Width": case "Height": case "MinWidth": case "MaxWidth": case "MinHeight": case "MaxHeight":
				return R.drawable.border_sides;
			case "Display": case "Position": case "Float": case "Clear":
			case "Top": case "Right": case "Bottom": case "Left":
				return R.drawable.box_padding;
			case "FlexDir": case "FlexWrap": case "JustifyContent": case "AlignItems":
			case "AlignSelf": case "AlignContent": case "Gap": case "RowGap": case "ColGap":
			case "GridCols": case "GridRows": case "GridGap":
				return R.drawable.freezerowcolumn;
			case "ObjectFit": case "AspectRatio": return R.drawable.resize;
			case "Overflow": return R.drawable.resize;
			case "TextDecor": return R.drawable.cursor_text;
			case "LineHeight": case "LetterSpace": return R.drawable.textsize;
			case "Gradient": return R.drawable.background;
			case "CssVar": case "CustomStyle": return R.drawable.icon_design_services_round;
			default: return R.drawable.cursor_text;
		}
	}

	public class Recyclerview1Adapter extends RecyclerView.Adapter<Recyclerview1Adapter.ViewHolder> {
		ArrayList<HashMap<String, Object>> data;

		public Recyclerview1Adapter(ArrayList<HashMap<String, Object>> arr) {
			data = arr;
		}

		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			View v = getLayoutInflater().inflate(R.layout.widgets, parent, false);
			return new ViewHolder(v);
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, final int position) {
			View view = holder.itemView;
			TextView textview1 = view.findViewById(R.id.textview1);
			TextView tvWidgetTag = view.findViewById(R.id.tvWidgetTag);
			View colorIndicator = view.findViewById(R.id.colorIndicator);

			Map<String, Object> item = data.get(position);
			String name = item.get("name").toString();
			String tag = item.get("tag").toString();
			String color = item.get("color").toString();

			textview1.setText(name);
			if (tvWidgetTag != null) {
				tvWidgetTag.setText("<" + tag + ">");
			}

			if (colorIndicator != null) {
				try {
					colorIndicator.setBackgroundColor(Color.parseColor(color));
				} catch (Exception e) {
					colorIndicator.setBackgroundColor(Color.LTGRAY);
				}
			}

			// Long press to drag (replaces touch-to-drag)
			view.setOnLongClickListener(v -> {
				ClipData.Item itemData = new ClipData.Item(String.valueOf(position));
				ClipData dragData = new ClipData("widget", new String[]{"text/plain"}, itemData);
				View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
				v.startDragAndDrop(dragData, shadowBuilder, v, 0);
				// Auto-close drawer when drag starts
				androidx.drawerlayout.widget.DrawerLayout drawer = findViewById(R.id._main);
				if (drawer != null) {
					drawer.closeDrawers();
				}
				return true;
			});
		}

		@Override
		public int getItemCount() {
			return data.size();
		}

		public class ViewHolder extends RecyclerView.ViewHolder {
			public ViewHolder(View v) { super(v); }
		}
	}
}
