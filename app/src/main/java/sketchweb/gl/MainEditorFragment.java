package sketchweb.gl;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import androidx.fragment.app.Fragment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainEditorFragment extends Fragment {

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
	private ViewPager2 viewPager;
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
	private View layoutLoading;
	private View progressSave;
	private LinearLayout rightPanel;
	private LinearLayout bottomPanel;
	private LinearLayout eventPanel;
	private LinearLayout assetsPanel;
	private NestedScrollView vscroll2;
	private Button button5, button4, delete, btnImportWidgets, btnImportImage, btnImportSvg;
	private Button btnDrawer, btnUndo, btnRedo, btnTheme, btnExport, btnHierarchy;
	private Button btnAddLogicBlock, btnViewAllBlocks, btnResetLogic;
	private LinearLayout blockEditorContainer;
	private Button btnNewFolder;
	private Button btnCopyWidget;
	private TextView textview2, tvAssetsPath, pageNameTv;
	private RecyclerView recyclerview3, recyclerview1, recyclerviewRightPanel, rvAssets;
	private RecyclerView rvDrawerWidgets;
	private com.google.android.material.card.MaterialCardView cardSelectedWidget;
	private android.view.View btnWidgetDropdown;
	private android.widget.TextView tvSelectedWidgetName;
	private android.widget.ImageView btnBottomLock, btnBottomHide;
	private View pageNameContainer;
	private TabLayout tabLayout;
	private Chip chipBasic, chipLayout;
	private ChipGroup chipGroupBottom;
	private TextInputEditText etSearchWidget, etSearchHierarchy;
	private ChipGroup chipGroupCategories;
	private ChipGroup chipGroupDrawerCategories;

	private androidx.drawerlayout.widget.DrawerLayout drawerLayout;
	private View rightPanelCard;

	private ActivityResultLauncher<android.content.Intent> importWidgetLauncher;
	private ActivityResultLauncher<android.content.Intent> importAssetLauncher;
	private ActivityResultLauncher<android.content.Intent> importSvgLauncher;
	private ActivityResultLauncher<android.content.Intent> importZipLauncher;
	private ActivityResultLauncher<android.content.Intent> importPageHtmlLauncher;
	private ActivityResultLauncher<Intent> managePageLauncher;
	private android.net.Uri pageImportHtmlUri = null;
	private TextView tvDialogHtmlFileName = null;
	private android.widget.ImageButton btnDialogClearHtml = null;
	private TextInputEditText etDialogPageName = null;

	// Track if a dialog is currently showing to avoid duplicates
	private boolean isDialogShowing = false;

	public PageManager getPageManager() {
		return pageManager;
	}

	public static MainEditorFragment newInstance(String projectId, String projectName) {
		MainEditorFragment fragment = new MainEditorFragment();
		Bundle args = new Bundle();
		args.putString("project_id", projectId);
		args.putString("project_name", projectName);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (getArguments() != null) {
			projectId = getArguments().getString("project_id");
			projectName = getArguments().getString("project_name");
		}

		// Register logic block activity launcher before fragment is active
		logicBlockLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == Activity.RESULT_OK) {
					// Reload logic blocks from file after returning
					if (logicBlockManager != null) {
						File dir = new File(new File(requireContext().getFilesDir(), "projects"), "logic");
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

		registerLaunchers();
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_main_editor, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		initializeViews(view);
		initializeLogic();
		loadProject();

		// Store initial paddings to avoid cumulative padding on re-application
		final int topBarInitialTop = topBar != null ? topBar.getPaddingTop() : 0;
		final int bottomPanelInitialBottom = bottomPanel != null ? bottomPanel.getPaddingBottom() : 0;
		final int mainInitialLeft = main != null ? main.getPaddingLeft() : 0;
		final int mainInitialRight = main != null ? main.getPaddingRight() : 0;

		View mainRoot = view.findViewById(R.id._main);
		if (mainRoot != null) {
			ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (v, insets) -> {
				Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
				
				// Keep default topBar padding without systemBars.top (since MainActivity toolbar handles top inset)
				if (topBar != null) {
					topBar.setPadding(topBar.getPaddingLeft(), topBarInitialTop, topBar.getPaddingRight(), topBar.getPaddingBottom());
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
	}

	private void registerLaunchers() {
		importWidgetLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
					android.net.Uri uri = result.getData().getData();
					if (uri != null) {
						try {
							InputStream is = requireActivity().getContentResolver().openInputStream(uri);
							byte[] buffer = new byte[is.available()];
							is.read(buffer);
							is.close();
							String json = new String(buffer, "UTF-8");
							widgetRegistry.importCustomWidgets(json);
							refreshWidgetList();
							Toast.makeText(requireContext(), "Widgets imported!", Toast.LENGTH_SHORT).show();
						} catch (Exception e) {
							Toast.makeText(requireContext(), "Failed to import widgets.", Toast.LENGTH_SHORT).show();
						}
					}
				}
			}
		);

		importZipLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
					android.net.Uri uri = result.getData().getData();
					if (uri != null) importProjectBackup(uri);
				}
			}
		);

		importSvgLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
					android.net.Uri uri = result.getData().getData();
					if (uri != null) {
						try {
							InputStream is = requireActivity().getContentResolver().openInputStream(uri);
							byte[] buffer = new byte[is.available()];
							is.read(buffer);
							is.close();
							String svgContent = new String(buffer, "UTF-8");
							widgetRegistry.importCustomWidgets(svgContent); 
							refreshWidgetList();
							Toast.makeText(requireContext(), "SVG imported!", Toast.LENGTH_SHORT).show();
						} catch (Exception e) {
							Toast.makeText(requireContext(), "Failed to import SVG.", Toast.LENGTH_SHORT).show();
						}
					}
				}
			}
		);

		importPageHtmlLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
					android.net.Uri uri = result.getData().getData();
					if (uri != null) {
						pageImportHtmlUri = uri;
						if (tvDialogHtmlFileName != null) {
							String name = resolveFileName(uri);
							tvDialogHtmlFileName.setText(name != null ? name : "HTML file selected");
							if (name != null && etDialogPageName != null) {
								String baseName = name;
								if (baseName.toLowerCase().endsWith(".html")) {
									baseName = baseName.substring(0, baseName.length() - 5);
								} else if (baseName.toLowerCase().endsWith(".htm")) {
									baseName = baseName.substring(0, baseName.length() - 4);
								}
								baseName = baseName.replaceAll("[^a-zA-Z0-9_-]", "");
								etDialogPageName.setText(baseName);
							}
						}
						if (btnDialogClearHtml != null) {
							btnDialogClearHtml.setVisibility(View.VISIBLE);
						}
					}
				}
			}
		);

		managePageLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
					Intent data = result.getData();
					String action = data.getStringExtra("action");
					if ("delete".equals(action)) {
						String deletedPage = data.getStringExtra("page_name");
						if (deletedPage != null) {
							pageManager.removePage(deletedPage);
							if (deletedPage.equals(pageManager.getCurrentPage())) {
								pageManager.setCurrentPage("index");
								loadCurrentPageLayout();
								
								File dir = new File(new File(requireContext().getFilesDir(), "projects"), "logic");
								File logicFile = new File(dir, projectId + "_index.logic");
								if (logicFile.exists()) {
									String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
									logicBlockManager.fromJson(logicJson);
								} else {
									logicBlockManager.fromJson("[]");
								}
								refreshLogicBlocksUI();
							}
						}
					} else if ("save".equals(action)) {
						String oldName = data.getStringExtra("old_page_name");
						String newName = data.getStringExtra("new_page_name");
						
						if (oldName != null && newName != null) {
							if (!oldName.equals(newName)) {
								pageManager.renamePage(oldName, newName);
							}
							if (oldName.equals(pageManager.getCurrentPage())) {
								pageManager.setCurrentPage(newName);
								loadCurrentPageLayout();
								
								File dir = new File(new File(requireContext().getFilesDir(), "projects"), "logic");
								File logicFile = new File(dir, projectId + "_" + newName + ".logic");
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
					updatePageSpinner();
				}
			}
		);
	}

	private void initializeViews(View view) {
		main = view.findViewById(R.id.main);
		topBar = view.findViewById(R.id.topBar);
		screen = view.findViewById(R.id.screen);
		layoutLoading = view.findViewById(R.id.layoutLoading);
		rightPanel = null;
		bottomPanel = view.findViewById(R.id.bottomPanel);
		vscroll2 = view.findViewById(R.id.vscroll2);
		button5 = view.findViewById(R.id.button5);
		button4 = requireActivity().findViewById(R.id.button4);
		progressSave = requireActivity().findViewById(R.id.progressSave);
		delete = view.findViewById(R.id.delete);
		textview2 = view.findViewById(R.id.textview2);
		recyclerview3 = view.findViewById(R.id.recyclerview3);
		recyclerview3.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
			@Override
			public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
				int action = e.getAction();
				if (action == android.view.MotionEvent.ACTION_DOWN || action == android.view.MotionEvent.ACTION_MOVE) {
					rv.getParent().requestDisallowInterceptTouchEvent(true);
				}
				return false;
			}

			@Override
			public void onTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {}

			@Override
			public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
		});
		recyclerview1 = null;
		recyclerviewRightPanel = view.findViewById(R.id.recyclerviewRightPanel);
		if (recyclerviewRightPanel != null) {
			recyclerviewRightPanel.setOnTouchListener((v, event) -> {
				v.getParent().requestDisallowInterceptTouchEvent(true);
				return false;
			});
		}
		rvDrawerWidgets = view.findViewById(R.id.rvDrawerWidgets);
		cardSelectedWidget = view.findViewById(R.id.cardSelectedWidget);
		btnWidgetDropdown = view.findViewById(R.id.btnWidgetDropdown);
		tvSelectedWidgetName = view.findViewById(R.id.tvSelectedWidgetName);
		btnBottomLock = view.findViewById(R.id.btnBottomLock);
		btnBottomHide = view.findViewById(R.id.btnBottomHide);
		pageNameContainer = view.findViewById(R.id.page_name_container);
		pageNameTv = view.findViewById(R.id.page_name);
		btnDrawer = view.findViewById(R.id.btnDrawer);
		btnUndo = view.findViewById(R.id.btnUndo);
		btnRedo = view.findViewById(R.id.btnRedo);
		btnTheme = requireActivity().findViewById(R.id.btnTheme);
		btnHierarchy = view.findViewById(R.id.btnHierarchy);
		btnExport = requireActivity().findViewById(R.id.btnExport);
		btnCopyWidget = view.findViewById(R.id.btnCopyWidget);
		chipBasic = view.findViewById(R.id.chipBasic);
		chipLayout = view.findViewById(R.id.chipLayout);
		chipGroupBottom = view.findViewById(R.id.chipGroupBottom);
		etSearchWidget = view.findViewById(R.id.etSearchWidget);
		etSearchHierarchy = view.findViewById(R.id.etSearchHierarchy);
		chipGroupCategories = view.findViewById(R.id.chipGroupCategories);
		chipGroupDrawerCategories = view.findViewById(R.id.chipGroupDrawerCategories);

		// Drawer button - store reference as field for reliability
		drawerLayout = view.findViewById(R.id._main);
		btnDrawer.setOnClickListener(v -> {
			try {
				if (drawerLayout != null) {
					View drawerContent = view.findViewById(R.id.drawerContent);
					if (drawerContent != null) {
						if (drawerLayout.isDrawerOpen(drawerContent)) {
							drawerLayout.closeDrawer(drawerContent);
						} else {
							drawerLayout.openDrawer(drawerContent);
						}
					}
				}
			} catch (Exception e) {
				Log.w("MainEditorFragment", "Drawer toggle error: " + e.getMessage());
			}
		});
		if (btnHierarchy != null) {
			btnHierarchy.setOnClickListener(v -> {
				try {
					if (drawerLayout != null) {
						View rightDrawer = view.findViewById(R.id.rightDrawerContent);
						if (rightDrawer != null) {
							if (drawerLayout.isDrawerOpen(rightDrawer)) {
								drawerLayout.closeDrawer(rightDrawer);
							} else {
								drawerLayout.openDrawer(rightDrawer);
							}
						}
					}
				} catch (Exception e) {
					Log.w("MainEditorFragment", "Hierarchy drawer toggle error: " + e.getMessage());
				}
			});
		}
		Button btnCloseHierarchy = view.findViewById(R.id.btnCloseHierarchy);
		if (btnCloseHierarchy != null) {
			btnCloseHierarchy.setOnClickListener(v -> {
				try {
					if (drawerLayout != null) {
						View rightDrawer = view.findViewById(R.id.rightDrawerContent);
						if (rightDrawer != null) {
							drawerLayout.closeDrawer(rightDrawer);
						}
					}
				} catch (Exception e) {
					Log.w("MainEditorFragment", "Hierarchy drawer close error: " + e.getMessage());
				}
			});
		}

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

		if (btnTheme != null) {
			btnTheme.setOnClickListener(v -> {
				android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), v);
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
		}

		btnExport.setOnClickListener(v -> showExportDialog());

		setupBottomChips();
		setupWidgetSearch();
		setupHierarchySearch();
		setupDrawerCategories(view);
	}

	private void setupDrawerCategories(View view) {
		if (chipGroupDrawerCategories == null) return;
		populateCategoryChips();
		chipGroupDrawerCategories.setOnCheckedStateChangeListener((group, checkedIds) -> {
			String filter = "all";
			if (!checkedIds.isEmpty()) {
				int checkedId = checkedIds.get(0);
				for (int i = 0; i < group.getChildCount(); i++) {
					View child = group.getChildAt(i);
					if (child.getId() == checkedId && child.getTag() instanceof String) {
						filter = (String) child.getTag();
						break;
					}
				}
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

	private void populateCategoryChips() {
		Set<String> categories = new LinkedHashSet<>();
		for (HashMap<String, Object> w : widgets) {
			String cat = String.valueOf(w.get("category"));
			if (cat != null && !"null".equals(cat) && !cat.isEmpty()) {
				categories.add(cat);
			}
		}

		// Populate hidden chipGroupCategories (used for programmatic access)
		if (chipGroupCategories != null) {
			chipGroupCategories.removeAllViews();
			Chip allChip = new Chip(requireContext());
			allChip.setText("All");
			allChip.setCheckable(true);
			allChip.setChecked(false);
			allChip.setTag("all");
			allChip.setId(View.generateViewId());
			chipGroupCategories.addView(allChip);
			for (String cat : categories) {
				Chip chip = new Chip(requireContext());
				chip.setText(cat.substring(0, 1).toUpperCase() + cat.substring(1));
				chip.setCheckable(true);
				chip.setTag(cat);
				chip.setId(View.generateViewId());
				chipGroupCategories.addView(chip);
			}
		}

		// Populate visible drawer chip group
		if (chipGroupDrawerCategories != null) {
			chipGroupDrawerCategories.removeAllViews();
			Chip allChip = new Chip(requireContext());
			allChip.setText("All");
			allChip.setChecked(false);
			allChip.setCheckable(true);
			allChip.setClickable(true);
			allChip.setTextSize(11);
			allChip.setTag("all");
			allChip.setId(View.generateViewId());
			chipGroupDrawerCategories.addView(allChip);
			for (String cat : categories) {
				Chip chip = new Chip(requireContext());
				chip.setText(cat.substring(0, 1).toUpperCase() + cat.substring(1));
				chip.setCheckable(true);
				chip.setClickable(true);
				chip.setTextSize(11);
				chip.setTag(cat);
				chip.setId(View.generateViewId());
				chipGroupDrawerCategories.addView(chip);
			}
		}
	}

	private void initializeLogic() {
		engine = new WidgetBuilderEngine(requireContext());
		widgetUpdater = new WidgetUpdater(requireContext(), engine);
		codeGenerator = new PageCodeGenerator();
		codeGenerator.setProjectInfo(projectName, null);
		projectDataManager = new ProjectDataManager(requireContext());
		themeManager = new ThemeManager();
		ProjectAssetManager.getInstance().setThemeManager(themeManager);
		exportManager = new ExportManager(requireContext(), themeManager);
		exportManager.setProjectId(projectId);

		IconLibraryManager iconMgr = new IconLibraryManager(requireContext(), projectId);
		exportManager.setIconLibraryManager(iconMgr);
		codeGenerator.setIconLibraryManager(iconMgr);
		AnimationLibraryManager animMgr = new AnimationLibraryManager(requireContext(), projectId);
		exportManager.setAnimationLibraryManager(animMgr);
		codeGenerator.setAnimationLibraryManager(animMgr);
		logicBlockManager = new LogicBlockManager(requireContext());
		customBlockManager = new ManageBlocksWidgets(requireContext());
		pageManager = new PageManager(requireContext(), projectId);

		// Load logic blocks for the initial page
		File dir = new File(new File(requireContext().getFilesDir(), "projects"), "logic");
		String pageName = pageManager != null ? pageManager.getCurrentPage() : "index";
		File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
		if (logicFile.exists()) {
			String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
			logicBlockManager.fromJson(logicJson);
		} else {
			File oldLogicFile = new File(dir.getParentFile(), projectId + "_" + pageName + ".logic");
			if (oldLogicFile.exists()) {
				String logicJson = FileUtil.readFile(oldLogicFile.getAbsolutePath());
				logicBlockManager.fromJson(logicJson);
			} else {
				File veryOldLogicFile = new File(dir.getParentFile(), projectId + ".logic");
				if (veryOldLogicFile.exists()) {
					String logicJson = FileUtil.readFile(veryOldLogicFile.getAbsolutePath());
					logicBlockManager.fromJson(logicJson);
				}
			}
		}

		// Undo/Redo
		undoRedoManager = new UndoRedoManager();
		undoRedoManager.setOnStateChangeListener((canUndo, canRedo) -> {
			if (btnUndo != null) btnUndo.setEnabled(canUndo);
			if (btnRedo != null) btnRedo.setEnabled(canRedo);
		});

		// Widget selector
		selector = new WidgetSelector(requireContext());

		// Load widgets
		widgetRegistry = new WidgetRegistry(requireContext());
		widgets = widgetRegistry.getAllWidgets();
		filteredWidgets = new ArrayList<>(widgets);

		populateCategoryChips();

		autoLoadCustomConfigs();

		// Setup hierarchy tree
		hierarchyAdapter = new HierarchyTreeAdapter(requireContext());
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


		// Widget selector listeners
		if (selector != null) {
			selector.setOnWidgetSelectedListener(widgetId -> {
				textview2.setText("Selected: " + widgetId);
				delete.setEnabled(true);
				updateWidgetSpinnerFromTree();
				if (hierarchyAdapter != null) {
					hierarchyAdapter.setSelectedView(selector.getSelectedView());
				}
				refreshActiveDesignList();
			});
			selector.attachTo(screen);
		}

		if (recyclerview1 != null) {
			recyclerview1.setAdapter(new Recyclerview1Adapter(filteredWidgets));
			recyclerview1.setLayoutManager(new LinearLayoutManager(requireContext()));
		}

		// Set up widget list in drawer (vertical - bigger)
		if (rvDrawerWidgets != null) {
			rvDrawerWidgets.setAdapter(new Recyclerview1Adapter(filteredWidgets));
			rvDrawerWidgets.setLayoutManager(new LinearLayoutManager(requireContext()));
		}

		if (recyclerviewRightPanel != null) {
			recyclerviewRightPanel.setAdapter(hierarchyAdapter);
			recyclerviewRightPanel.setLayoutManager(new LinearLayoutManager(requireContext()));
			if (hierarchyAdapter != null) {
				hierarchyAdapter.attachToRecyclerView(recyclerviewRightPanel);
			}
		}

		dropZoneManager = new DropZoneManager(requireContext(), screen, filteredWidgets, engine, selector);
		dropZoneManager.setOnTreeChangedListener(() -> {
			saveUndoState();
			updateWidgetSpinnerFromTree();
			refreshHierarchy();
		});

		setupHierarchyTracker(screen);
		setupCanvasDragListener();

		// Set up page selector (which loads layout)
		setupPageSelector();

		if (main != null) {
			main.setOnClickListener(v -> {
				View selected = selector.getSelectedView();
				if (selected != null) {
					selector.clearSelection();
					textview2.setText("No widget selected");
					delete.setEnabled(false);
					if (hierarchyAdapter != null) {
						hierarchyAdapter.setSelectedView(null);
					}
					updateWidgetSpinnerFromTree();
				}
			});
		}

		loadClipboardFromDisk();
		buildDesignList();
		saveUndoState();
		refreshHierarchy();
	}

	// ---- Add Logic Block with target selector ----


	// ---- Widget Context Menu (long press on hierarchy) ----

	private void showWidgetContextMenu(View widgetView) {
		String[] options = {"Copy", "Duplicate", "Lock/Unlock", "Hide/Show", "Delete"};
		new MaterialAlertDialogBuilder(requireContext())
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
			try {
				java.io.File clipboardFile = new java.io.File(requireContext().getFilesDir(), "widget_clipboard.json");
				FileUtil.writeFile(clipboardFile.getAbsolutePath(), new Gson().toJson(widgetClipboard));
			} catch (Exception e) {
				e.printStackTrace();
			}
			Toast.makeText(requireContext(), "Widget copied", Toast.LENGTH_SHORT).show();
		}
	}

	private void pasteWidget() {
		if (widgetClipboard == null) {
			Toast.makeText(requireContext(), "Nothing to paste", Toast.LENGTH_SHORT).show();
			return;
		}
		Map<String, Object> pastedMap = deepCopyWidgetMap(widgetClipboard);
		View selected = selector.getSelectedView();
		if (selected != null) {
			if (selected instanceof ViewGroup) {
				rebuildView(pastedMap, (ViewGroup) selected);
			} else if (selected.getParent() instanceof ViewGroup) {
				ViewGroup parent = (ViewGroup) selected.getParent();
				int index = parent.indexOfChild(selected);
				rebuildViewAt(pastedMap, parent, index + 1);
			} else {
				rebuildView(pastedMap, screen);
			}
		} else {
			rebuildView(pastedMap, screen);
		}
		saveUndoState();
		refreshHierarchy();
		updateWidgetSpinnerFromTree();
		Toast.makeText(requireContext(), "Widget pasted", Toast.LENGTH_SHORT).show();
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
			Map<String, Object> duplicatedMap = deepCopyWidgetMap(original);
			rebuildViewAt(duplicatedMap, parent, index + 1);
			saveUndoState();
			refreshHierarchy();
			updateWidgetSpinnerFromTree();
			Toast.makeText(requireContext(), "Widget duplicated", Toast.LENGTH_SHORT).show();
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
			Toast.makeText(requireContext(), locked ? "Widget unlocked" : "Widget locked", Toast.LENGTH_SHORT).show();
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
			Toast.makeText(requireContext(), hidden ? "Widget visible" : "Widget hidden", Toast.LENGTH_SHORT).show();
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

	private void loadClipboardFromDisk() {
		try {
			java.io.File clipboardFile = new java.io.File(requireContext().getFilesDir(), "widget_clipboard.json");
			if (clipboardFile.exists()) {
				String json = FileUtil.readFile(clipboardFile.getAbsolutePath());
				widgetClipboard = new Gson().fromJson(json, Map.class);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// ---- Copy/Paste Menu ----

	private void showCopyPasteMenu() {
		List<String> options = new ArrayList<>();
		View selected = selector.getSelectedView();

		if (selected != null) {
			options.add("Copy Selected Widget");
			options.add("Duplicate Selected Widget");
		}
		if (widgetClipboard != null) {
			options.add("Paste Widget");
		}
		options.add("Duplicate Multiple…");

		new MaterialAlertDialogBuilder(requireContext())
			.setTitle("Copy / Paste")
			.setItems(options.toArray(new String[0]), (dialog, which) -> {
				String choice = options.get(which);
				if ("Copy Selected Widget".equals(choice)) {
					if (selected != null) copyWidget(selected);
				} else if ("Paste Widget".equals(choice)) {
					pasteWidget();
				} else if ("Duplicate Selected Widget".equals(choice)) {
					if (selected != null) duplicateWidget(selected);
				} else if ("Duplicate Multiple…".equals(choice)) {
					showDuplicateMultiPicker();
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
			Toast.makeText(requireContext(), "Nothing to duplicate yet", Toast.LENGTH_SHORT).show();
			return;
		}
		boolean[] checked = new boolean[flat.size()];
		new MaterialAlertDialogBuilder(requireContext())
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
				Toast.makeText(requireContext(), count + " duplicated", Toast.LENGTH_SHORT).show();
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



	public void useFileAsImageSource(File file) {
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
				Toast.makeText(requireContext(), "Image source set", Toast.LENGTH_SHORT).show();
			} catch (Exception e) {
				Toast.makeText(requireContext(), "Could not load file", Toast.LENGTH_SHORT).show();
			}
		} else {
			Toast.makeText(requireContext(), "Select an Image widget first", Toast.LENGTH_SHORT).show();
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
		if (pageNameContainer != null) {
			pageNameContainer.setOnClickListener(v -> showPageSelectorPopup());
		}
		updatePageSpinner();
	}

	private void updatePageSpinner() {
		if (pageManager == null) return;
		if (pageNameTv != null) {
			pageNameTv.setText(pageManager.getCurrentPage());
		}
	}

	private void switchPage(String selectedPage) {
		if (pageManager == null) return;

		// Save current project state first, which also compiles and updates assets
		saveProject();

		// Switch to new page
		pageManager.setCurrentPage(selectedPage);

		// Load the new page layout
		loadCurrentPageLayout();

		// Load logic blocks for new page
		if (logicBlockManager != null) {
			File dir = new File(new File(requireContext().getFilesDir(), "projects"), "logic");
			File logicFile = new File(dir, projectId + "_" + selectedPage + ".logic");
			if (logicFile.exists()) {
				String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
				logicBlockManager.fromJson(logicJson);
			} else {
				logicBlockManager.fromJson("[]"); // clear for new page
			}
			refreshLogicBlocksUI();
		}

		saveUndoState();
		refreshHierarchy();
		updatePageSpinner();
	}

	private void showRenamePageDialog(String pageName, android.app.Dialog selectorDialog) {
		if (getContext() == null) return;
		new UniversalM3Dialog(getContext())
			.setTitle("Rename Page")
			.setHint("New page name")
			.setInitialValue(pageName)
			.showTextInput(newName -> {
				if (newName != null && !newName.trim().isEmpty() && !newName.equals(pageName)) {
					String cleanName = newName.trim().replaceAll("[^a-zA-Z0-9_-]", "");
					if (cleanName.isEmpty()) return;
					pageManager.renamePage(pageName, cleanName);
					selectorDialog.dismiss();
					updatePageSpinner();
				}
			});
	}

	private void showDeletePageConfirmDialog(String pageName, android.app.Dialog selectorDialog) {
		if (getContext() == null) return;
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
			.setTitle("Delete Page")
			.setMessage("Are you sure you want to delete '" + pageName + "'? This cannot be undone.")
			.setPositiveButton("Delete", (d, w) -> {
				pageManager.removePage(pageName);
				// If we deleted the current page, switch back to index
				if (pageName.equals(pageManager.getCurrentPage())) {
					pageManager.setCurrentPage("index");
					loadCurrentPageLayout();
					File dir = new File(new File(requireContext().getFilesDir(), "projects"), "logic");
					File logicFile = new File(dir, projectId + "_index.logic");
					if (logicFile.exists()) {
						String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
						logicBlockManager.fromJson(logicJson);
					} else {
						logicBlockManager.fromJson("[]");
					}
					refreshLogicBlocksUI();
				}
				selectorDialog.dismiss();
				updatePageSpinner();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private String getPageCssPath(String pageName) {
		File metaFile = new File(requireContext().getFilesDir(), "projects/" + projectId + "_" + pageName + ".meta");
		if (metaFile.exists()) {
			try {
				String json = FileUtil.readFile(metaFile.getAbsolutePath());
				Map<String, Object> map = new Gson().fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
				if (map != null) {
					boolean useGlobal = map.containsKey("useGlobalCss") ? (Boolean) map.get("useGlobalCss") : true;
					if (useGlobal) {
						return "css/style.css";
					} else if (map.containsKey("customCssPath")) {
						return (String) map.get("customCssPath");
					}
				}
			} catch (Exception e) {
				// Ignore
			}
		}
		return "css/style.css";
	}

	private void populateMiniPreview(LinearLayout container, List<Map<String, Object>> widgets) {
		container.removeAllViews();
		if (widgets == null || widgets.isEmpty()) {
			return;
		}

		int count = 0;
		for (Map<String, Object> widget : widgets) {
			if (count >= 6) break;
			String tag = widget.containsKey("tag") ? String.valueOf(widget.get("tag")) : "div";
			
			View v = new View(container.getContext());
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			);
			lp.setMargins(0, 0, 0, (int) (3 * getResources().getDisplayMetrics().density));
			
			if ("button".equals(tag)) {
				lp.height = (int) (6 * getResources().getDisplayMetrics().density);
				lp.width = (int) (40 * getResources().getDisplayMetrics().density);
				android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
				gd.setColor(getResources().getColor(android.R.color.holo_blue_dark));
				gd.setCornerRadius(3 * getResources().getDisplayMetrics().density);
				v.setBackground(gd);
			} else if (tag.startsWith("h")) {
				lp.height = (int) (6 * getResources().getDisplayMetrics().density);
				v.setBackgroundColor(android.graphics.Color.DKGRAY);
			} else if ("p".equals(tag) || "span".equals(tag)) {
				lp.height = (int) (3 * getResources().getDisplayMetrics().density);
				v.setBackgroundColor(android.graphics.Color.LTGRAY);
			} else {
				lp.height = (int) (12 * getResources().getDisplayMetrics().density);
				android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
				gd.setStroke(1, android.graphics.Color.GRAY);
				gd.setColor(android.graphics.Color.TRANSPARENT);
				gd.setCornerRadius(2 * getResources().getDisplayMetrics().density);
				v.setBackground(gd);
			}
			v.setLayoutParams(lp);
			container.addView(v);
			count++;
		}
	}

	private void showPageSelectorPopup() {
		showPageSelectorPopupWithTab(R.id.option_view);
	}

	private void showPageSelectorPopupWithTab(int defaultCheckedId) {
		if (getContext() == null || pageManager == null) return;

		View popupView = LayoutInflater.from(requireContext()).inflate(R.layout.page_selector_popup, null);
		android.app.Dialog dialog = new android.app.Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar);
		dialog.setContentView(popupView);

		// Setup window animations and edge-to-edge
		android.view.Window window = dialog.getWindow();
		if (window != null) {
			window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
			androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false);
			window.setStatusBarColor(Color.TRANSPARENT);
			window.setNavigationBarColor(Color.TRANSPARENT);
			window.setWindowAnimations(android.R.style.Animation_Dialog);
		}

		// Setup dim background click listener to close
		View container = popupView.findViewById(R.id.container);
		if (container != null) {
			container.setOnClickListener(v -> dialog.dismiss());
			ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> {
				Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
				v.setPadding(
					v.getPaddingLeft(),
					systemBars.top + (int) (16 * getResources().getDisplayMetrics().density),
					v.getPaddingRight(),
					systemBars.bottom + (int) (16 * getResources().getDisplayMetrics().density)
				);
				return insets;
			});
		}

		// Setup MaterialButtonToggleGroup
		com.google.android.material.button.MaterialButtonToggleGroup optionsSelector = popupView.findViewById(R.id.options_selector);
		TextView emptyMessage = popupView.findViewById(R.id.empty_message);
		RecyclerView listXml = popupView.findViewById(R.id.list_xml);
		View createNewView = popupView.findViewById(R.id.createNewView);

		if (createNewView != null) {
			createNewView.setOnClickListener(v -> {
				if (optionsSelector != null && optionsSelector.getCheckedButtonId() == R.id.option_custom_view) {
					dialog.dismiss();
					showAddCssFileDialog(() -> {
						showPageSelectorPopupWithTab(R.id.option_custom_view);
					});
				} else if (optionsSelector != null && optionsSelector.getCheckedButtonId() == R.id.option_js_view) {
					dialog.dismiss();
					showAddJsFileDialog(() -> {
						showPageSelectorPopupWithTab(R.id.option_js_view);
					});
				} else {
					dialog.dismiss();
					showAddPageDialog();
				}
			});
		}

		// RecyclerView setup
		listXml.setLayoutManager(new LinearLayoutManager(requireContext()));

		class PageSelectorAdapter extends RecyclerView.Adapter<PageSelectorAdapter.ViewHolder> {
			private final List<String> items;

			PageSelectorAdapter(List<String> items) {
				this.items = items;
			}

			class ViewHolder extends RecyclerView.ViewHolder {
				TextView tvPageName, tvLinkedStyleName, tvPreview;
				View cardView, actionContainer;
				Button imgEdit;
				LinearLayout layoutPreviewContainer;

				ViewHolder(View view) {
					super(view);
					tvPageName = view.findViewById(R.id.tv_pagename);
					tvLinkedStyleName = view.findViewById(R.id.tv_linked_stylename);
					tvPreview = view.findViewById(R.id.tv_preview);
					cardView = view.findViewById(R.id.cardView);
					actionContainer = view.findViewById(R.id.action_container);
					imgEdit = view.findViewById(R.id.img_edit);
					layoutPreviewContainer = view.findViewById(R.id.layout_preview_container);
				}
			}

			@NonNull
			@Override
			public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
				View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.page_selector_item, parent, false);
				return new ViewHolder(v);
			}

			@Override
			public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
				String name = items.get(position);
				boolean isCss = name.endsWith(".css");
				boolean isJs = name.endsWith(".js");

				if (isCss || isJs) {
					String displayName = name.substring(name.lastIndexOf('/') + 1);
					holder.tvPageName.setText(displayName);
					holder.tvLinkedStyleName.setText(name + " (" + getBlockCountForCss(name) + " blocks)");

					if (holder.tvPreview != null) {
						holder.tvPreview.setText(isJs ? "JS" : "CSS");
						holder.tvPreview.setVisibility(View.VISIBLE);
					}
					if (holder.layoutPreviewContainer != null) {
						holder.layoutPreviewContainer.setVisibility(View.GONE);
					}

					if (holder.imgEdit != null) {
						holder.imgEdit.setVisibility(View.GONE);
					}

					if (holder.cardView instanceof com.google.android.material.card.MaterialCardView) {
						com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) holder.cardView;
						card.setStrokeWidth(0);
					}

					if (holder.actionContainer != null) {
						holder.actionContainer.setOnClickListener(v -> {});
					}

					holder.itemView.setOnClickListener(v -> {
						saveProject();
						dialog.dismiss();
						Intent intent = new Intent(requireContext(), LogicBlockActivity.class);
						intent.putExtra("project_id", projectId);
						intent.putExtra("page_name", name);
						intent.putExtra("id", "onCreate");
						intent.putExtra("event", "initializeLogic");
						intent.putExtra("filename", name + ".html");
						intent.putExtra("event_text", "Page Initialization");
						logicBlockLauncher.launch(intent);
					});
				} else {
					holder.tvPageName.setText(name + ".html");
					holder.tvLinkedStyleName.setText(getPageCssPath(name));

					String previewText = name.substring(0, Math.min(2, name.length())).toUpperCase();
					if (holder.tvPreview != null) {
						holder.tvPreview.setText(previewText);
					}

					String pageJson = pageManager.loadPageLayout(name);
					List<Map<String, Object>> pageWidgets = null;
					try {
						pageWidgets = new Gson().fromJson(pageJson, new TypeToken<List<Map<String, Object>>>(){}.getType());
					} catch (Exception e) { android.util.Log.e("MainEditor", "Error", e); }

					if (holder.layoutPreviewContainer != null) {
						if (pageWidgets != null && !pageWidgets.isEmpty()) {
							holder.tvPreview.setVisibility(View.GONE);
							holder.layoutPreviewContainer.setVisibility(View.VISIBLE);
							populateMiniPreview(holder.layoutPreviewContainer, pageWidgets);
						} else {
							holder.tvPreview.setVisibility(View.VISIBLE);
							holder.layoutPreviewContainer.setVisibility(View.GONE);
						}
					}

					boolean isCurrent = name.equals(pageManager.getCurrentPage());
					if (holder.cardView instanceof com.google.android.material.card.MaterialCardView) {
						com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) holder.cardView;
						card.setStrokeWidth(isCurrent ? 4 : 0);
						card.setStrokeColor(isCurrent ? getResources().getColor(android.R.color.holo_blue_light) : 0);
					}

					if (holder.actionContainer != null) {
						holder.actionContainer.setOnClickListener(v -> {});
					}

					holder.itemView.setOnClickListener(v -> {
						dialog.dismiss();
						if (!name.equals(pageManager.getCurrentPage())) {
							switchPage(name);
						}
					});

					if (holder.imgEdit != null) {
						holder.imgEdit.setVisibility(View.VISIBLE);
						holder.imgEdit.setOnClickListener(v -> {
							dialog.dismiss();
							Intent intent = new Intent(requireContext(), ManagePageActivity.class);
							intent.putExtra("project_id", projectId);
							intent.putExtra("page_name", name);
							managePageLauncher.launch(intent);
						});
					}
				}
			}

			@Override
			public int getItemCount() {
				return items.size();
			}
		}

		// Handle Toggle selection changes
		if (optionsSelector != null) {
			optionsSelector.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
				if (!isChecked) return;
				if (checkedId == R.id.option_view) {
					listXml.setVisibility(View.VISIBLE);
					if (emptyMessage != null) emptyMessage.setVisibility(View.GONE);
					listXml.setAdapter(new PageSelectorAdapter(pageManager.getPages()));
					if (createNewView instanceof TextView) {
						((TextView) createNewView).setText("Create new page");
					}
				} else if (checkedId == R.id.option_custom_view) {
					List<String> cssFiles = getCssFiles();
					if (cssFiles.isEmpty()) {
						listXml.setVisibility(View.GONE);
						if (emptyMessage != null) {
							emptyMessage.setVisibility(View.VISIBLE);
							emptyMessage.setText("No custom stylesheets found.");
						}
					} else {
						listXml.setVisibility(View.VISIBLE);
						if (emptyMessage != null) emptyMessage.setVisibility(View.GONE);
						listXml.setAdapter(new PageSelectorAdapter(cssFiles));
					}
					if (createNewView instanceof TextView) {
						((TextView) createNewView).setText("Create new CSS");
					}
				} else if (checkedId == R.id.option_js_view) {
					List<String> jsFiles = getJsFiles();
					if (jsFiles.isEmpty()) {
						listXml.setVisibility(View.GONE);
						if (emptyMessage != null) {
							emptyMessage.setVisibility(View.VISIBLE);
							emptyMessage.setText("No JavaScript files found.");
						}
					} else {
						listXml.setVisibility(View.VISIBLE);
						if (emptyMessage != null) emptyMessage.setVisibility(View.GONE);
						listXml.setAdapter(new PageSelectorAdapter(jsFiles));
					}
					if (createNewView instanceof TextView) {
						((TextView) createNewView).setText("Create new JS");
					}
				}
			});

			optionsSelector.check(defaultCheckedId);
			if (defaultCheckedId == R.id.option_view) {
				listXml.setVisibility(View.VISIBLE);
				if (emptyMessage != null) emptyMessage.setVisibility(View.GONE);
				listXml.setAdapter(new PageSelectorAdapter(pageManager.getPages()));
				if (createNewView instanceof TextView) {
					((TextView) createNewView).setText("Create new page");
				}
			} else if (defaultCheckedId == R.id.option_custom_view) {
				List<String> cssFiles = getCssFiles();
				if (cssFiles.isEmpty()) {
					listXml.setVisibility(View.GONE);
					if (emptyMessage != null) {
						emptyMessage.setVisibility(View.VISIBLE);
						emptyMessage.setText("No custom stylesheets found.");
					}
				} else {
					listXml.setVisibility(View.VISIBLE);
					if (emptyMessage != null) emptyMessage.setVisibility(View.GONE);
					listXml.setAdapter(new PageSelectorAdapter(cssFiles));
				}
				if (createNewView instanceof TextView) {
					((TextView) createNewView).setText("Create new CSS");
				}
			} else if (defaultCheckedId == R.id.option_js_view) {
				List<String> jsFiles = getJsFiles();
				if (jsFiles.isEmpty()) {
					listXml.setVisibility(View.GONE);
					if (emptyMessage != null) {
						emptyMessage.setVisibility(View.VISIBLE);
						emptyMessage.setText("No JavaScript files found.");
					}
				} else {
					listXml.setVisibility(View.VISIBLE);
					if (emptyMessage != null) emptyMessage.setVisibility(View.GONE);
					listXml.setAdapter(new PageSelectorAdapter(jsFiles));
				}
				if (createNewView instanceof TextView) {
					((TextView) createNewView).setText("Create new JS");
				}
			}
		}

		dialog.show();
	}

	private List<String> getCssFiles() {
		List<String> cssFiles = new ArrayList<>();
		cssFiles.add("css/style.css");
		String path = Environment.getExternalStorageDirectory().getAbsolutePath()
			+ "/.dragweb/projects/" + projectId + "/assets";
		File dir = new File(path);
		if (dir.exists() && dir.isDirectory()) {
			collectCssFilesRecursive(dir, dir, cssFiles);
		}
		return cssFiles;
	}

	private void collectCssFilesRecursive(File root, File current, List<String> cssFiles) {
		File[] files = current.listFiles();
		if (files != null) {
			for (File f : files) {
				if (f.isDirectory()) {
					collectCssFilesRecursive(root, f, cssFiles);
				} else if (f.isFile() && f.getName().toLowerCase().endsWith(".css")) {
					String relative = f.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
					relative = relative.replace("\\", "/");
					if (!cssFiles.contains(relative)) {
						cssFiles.add(relative);
					}
				}
			}
		}
	}

	private List<String> getAssetsDirectories() {
		List<String> dirs = new ArrayList<>();
		dirs.add("assets");
		String path = Environment.getExternalStorageDirectory().getAbsolutePath()
			+ "/.dragweb/projects/" + projectId + "/assets";
		File dir = new File(path);
		if (!dir.exists()) dir.mkdirs();
		collectSubdirectories(dir, dir, dirs);
		return dirs;
	}

	private void collectSubdirectories(File root, File current, List<String> dirs) {
		File[] files = current.listFiles();
		if (files != null) {
			for (File f : files) {
				if (f.isDirectory()) {
					String relative = f.getAbsolutePath().substring(root.getParentFile().getAbsolutePath().length() + 1);
					relative = relative.replace("\\", "/");
					dirs.add(relative);
					collectSubdirectories(root, f, dirs);
				}
			}
		}
	}

	private int getBlockCountForCss(String cssPath) {
		if (getContext() == null) return 0;
		File dir = new File(new File(requireContext().getFilesDir(), "projects"), "logic");
		String safeName = cssPath.replace("/", "_").replace(".", "_");
		File logicFile = new File(dir, projectId + "_" + safeName + ".logic");
		if (logicFile.exists()) {
			try {
				String json = FileUtil.readFile(logicFile.getAbsolutePath());
				List<Map<String, Object>> parsed = new Gson().fromJson(json,
					new TypeToken<List<Map<String, Object>>>(){}.getType());
				return parsed != null ? parsed.size() : 0;
			} catch (Exception e) {
				return 0;
			}
		}
		return 0;
	}

	private void showAddCssFileDialog(Runnable onCreated) {
		if (getContext() == null) return;

		List<String> dirs = getAssetsDirectories();

		LinearLayout layout = new LinearLayout(requireContext());
		layout.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (24 * getResources().getDisplayMetrics().density);
		layout.setPadding(pad, (int) (16 * getResources().getDisplayMetrics().density), pad, (int) (16 * getResources().getDisplayMetrics().density));

		com.google.android.material.textfield.TextInputLayout tilName = new com.google.android.material.textfield.TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle);
		tilName.setHint("Stylesheet Name");
		tilName.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
		int dp14 = (int) (14 * getResources().getDisplayMetrics().density);
		tilName.setBoxCornerRadii(dp14, dp14, dp14, dp14);

		com.google.android.material.textfield.TextInputEditText etName = new com.google.android.material.textfield.TextInputEditText(tilName.getContext());
		etName.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
		etName.setSingleLine(true);
		etName.setMinimumHeight((int) (56 * getResources().getDisplayMetrics().density));
		int hp = (int) (16 * getResources().getDisplayMetrics().density);
		int vp = (int) (12 * getResources().getDisplayMetrics().density);
		etName.setPadding(hp, vp, hp, vp);
		etName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
		tilName.addView(etName);
		layout.addView(tilName);

		com.google.android.material.textfield.TextInputLayout tilLocation = new com.google.android.material.textfield.TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle);
		tilLocation.setHint("Select Location");
		tilLocation.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
		tilLocation.setBoxCornerRadii(dp14, dp14, dp14, dp14);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
		tilLocation.setLayoutParams(lp);

		com.google.android.material.textfield.MaterialAutoCompleteTextView actvLocation = new com.google.android.material.textfield.MaterialAutoCompleteTextView(tilLocation.getContext());
		actvLocation.setInputType(android.text.InputType.TYPE_NULL);
		actvLocation.setSingleLine(true);
		actvLocation.setMinimumHeight((int) (56 * getResources().getDisplayMetrics().density));
		actvLocation.setPadding(hp, vp, hp, vp);
		actvLocation.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
		actvLocation.setOnClickListener(v -> actvLocation.showDropDown());

		android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(requireContext(),
			android.R.layout.simple_list_item_1, dirs);
		actvLocation.setAdapter(adapter);
		actvLocation.setText("assets", false);
		tilLocation.addView(actvLocation);
		layout.addView(tilLocation);

		new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle("New Stylesheet")
			.setView(layout)
			.setPositiveButton("Create", (dialog, which) -> {
				String rawName = etName.getText() != null ? etName.getText().toString() : "";
				String cssName = rawName.trim().replaceAll("[^a-zA-Z0-9_-]", "");
				if (cssName.isEmpty()) {
					Toast.makeText(getContext(), "Invalid stylesheet name", Toast.LENGTH_SHORT).show();
					return;
				}
				if (!cssName.toLowerCase().endsWith(".css")) {
					cssName += ".css";
				}

				String chosenDir = actvLocation.getText().toString();
				String projectPath = Environment.getExternalStorageDirectory().getAbsolutePath()
					+ "/.dragweb/projects/" + projectId;
				File targetDir = new File(projectPath, chosenDir);
				if (!targetDir.exists()) targetDir.mkdirs();

				File cssFile = new File(targetDir, cssName);
				if (cssFile.exists()) {
					Toast.makeText(getContext(), "Stylesheet already exists", Toast.LENGTH_SHORT).show();
					return;
				}

				try {
					FileUtil.writeFile(cssFile.getAbsolutePath(), "/* Custom styles for " + cssName + " */\n");
					Toast.makeText(getContext(), "Stylesheet created successfully", Toast.LENGTH_SHORT).show();
					if (onCreated != null) {
						onCreated.run();
					}
				} catch (Exception e) {
					Toast.makeText(getContext(), "Failed to create stylesheet: " + e.getMessage(), Toast.LENGTH_SHORT).show();
				}
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private List<String> getJsFiles() {
		List<String> jsFiles = new ArrayList<>();
		jsFiles.add("js/script.js");
		String path = Environment.getExternalStorageDirectory().getAbsolutePath()
			+ "/.dragweb/projects/" + projectId + "/assets";
		File dir = new File(path);
		if (dir.exists() && dir.isDirectory()) {
			collectJsFilesRecursive(dir, dir, jsFiles);
		}
		return jsFiles;
	}

	private void collectJsFilesRecursive(File root, File current, List<String> jsFiles) {
		File[] files = current.listFiles();
		if (files != null) {
			for (File f : files) {
				if (f.isDirectory()) {
					collectJsFilesRecursive(root, f, jsFiles);
				} else if (f.isFile() && f.getName().toLowerCase().endsWith(".js")) {
					String relative = f.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
					relative = relative.replace("\\", "/");
					if (!jsFiles.contains(relative)) {
						jsFiles.add(relative);
					}
				}
			}
		}
	}

	private void showAddJsFileDialog(Runnable onCreated) {
		if (getContext() == null) return;

		List<String> dirs = getAssetsDirectories();

		LinearLayout layout = new LinearLayout(requireContext());
		layout.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (24 * getResources().getDisplayMetrics().density);
		layout.setPadding(pad, (int) (16 * getResources().getDisplayMetrics().density), pad, (int) (16 * getResources().getDisplayMetrics().density));

		com.google.android.material.textfield.TextInputLayout tilName = new com.google.android.material.textfield.TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle);
		tilName.setHint("JavaScript File Name");
		tilName.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
		int dp14 = (int) (14 * getResources().getDisplayMetrics().density);
		tilName.setBoxCornerRadii(dp14, dp14, dp14, dp14);

		com.google.android.material.textfield.TextInputEditText etName = new com.google.android.material.textfield.TextInputEditText(tilName.getContext());
		etName.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
		etName.setSingleLine(true);
		etName.setMinimumHeight((int) (56 * getResources().getDisplayMetrics().density));
		int hp = (int) (16 * getResources().getDisplayMetrics().density);
		int vp = (int) (12 * getResources().getDisplayMetrics().density);
		etName.setPadding(hp, vp, hp, vp);
		etName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
		tilName.addView(etName);
		layout.addView(tilName);

		com.google.android.material.textfield.TextInputLayout tilLocation = new com.google.android.material.textfield.TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputOutlinedStyle);
		tilLocation.setHint("Select Location");
		tilLocation.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
		tilLocation.setBoxCornerRadii(dp14, dp14, dp14, dp14);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
		tilLocation.setLayoutParams(lp);

		com.google.android.material.textfield.MaterialAutoCompleteTextView actvLocation = new com.google.android.material.textfield.MaterialAutoCompleteTextView(tilLocation.getContext());
		actvLocation.setInputType(android.text.InputType.TYPE_NULL);
		actvLocation.setSingleLine(true);
		actvLocation.setMinimumHeight((int) (56 * getResources().getDisplayMetrics().density));
		actvLocation.setPadding(hp, vp, hp, vp);
		actvLocation.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
		actvLocation.setOnClickListener(v -> actvLocation.showDropDown());

		android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(requireContext(),
			android.R.layout.simple_list_item_1, dirs);
		actvLocation.setAdapter(adapter);
		actvLocation.setText("assets", false);
		tilLocation.addView(actvLocation);
		layout.addView(tilLocation);

		new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle("New JavaScript File")
			.setView(layout)
			.setPositiveButton("Create", (dialog, which) -> {
				String rawName = etName.getText() != null ? etName.getText().toString() : "";
				String jsName = rawName.trim().replaceAll("[^a-zA-Z0-9_-]", "");
				if (jsName.isEmpty()) {
					Toast.makeText(getContext(), "Invalid JavaScript file name", Toast.LENGTH_SHORT).show();
					return;
				}
				if (!jsName.toLowerCase().endsWith(".js")) {
					jsName += ".js";
				}

				String chosenDir = actvLocation.getText().toString();
				String projectPath = Environment.getExternalStorageDirectory().getAbsolutePath()
					+ "/.dragweb/projects/" + projectId;
				File targetDir = new File(projectPath, chosenDir);
				if (!targetDir.exists()) targetDir.mkdirs();

				File jsFile = new File(targetDir, jsName);
				if (jsFile.exists()) {
					Toast.makeText(getContext(), "JavaScript file already exists", Toast.LENGTH_SHORT).show();
					return;
				}

				try {
					FileUtil.writeFile(jsFile.getAbsolutePath(), "// JavaScript code for " + jsName + "\n");
					Toast.makeText(getContext(), "JavaScript file created successfully", Toast.LENGTH_SHORT).show();
					if (onCreated != null) {
						onCreated.run();
					}
				} catch (Exception e) {
					Toast.makeText(getContext(), "Failed to create JavaScript file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
				}
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void showAddPageDialog() {
		pageImportHtmlUri = null;
		View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_page, null);
		TextInputLayout tilPageName = dialogView.findViewById(R.id.tilPageName);
		TextInputEditText etPageName = dialogView.findViewById(R.id.etPageName);
		etDialogPageName = etPageName;
		View cardSelectHtml = dialogView.findViewById(R.id.cardSelectHtml);
		tvDialogHtmlFileName = dialogView.findViewById(R.id.tvHtmlFileName);
		btnDialogClearHtml = dialogView.findViewById(R.id.btnClearHtml);

		cardSelectHtml.setOnClickListener(v -> {
			android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
			intent.setType("text/html");
			importPageHtmlLauncher.launch(intent);
		});

		btnDialogClearHtml.setOnClickListener(v -> {
			pageImportHtmlUri = null;
			tvDialogHtmlFileName.setText("No file selected");
			btnDialogClearHtml.setVisibility(View.GONE);
		});

		new MaterialAlertDialogBuilder(requireContext())
			.setTitle("New Page")
			.setView(dialogView)
			.setPositiveButton("Create", (dialog, which) -> {
				String rawName = etPageName.getText().toString().trim();
				String pageName = rawName.replaceAll("[^a-zA-Z0-9_-]", "");
				if (pageName.isEmpty()) {
					Toast.makeText(requireContext(), "Invalid page name", Toast.LENGTH_SHORT).show();
					return;
				}
				if (pageManager.getPages().contains(pageName)) {
					Toast.makeText(requireContext(), "Page already exists", Toast.LENGTH_SHORT).show();
					return;
				}

				saveCurrentPageLayout();

				if (pageImportHtmlUri != null) {
					// Parse the selected HTML file
					String htmlContent = readUriContent(pageImportHtmlUri);
					if (htmlContent == null || htmlContent.trim().isEmpty()) {
						Toast.makeText(requireContext(), "Could not read HTML file", Toast.LENGTH_SHORT).show();
						return;
					}

					HtmlCssImporter importer = new HtmlCssImporter(requireContext());
					HtmlCssImporter.ImportResult result = importer.importHtmlCss(htmlContent, null);

					if (!result.success) {
						Toast.makeText(requireContext(), "Import failed: " + result.message, Toast.LENGTH_LONG).show();
						return;
					}

					// Disable default styles and inline styles for the project on import
					themeManager.setUseInlineStyles(false);
					themeManager.setDisableDefaultStyles(true);
					File dir = new File(requireContext().getFilesDir(), "projects");
					if (!dir.exists()) dir.mkdirs();
					File themeFile = new File(dir, projectId + ".theme");
					FileUtil.writeFile(themeFile.getAbsolutePath(), themeManager.toJson());

					// Enable detected standard icon libraries
					if (result.enabledLibraries != null && !result.enabledLibraries.isEmpty()) {
						IconLibraryManager ilm = new IconLibraryManager(requireContext(), projectId);
						for (String libId : result.enabledLibraries) {
							ilm.enable(libId);
						}
					}

					// Save imported page layout
					String widgetTreeJson = new Gson().toJson(result.widgetTree);
					pageManager.addPage(pageName);
					pageManager.savePageLayout(pageName, widgetTreeJson);

					// Save logic blocks for the new page
					if (result.logicBlocks != null && !result.logicBlocks.isEmpty()) {
						File logicDir = new File(dir, "logic");
						if (!logicDir.exists()) logicDir.mkdirs();
						File logicFile = new File(logicDir, projectId + "_" + pageName + ".logic");
						FileUtil.writeFile(logicFile.getAbsolutePath(), new Gson().toJson(result.logicBlocks));
					}
				} else {
					// Standard empty page
					pageManager.addPage(pageName);
				}

				// Switch to new page
				pageManager.setCurrentPage(pageName);

				// Load current page layout (this will rebuild layout from json)
				loadCurrentPageLayout();

				// Load logic blocks for new page
				if (logicBlockManager != null) {
					File dir = new File(new File(requireContext().getFilesDir(), "projects"), "logic");
					File logicFile = new File(dir, projectId + "_" + pageName + ".logic");
					if (logicFile.exists()) {
						String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
						logicBlockManager.fromJson(logicJson);
					} else {
						logicBlockManager.fromJson("[]");
					}
					refreshLogicBlocksUI();
				}

				saveUndoState();
				refreshHierarchy();
				updatePageSpinner();
				Toast.makeText(requireContext(), "Page '" + pageName + "' created", Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton("Cancel", null)
			.setOnDismissListener(dialog -> {
				tvDialogHtmlFileName = null;
				btnDialogClearHtml = null;
			})
			.show();
	}

	private void saveCurrentPageLayout() {
		List<Map<String, Object>> widgetTree = serializeChildren(screen);
		String json = new Gson().toJson(widgetTree);
		pageManager.savePageLayout(pageManager.getCurrentPage(), json);
	}

	private void loadCurrentPageLayout() {
		if (layoutLoading != null) layoutLoading.setVisibility(View.VISIBLE);
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
		setupHierarchyTracker(screen);
		saveCurrentPageLayout();
		selector.clearSelection();
		selector.attachTo(screen);
		textview2.setText("No widget selected");
		delete.setEnabled(false);
		undoRedoManager.clear();
		saveUndoState();
		refreshHierarchy();
		updateWidgetSpinnerFromTree();
		if (layoutLoading != null) {
			layoutLoading.postDelayed(() -> layoutLoading.setVisibility(View.GONE), 300);
		}
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
			} else if (id == R.id.chipText) {
				buildTextDesignList();
			} else if (id == R.id.chipMore) {
				buildMoreDesignList();
			}
		});
	}

	private void refreshWidgetList() {
		widgets = widgetRegistry.getAllWidgets();
		filteredWidgets.clear();
		filteredWidgets.addAll(widgets);
		notifyWidgetAdapters();
		populateCategoryChips();
	}

	private void refreshHierarchy() {
		if (hierarchyAdapter != null) {
			hierarchyAdapter.buildTree(screen);
		}
	}

	private final ViewGroup.OnHierarchyChangeListener hierarchyChangeListener = new ViewGroup.OnHierarchyChangeListener() {
		@Override
		public void onChildViewAdded(View parent, View child) {
			if (child instanceof ViewGroup) {
				setupHierarchyTracker((ViewGroup) child);
			}
			refreshHierarchy();
			updateWidgetSpinnerFromTree();
		}

		@Override
		public void onChildViewRemoved(View parent, View child) {
			refreshHierarchy();
			updateWidgetSpinnerFromTree();
		}
	};

	private void setupHierarchyTracker(ViewGroup vg) {
		vg.setOnHierarchyChangeListener(hierarchyChangeListener);
		for (int i = 0; i < vg.getChildCount(); i++) {
			View child = vg.getChildAt(i);
			if (child instanceof ViewGroup) {
				setupHierarchyTracker((ViewGroup) child);
			}
		}
	}

	// ---- Widget Spinner ----

	private void updateWidgetSpinnerFromTree() {
		if (tvSelectedWidgetName == null) return;

		View selectedView = selector.getSelectedView();
		if (selectedView != null && selectedView != screen) {
			String tag = "unknown";
			String id = "";
			String cssClass = "";
			boolean isLocked = false;
			boolean isHidden = false;

			if (selectedView.getTag() instanceof Map) {
				Map<String, Object> widgetMap = (Map<String, Object>) selectedView.getTag();
				if (widgetMap.containsKey("tag")) tag = widgetMap.get("tag").toString();
				if (widgetMap.containsKey("id")) id = widgetMap.get("id").toString();
				isLocked = Boolean.TRUE.equals(widgetMap.get("locked"));
				isHidden = Boolean.TRUE.equals(widgetMap.get("hidden"));

				@SuppressWarnings("unchecked")
				Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
				if (function != null) {
					if (function.containsKey("id") && id.isEmpty()) id = function.get("id").toString();
					if (function.containsKey("class")) cssClass = function.get("class").toString();
				}
			}

			StringBuilder name = new StringBuilder("<" + tag + ">");
			if (!id.isEmpty()) name.append(" #").append(id);
			if (!cssClass.isEmpty()) {
				String shortClass = cssClass.length() > 10 ? cssClass.substring(0, 10) + ".." : cssClass;
				name.append(" .").append(shortClass);
			}
			tvSelectedWidgetName.setText(name.toString());

			// Show Lock & Hide buttons
			if (btnBottomLock != null) {
				btnBottomLock.setVisibility(View.VISIBLE);
				btnBottomLock.setImageResource(isLocked ? R.drawable.lock : R.drawable.lock_open);
				btnBottomLock.setImageTintList(android.content.res.ColorStateList.valueOf(isLocked ? Color.parseColor("#F44336") : Color.parseColor("#808080")));
				btnBottomLock.setAlpha(isLocked ? 1.0f : 0.6f);
				btnBottomLock.setOnClickListener(v -> {
					if (selectedView.getTag() instanceof Map) {
						Map<String, Object> widgetMap = (Map<String, Object>) selectedView.getTag();
						boolean locked = Boolean.TRUE.equals(widgetMap.get("locked"));
						widgetMap.put("locked", !locked);
						selectedView.setTag(widgetMap);
						saveUndoState();
						refreshHierarchy();
						updateWidgetSpinnerFromTree();
					}
				});
			}

			if (btnBottomHide != null) {
				btnBottomHide.setVisibility(View.VISIBLE);
				btnBottomHide.setImageResource(isHidden ? R.drawable.eye_closed : R.drawable.eye);
				btnBottomHide.setImageTintList(android.content.res.ColorStateList.valueOf(isHidden ? Color.parseColor("#808080") : Color.parseColor("#2196F3")));
				btnBottomHide.setAlpha(isHidden ? 0.3f : 1.0f);
				btnBottomHide.setOnClickListener(v -> {
					if (selectedView.getTag() instanceof Map) {
						Map<String, Object> widgetMap = (Map<String, Object>) selectedView.getTag();
						boolean hidden = Boolean.TRUE.equals(widgetMap.get("hidden"));
						widgetMap.put("hidden", !hidden);
						selectedView.setVisibility(hidden ? View.VISIBLE : View.GONE);
						selectedView.setTag(widgetMap);
						saveUndoState();
						refreshHierarchy();
						updateWidgetSpinnerFromTree();
					}
				});
			}
		} else {
			tvSelectedWidgetName.setText("body (screen)");
			if (btnBottomLock != null) btnBottomLock.setVisibility(View.GONE);
			if (btnBottomHide != null) btnBottomHide.setVisibility(View.GONE);
		}

		if (btnWidgetDropdown != null) {
			btnWidgetDropdown.setOnClickListener(v -> {
				android.widget.PopupMenu popup = new android.widget.PopupMenu(requireContext(), v);
				final List<String> items = new ArrayList<>();
				items.add("body (screen)");
				collectWidgetNames(screen, items, 0);

				for (int i = 0; i < items.size(); i++) {
					popup.getMenu().add(0, i, i, items.get(i));
				}
				popup.setOnMenuItemClickListener(item -> {
					int position = item.getItemId();
					if (position == 0) {
						selector.clearSelection();
						textview2.setText("No selection");
						delete.setEnabled(false);
						updateWidgetSpinnerFromTree();
					} else {
						View targetView = findViewAtTreeIndex(screen, position, new int[]{1});
						if (targetView != null && targetView != selector.getSelectedView()) {
							selector.clearSelection();
							targetView.performClick();
						}
					}
					return true;
				});
				popup.show();
			});
		}
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
					return true;
				case DragEvent.ACTION_DRAG_EXITED:
					return true;
				case DragEvent.ACTION_DROP:
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
							Toast.makeText(requireContext(), "Error parsing widget position.", Toast.LENGTH_SHORT).show();
						} catch (Exception e) {
							Toast.makeText(requireContext(), "Error creating widget.", Toast.LENGTH_SHORT).show();
							Log.e("MainActivity", "Error: " + e.getMessage());
						}
					}
					return true;
				case DragEvent.ACTION_DRAG_ENDED:
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
			String tag = widgetDefinition.get("tag") != null ? widgetDefinition.get("tag").toString() : "div";
			int count = countWidgetsWithTag(screen, tag) + 1;
			String autoClass = tag + "-" + count;

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
				newFunction.put("class", autoClass);

				Map<String, Object> defStyle = (Map<String, Object>) defFunction.get("style");
				Map<String, Object> newStyle = (Map<String, Object>) newFunction.get("style");
				if (newStyle == null) {
					newStyle = new java.util.HashMap<>();
					newFunction.put("style", newStyle);
				}
				if (defStyle != null) {
					newStyle.putAll(defStyle);
				}

				// Apply sensible default layouts for different widget tags so they don't collapse on full screen canvas
				if ("div".equalsIgnoreCase(tag) || "section".equalsIgnoreCase(tag) || "header".equalsIgnoreCase(tag) 
						|| "footer".equalsIgnoreCase(tag) || "nav".equalsIgnoreCase(tag) || "main".equalsIgnoreCase(tag) 
						|| "aside".equalsIgnoreCase(tag) || "form".equalsIgnoreCase(tag)) {
					if (!newStyle.containsKey("min-height") && !newStyle.containsKey("height")) {
						newStyle.put("min-height", "100px");
					}
					if (!newStyle.containsKey("width")) {
						newStyle.put("width", "100%");
					}
					if (!newStyle.containsKey("padding")) {
						newStyle.put("padding", "16px");
					}
				} else if ("img".equalsIgnoreCase(tag)) {
					if (!newStyle.containsKey("width")) {
						newStyle.put("width", "150px");
					}
					if (!newStyle.containsKey("height")) {
						newStyle.put("height", "150px");
					}
					if (!newStyle.containsKey("background-color")) {
						newStyle.put("background-color", "#e9ecef");
					}
				} else if ("button".equalsIgnoreCase(tag)) {
					if (!newStyle.containsKey("padding")) {
						newStyle.put("padding", "8px 16px");
					}
				} else if ("input".equalsIgnoreCase(tag) || "textarea".equalsIgnoreCase(tag) || "select".equalsIgnoreCase(tag)) {
					if (!newStyle.containsKey("width")) {
						newStyle.put("width", "100%");
					}
					if (!newStyle.containsKey("padding")) {
						newStyle.put("padding", "8px");
					}
				}
			}
		}
		engine.applyPropertiesToView(newWidgetView, (Map<String, Object>) newWidgetView.getTag());
	}

	private int countWidgetsWithTag(View root, String tag) {
		int count = 0;
		if (root == null) return 0;
		Object tagObj = root.getTag();
		if (tagObj instanceof Map) {
			Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
			String childTag = widgetMap.get("tag") != null ? widgetMap.get("tag").toString() : "";
			if (tag.equalsIgnoreCase(childTag)) {
				count++;
			}
		}
		if (root instanceof ViewGroup) {
			ViewGroup vg = (ViewGroup) root;
			for (int i = 0; i < vg.getChildCount(); i++) {
				count += countWidgetsWithTag(vg.getChildAt(i), tag);
			}
		}
		return count;
	}

	private Map<String, Object> deepCopyWidgetMap(Map<String, Object> original) {
		if (original == null) return null;
		Map<String, Object> copy = new HashMap<>();
		for (Map.Entry<String, Object> entry : original.entrySet()) {
			String key = entry.getKey();
			Object val = entry.getValue();
			if ("children".equals(key) && val instanceof List) {
				List<Map<String, Object>> list = (List<Map<String, Object>>) val;
				List<Map<String, Object>> listCopy = new ArrayList<>();
				for (Object item : list) {
					if (item instanceof Map) {
						listCopy.add(deepCopyWidgetMap((Map<String, Object>) item));
					}
				}
				copy.put(key, listCopy);
			} else if ("function".equals(key) && val instanceof Map) {
				Map<String, Object> func = (Map<String, Object>) val;
				Map<String, Object> funcCopy = new HashMap<>();
				for (Map.Entry<String, Object> fEntry : func.entrySet()) {
					if ("style".equals(fEntry.getKey()) && fEntry.getValue() instanceof Map) {
						funcCopy.put(fEntry.getKey(), new HashMap<>((Map<String, Object>) fEntry.getValue()));
					} else {
						funcCopy.put(fEntry.getKey(), fEntry.getValue());
					}
				}
				copy.put(key, funcCopy);
			} else {
				copy.put(key, val);
			}
		}
		return copy;
	}

	private void assignUniqueClassNames(Map<String, Object> nodeMap, Map<String, Integer> extraCounts) {
		if (nodeMap == null) return;
		String tag = nodeMap.get("tag") != null ? nodeMap.get("tag").toString() : "div";
		
		int baseCount = countWidgetsWithTag(screen, tag);
		int extra = extraCounts.containsKey(tag) ? extraCounts.get(tag) : 0;
		int nextCount = baseCount + extra + 1;
		extraCounts.put(tag, extra + 1);
		
		String autoClass = tag + "-" + nextCount;

		Map<String, Object> function = (Map<String, Object>) nodeMap.get("function");
		if (function == null) {
			function = new HashMap<>();
			nodeMap.put("function", function);
		} else {
			function = new HashMap<>(function);
			nodeMap.put("function", function);
		}
		function.put("class", autoClass);

		if (nodeMap.containsKey("children")) {
			Object childrenObj = nodeMap.get("children");
			if (childrenObj instanceof List) {
				List<Map<String, Object>> children = (List<Map<String, Object>>) childrenObj;
				List<Map<String, Object>> clonedChildren = new ArrayList<>();
				for (Object child : children) {
					if (child instanceof Map) {
						Map<String, Object> childClone = deepCopyWidgetMap((Map<String, Object>) child);
						assignUniqueClassNames(childClone, extraCounts);
						clonedChildren.add(childClone);
					}
				}
				nodeMap.put("children", clonedChildren);
			}
		}
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
					Toast.makeText(requireContext(), "Widget is locked", Toast.LENGTH_SHORT).show();
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
		// Save project first so all pages, assets, and logics are fully updated
		saveProject(() -> {
			File previewDir = new File(requireContext().getCacheDir(), "preview_" + projectId);
			deleteDir(previewDir);
			previewDir.mkdirs();

			if (exportManager != null) {
				exportManager.generateExportFiles(screen, projectName, logicBlockManager, customBlockManager, previewDir, pageManager);
			}

			List<String> allPages = pageManager != null ? pageManager.getPages() : new ArrayList<>();
			if (allPages.isEmpty()) allPages.add("index");
			ArrayList<String> pageNames = new ArrayList<>(allPages);

			String currentPage = pageManager != null ? pageManager.getCurrentPage() : "index";
			int startIndex = pageNames.indexOf(currentPage);
			if (startIndex < 0) startIndex = 0;

			Intent previewIntent = new Intent(requireContext(), PreviewActivity.class);
			previewIntent.putStringArrayListExtra("page_names", pageNames);
			previewIntent.putExtra("start_page_index", startIndex);
			previewIntent.putExtra("preview_project_dir", previewDir.getAbsolutePath());
			previewIntent.putExtra("project_id", projectId);
			startActivity(previewIntent);
		});
	}

	private void deleteDir(File file) {
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteDir(child);
				}
			}
		}
		file.delete();
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
		new UniversalM3Dialog(requireContext())
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
						Toast.makeText(requireContext(), "Folder created: " + value, Toast.LENGTH_SHORT).show();
						if (fileExplorerAdapter != null) {
							fileExplorerAdapter.navigateTo(fileExplorerAdapter.getCurrentDir());
						}
					} else {
						Toast.makeText(requireContext(), "Could not create folder", Toast.LENGTH_SHORT).show();
					}
				} catch (Exception e) {
					Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
				}
			});
	}

	@Override
	public void onPause() {
		super.onPause();
		syncProjectAssets();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (widgetRegistry != null) {
			try {
				refreshWidgetList();
			} catch (Exception e) {
				Log.w("MainActivity", "Failed to refresh widgets on resume: " + e.getMessage());
			}
		}
		if (customBlockManager != null) {
			try {
				customBlockManager.loadLibrary();
			} catch (Exception e) {
				Log.w("MainActivity", "Failed to refresh blocks on resume: " + e.getMessage());
			}
		}
	}

	// ---- Custom JSON Support ----

	private void autoLoadCustomConfigs() {
		if (widgetRegistry != null) {
			try {
				refreshWidgetList();
			} catch (Exception e) {
				Log.w("MainActivity", "Failed to load custom widgets: " + e.getMessage());
			}
		}

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
				Toast.makeText(requireContext(), "Custom widgets loaded", Toast.LENGTH_SHORT).show();
			} catch (Exception e) {
				Toast.makeText(requireContext(), "Failed to load: " + e.getMessage(), Toast.LENGTH_SHORT).show();
			}
		} else {
			File dir = new File(Environment.getExternalStorageDirectory(), ".dragweb/custom");
			dir.mkdirs();
			Toast.makeText(requireContext(), "Place widgets.json in:\n" + widgetsPath, Toast.LENGTH_LONG).show();
		}
	}

	private void loadCustomBlocksFromDevice() {
		String blocksPath = Environment.getExternalStorageDirectory().getAbsolutePath()
			+ "/.dragweb/custom/blocks.json";
		File file = new File(blocksPath);
		if (file.exists()) {
			try {
				if (customBlockManager == null) customBlockManager = new ManageBlocksWidgets(requireContext());
				customBlockManager.loadLibrary();
				int count = customBlockManager.getDefinitions().size();
				Toast.makeText(requireContext(), "Loaded " + count + " custom block templates", Toast.LENGTH_SHORT).show();
			} catch (Exception e) {
				Toast.makeText(requireContext(), "Failed to load: " + e.getMessage(), Toast.LENGTH_SHORT).show();
			}
		} else {
			File dir = new File(Environment.getExternalStorageDirectory(), ".dragweb/custom");
			dir.mkdirs();
			Toast.makeText(requireContext(), "Place blocks.json in:\n" + blocksPath, Toast.LENGTH_LONG).show();
		}
	}



	private void importProjectBackup(android.net.Uri uri) {
		try {
			File tempZip = new File(requireContext().getCacheDir(), "import.zip");
			InputStream is = requireActivity().getContentResolver().openInputStream(uri);
			FileOutputStream fos = new FileOutputStream(tempZip);
			byte[] buffer = new byte[8192];
			int len;
			while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
			is.close();
			fos.close();

			if (exportManager.restoreProjectFromZip(tempZip)) {
				Toast.makeText(requireContext(), "Project restored! Please reopen the project.", Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(requireContext(), "Restore failed. Check ZIP structure.", Toast.LENGTH_SHORT).show();
			}
			tempZip.delete();
		} catch (Exception e) {
			Toast.makeText(requireContext(), "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}



	// ---- Logic Blocks UI ----

	private void refreshLogicBlocksUI() {
		// Event list has been moved to EventAssetsActivity
	}


	// ---- Project Save/Load ----

	public void saveProject() {
		saveProject(null);
	}

	public void saveProject(Runnable onComplete) {
		if (progressSave != null) progressSave.setVisibility(View.VISIBLE);
		if (button4 != null) button4.setVisibility(View.GONE);

		new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
			performSaveWork();

			if (progressSave != null) progressSave.setVisibility(View.GONE);
			if (button4 != null) {
				button4.setVisibility(View.VISIBLE);
				if (button4 instanceof com.google.android.material.button.MaterialButton) {
					((com.google.android.material.button.MaterialButton) button4).setIconResource(R.drawable.rounded_check_24);
				}

				new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
					if (button4 instanceof com.google.android.material.button.MaterialButton) {
						((com.google.android.material.button.MaterialButton) button4).setIconResource(R.drawable.device_floppy_act);
					}
				}, 1500);
			}

			if (onComplete != null) {
				onComplete.run();
			}
		}, 150);
	}

	private void performSaveWork() {
		// Save current page layout first
		saveCurrentPageLayout();

		// Save all cached pages to disk
		pageManager.saveAllPages();

		// Save main layout (index page for backwards compat)
		projectDataManager.saveProject(screen, projectId, null);

		File dir = new File(requireContext().getFilesDir(), "projects");
		if (!dir.exists()) dir.mkdirs();
		File themeFile = new File(dir, projectId + ".theme");
		FileUtil.writeFile(themeFile.getAbsolutePath(), themeManager.toJson());

		File logicDir = new File(dir, "logic");
		if (!logicDir.exists()) logicDir.mkdirs();
		String currentPageName = pageManager != null ? pageManager.getCurrentPage() : "index";
		File logicFile = new File(logicDir, projectId + "_" + currentPageName + ".logic");
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

		// Compile all pages and sync with assets folder in real-time
		if (exportManager != null) {
			try {
				File assetsDir = new File(Environment.getExternalStorageDirectory(), "/.dragweb/projects/" + projectId + "/assets");
				assetsDir.mkdirs();
				exportManager.generateExportFiles(screen, projectName, logicBlockManager, customBlockManager, assetsDir, pageManager);
			} catch (Exception e) {
				Log.w("MainEditor", "Failed to sync compiled files to assets: " + e.getMessage());
			}
		}
	}

	private void saveProjectToExternal() {
		try {
			String basePath = Environment.getExternalStorageDirectory().getAbsolutePath()
				+ "/.dragweb/projects/" + projectId;
			File extDir = new File(basePath);
			if (!extDir.exists()) extDir.mkdirs();

			File internalDir = new File(requireContext().getFilesDir(), "projects");
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
					File logicFile = new File(new File(internalDir, "logic"), projectId + "_" + page + ".logic");
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
		if (hierarchyAdapter != null) {
			hierarchyAdapter.setLoading(true);
		}

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
			projectDataManager.loadProject(screen, projectId, engine, selector, dropZoneManager, null);
		}



		// Register all loaded widgets for reorder drag
		registerAllWidgetsForDrag(screen);

		File dir = new File(requireContext().getFilesDir(), "projects");
		File themeFile = new File(dir, projectId + ".theme");
		if (themeFile.exists()) {
			String themeJson = FileUtil.readFile(themeFile.getAbsolutePath());
			themeManager.fromJson(themeJson);
		}

		File logicDir = new File(dir, "logic");
		String currentPageName = pageManager != null ? pageManager.getCurrentPage() : "index";
		File logicFile = new File(logicDir, projectId + "_" + currentPageName + ".logic");
		if (logicFile.exists()) {
			String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
			logicBlockManager.fromJson(logicJson);
		} else {
			// Fallback for older projects
			File oldLogicFile = new File(dir, projectId + "_" + currentPageName + ".logic");
			if (oldLogicFile.exists()) {
				String logicJson = FileUtil.readFile(oldLogicFile.getAbsolutePath());
				logicBlockManager.fromJson(logicJson);
			} else {
				File veryOldLogicFile = new File(dir, projectId + ".logic");
				if (veryOldLogicFile.exists()) {
					String logicJson = FileUtil.readFile(veryOldLogicFile.getAbsolutePath());
					logicBlockManager.fromJson(logicJson);
				}
			}
		}

		// Save initial page layout so it's cached
		if (pageManager != null && screen.getChildCount() > 0) {
			saveCurrentPageLayout();
		}

		if (hierarchyAdapter != null) {
			hierarchyAdapter.setLoading(false);
		}
		refreshHierarchy();
		updateWidgetSpinnerFromTree();
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
		if (hierarchyAdapter != null) {
			hierarchyAdapter.setLoading(true);
		}
		screen.removeAllViews();
		for (Map<String, Object> nodeMap : state) {
			rebuildView(nodeMap, screen);
		}
		setupHierarchyTracker(screen);
		selector.clearSelection();
		selector.attachTo(screen);
		textview2.setText("No widget selected");
		delete.setEnabled(false);
		if (hierarchyAdapter != null) {
			hierarchyAdapter.setLoading(false);
		}
		refreshHierarchy();
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
		View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_theme_settings, null);
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
		com.google.android.material.materialswitch.MaterialSwitch switchInlineStyles = dialogView.findViewById(R.id.switchInlineStyles);
		Button btnResetTheme = dialogView.findViewById(R.id.btnResetTheme);

		// Local custom variables buffer map: key -> {lightVal, darkVal}
		final Map<String, String[]> dialogCustomVars = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : themeManager.getCustomCssVars().entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			String lightVal = "";
			String darkVal = "";
			if (value != null && value.contains("|")) {
				String[] parts = value.split("\\|", 2);
				lightVal = parts[0];
				darkVal = parts[1];
			} else {
				lightVal = value != null ? value : "";
				darkVal = lightVal;
			}
			dialogCustomVars.put(key, new String[]{lightVal, darkVal});
		}

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
		Runnable populateFields = new Runnable() {
			@Override
			public void run() {
				String t = editingTheme[0];
				etPrimary.setText(themeManager.getStyleForTheme(t, "primaryColor"));
				etSecondary.setText(themeManager.getStyleForTheme(t, "secondaryColor"));
				etAccent.setText(themeManager.getStyleForTheme(t, "accentColor"));
				etBackground.setText(themeManager.getStyleForTheme(t, "bodyBackground"));
				etBodyColor.setText(themeManager.getStyleForTheme(t, "bodyColor"));
				etLinkColor.setText(themeManager.getStyleForTheme(t, "linkColor"));
				etBorderColor.setText(themeManager.getStyleForTheme(t, "borderColor"));

				customVarsContainer.removeAllViews();
				int themeIndex = ThemeManager.THEME_DARK.equals(t) ? 1 : 0;
				for (Map.Entry<String, String[]> entry : dialogCustomVars.entrySet()) {
					addCssVarRow(customVarsContainer, entry.getKey(), entry.getValue()[themeIndex], themeIndex, dialogCustomVars, this);
				}
			}
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

			int themeIndex = ThemeManager.THEME_DARK.equals(t) ? 1 : 0;
			for (int i = 0; i < customVarsContainer.getChildCount(); i++) {
				View row = customVarsContainer.getChildAt(i);
				TextInputEditText etValue = row.findViewById(R.id.etCustomVarValue);
				TextInputLayout til = row.findViewById(R.id.tilCustomVar);
				if (til != null && etValue != null) {
					String key = til.getHint() != null ? til.getHint().toString() : "";
					if (key.startsWith("--")) key = key.substring(2);
					String valueVal = etValue.getText().toString().trim();
					if (dialogCustomVars.containsKey(key)) {
						dialogCustomVars.get(key)[themeIndex] = valueVal;
					}
				}
			}
		};

		populateFields.run();

		if (ThemeManager.THEME_DARK.equals(editingTheme[0])) {
			btnDark.performClick();
		} else {
			btnLight.performClick();
		}

		btnLight.setOnClickListener(v -> {
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

		if (btnAddCssVar != null) {
			btnAddCssVar.setOnClickListener(v -> {
				saveFieldsToTheme.run();
				UniversalDialog.textInput(requireContext(), "Add Custom Variable", "Variable name", "", varName -> {
					String cleanedName = varName.trim();
					if (cleanedName.startsWith("--")) {
						cleanedName = cleanedName.substring(2);
					}
					if (cleanedName.isEmpty()) {
						Toast.makeText(requireContext(), "Variable name cannot be empty", Toast.LENGTH_SHORT).show();
						return;
					}
					if (dialogCustomVars.containsKey(cleanedName)) {
						Toast.makeText(requireContext(), "Variable already exists", Toast.LENGTH_SHORT).show();
						return;
					}
					dialogCustomVars.put(cleanedName, new String[]{"", ""});
					populateFields.run();
				});
			});
		}

		if (btnResetTheme != null) {
			btnResetTheme.setOnClickListener(v -> {
				UniversalDialog.confirm(requireContext(), "Reset Theme", 
						"Are you sure you want to reset theme settings to defaults?", "Reset", true, () -> {
					themeManager.resetToDefaults();
					dialogCustomVars.clear();
					for (Map.Entry<String, String> entry : themeManager.getCustomCssVars().entrySet()) {
						String key = entry.getKey();
						String value = entry.getValue();
						String lightVal = "";
						String darkVal = "";
						if (value != null && value.contains("|")) {
							String[] parts = value.split("\\|", 2);
							lightVal = parts[0];
							darkVal = parts[1];
						} else {
							lightVal = value != null ? value : "";
							darkVal = lightVal;
						}
						dialogCustomVars.put(key, new String[]{lightVal, darkVal});
					}
					populateFields.run();
					if (switchInlineStyles != null) {
						switchInlineStyles.setChecked(themeManager.isUseInlineStyles());
					}
					if (ThemeManager.THEME_DARK.equals(themeManager.getCurrentTheme())) {
						btnDark.performClick();
					} else {
						btnLight.performClick();
					}
					Toast.makeText(requireContext(), "Theme reset to defaults", Toast.LENGTH_SHORT).show();
				});
			});
		}

		new MaterialAlertDialogBuilder(requireContext())
			.setTitle("Theme Settings")
			.setView(dialogView)
			.setPositiveButton("Apply", (dialog, which) -> {
				saveFieldsToTheme.run();

				if (switchInlineStyles != null) {
					themeManager.setUseInlineStyles(switchInlineStyles.isChecked());
				}

				Map<String, String> newVars = new LinkedHashMap<>();
				for (Map.Entry<String, String[]> entry : dialogCustomVars.entrySet()) {
					String key = entry.getKey();
					String lVal = entry.getValue()[0];
					String dVal = entry.getValue()[1];
					if (lVal.isEmpty()) lVal = "#FFFFFF";
					if (dVal.isEmpty()) dVal = lVal;
					newVars.put(key, lVal + "|" + dVal);
				}
				themeManager.setCustomCssVars(newVars);

				// Persist theme changes immediately
				try {
					File dir = new File(requireContext().getFilesDir(), "projects");
					if (!dir.exists()) dir.mkdirs();
					File themeFile = new File(dir, projectId + ".theme");
					FileUtil.writeFile(themeFile.getAbsolutePath(), themeManager.toJson());
					saveProjectToExternal();
				} catch (Exception e) {
					Log.w("MainActivity", "Failed to auto-save theme: " + e.getMessage());
				}

				Toast.makeText(requireContext(), "Theme updated (light + dark)", Toast.LENGTH_SHORT).show();
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

		UniversalDialog.autocompleteInput(requireContext(), "Font Settings", "Font Family", currentFont, fontSuggestions, font -> {
			if (!font.isEmpty()) {
				themeManager.setStyleForTheme(ThemeManager.THEME_LIGHT, "fontFamily", font);
				themeManager.setStyleForTheme(ThemeManager.THEME_DARK, "fontFamily", font);
				Toast.makeText(requireContext(), "Font family updated", Toast.LENGTH_SHORT).show();
			}
		});
	}

	private void setupColorInputField(TextInputEditText et) {
		et.setFocusable(false);
		et.setCursorVisible(false);
		et.setOnClickListener(v -> {
			String current = et.getText().toString();
			UniversalDialog.colorPicker(requireContext(), "Select Color", current, getColorSuggestions(), hex -> {
				et.setText(hex);
			});
		});

		et.addTextChangedListener(new android.text.TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
			@Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
			@Override public void afterTextChanged(android.text.Editable s) {
				String color = s.toString().trim();
				try {
					int parsed;
					if (color.isEmpty()) {
						parsed = Color.TRANSPARENT;
					} else {
						parsed = Color.parseColor(color.startsWith("#") ? color : "#000000");
					}
					GradientDrawable gd = new GradientDrawable();
					gd.setShape(GradientDrawable.OVAL);
					gd.setColor(parsed);
					gd.setStroke((int)(1 * getResources().getDisplayMetrics().density), Color.LTGRAY);
					int size = (int)(20 * getResources().getDisplayMetrics().density);
					gd.setSize(size, size);
					et.setCompoundDrawablesWithIntrinsicBounds(gd, null, null, null);
					et.setCompoundDrawablePadding((int)(8 * getResources().getDisplayMetrics().density));
				} catch (Exception e) {
					try {
						GradientDrawable gd = new GradientDrawable();
						gd.setShape(GradientDrawable.OVAL);
						gd.setColor(Color.TRANSPARENT);
						gd.setStroke((int)(1 * getResources().getDisplayMetrics().density), Color.LTGRAY);
						int size = (int)(20 * getResources().getDisplayMetrics().density);
						gd.setSize(size, size);
						et.setCompoundDrawablesWithIntrinsicBounds(gd, null, null, null);
						et.setCompoundDrawablePadding((int)(8 * getResources().getDisplayMetrics().density));
					} catch (Exception ex) { android.util.Log.e("MainEditor", "Error", ex); }
				}
			}
		});
	}

	private void addCssVarRow(LinearLayout container, String name, String value, int themeIndex, 
			Map<String, String[]> dialogCustomVars, Runnable populateFields) {
		View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_custom_var, container, false);

		TextInputLayout tilCustomVar = row.findViewById(R.id.tilCustomVar);
		TextInputEditText etCustomVarValue = row.findViewById(R.id.etCustomVarValue);
		ImageButton btnEditCustomVar = row.findViewById(R.id.btnEditCustomVar);

		setupColorInputField(etCustomVarValue);
		tilCustomVar.setHint("--" + name);
		etCustomVarValue.setText(value);

		btnEditCustomVar.setOnClickListener(v -> {
			// Save current input values first so they are not lost when refreshing!
			for (int i = 0; i < container.getChildCount(); i++) {
				View child = container.getChildAt(i);
				TextInputEditText etVal = child.findViewById(R.id.etCustomVarValue);
				TextInputLayout til = child.findViewById(R.id.tilCustomVar);
				if (til != null && etVal != null) {
					String key = til.getHint() != null ? til.getHint().toString() : "";
					if (key.startsWith("--")) key = key.substring(2);
					String valueVal = etVal.getText().toString().trim();
					if (dialogCustomVars.containsKey(key)) {
						dialogCustomVars.get(key)[themeIndex] = valueVal;
					}
				}
			}

			// Show Dialog with Options: Rename or Delete
			String[] options = {"Rename Variable", "Delete Variable"};
			UniversalDialog.singleChoice(requireContext(), "Options: --" + name, options, (dialogIdx, label) -> {
				if (dialogIdx == 0) {
					// Rename
					UniversalDialog.textInput(requireContext(), "Rename Variable", "Variable name", name, newName -> {
						String cleanedName = newName.trim();
						if (cleanedName.startsWith("--")) {
							cleanedName = cleanedName.substring(2);
						}
						if (cleanedName.isEmpty()) {
							Toast.makeText(requireContext(), "Variable name cannot be empty", Toast.LENGTH_SHORT).show();
							return;
						}
						if (dialogCustomVars.containsKey(cleanedName) && !cleanedName.equals(name)) {
							Toast.makeText(requireContext(), "Variable name already exists", Toast.LENGTH_SHORT).show();
							return;
						}
						// Update key in map preserving values and order
						Map<String, String[]> updatedVars = new LinkedHashMap<>();
						for (Map.Entry<String, String[]> entry : dialogCustomVars.entrySet()) {
							if (entry.getKey().equals(name)) {
								updatedVars.put(cleanedName, entry.getValue());
							} else {
								updatedVars.put(entry.getKey(), entry.getValue());
							}
						}
						dialogCustomVars.clear();
						dialogCustomVars.putAll(updatedVars);
						populateFields.run();
					});
				} else if (dialogIdx == 1) {
					// Delete
					UniversalDialog.confirm(requireContext(), "Delete --" + name, 
							"Are you sure you want to delete this custom variable?", "Delete", true, () -> {
						dialogCustomVars.remove(name);
						populateFields.run();
					});
				}
			});
		});

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
		new MaterialAlertDialogBuilder(requireContext())
			.setTitle("Project libraries")
			.setItems(options, (d, which) -> {
				if (which == 0) showIconLibrariesDialog();
				else showAnimationLibraryDialog();
			})
			.setNegativeButton("Close", null)
			.show();
	}

	private void showIconLibrariesDialog() {
		IconLibraryManager mgr = new IconLibraryManager(requireContext(), projectId);
		java.util.List<IconLibraryManager.Library> all = mgr.allLibraries();
		String[] labels = new String[all.size()];
		boolean[] checked = new boolean[all.size()];
		for (int i = 0; i < all.size(); i++) {
			labels[i] = all.get(i).displayName + "  ·  " + all.get(i).version;
			checked[i] = mgr.isEnabled(all.get(i).id);
		}
		new MaterialAlertDialogBuilder(requireContext())
			.setTitle("Icon libraries")
			.setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
				if (isChecked) mgr.enable(all.get(which).id);
				else mgr.disable(all.get(which).id);
			})
			.setPositiveButton("Done", (d, w) -> {
				if (exportManager != null) exportManager.setIconLibraryManager(mgr);
				if (codeGenerator != null) codeGenerator.setIconLibraryManager(mgr);
				Toast.makeText(requireContext(), "Icon libraries updated", Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void showAnimationLibraryDialog() {
		AnimationLibraryManager mgr = new AnimationLibraryManager(requireContext(), projectId);
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
		
		new MaterialAlertDialogBuilder(requireContext())
			.setTitle("Animation Libraries")
			.setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
				if (isChecked) mgr.enable(all.get(which));
				else mgr.disable(all.get(which));
			})
			.setPositiveButton("Done", (d, w) -> {
				if (exportManager != null) exportManager.setAnimationLibraryManager(mgr);
				if (codeGenerator != null) codeGenerator.setAnimationLibraryManager(mgr);
				Toast.makeText(requireContext(), "Animation libraries updated", Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	// ---- Export Dialog ----

	private void showExportDialog() {
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle("Confirm Export")
			.setMessage("Are you sure you want to export the project source code?")
			.setPositiveButton("Export", (dialog, which) -> {
				performExport();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void performExport() {
		View dialogView = getLayoutInflater().inflate(R.layout.dialog_progress_material, null);
		android.widget.TextView progressMessage = dialogView.findViewById(R.id.progress_message);
		progressMessage.setText("Exporting ZIP and separate files...");
		final androidx.appcompat.app.AlertDialog progress = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle("Exporting Project")
				.setView(dialogView)
				.setCancelable(false)
				.create();
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
						screen, projectName, logicBlockManager, customBlockManager, null, pageManager);
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

			requireActivity().runOnUiThread(() -> {
				progress.dismiss();
				if (finalFolderOk && finalZipOk) {
					new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireActivity())
						.setTitle("Export Successful")
						.setMessage("Exported separate files to:\n" + finalFolderPath + "\n\nExported ZIP file to:\n" + finalZipPath)
						.setPositiveButton("OK", null)
						.show();
				} else {
					Toast.makeText(requireActivity(), "Export failed: " + finalErrMsg, Toast.LENGTH_LONG).show();
				}
			});
		}).start();
	}

	// ---- Design Property Lists (removed event items from view section) ----

	private void refreshActiveDesignList() {
		if (chipGroupBottom == null) {
			buildDesignList();
			return;
		}
		int checkedId = chipGroupBottom.getCheckedChipId();
		if (checkedId == R.id.chipLayout) {
			buildLayoutDesignList();
		} else if (checkedId == R.id.chipText) {
			buildTextDesignList();
		} else if (checkedId == R.id.chipMore) {
			buildMoreDesignList();
		} else {
			buildDesignList();
		}
	}

	private void buildDesignList() {
		design.clear();
		View selected = selector.getSelectedView();
		Map<String, Object> currentStyle = getWidgetStyle(selected);
		Map<String, Object> currentFunction = getWidgetFunction(selected);

		List<String> items = new ArrayList<>();
		String tag = getSelectedTag();
		if ("p".equals(tag) || "h1".equals(tag) || "h2".equals(tag) || "h3".equals(tag) || "h4".equals(tag) || "h5".equals(tag) || "h6".equals(tag)
			|| "span".equals(tag) || "label".equals(tag) || "a".equals(tag) || "button".equals(tag) || "li".equals(tag)) {
			items.add("Edittext");
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
		items.add("Background");
		items.add("Color");
		items.add("Opacity");
		items.add("ZIndex");

		populateDesignData(items, currentStyle, currentFunction);
	}

	private void buildLayoutDesignList() {
		design.clear();
		View selected = selector.getSelectedView();
		Map<String, Object> currentStyle = getWidgetStyle(selected);
		Map<String, Object> currentFunction = getWidgetFunction(selected);

		List<String> items = new ArrayList<>();
		// Sizing
		items.add("Width");
		items.add("Height");
		items.add("MinWidth");
		items.add("MaxWidth");
		items.add("MinHeight");
		items.add("MaxHeight");
		items.add("AspectRatio");
		// Spacing
		items.add("Padding");
		items.add("Margin");
		// Display & Position
		items.add("Display");
		items.add("Position");
		items.add("Top");
		items.add("Right");
		items.add("Bottom");
		items.add("Left");
		items.add("Float");
		items.add("Clear");
		items.add("Overflow");
		// Flex / Grid
		items.add("FlexDir");
		items.add("FlexWrap");
		items.add("JustifyContent");
		items.add("AlignItems");
		items.add("AlignSelf");
		items.add("AlignContent");
		items.add("Gap");
		items.add("RowGap");
		items.add("ColGap");
		items.add("GridCols");
		items.add("GridRows");
		items.add("GridGap");
		items.add("ObjectFit");

		populateDesignData(items, currentStyle, currentFunction);
	}

	private void buildTextDesignList() {
		design.clear();
		View selected = selector.getSelectedView();
		Map<String, Object> currentStyle = getWidgetStyle(selected);
		Map<String, Object> currentFunction = getWidgetFunction(selected);

		List<String> items = new ArrayList<>();
		items.add("Color");
		items.add("TextSize");
		items.add("Font");
		items.add("FontFamily");
		items.add("TextAlign");
		items.add("LineHeight");
		items.add("LetterSpace");
		items.add("TextDecor");
		items.add("TextTransform");

		populateDesignData(items, currentStyle, currentFunction);
	}

	private void buildMoreDesignList() {
		design.clear();
		View selected = selector.getSelectedView();
		Map<String, Object> currentStyle = getWidgetStyle(selected);
		Map<String, Object> currentFunction = getWidgetFunction(selected);

		List<String> items = new ArrayList<>();
		// custom advance
		items.add("HTML Attributes");
		items.add("JSEvents");
		items.add("CustomStyle");
		items.add("Cursor");
		items.add("CssVar");

		// Borders
		items.add("BorderWidth");
		items.add("BorderColor");
		items.add("BorderRadius");
		items.add("BorderTop");
		items.add("BorderRight");
		items.add("BorderBottom");
		items.add("BorderLeft");
		items.add("RadiusTL");
		items.add("RadiusTR");
		items.add("RadiusBL");
		items.add("RadiusBR");
		// Shadow/effects
		items.add("BoxShadow");
		items.add("Elevation");
		items.add("Rotation");
		items.add("Gradient");

		populateDesignData(items, currentStyle, currentFunction);
	}

	private void populateDesignData(List<String> items, Map<String, Object> currentStyle, Map<String, Object> currentFunction) {
		for (String item : items) {
			HashMap<String, Object> map = new HashMap<>();
			map.put("edit", item);
			String value = "";
			if (item.equals("Edittext")) value = currentFunction.containsKey("text") ? currentFunction.get("text").toString() : "";
			else if (item.equals("SetId")) value = currentFunction.containsKey("id") ? currentFunction.get("id").toString() : "";
			else if (item.equals("SetClass")) value = currentFunction.containsKey("class") ? currentFunction.get("class").toString() : "";
			else if (item.equals("Custom Attributes")) {
				StringBuilder sb = new StringBuilder();
				for (Map.Entry<String, Object> entry : currentFunction.entrySet()) {
					String key = entry.getKey();
					if (!"id".equals(key) && !"class".equals(key) && !"style".equals(key)
						&& !"tag".equals(key) && !"children".equals(key) && !"text".equals(key)
						&& !"href".equals(key) && !"src".equals(key) && !"placeholder".equals(key)
						&& !"type".equals(key) && !"alt".equals(key) && !"controls".equals(key)
						&& !key.startsWith("on") && entry.getValue() != null) {
						if (sb.length() > 0) sb.append(", ");
						sb.append(key).append("=\"").append(entry.getValue()).append("\"");
					}
				}
				value = sb.toString();
			}
			else if (item.equals("SetHref")) value = currentFunction.containsKey("href") ? currentFunction.get("href").toString() : "";
			else if (item.equals("SetPlaceholder")) value = currentFunction.containsKey("placeholder") ? currentFunction.get("placeholder").toString() : "";
			else if (item.equals("JSEvents")) {
				int count = 0;
				for (String key : currentFunction.keySet()) {
					if (key.startsWith("on") && currentFunction.get(key) != null && !String.valueOf(currentFunction.get(key)).trim().isEmpty()) {
						count++;
					}
				}
				value = count > 0 ? "(" + count + ")" : "";
			}
			else {
				String cssKey = getCssKeyFromItem(item);
				if (currentStyle.containsKey(cssKey)) {
					Object o = currentStyle.get(cssKey);
					value = o != null ? o.toString() : "";
				}
			}
			map.put("value", value);
			design.add(map);
		}
		recyclerview3.setAdapter(new Recyclerview3Adapter(design));
		recyclerview3.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
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
			if (wm.containsKey("tag")) {
				String widgetTag = String.valueOf(wm.get("tag")).trim();
				if (!widgetTag.isEmpty()) set.add(widgetTag);
			}
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
			Toast.makeText(requireContext(), "Select a widget first", Toast.LENGTH_SHORT).show();
			return;
		}

		String editType = design.get(position).get("edit").toString();
		boolean isStyleProperty = !editType.equals("Edittext") && !editType.equals("SetId") 
				&& !editType.equals("SetClass") && !editType.equals("Custom Attributes") 
				&& !editType.equals("JSEvents") && !editType.equals("PickIcon")
				&& !editType.equals("SetHref") && !editType.equals("SetTarget")
				&& !editType.equals("SetPlaceholder") && !editType.equals("SetType")
				&& !editType.equals("ImageSrc") && !editType.equals("ListItems");

		if (isStyleProperty) {
			Map<String, Object> function = getWidgetFunction(selected);
			String classVal = function.containsKey("class") ? String.valueOf(function.get("class")).trim() : "";
			if (classVal.isEmpty()) {
				Toast.makeText(requireContext(), "Please set a class first before applying styles", Toast.LENGTH_SHORT).show();
				return;
			}
		}

		String initialValue = design.get(position).containsKey("value") ? design.get(position).get("value").toString() : "";
		syncProjectAssets();
		UniversalM3Dialog dialog = new UniversalM3Dialog(requireContext()).setTitle(editType).setInitialValue(initialValue);

		switch (editType) {
			case "Edittext":
				dialog.setHint("Text content").setMultiline(true).showTextInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("text", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "SetId":
				dialog.setHint("my-element-id").showTextInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("id", value.replaceFirst("^#", ""));
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshHierarchy();
					refreshActiveDesignList();
				});
				break;
			case "SetClass":
				dialog.setHint("class1 class2").showClassChipsInput(ProjectAssetManager.getInstance().getClasses(), value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("class", value.replaceFirst("^\\.", ""));
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshHierarchy();
					refreshActiveDesignList();
				});
				break;
			case "PickIcon":
				dialog.showIconPicker(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("class", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "SetHref":
				dialog.setHint("https://...").showTextInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("href", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "SetTarget":
				dialog.setOptions(new String[]{"_self", "_blank", "_parent", "_top"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("target", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "SetPlaceholder":
				dialog.setHint("Placeholder...").showTextInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("placeholder", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "SetType":
				dialog.setOptions(new String[]{"text", "password", "email", "number", "tel", "url", "date", "time", "color", "range", "file", "checkbox", "radio", "submit", "reset", "hidden"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("type", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "ImageSrc":
				dialog.setHint("URL or base64").showTextInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("src", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "ListItems":
				showListItemsDialog(selected);
				break;
			case "JSEvents":
				showJsEventsDialog(selected);
				break;
			case "TextSize":
				dialog.setUnits(new String[]{"px", "rem", "em", "vh", "vw", "pt"}).showUnitInput("fontSize", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("fontSize", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Color":
				dialog.setSuggestions(getColorSuggestions()).showColorInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("color", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Font":
				dialog.setOptions(new String[]{"normal", "bold", "lighter", "100", "200", "300", "400", "500", "600", "700", "800", "900"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("fontWeight", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "TextAlign":
				dialog.setOptions(new String[]{"left", "center", "right", "justify"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("textAlign", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Background":
				dialog.setSuggestions(getColorSuggestions()).showColorInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("backgroundColor", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "BorderRadius":
				dialog.setUnits(new String[]{"px", "rem", "%", "em"}).showUnitInput("borderRadius", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("borderRadius", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "BorderWidth":
				dialog.setUnits(new String[]{"px", "rem", "em"}).showUnitInput("borderWidth", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("borderWidth", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "BorderColor":
				dialog.setSuggestions(getColorSuggestions()).showColorInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("borderColor", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Padding":
				dialog.setUnits(new String[]{"px", "rem", "%", "em"}).showFourValueInput("padding", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("padding", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Margin":
				dialog.setUnits(new String[]{"px", "rem", "%", "em", "auto"}).showFourValueInput("margin", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("margin", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Width":
				dialog.setUnits(new String[]{"px", "rem", "%", "em", "vw", "auto"}).showUnitInput("width", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("width", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Height":
				dialog.setUnits(new String[]{"px", "rem", "%", "em", "vh", "auto"}).showUnitInput("height", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("height", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Display":
				dialog.setOptions(new String[]{"block", "flex", "grid", "inline", "inline-block", "inline-flex", "none", "contents"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("display", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Position":
				dialog.setOptions(new String[]{"static", "relative", "absolute", "fixed", "sticky"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("position", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "FlexDir":
				dialog.setOptions(new String[]{"row", "column", "row-reverse", "column-reverse"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("flexDirection", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "FlexWrap":
				dialog.setOptions(new String[]{"nowrap", "wrap", "wrap-reverse"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("flexWrap", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "JustifyContent":
				dialog.setOptions(new String[]{"flex-start", "center", "flex-end", "space-between", "space-around", "space-evenly"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("justifyContent", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "AlignItems":
				dialog.setOptions(new String[]{"flex-start", "center", "flex-end", "stretch", "baseline"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("alignItems", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "AlignSelf":
				dialog.setOptions(new String[]{"auto", "flex-start", "center", "flex-end", "stretch", "baseline"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("alignSelf", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "AlignContent":
				dialog.setOptions(new String[]{"flex-start", "center", "flex-end", "stretch", "space-between", "space-around"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("alignContent", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Gap":
				dialog.setUnits(new String[]{"px", "rem", "em"}).showUnitInput("gap", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("gap", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "RowGap":
				dialog.setUnits(new String[]{"px", "rem", "em"}).showUnitInput("rowGap", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("rowGap", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "ColGap":
				dialog.setUnits(new String[]{"px", "rem", "em"}).showUnitInput("columnGap", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("columnGap", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Top":
				dialog.setUnits(new String[]{"px", "%", "rem", "em"}).showUnitInput("top", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("top", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Right":
				dialog.setUnits(new String[]{"px", "%", "rem", "em"}).showUnitInput("right", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("right", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Bottom":
				dialog.setUnits(new String[]{"px", "%", "rem", "em"}).showUnitInput("bottom", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("bottom", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Left":
				dialog.setUnits(new String[]{"px", "%", "rem", "em"}).showUnitInput("left", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("left", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "ObjectFit":
				dialog.setOptions(new String[]{"fill", "contain", "cover", "none", "scale-down"}).showChoiceInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("objectFit", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "AspectRatio":
				dialog.setHint("16/9").showTextInput(value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("aspectRatio", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
					refreshActiveDesignList();
				});
				break;
			case "Custom Attributes":
				showCustomAttributesDialog(selected);
				break;
			default:
				// Determine input type based on the property name dynamically
				String cssKey = getCssKeyFromItem(editType);
				if (editType.contains("Color") || editType.equals("Background")) {
					dialog.setSuggestions(getColorSuggestions()).showColorInput(value -> {
						Map<String, Object> style = new HashMap<>();
						style.put(cssKey, value);
						widgetUpdater.updateWidget(selected, "", style);
						saveUndoState();
						refreshActiveDesignList();
					});
				} else if (editType.equals("Display")) {
					dialog.setOptions(new String[]{"block", "flex", "grid", "inline", "inline-block", "none"}).showChoiceInput(value -> {
						Map<String, Object> style = new HashMap<>();
						style.put(cssKey, value);
						widgetUpdater.updateWidget(selected, "", style);
						saveUndoState();
						refreshActiveDesignList();
					});
				} else if (editType.equals("Position")) {
					dialog.setOptions(new String[]{"static", "relative", "absolute", "fixed", "sticky"}).showChoiceInput(value -> {
						Map<String, Object> style = new HashMap<>();
						style.put(cssKey, value);
						widgetUpdater.updateWidget(selected, "", style);
						saveUndoState();
						refreshActiveDesignList();
					});
				} else if (editType.equals("Overflow")) {
					dialog.setOptions(new String[]{"visible", "hidden", "scroll", "auto"}).showChoiceInput(value -> {
						Map<String, Object> style = new HashMap<>();
						style.put(cssKey, value);
						widgetUpdater.updateWidget(selected, "", style);
						saveUndoState();
						refreshActiveDesignList();
					});
				} else if (editType.equals("Cursor")) {
					dialog.setOptions(new String[]{"default", "pointer", "text", "move", "not-allowed", "wait"}).showChoiceInput(value -> {
						Map<String, Object> style = new HashMap<>();
						style.put(cssKey, value);
						widgetUpdater.updateWidget(selected, "", style);
						saveUndoState();
						refreshActiveDesignList();
					});
				} else if (editType.equals("Float")) {
					dialog.setOptions(new String[]{"none", "left", "right"}).showChoiceInput(value -> {
						Map<String, Object> style = new HashMap<>();
						style.put(cssKey, value);
						widgetUpdater.updateWidget(selected, "", style);
						saveUndoState();
						refreshActiveDesignList();
					});
				} else if (editType.equals("Clear")) {
					dialog.setOptions(new String[]{"none", "left", "right", "both"}).showChoiceInput(value -> {
						Map<String, Object> style = new HashMap<>();
						style.put(cssKey, value);
						widgetUpdater.updateWidget(selected, "", style);
						saveUndoState();
						refreshActiveDesignList();
					});
				} else if (editType.equals("TextAlign")) {
					dialog.setOptions(new String[]{"left", "center", "right", "justify"}).showChoiceInput(value -> {
						Map<String, Object> style = new HashMap<>();
						style.put(cssKey, value);
						widgetUpdater.updateWidget(selected, "", style);
						saveUndoState();
						refreshActiveDesignList();
					});
				} else if (editType.equals("TextTransform")) {
					dialog.setOptions(new String[]{"none", "capitalize", "uppercase", "lowercase"}).showChoiceInput(value -> {
						Map<String, Object> style = new HashMap<>();
						style.put(cssKey, value);
						widgetUpdater.updateWidget(selected, "", style);
						saveUndoState();
						refreshActiveDesignList();
					});
				} else if (editType.equals("TextDecor")) {
					dialog.setOptions(new String[]{"none", "underline", "line-through", "overline"}).showChoiceInput(value -> {
						Map<String, Object> style = new HashMap<>();
						style.put(cssKey, value);
						widgetUpdater.updateWidget(selected, "", style);
						saveUndoState();
						refreshActiveDesignList();
					});
				} else if (editType.equals("FontFamily")) {
					dialog.setOptions(new String[]{"sans-serif", "serif", "monospace", "cursive", "fantasy", "Inter", "Roboto", "Outfit"}).showChoiceInput(value -> {
						Map<String, Object> style = new HashMap<>();
						style.put(cssKey, value);
						widgetUpdater.updateWidget(selected, "", style);
						saveUndoState();
						refreshActiveDesignList();
					});
				} else if (editType.equals("ZIndex") || editType.equals("Elevation") || editType.equals("Opacity") || editType.equals("Rotation")) {
					dialog.setHint("e.g. 1 (number)").showTextInput(value -> {
						Map<String, Object> style = new HashMap<>();
						style.put(cssKey, value);
						widgetUpdater.updateWidget(selected, "", style);
						saveUndoState();
						refreshActiveDesignList();
					});
				} else if (editType.equals("BoxShadow") || editType.equals("Gradient") || editType.equals("CssVar") || editType.equals("CustomStyle") || editType.equals("GridCols") || editType.equals("GridRows")) {
					dialog.setHint("Enter value").showTextInput(value -> {
						Map<String, Object> style = new HashMap<>();
						style.put(cssKey, value);
						widgetUpdater.updateWidget(selected, "", style);
						saveUndoState();
						refreshActiveDesignList();
					});
				} else {
					dialog.setUnits(new String[]{"px", "rem", "%", "em", "vh", "vw", "auto"}).showUnitInput(cssKey, value -> {
						Map<String, Object> style = new HashMap<>();
						style.put(cssKey, value);
						widgetUpdater.updateWidget(selected, "", style);
						saveUndoState();
						refreshActiveDesignList();
					});
				}
				break;
		}
	}

	@SuppressWarnings("unchecked")
	private void showCustomAttributesDialog(View selected) {
		Object tag = selected.getTag();
		if (!(tag instanceof Map)) return;
		Map<String, Object> wm = (Map<String, Object>) tag;
		final Map<String, Object> function = (Map<String, Object>) wm.get("function");
		if (function == null) return;

		View dialogView = getLayoutInflater().inflate(R.layout.dialog_custom_attributes_list, null);
		LinearLayout container = dialogView.findViewById(R.id.llAttributesContainer);
		com.google.android.material.button.MaterialButton btnAdd = dialogView.findViewById(R.id.btnAddAttribute);

		final com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
			new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle("Custom Attributes")
				.setView(dialogView)
				.setNegativeButton("Close", null);

		final androidx.appcompat.app.AlertDialog dialog = builder.create();

		container.removeAllViews();
		for (Map.Entry<String, Object> entry : function.entrySet()) {
			final String key = entry.getKey();
			if (!"id".equals(key) && !"class".equals(key) && !"style".equals(key)
				&& !"tag".equals(key) && !"children".equals(key) && !"text".equals(key)
				&& !"href".equals(key) && !"src".equals(key) && !"placeholder".equals(key)
				&& !"type".equals(key) && !"alt".equals(key) && !"controls".equals(key)
				&& !key.startsWith("on") && entry.getValue() != null) {

				final String val = String.valueOf(entry.getValue());
				View row = getLayoutInflater().inflate(R.layout.item_dialog_custom_attribute, null);
				TextView tvKey = row.findViewById(R.id.tvKey);
				TextView tvValue = row.findViewById(R.id.tvValue);
				ImageButton btnEdit = row.findViewById(R.id.btnEdit);
				ImageButton btnDelete = row.findViewById(R.id.btnDelete);

				tvKey.setText(key);
				tvValue.setText(val);

				btnEdit.setOnClickListener(v -> {
					dialog.dismiss();
					showEditCustomAttributeDialog(selected, function, key, val);
				});

				btnDelete.setOnClickListener(v -> {
					function.remove(key);
					saveUndoState();
					buildDesignList();
					dialog.dismiss();
					showCustomAttributesDialog(selected);
				});

				container.addView(row);
			}
		}

		if (btnAdd != null) {
			btnAdd.setOnClickListener(v -> {
				dialog.dismiss();
				showAddCustomAttributeDialog(selected, function);
			});
		}

		dialog.show();
	}

	private void showAddCustomAttributeDialog(View selected, Map<String, Object> function) {
		View view = getLayoutInflater().inflate(R.layout.dialog_custom_attribute, null);
		com.google.android.material.textfield.TextInputLayout tilName = view.findViewById(R.id.tilAttrName);
		com.google.android.material.textfield.TextInputLayout tilValue = view.findViewById(R.id.tilAttrValue);
		final com.google.android.material.textfield.TextInputEditText etKey = view.findViewById(R.id.etAttrName);
		final com.google.android.material.textfield.TextInputEditText etVal = view.findViewById(R.id.etAttrValue);

		int dp14 = (int) (14 * getResources().getDisplayMetrics().density);
		if (tilName != null) tilName.setBoxCornerRadii(dp14, dp14, dp14, dp14);
		if (tilValue != null) tilValue.setBoxCornerRadii(dp14, dp14, dp14, dp14);

		new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle("Add Custom Attribute")
			.setView(view)
			.setPositiveButton("Add", (dialog, which) -> {
				String key = etKey.getText().toString().trim();
				String val = etVal.getText().toString().trim();
				if (!key.isEmpty()) {
					function.put(key, val);
					saveUndoState();
					buildDesignList();
					showCustomAttributesDialog(selected);
				}
			})
			.setNegativeButton("Cancel", (dialog, which) -> showCustomAttributesDialog(selected))
			.show();
	}

	private void showEditCustomAttributeDialog(View selected, Map<String, Object> function, String key, String val) {
		View view = getLayoutInflater().inflate(R.layout.dialog_custom_attribute, null);
		com.google.android.material.textfield.TextInputLayout tilName = view.findViewById(R.id.tilAttrName);
		com.google.android.material.textfield.TextInputLayout tilValue = view.findViewById(R.id.tilAttrValue);
		final com.google.android.material.textfield.TextInputEditText etKey = view.findViewById(R.id.etAttrName);
		final com.google.android.material.textfield.TextInputEditText etVal = view.findViewById(R.id.etAttrValue);

		int dp14 = (int) (14 * getResources().getDisplayMetrics().density);
		if (tilName != null) tilName.setBoxCornerRadii(dp14, dp14, dp14, dp14);
		if (tilValue != null) tilValue.setBoxCornerRadii(dp14, dp14, dp14, dp14);

		etKey.setText(key);
		etKey.setEnabled(false);
		if (tilName != null) {
			tilName.setEnabled(false);
		}
		etVal.setText(val);

		new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle("Edit Attribute")
			.setView(view)
			.setPositiveButton("Save", (dialog, which) -> {
				String newVal = etVal.getText().toString().trim();
				function.put(key, newVal);
				saveUndoState();
				buildDesignList();
				showCustomAttributesDialog(selected);
			})
			.setNegativeButton("Cancel", (dialog, which) -> showCustomAttributesDialog(selected))
			.setNeutralButton("Delete", (dialog, which) -> {
				function.remove(key);
				saveUndoState();
				buildDesignList();
				showCustomAttributesDialog(selected);
			})
			.show();
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

		new MaterialAlertDialogBuilder(requireContext())
			.setTitle("Apply CSS Variable")
			.setItems(cssVars, (dialog, which) -> {
				String varName = cssVars[which];
				String[] properties = {"color", "backgroundColor", "borderColor", "fontFamily", "background"};
				new MaterialAlertDialogBuilder(requireContext())
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
			TextView tv = new TextView(requireContext());
			String prefix = ordered ? (i + 1) + ". " : "• ";
			tv.setText(prefix + items.get(i));
			tv.setTextColor(Color.parseColor("#222222"));
			tv.setPadding(8, 4, 8, 4);
			vg.addView(tv);
		}
	}

	@SuppressWarnings("unchecked")
	private void showJsEventsDialog(final View selected) {
		Object tagObj = selected.getTag();
		if (!(tagObj instanceof Map)) return;
		final Map<String, Object> wm = (Map<String, Object>) tagObj;
		Map<String, Object> fn = (Map<String, Object>) wm.get("function");
		if (fn == null) {
			fn = new HashMap<>();
			wm.put("function", fn);
		}

		final Map<String, Object> fnRef = fn;
		final Map<String, String> localEventsMap = new HashMap<>();
		for (Map.Entry<String, Object> entry : fnRef.entrySet()) {
			if (entry.getKey().startsWith("on") && entry.getValue() != null) {
				localEventsMap.put(entry.getKey(), String.valueOf(entry.getValue()));
			}
		}

		View dialogView = getLayoutInflater().inflate(R.layout.dialog_js_events, null);
		final LinearLayout eventsContainer = dialogView.findViewById(R.id.eventsContainer);
		Button btnAddEvent = dialogView.findViewById(R.id.btnAddEvent);

		final Map<String, TextInputEditText> eventInputFields = new HashMap<>();

		final Runnable rebuildRows = new Runnable() {
			@Override
			public void run() {
				eventsContainer.removeAllViews();
				eventInputFields.clear();

				List<String> sortedKeys = new ArrayList<>(localEventsMap.keySet());
				java.util.Collections.sort(sortedKeys);

				if (sortedKeys.isEmpty()) {
					TextView tvNoEvents = new TextView(requireActivity());
					tvNoEvents.setText("No JS events configured. Use the add button below.");
					tvNoEvents.setPadding(0, 24, 0, 24);
					tvNoEvents.setGravity(Gravity.CENTER);
					tvNoEvents.setTextSize(14);
					eventsContainer.addView(tvNoEvents);
					return;
				}

				for (final String eventName : sortedKeys) {
					View rowView = getLayoutInflater().inflate(R.layout.item_js_event_row, null);
					TextInputLayout tilEventValue = rowView.findViewById(R.id.tilEventValue);
					TextInputEditText etEventValue = rowView.findViewById(R.id.etEventValue);
					ImageButton btnDeleteEvent = rowView.findViewById(R.id.btnDeleteEvent);

					tilEventValue.setHint(eventName);
					etEventValue.setText(localEventsMap.get(eventName));
					eventInputFields.put(eventName, etEventValue);

					btnDeleteEvent.setOnClickListener(v -> {
						localEventsMap.remove(eventName);
						run();
					});

					eventsContainer.addView(rowView);
				}
			}
		};

		rebuildRows.run();

		btnAddEvent.setOnClickListener(v -> {
			final String[] eventOptions = {
				"onclick", "onchange", "onmouseover", "onmouseout", 
				"onkeydown", "onkeyup", "onkeypress", "oninput", 
				"onfocus", "onblur", "onload", "onsubmit", "Custom..."
			};
			new MaterialAlertDialogBuilder(requireActivity())
				.setTitle("Select Event Type")
				.setItems(eventOptions, (dialog, which) -> {
					String selectedOption = eventOptions[which];
					if (selectedOption.equals("Custom...")) {
						new UniversalM3Dialog(requireActivity())
							.setTitle("Custom Event")
							.setHint("e.g. onmouseenter")
							.showTextInput(customEvent -> {
								String cleaned = customEvent.trim().toLowerCase();
								if (!cleaned.isEmpty()) {
									if (!cleaned.startsWith("on")) {
										cleaned = "on" + cleaned;
									}
									if (!localEventsMap.containsKey(cleaned)) {
										for (Map.Entry<String, TextInputEditText> entry : eventInputFields.entrySet()) {
											localEventsMap.put(entry.getKey(), entry.getValue().getText().toString());
										}
										localEventsMap.put(cleaned, "");
										rebuildRows.run();
									} else {
										Toast.makeText(requireActivity(), "Event already exists", Toast.LENGTH_SHORT).show();
									}
								}
							});
					} else {
						if (!localEventsMap.containsKey(selectedOption)) {
							for (Map.Entry<String, TextInputEditText> entry : eventInputFields.entrySet()) {
								localEventsMap.put(entry.getKey(), entry.getValue().getText().toString());
							}
							localEventsMap.put(selectedOption, "");
							rebuildRows.run();
						} else {
							Toast.makeText(requireActivity(), "Event already exists", Toast.LENGTH_SHORT).show();
						}
					}
				})
				.show();
		});

		androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
			.setView(dialogView)
			.setPositiveButton("Save", (dialogInterface, which) -> {
				List<String> keysToRemove = new ArrayList<>();
				for (String key : fnRef.keySet()) {
					if (key.startsWith("on")) {
						keysToRemove.add(key);
					}
				}
				for (String key : keysToRemove) {
					fnRef.remove(key);
				}

				for (Map.Entry<String, TextInputEditText> entry : eventInputFields.entrySet()) {
					String code = entry.getValue().getText().toString().trim();
					if (!code.isEmpty()) {
						fnRef.put(entry.getKey(), code);
					}
				}
				selected.setTag(wm);
				saveUndoState();
				refreshHierarchy();
				buildDesignList();
				Toast.makeText(requireActivity(), "Events saved", Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton("Cancel", null)
			.create();

		dialog.show();

		if (dialog.getWindow() != null) {
			dialog.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
			dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		}

		dialogView.post(() -> {
			if (!eventInputFields.isEmpty()) {
				TextInputEditText et = eventInputFields.values().iterator().next();
				if (et != null) {
					et.requestFocus();
					android.view.inputmethod.InputMethodManager imm = 
						(android.view.inputmethod.InputMethodManager) requireActivity().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
					if (imm != null) {
						imm.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
					}
				}
			}
		});
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

		LinearLayout container = new LinearLayout(requireContext());
		container.setOrientation(LinearLayout.VERTICAL);
		container.setPadding(48, 16, 48, 0);

		TextView help = new TextView(requireContext());
		help.setText("Enter one item per line:");
		help.setTextSize(12);
		container.addView(help);

		TextInputLayout til = new TextInputLayout(requireContext());
		til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
		TextInputEditText input = new TextInputEditText(requireContext());
		input.setText(seed.toString());
		input.setMinLines(5);
		input.setMaxLines(15);
		input.setGravity(Gravity.TOP | Gravity.START);
		til.addView(input);
		container.addView(til);

		final Map<String, Object> fnRef = fn;
		new MaterialAlertDialogBuilder(requireContext())
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
			String displayValue = value;
			if (value.length() > 20) {
				displayValue = value.substring(0, 17) + "...";
			}
			textview1.setText(editType + (value.isEmpty() ? "" : ": " + displayValue));
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
			case "SetId": case "SetClass": return R.drawable.pencil_code;
			case "SetHref": case "SetTarget": return R.drawable.layers_linked;
			case "SetPlaceholder": case "SetType": return R.drawable.bulb;
			case "ImageSrc": return R.drawable.photo;
			case "PickIcon": return R.drawable.photo;
			case "TextSize": return R.drawable.text_size;
			case "TextAlign": return R.drawable.focus_centered;
			case "Color": return R.drawable.text_color;
			case "Font": return R.drawable.alphabet_cyrillic;
			case "Background": return R.drawable.background;
			case "BorderRadius": case "RadiusTL": case "RadiusTR": case "RadiusBL": case "RadiusBR":
				return R.drawable.border_radius;
			case "BorderWidth": case "BorderTop": case "BorderRight": case "BorderBottom": case "BorderLeft":
				return R.drawable.border_style;
			case "BorderColor": return R.drawable.table;
			case "Padding": return R.drawable.box_padding;
			case "Margin": return R.drawable.box_margin;
			case "Elevation": case "BoxShadow": return R.drawable.shadow;
			case "ZIndex": return R.drawable.stack_2;
			case "Opacity": return R.drawable.droplet;
			case "Rotation": return R.drawable.refresh;
			case "Cursor": return R.drawable.cursor_text;
			case "Width": case "MinWidth": case "MaxWidth": return R.drawable.arrow_autofit_width;
			case "MinHeight": case "MaxHeight": case "Height": return R.drawable.arrow_autofit_height;
			case "Display": case "Position": case "Float": case "Clear":
			case "Top": case "Right": case "Bottom": case "Left":
				return R.drawable.box_model_2;
			case "FlexDir": case "FlexWrap": case "JustifyContent": case "AlignItems":
			case "AlignSelf": case "AlignContent": case "Gap": case "RowGap": case "ColGap":
			case "GridCols": case "GridRows": case "GridGap":
				return R.drawable.columns_3;
			case "ObjectFit": case "AspectRatio": return R.drawable.box_model_2;
			case "Overflow": return R.drawable.arrows_move_vertical;
			case "TextDecor": return R.drawable.cursor_text;
			case "LineHeight": case "LetterSpace": return R.drawable.text_size;
			case "Gradient": return R.drawable.background;
			case "CssVar": case "CustomStyle": return R.drawable.code_plus;
			case "JSEvents": return R.drawable.settings_code;
			default: return R.drawable.pencil_question;
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
				androidx.drawerlayout.widget.DrawerLayout drawer = drawerLayout;
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

	private String resolveFileName(android.net.Uri uri) {
		try {
			android.database.Cursor c = requireActivity().getContentResolver().query(
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

	private String readUriContent(android.net.Uri uri) {
		try (java.io.InputStream is = requireActivity().getContentResolver().openInputStream(uri);
			 java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n");
			}
			return sb.toString();
		} catch (Exception e) {
			Log.e("MainActivity", "Failed to read URI: " + e.getMessage());
			return null;
		}
	}
}
