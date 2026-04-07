package sketchweb.gl;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
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
	private HierarchyTreeAdapter hierarchyAdapter;

	private ArrayList<HashMap<String, Object>> widgets = new ArrayList<>();
	private ArrayList<HashMap<String, Object>> filteredWidgets = new ArrayList<>();
	private ArrayList<HashMap<String, Object>> design = new ArrayList<>();

	private String projectName = "Untitled";

	// Views
	private LinearLayout main;
	private LinearLayout topBar;
	private LinearLayout screen;
	private LinearLayout rightPanel;
	private LinearLayout leftPanel;
	private LinearLayout bottomPanel;
	private LinearLayout eventPanel;
	private LinearLayout assetsPanel;
	private NestedScrollView vscroll2;
	private Button button5, button4, delete, btnImportWidgets, btnImportImage, btnImportSvg;
	private Button btnDrawer, btnBack, btnUndo, btnRedo, btnTheme, btnExport, btnViewStyles;
	private Button btnAddLogicBlock, btnViewAllBlocks;
	private Button btnLoadCustomWidgets, btnLoadCustomBlocks;
	private TextView textview2, tvProjectTitle;
	private RecyclerView recyclerview3, recyclerview1, recyclerviewRightPanel, rvAssets;
	private android.widget.Spinner widgetSpinner;
	private TabLayout tabLayout, tabWidgetCategories;
	private Chip chipBasic, chipStyles, chipEvent;
	private TextInputEditText etSearchWidget, etSearchHierarchy;
	private ChipGroup chipGroupCategories;

	private ActivityResultLauncher<android.content.Intent> importWidgetLauncher;
	private ActivityResultLauncher<android.content.Intent> importImageLauncher;
	private ActivityResultLauncher<android.content.Intent> importSvgLauncher;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		initialize(savedInstanceState);
		initializeLogic();
	}

	private void initialize(Bundle savedInstanceState) {
		main = findViewById(R.id.main);
		topBar = findViewById(R.id.topBar);
		screen = findViewById(R.id.screen);
		rightPanel = findViewById(R.id.rightPanel);
		leftPanel = findViewById(R.id.leftPanel);
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
		textview2 = findViewById(R.id.textview2);
		recyclerview3 = findViewById(R.id.recyclerview3);
		recyclerview1 = findViewById(R.id.recyclerview1);
		recyclerviewRightPanel = findViewById(R.id.recyclerviewRightPanel);
		rvAssets = findViewById(R.id.rvAssets);
		widgetSpinner = findViewById(R.id.widgetSpinner);
		btnDrawer = findViewById(R.id.btnDrawer);
		btnBack = findViewById(R.id.btnBack);
		btnUndo = findViewById(R.id.btnUndo);
		btnRedo = findViewById(R.id.btnRedo);
		btnTheme = findViewById(R.id.btnTheme);
		btnExport = findViewById(R.id.btnExport);
		btnViewStyles = findViewById(R.id.btnViewStyles);
		btnAddLogicBlock = findViewById(R.id.btnAddLogicBlock);
		btnViewAllBlocks = findViewById(R.id.btnViewAllBlocks);
		btnLoadCustomWidgets = findViewById(R.id.btnLoadCustomWidgets);
		btnLoadCustomBlocks = findViewById(R.id.btnLoadCustomBlocks);
		tvProjectTitle = findViewById(R.id.tvProjectTitle);
		tabLayout = findViewById(R.id.tabLayout);
		tabWidgetCategories = findViewById(R.id.tabWidgetCategories);
		chipBasic = findViewById(R.id.chipBasic);
		chipStyles = findViewById(R.id.chipStyles);
		chipEvent = findViewById(R.id.chipEvent);
		etSearchWidget = findViewById(R.id.etSearchWidget);
		etSearchHierarchy = findViewById(R.id.etSearchHierarchy);
		chipGroupCategories = findViewById(R.id.chipGroupCategories);

		// Get project name from intent
		if (getIntent().hasExtra("project_name")) {
			projectName = getIntent().getStringExtra("project_name");
		}
		tvProjectTitle.setText(projectName);

		// Drawer button
		btnDrawer.setOnClickListener(v -> {
			androidx.drawerlayout.widget.DrawerLayout drawer = findViewById(R.id._main);
			if (drawer != null) {
				drawer.openDrawer(androidx.core.view.GravityCompat.START);
			}
		});

		// Preview button
		button5.setOnClickListener(v -> showPreview());

		// Save button
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

		// View Applied Styles button
		btnViewStyles.setOnClickListener(v -> showViewStylesDialog());

		// Import Image Button
		btnImportImage.setOnClickListener(v -> {
			android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
			intent.setType("image/*");
			importImageLauncher.launch(intent);
		});

		// Import JSON Button
		btnImportWidgets.setOnClickListener(v -> {
			android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
			intent.setType("*/*");
			String[] mimetypes = {"application/json", "text/plain"};
			intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimetypes);
			importWidgetLauncher.launch(intent);
		});

		// Import SVG Button
		if (btnImportSvg != null) {
			btnImportSvg.setOnClickListener(v -> {
				android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
				intent.setType("image/svg+xml");
				importImageLauncher.launch(intent);
			});
		}

		// Custom JSON loaders
		if (btnLoadCustomWidgets != null) {
			btnLoadCustomWidgets.setOnClickListener(v -> loadCustomWidgetsFromDevice());
		}
		if (btnLoadCustomBlocks != null) {
			btnLoadCustomBlocks.setOnClickListener(v -> loadCustomBlocksFromDevice());
		}

		// Icon library chips
		Chip chipFontAwesome = findViewById(R.id.chipFontAwesome);
		Chip chipTablerIcons = findViewById(R.id.chipTablerIcons);
		Chip chipCustomSvg = findViewById(R.id.chipCustomSvg);
		if (chipFontAwesome != null) {
			chipFontAwesome.setOnClickListener(v -> addIconLibraryWidget("Font Awesome", "fa"));
		}
		if (chipTablerIcons != null) {
			chipTablerIcons.setOnClickListener(v -> addIconLibraryWidget("Tabler Icons", "tabler"));
		}
		if (chipCustomSvg != null) {
			chipCustomSvg.setOnClickListener(v -> {
				android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
				intent.setType("image/svg+xml");
				importImageLauncher.launch(intent);
			});
		}

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

		importImageLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == RESULT_OK && result.getData() != null) {
					android.net.Uri uri = result.getData().getData();
					if (uri != null) {
						try {
							InputStream is = getContentResolver().openInputStream(uri);
							java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
							int nRead;
							byte[] data = new byte[16384];
							while ((nRead = is.read(data, 0, data.length)) != -1) {
								buffer.write(data, 0, nRead);
							}
							buffer.flush();
							byte[] imageBytes = buffer.toByteArray();
							is.close();
							String base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);
							String mimeType = getContentResolver().getType(uri);
							if (mimeType == null) mimeType = "image/png";
							String src = "data:" + mimeType + ";base64," + base64Image;

							// Save asset to project directory
							saveAssetToProject(projectName, uri, base64Image, mimeType);

							HashMap<String, Object> newImgWidget = new HashMap<>();
							newImgWidget.put("tag", "img");
							newImgWidget.put("name", "Imported Image");
							newImgWidget.put("color", "#FFC107");
							newImgWidget.put("category", "media");
							HashMap<String, Object> function = new HashMap<>();
							function.put("src", src);
							HashMap<String, Object> style = new HashMap<>();
							style.put("width", "100px");
							style.put("height", "100px");
							function.put("style", style);
							newImgWidget.put("function", function);

							widgets.add(newImgWidget);
							refreshWidgetList();
							Toast.makeText(this, "Image imported!", Toast.LENGTH_SHORT).show();
						} catch (Exception e) {
							Toast.makeText(this, "Failed to import image.", Toast.LENGTH_SHORT).show();
						}
					}
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

							HashMap<String, Object> svgWidget = new HashMap<>();
							svgWidget.put("tag", "svg");
							svgWidget.put("name", "Custom SVG");
							svgWidget.put("color", "#9C27B0");
							svgWidget.put("category", "media");
							HashMap<String, Object> function = new HashMap<>();
							function.put("text", svgContent);
							HashMap<String, Object> style = new HashMap<>();
							style.put("width", "48px");
							style.put("height", "48px");
							function.put("style", style);
							svgWidget.put("function", function);
							widgets.add(svgWidget);
							refreshWidgetList();
							Toast.makeText(this, "SVG imported!", Toast.LENGTH_SHORT).show();
						} catch (Exception e) {
							Toast.makeText(this, "Failed to import SVG.", Toast.LENGTH_SHORT).show();
						}
					}
				}
			}
		);

		// Back button
		btnBack.setOnClickListener(v -> {
			saveProject();
			finish();
		});

		// Undo button
		btnUndo.setOnClickListener(v -> {
			List<Map<String, Object>> state = undoRedoManager.undo();
			if (state != null) {
				restoreState(state);
				refreshHierarchy();
			}
		});

		// Redo button
		btnRedo.setOnClickListener(v -> {
			List<Map<String, Object>> state = undoRedoManager.redo();
			if (state != null) {
				restoreState(state);
				refreshHierarchy();
			}
		});

		// Theme button
		btnTheme.setOnClickListener(v -> showThemeDialog());

		// Export button
		btnExport.setOnClickListener(v -> showExportDialog());

		// Logic block buttons
		btnAddLogicBlock.setOnClickListener(v -> {
			View selected = selector.getSelectedView();
			String targetTag = "body";
			if (selected != null) {
				Object tagObj = selected.getTag();
				if (tagObj instanceof Map) {
					Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
					if (widgetMap.containsKey("tag")) {
						targetTag = widgetMap.get("tag").toString();
					}
				}
			}
			logicBlockManager.showAddBlockDialog(targetTag, block -> {
				Toast.makeText(this, "Block added: " + block.event + " → " + block.action, Toast.LENGTH_SHORT).show();
				refreshLogicBlocksUI();
			});
		});

		btnViewAllBlocks.setOnClickListener(v -> logicBlockManager.showBlocksDialog());

		// Setup top tab layout (View / Event / Assets)
		setupTabLayout();

		// Setup widget category chips
		setupWidgetCategoryChips();

		// Setup bottom chips
		setupBottomChips();

		// Setup search
		setupWidgetSearch();
		setupHierarchySearch();
	}

	private void initializeLogic() {
		// Initialize engines
		engine = new WidgetBuilderEngine(this);
		widgetUpdater = new WidgetUpdater(this, engine);
		codeGenerator = new PageCodeGenerator();
		projectDataManager = new ProjectDataManager(this);
		themeManager = new ThemeManager();
		exportManager = new ExportManager(this, themeManager);
		logicBlockManager = new LogicBlockManager(this);

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
		});
		selector.attachTo(screen);

		// Setup initial spinner
		updateWidgetSpinnerFromTree();

		// Load widgets from JSON asset registry
		widgetRegistry = new WidgetRegistry(this);
		widgets = widgetRegistry.getAllWidgets();
		filteredWidgets = new ArrayList<>(widgets);

		// Auto-load custom widgets from device
		autoLoadCustomConfigs();

		// Use LinearLayoutManager for vertical clean list
		recyclerview1.setAdapter(new Recyclerview1Adapter(filteredWidgets));
		recyclerview1.setLayoutManager(new LinearLayoutManager(this));

		// Setup hierarchy tree
		hierarchyAdapter = new HierarchyTreeAdapter(this);
		hierarchyAdapter.setOnItemClickListener(widgetView -> {
			selector.clearSelection();
			widgetView.performClick();
		});
		hierarchyAdapter.setOnItemLongClickListener(widgetView -> {
			// Start drag for hierarchy reorder
			ClipData.Item item = new ClipData.Item("reorder:" + widgetView.hashCode());
			ClipData dragData = new ClipData("reorder", new String[]{"text/plain"}, item);
			View.DragShadowBuilder shadow = new View.DragShadowBuilder(widgetView);
			widgetView.startDragAndDrop(dragData, shadow, widgetView, 0);
		});
		recyclerviewRightPanel.setAdapter(hierarchyAdapter);
		recyclerviewRightPanel.setLayoutManager(new LinearLayoutManager(this));

		// Drop zone
		dropZoneManager = new DropZoneManager(this, screen, widgets, engine, selector);
		setupCanvasDragListener();

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

		// Setup design list
		buildDesignList();

		// Load project if it exists
		loadProject();

		// Save initial undo state
		saveUndoState();

		// Initial hierarchy refresh
		refreshHierarchy();
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
		if (recyclerview1.getAdapter() != null) {
			recyclerview1.getAdapter().notifyDataSetChanged();
		}
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
					case 0: // View
						showViewMode();
						break;
					case 1: // Event
						showEventMode();
						break;
					case 2: // Assets
						showAssetsMode();
						break;
				}
			}
			@Override
			public void onTabUnselected(TabLayout.Tab tab) {}
			@Override
			public void onTabReselected(TabLayout.Tab tab) {}
		});
	}

	private void showViewMode() {
		LinearLayout scre = findViewById(R.id.scre);
		if (scre != null) scre.setVisibility(View.VISIBLE);
		eventPanel.setVisibility(View.GONE);
		if (assetsPanel != null) assetsPanel.setVisibility(View.GONE);
		bottomPanel.setVisibility(View.VISIBLE);
		View rightPanelCard = findViewById(R.id.rightPanelCard);
		if (rightPanelCard != null) rightPanelCard.setVisibility(View.VISIBLE);
		View leftPanelCard = findViewById(R.id.leftPanelCard);
		if (leftPanelCard != null) leftPanelCard.setVisibility(View.VISIBLE);
	}

	private void showEventMode() {
		LinearLayout scre = findViewById(R.id.scre);
		if (scre != null) scre.setVisibility(View.GONE);
		eventPanel.setVisibility(View.VISIBLE);
		if (assetsPanel != null) assetsPanel.setVisibility(View.GONE);
		bottomPanel.setVisibility(View.GONE);
		View rightPanelCard = findViewById(R.id.rightPanelCard);
		if (rightPanelCard != null) rightPanelCard.setVisibility(View.GONE);
		View leftPanelCard = findViewById(R.id.leftPanelCard);
		if (leftPanelCard != null) leftPanelCard.setVisibility(View.GONE);
		refreshLogicBlocksUI();
	}

	private void showAssetsMode() {
		LinearLayout scre = findViewById(R.id.scre);
		if (scre != null) scre.setVisibility(View.GONE);
		eventPanel.setVisibility(View.GONE);
		if (assetsPanel != null) assetsPanel.setVisibility(View.VISIBLE);
		bottomPanel.setVisibility(View.GONE);
		View rightPanelCard = findViewById(R.id.rightPanelCard);
		if (rightPanelCard != null) rightPanelCard.setVisibility(View.GONE);
		View leftPanelCard = findViewById(R.id.leftPanelCard);
		if (leftPanelCard != null) leftPanelCard.setVisibility(View.GONE);
	}

	// ---- Widget Category Chips ----

	private void setupWidgetCategoryChips() {
		if (chipGroupCategories == null) return;
		chipGroupCategories.setOnCheckedStateChangeListener((group, checkedIds) -> {
			if (checkedIds.isEmpty()) {
				filterWidgetsByCategory("all");
				return;
			}
			int checkedId = checkedIds.get(0);
			if (checkedId == R.id.chipCatAll) filterWidgetsByCategory("all");
			else if (checkedId == R.id.chipCatLayout) filterWidgetsByCategory("layout");
			else if (checkedId == R.id.chipCatBasic) filterWidgetsByCategory("basic");
			else if (checkedId == R.id.chipCatForm) filterWidgetsByCategory("form");
			else if (checkedId == R.id.chipCatMedia) filterWidgetsByCategory("media");
			else filterWidgetsByCategory("all");
		});

		// Also keep drawer tabs working
		setupWidgetCategoryTabs();
	}

	private void setupWidgetCategoryTabs() {
		if (tabWidgetCategories == null) return;
		String[] categories = {"all", "layout", "basic", "form", "media", "advanced"};
		tabWidgetCategories.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			@Override
			public void onTabSelected(TabLayout.Tab tab) {
				int pos = tab.getPosition();
				if (pos >= 0 && pos < categories.length) {
					filterWidgetsByCategory(categories[pos]);
				}
			}
			@Override
			public void onTabUnselected(TabLayout.Tab tab) {}
			@Override
			public void onTabReselected(TabLayout.Tab tab) {}
		});
	}

	private void filterWidgetsByCategory(String category) {
		filteredWidgets.clear();
		if ("all".equals(category)) {
			filteredWidgets.addAll(widgets);
		} else {
			for (HashMap<String, Object> widget : widgets) {
				String cat = widget.containsKey("category") ? widget.get("category").toString() : "basic";
				if (cat.equals(category)) {
					filteredWidgets.add(widget);
				}
			}
		}
		if (recyclerview1.getAdapter() != null) {
			recyclerview1.getAdapter().notifyDataSetChanged();
		}
	}

	private void setupBottomChips() {
		chipBasic.setOnCheckedChangeListener((btn, checked) -> {
			if (checked) {
				chipStyles.setChecked(false);
				chipEvent.setChecked(false);
				buildDesignList();
			}
		});
		chipStyles.setOnCheckedChangeListener((btn, checked) -> {
			if (checked) {
				chipBasic.setChecked(false);
				chipEvent.setChecked(false);
				buildAdvancedDesignList();
			}
		});
		chipEvent.setOnCheckedChangeListener((btn, checked) -> {
			if (checked) {
				chipBasic.setChecked(false);
				chipStyles.setChecked(false);
				buildEventDesignList();
			}
		});
	}

	private void refreshWidgetList() {
		widgets = widgetRegistry.getAllWidgets();
		filterWidgetsByCategory("all");
		filteredWidgets.clear();
		filteredWidgets.addAll(widgets);
		if (recyclerview1.getAdapter() != null) {
			recyclerview1.getAdapter().notifyDataSetChanged();
		}
	}

	private void refreshHierarchy() {
		if (hierarchyAdapter != null) {
			hierarchyAdapter.buildTree(screen);
		}
	}

	// ---- Widget Spinner (shows all tree-based widgets) ----

	private void updateWidgetSpinnerFromTree() {
		List<String> items = new ArrayList<>();
		items.add("body (screen)");
		collectWidgetNames(screen, items, 0);

		// Highlight selected
		int selectedIndex = 0;
		View selectedView = selector.getSelectedView();
		if (selectedView != null) {
			selectedIndex = findViewIndexInTree(screen, selectedView, new int[]{1});
			if (selectedIndex < 0 || selectedIndex >= items.size()) {
				selectedIndex = 0;
			}
		}

		android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
			this,
			android.R.layout.simple_spinner_item,
			items
		);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		widgetSpinner.setAdapter(adapter);
		widgetSpinner.setSelection(selectedIndex);

		// Spinner selection listener to sync with hierarchy
		final int finalSelectedIndex = selectedIndex;
		widgetSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
				if (position == 0) return; // body
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
				String id = tagData.containsKey("id") ? tagData.get("id").toString() : "";
				Map<String, Object> fn = (Map<String, Object>) tagData.get("function");
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
							// Check if it's a reorder drag
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
					highlightDropPosition(screen, event.getY());
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

	private void highlightDropPosition(ViewGroup parent, float dropY) {
		// Visual feedback for drop position
	}

	private void setupWidgetReorderDrag(View widget) {
		widget.setOnLongClickListener(v -> {
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
			ViewGroup parent = (ViewGroup) draggedView.getParent();
			parent.removeView(draggedView);
			int newIndex = findDropIndex(screen, event.getY());
			screen.addView(draggedView, Math.min(newIndex, screen.getChildCount()));
			saveUndoState();
			refreshHierarchy();
			updateWidgetSpinnerFromTree();
		}
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
		PageCodeGenerator codeGen = new PageCodeGenerator();
		String finalHtml = codeGen.generateFullCode(screen, themeManager, logicBlockManager);
		Bundle bundle = new Bundle();
		bundle.putString("finalCode", finalHtml);
		PreviewBottomdialogFragmentActivity fragment = new PreviewBottomdialogFragmentActivity();
		fragment.setArguments(bundle);
		fragment.show(getSupportFragmentManager(), "fragment");
	}

	// ---- View Applied Styles Dialog ----

	private void showViewStylesDialog() {
		View selected = selector.getSelectedView();
		if (selected == null) {
			Toast.makeText(this, "Select a widget first", Toast.LENGTH_SHORT).show();
			return;
		}

		Object tagObj = selected.getTag();
		if (!(tagObj instanceof Map)) return;

		Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
		Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
		Map<String, Object> style = function != null ? (Map<String, Object>) function.get("style") : null;

		View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view_styles, null);
		TextView tvContent = dialogView.findViewById(R.id.tvStyleContent);
		TabLayout tabFormat = dialogView.findViewById(R.id.tabStyleFormat);

		String jsonStr = style != null ? new Gson().toJson(style) : "{}";
		String cssStr = generateCssFromStyle(widgetMap, style);

		// Default show JSON
		tvContent.setText(formatJson(jsonStr));

		tabFormat.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			@Override
			public void onTabSelected(TabLayout.Tab tab) {
				if (tab.getPosition() == 0) {
					tvContent.setText(formatJson(jsonStr));
				} else {
					tvContent.setText(cssStr);
				}
			}
			@Override
			public void onTabUnselected(TabLayout.Tab tab) {}
			@Override
			public void onTabReselected(TabLayout.Tab tab) {}
		});

		new MaterialAlertDialogBuilder(this)
			.setTitle("Applied Styles")
			.setView(dialogView)
			.setPositiveButton("Copy", (dialog, which) -> {
				String content = tvContent.getText().toString();
				ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
				clipboard.setPrimaryClip(ClipData.newPlainText("styles", content));
				Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton("Close", null)
			.show();
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

	// ---- Custom JSON Support ----

	private void autoLoadCustomConfigs() {
		// Auto-load user custom widgets from device path
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

		// Auto-load custom blocks
		String blocksPath = Environment.getExternalStorageDirectory().getAbsolutePath()
			+ "/.dragweb/custom/blocks.json";
		File customBlocksFile = new File(blocksPath);
		if (customBlocksFile.exists()) {
			try {
				String json = FileUtil.readFile(blocksPath);
				if (json != null && !json.isEmpty()) {
					logicBlockManager.fromJson(json);
				}
			} catch (Exception e) {
				Log.w("MainActivity", "Failed to load custom blocks: " + e.getMessage());
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
				Toast.makeText(this, "Custom widgets loaded from " + widgetsPath, Toast.LENGTH_LONG).show();
			} catch (Exception e) {
				Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_SHORT).show();
			}
		} else {
			// Create the directory structure
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
				String json = FileUtil.readFile(blocksPath);
				logicBlockManager.fromJson(json);
				Toast.makeText(this, "Custom blocks loaded from " + blocksPath, Toast.LENGTH_LONG).show();
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

	private void saveAssetToProject(String projectName, android.net.Uri uri, String base64, String mimeType) {
		try {
			// Save to external storage project directory
			String basePath = Environment.getExternalStorageDirectory().getAbsolutePath()
				+ "/.dragweb/projects/" + projectName + "/assets";
			File assetsDir = new File(basePath);
			if (!assetsDir.exists()) assetsDir.mkdirs();

			String fileName = "asset_" + System.currentTimeMillis();
			if (mimeType.contains("png")) fileName += ".png";
			else if (mimeType.contains("svg")) fileName += ".svg";
			else if (mimeType.contains("jpeg") || mimeType.contains("jpg")) fileName += ".jpg";
			else fileName += ".img";

			File assetFile = new File(assetsDir, fileName);
			byte[] decoded = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP);
			java.io.FileOutputStream fos = new java.io.FileOutputStream(assetFile);
			fos.write(decoded);
			fos.close();
		} catch (Exception e) {
			Log.w("MainActivity", "Could not save asset: " + e.getMessage());
		}
	}

	private void addIconLibraryWidget(String libraryName, String prefix) {
		showStyleDialog("Add " + libraryName + " Icon", "Icon name (e.g. " + prefix + "-home)", value -> {
			HashMap<String, Object> iconWidget = new HashMap<>();
			iconWidget.put("tag", "span");
			iconWidget.put("name", libraryName + ": " + value);
			iconWidget.put("color", "#9C27B0");
			iconWidget.put("category", "media");
			HashMap<String, Object> function = new HashMap<>();
			function.put("text", value);
			function.put("class", prefix + " " + value);
			HashMap<String, Object> style = new HashMap<>();
			style.put("fontSize", "24px");
			function.put("style", style);
			iconWidget.put("function", function);
			widgets.add(iconWidget);
			refreshWidgetList();
			Toast.makeText(this, libraryName + " icon added!", Toast.LENGTH_SHORT).show();
		});
	}

	// ---- Logic Blocks UI ----

	private void refreshLogicBlocksUI() {
		LinearLayout blockContainer = findViewById(R.id.blockContainer);
		if (blockContainer == null) return;
		blockContainer.removeAllViews();

		List<LogicBlockManager.LogicBlock> blocks = logicBlockManager.getBlocks();
		for (int i = 0; i < blocks.size(); i++) {
			LogicBlockManager.LogicBlock block = blocks.get(i);
			final int index = i;

			com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
			LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			cardParams.setMargins(0, 4, 0, 4);
			card.setLayoutParams(cardParams);
			card.setCardElevation(2);
			card.setRadius(12);

			LinearLayout blockView = new LinearLayout(this);
			blockView.setOrientation(LinearLayout.VERTICAL);
			blockView.setPadding(16, 12, 16, 12);

			// Event label
			TextView eventLabel = new TextView(this);
			eventLabel.setText("WHEN " + block.event.toUpperCase() + " on <" + block.targetWidget + ">");
			eventLabel.setTextColor(Color.parseColor("#FF9800"));
			eventLabel.setTextSize(12);
			eventLabel.setTypeface(null, android.graphics.Typeface.BOLD);
			blockView.addView(eventLabel);

			// Action label
			TextView actionLabel = new TextView(this);
			actionLabel.setText("DO " + block.action + "(" + block.params + ")");
			actionLabel.setTextColor(Color.parseColor("#4CAF50"));
			actionLabel.setTextSize(12);
			blockView.addView(actionLabel);

			card.addView(blockView);

			card.setOnLongClickListener(v -> {
				new MaterialAlertDialogBuilder(this)
					.setTitle("Delete Block?")
					.setMessage("Remove this logic block?")
					.setPositiveButton("Delete", (d, w) -> {
						logicBlockManager.removeBlock(index);
						refreshLogicBlocksUI();
					})
					.setNegativeButton("Cancel", null)
					.show();
				return true;
			});

			blockContainer.addView(card);
		}
	}

	// ---- Project Save/Load ----

	private void saveProject() {
		// Save to internal storage
		projectDataManager.saveProject(screen, projectName);

		File dir = new File(getFilesDir(), "projects");
		File themeFile = new File(dir, projectName + ".theme");
		FileUtil.writeFile(themeFile.getAbsolutePath(), themeManager.toJson());

		File logicFile = new File(dir, projectName + ".logic");
		FileUtil.writeFile(logicFile.getAbsolutePath(), logicBlockManager.toJson());

		// Also save to external storage for persistence across reinstalls
		saveProjectToExternal();

		Toast.makeText(this, "Project saved", Toast.LENGTH_SHORT).show();
	}

	private void saveProjectToExternal() {
		try {
			String basePath = Environment.getExternalStorageDirectory().getAbsolutePath()
				+ "/.dragweb/projects/" + projectName;
			File extDir = new File(basePath);
			if (!extDir.exists()) extDir.mkdirs();

			// Save layout JSON
			File internalDir = new File(getFilesDir(), "projects");
			File layoutFile = new File(internalDir, projectName + ".json");
			if (layoutFile.exists()) {
				String json = FileUtil.readFile(layoutFile.getAbsolutePath());
				FileUtil.writeFile(new File(extDir, "layout.json").getAbsolutePath(), json);
			}

			// Save theme
			FileUtil.writeFile(new File(extDir, "theme.json").getAbsolutePath(), themeManager.toJson());

			// Save logic
			FileUtil.writeFile(new File(extDir, "logic.json").getAbsolutePath(), logicBlockManager.toJson());

			// Save styles
			File stylesFile = new File(internalDir, projectName + ".theme");
			if (stylesFile.exists()) {
				String styles = FileUtil.readFile(stylesFile.getAbsolutePath());
				FileUtil.writeFile(new File(extDir, "styles.json").getAbsolutePath(), styles);
			}
		} catch (Exception e) {
			Log.w("MainActivity", "Could not save to external: " + e.getMessage());
		}
	}

	private void loadProject() {
		projectDataManager.loadProject(screen, projectName, engine, selector, dropZoneManager);

		for (int i = 0; i < screen.getChildCount(); i++) {
			setupWidgetReorderDrag(screen.getChildAt(i));
		}

		File dir = new File(getFilesDir(), "projects");
		File themeFile = new File(dir, projectName + ".theme");
		if (themeFile.exists()) {
			String themeJson = FileUtil.readFile(themeFile.getAbsolutePath());
			themeManager.fromJson(themeJson);
		}

		File logicFile = new File(dir, projectName + ".logic");
		if (logicFile.exists()) {
			String logicJson = FileUtil.readFile(logicFile.getAbsolutePath());
			logicBlockManager.fromJson(logicJson);
		}
	}

	private void saveUndoState() {
		undoRedoManager.saveState(screen);
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
		TextInputEditText etFont = dialogView.findViewById(R.id.etFontFamily);
		TextInputEditText etBackground = dialogView.findViewById(R.id.etBodyBackground);

		etPrimary.setText(themeManager.getGlobalStyle("primaryColor"));
		etFont.setText(themeManager.getGlobalStyle("fontFamily"));
		etBackground.setText(themeManager.getGlobalStyle("bodyBackground"));

		if (ThemeManager.THEME_DARK.equals(themeManager.getCurrentTheme())) {
			btnDark.performClick();
		} else {
			btnLight.performClick();
		}

		btnLight.setOnClickListener(v -> themeManager.setTheme(ThemeManager.THEME_LIGHT));
		btnDark.setOnClickListener(v -> themeManager.setTheme(ThemeManager.THEME_DARK));

		new MaterialAlertDialogBuilder(this)
			.setTitle("Theme Settings")
			.setView(dialogView)
			.setPositiveButton("Apply", (dialog, which) -> {
				String primary = etPrimary.getText().toString().trim();
				String font = etFont.getText().toString().trim();
				String bg = etBackground.getText().toString().trim();
				if (!primary.isEmpty()) themeManager.setGlobalStyle("primaryColor", primary);
				if (!font.isEmpty()) themeManager.setGlobalStyle("fontFamily", font);
				if (!bg.isEmpty()) themeManager.setGlobalStyle("bodyBackground", bg);
				Toast.makeText(this, "Theme updated", Toast.LENGTH_SHORT).show();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	// ---- Export Dialog ----

	private void showExportDialog() {
		View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_export, null);

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
		builder.setTitle("Export Project");
		builder.setView(dialogView);
		builder.setNegativeButton("Close", null);

		androidx.appcompat.app.AlertDialog dialog = builder.create();

		dialogView.findViewById(R.id.cardExportHtml).setOnClickListener(v -> {
			ExportManager.ExportResult result = exportManager.generateExportFiles(screen, projectName, logicBlockManager);
			if (result.success) {
				Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
			}
			dialog.dismiss();
		});

		dialogView.findViewById(R.id.cardExportZip).setOnClickListener(v -> {
			try {
				File zipFile = exportManager.exportAsZip(screen, projectName, logicBlockManager);
				Toast.makeText(this, "ZIP exported: " + zipFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
			} catch (Exception e) {
				Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
			}
			dialog.dismiss();
		});

		dialogView.findViewById(R.id.cardExportPreview).setOnClickListener(v -> {
			PageCodeGenerator gen = new PageCodeGenerator();
			String html = gen.generateFullCode(screen, themeManager, logicBlockManager);
			ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
			clipboard.setPrimaryClip(ClipData.newPlainText("html", html));
			Toast.makeText(this, "HTML copied to clipboard", Toast.LENGTH_SHORT).show();
			dialog.dismiss();
		});

		dialog.show();
	}

	// ---- Design Property List ----

	private void buildDesignList() {
		design.clear();
		View selected = selector.getSelectedView();
		String tag = getSelectedTag();

		// Context-aware styles based on widget type
		List<String> items = new ArrayList<>();
		items.add("Edittext");
		items.add("Color");
		items.add("Background");
		items.add("Width");
		items.add("Height");
		items.add("Padding");
		items.add("Margin");

		if ("img".equals(tag)) {
			items.add(0, "ImageSrc");
		}
		if ("p".equals(tag) || "h1".equals(tag) || "h2".equals(tag) || "h3".equals(tag)
			|| "span".equals(tag) || "label".equals(tag) || "a".equals(tag) || "button".equals(tag)) {
			items.add("TextSize");
			items.add("Font");
			items.add("TextAlign");
		}
		items.add("BorderRadius");
		items.add("BorderWidth");
		items.add("BorderColor");

		for (String item : items) {
			HashMap<String, Object> map = new HashMap<>();
			map.put("edit", item);
			design.add(map);
		}
		recyclerview3.setAdapter(new Recyclerview3Adapter(design));
		recyclerview3.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
	}

	private void buildAdvancedDesignList() {
		design.clear();
		String[] items = {
			"Elevation", "Gravity", "Opacity", "Rotation",
			"FlexDir", "JustifyContent", "AlignItems", "Gap",
			"Display", "Position", "Overflow", "BoxShadow",
			"TextDecor", "LineHeight", "LetterSpace", "ZIndex",
			"BorderTop", "BorderRight", "BorderBottom", "BorderLeft",
			"RadiusTL", "RadiusTR", "RadiusBL", "RadiusBR",
			"Gradient", "CssVar"
		};
		for (String item : items) {
			HashMap<String, Object> map = new HashMap<>();
			map.put("edit", item);
			design.add(map);
		}
		recyclerview3.setAdapter(new Recyclerview3Adapter(design));
		recyclerview3.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
	}

	private void buildEventDesignList() {
		design.clear();
		String[] items = {
			"OnClick", "OnHover", "OnInput", "OnLoad",
			"Animate", "Navigate", "ShowHide", "SetText"
		};
		for (String item : items) {
			HashMap<String, Object> map = new HashMap<>();
			map.put("edit", item);
			design.add(map);
		}
		recyclerview3.setAdapter(new Recyclerview3Adapter(design));
		recyclerview3.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
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

	public void handleDesignItemClick(int position) {
		View selected = selector.getSelectedView();
		if (selected == null) {
			Toast.makeText(this, "Select a widget first", Toast.LENGTH_SHORT).show();
			return;
		}

		String editType = design.get(position).get("edit").toString();

		// Get target tag for logic blocks
		String targetTag = getSelectedTag();
		final String fTargetTag = targetTag;

		switch (editType) {
			case "Edittext":
				showStyleDialog("Set New Value", "New Value", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("text", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "ImageSrc":
				showStyleDialog("Set Image Source", "URL or base64 data", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("src", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "TextSize":
				showStyleDialogWithUnits("Change Text Size", "16", "fontSize", selected);
				break;
			case "Color":
				showColorPickerDialog("Set Text Color", "color", selected);
				break;
			case "Font":
				showChoiceDialog("Font Weight", new String[]{"normal", "bold", "lighter", "100", "200", "300", "400", "500", "600", "700", "800", "900"}, value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("fontWeight", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "TextAlign":
				showChoiceDialog("Text Align", new String[]{"left", "center", "right", "justify"}, value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("textAlign", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "Background":
				showColorPickerDialog("Set Background Color", "backgroundColor", selected);
				break;
			case "BorderRadius":
				showStyleDialogWithUnits("Set Border Radius", "12", "borderRadius", selected);
				break;
			case "BorderWidth":
				showStyleDialogWithUnits("Set Border Width", "2", "borderWidth", selected);
				break;
			case "BorderColor":
				showColorPickerDialog("Set Border Color", "borderColor", selected);
				break;
			case "Padding":
				showStyleDialogWithUnits("Set Padding", "12", "padding", selected);
				break;
			case "Margin":
				showStyleDialogWithUnits("Set Margin", "12", "margin", selected);
				break;
			case "Width":
				showStyleDialogWithUnits("Set Width", "100", "width", selected);
				break;
			case "Height":
				showStyleDialogWithUnits("Set Height", "100", "height", selected);
				break;
			// Advanced styles
			case "Elevation":
				showStyleDialog("Set Elevation", "4", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("elevation", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "Gravity":
				showGravityDialog(selected);
				break;
			case "Opacity":
				showStyleDialog("Set Opacity", "0.0 - 1.0", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("opacity", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "Rotation":
				showStyleDialog("Set Rotation", "0 - 360", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("transform", "rotate(" + value + "deg)");
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "FlexDir":
				showChoiceDialog("Flex Direction", new String[]{"row", "column", "row-reverse", "column-reverse"}, value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("flexDirection", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "JustifyContent":
				showChoiceDialog("Justify Content", new String[]{"flex-start", "center", "flex-end", "space-between", "space-around", "space-evenly"}, value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("justifyContent", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "AlignItems":
				showChoiceDialog("Align Items", new String[]{"flex-start", "center", "flex-end", "stretch", "baseline"}, value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("alignItems", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "Gap":
				showStyleDialogWithUnits("Set Gap", "8", "gap", selected);
				break;
			case "Display":
				showChoiceDialog("Display", new String[]{"block", "flex", "grid", "inline", "inline-block", "none"}, value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("display", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "Position":
				showChoiceDialog("Position", new String[]{"static", "relative", "absolute", "fixed", "sticky"}, value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("position", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "Overflow":
				showChoiceDialog("Overflow", new String[]{"visible", "hidden", "scroll", "auto"}, value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("overflow", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "BoxShadow":
				showStyleDialog("Set Box Shadow", "0 2px 8px rgba(0,0,0,0.1)", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("boxShadow", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "TextDecor":
				showChoiceDialog("Text Decoration", new String[]{"none", "underline", "line-through", "overline"}, value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("textDecoration", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "LineHeight":
				showStyleDialog("Set Line Height", "1.6", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("lineHeight", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "LetterSpace":
				showStyleDialogWithUnits("Set Letter Spacing", "1", "letterSpacing", selected);
				break;
			case "ZIndex":
				showStyleDialog("Set Z-Index", "10", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("zIndex", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			// Per-side borders
			case "BorderTop":
				showStyleDialog("Border Top", "2px solid #000", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("borderTop", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "BorderRight":
				showStyleDialog("Border Right", "2px solid #000", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("borderRight", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "BorderBottom":
				showStyleDialog("Border Bottom", "2px solid #000", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("borderBottom", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "BorderLeft":
				showStyleDialog("Border Left", "2px solid #000", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("borderLeft", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			// Per-corner radius
			case "RadiusTL":
				showStyleDialogWithUnits("Top-Left Radius", "8", "borderTopLeftRadius", selected);
				break;
			case "RadiusTR":
				showStyleDialogWithUnits("Top-Right Radius", "8", "borderTopRightRadius", selected);
				break;
			case "RadiusBL":
				showStyleDialogWithUnits("Bottom-Left Radius", "8", "borderBottomLeftRadius", selected);
				break;
			case "RadiusBR":
				showStyleDialogWithUnits("Bottom-Right Radius", "8", "borderBottomRightRadius", selected);
				break;
			// Gradient
			case "Gradient":
				showStyleDialog("Set Gradient", "linear-gradient(135deg, #667eea 0%, #764ba2 100%)", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("background", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			// CSS Variable
			case "CssVar":
				showCssVariableDialog(selected);
				break;
			// Event items
			case "OnClick":
			case "OnHover":
			case "OnInput":
			case "OnLoad":
				logicBlockManager.showAddBlockDialog(fTargetTag, block -> {
					Toast.makeText(this, block.event + " event added", Toast.LENGTH_SHORT).show();
					refreshLogicBlocksUI();
				});
				break;
			case "Animate":
			case "Navigate":
			case "ShowHide":
			case "SetText":
				logicBlockManager.showAddBlockDialog(fTargetTag, block -> {
					Toast.makeText(this, "Action added", Toast.LENGTH_SHORT).show();
					refreshLogicBlocksUI();
				});
				break;
		}
	}

	// ---- Style Dialog with Unit Chips (px / rem / %) ----

	private void showStyleDialogWithUnits(String title, String defaultValue, String cssProperty, View targetView) {
		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(48, 24, 48, 0);

		TextInputLayout inputLayout = new TextInputLayout(this, null,
			com.google.android.material.R.attr.textInputOutlinedStyle);
		inputLayout.setHint("Value");
		TextInputEditText inputField = new TextInputEditText(inputLayout.getContext());
		inputField.setText(defaultValue);
		inputField.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
		inputLayout.addView(inputField);
		layout.addView(inputLayout);

		// Unit chips
		LinearLayout chipRow = new LinearLayout(this);
		chipRow.setOrientation(LinearLayout.HORIZONTAL);
		chipRow.setPadding(0, 8, 0, 8);

		final String[] selectedUnit = {"px"};

		String[] units = {"px", "rem", "%", "em", "vh", "vw", "auto"};
		for (String unit : units) {
			Chip chip = new Chip(this);
			chip.setText(unit);
			chip.setTextSize(11);
			chip.setCheckable(true);
			chip.setChecked("px".equals(unit));
			chip.setOnClickListener(v -> {
				selectedUnit[0] = unit;
				// Uncheck all others
				for (int i = 0; i < chipRow.getChildCount(); i++) {
					View child = chipRow.getChildAt(i);
					if (child instanceof Chip) {
						((Chip) child).setChecked(child == chip);
					}
				}
			});
			chipRow.addView(chip);
		}
		layout.addView(chipRow);

		new MaterialAlertDialogBuilder(this)
			.setTitle(title)
			.setView(layout)
			.setPositiveButton("Apply", (dialog, which) -> {
				String value = inputField.getText().toString().trim();
				if (value.isEmpty()) return;
				String finalValue = "auto".equals(selectedUnit[0]) ? "auto" : value + selectedUnit[0];
				Map<String, Object> style = new HashMap<>();
				style.put(cssProperty, finalValue);
				widgetUpdater.updateWidget(targetView, "", style);
				saveUndoState();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	// ---- Color Picker Dialog ----

	private void showColorPickerDialog(String title, String cssProperty, View targetView) {
		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(48, 24, 48, 0);

		TextInputLayout inputLayout = new TextInputLayout(this, null,
			com.google.android.material.R.attr.textInputOutlinedStyle);
		inputLayout.setHint("Color value (#hex, rgb, var())");
		TextInputEditText inputField = new TextInputEditText(inputLayout.getContext());
		inputField.setText("#333333");
		inputLayout.addView(inputField);
		layout.addView(inputLayout);

		// Preset color grid
		LinearLayout colorRow1 = new LinearLayout(this);
		colorRow1.setOrientation(LinearLayout.HORIZONTAL);
		colorRow1.setPadding(0, 12, 0, 4);
		LinearLayout colorRow2 = new LinearLayout(this);
		colorRow2.setOrientation(LinearLayout.HORIZONTAL);
		colorRow2.setPadding(0, 4, 0, 12);

		String[] presetColors1 = {"#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3"};
		String[] presetColors2 = {"#4CAF50", "#FF9800", "#795548", "#607D8B", "#000000", "#FFFFFF"};

		for (String color : presetColors1) {
			addColorSwatch(colorRow1, color, inputField);
		}
		for (String color : presetColors2) {
			addColorSwatch(colorRow2, color, inputField);
		}
		layout.addView(colorRow1);
		layout.addView(colorRow2);

		// CSS variable support
		TextView cssVarHint = new TextView(this);
		cssVarHint.setText("CSS Variables: var(--primary), var(--accent)");
		cssVarHint.setTextSize(11);
		cssVarHint.setPadding(0, 0, 0, 12);
		layout.addView(cssVarHint);

		new MaterialAlertDialogBuilder(this)
			.setTitle(title)
			.setView(layout)
			.setPositiveButton("Apply", (dialog, which) -> {
				String value = inputField.getText().toString().trim();
				if (value.isEmpty()) return;
				Map<String, Object> style = new HashMap<>();
				style.put(cssProperty, value);
				widgetUpdater.updateWidget(targetView, value, style);
				saveUndoState();
			})
			.setNegativeButton("Cancel", null)
			.show();
	}

	private void addColorSwatch(LinearLayout row, String color, TextInputEditText inputField) {
		View swatch = new View(this);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(48, 48);
		params.setMargins(4, 4, 4, 4);
		swatch.setLayoutParams(params);
		try {
			swatch.setBackgroundColor(Color.parseColor(color));
		} catch (Exception e) {
			swatch.setBackgroundColor(Color.GRAY);
		}
		swatch.setOnClickListener(v -> inputField.setText(color));
		row.addView(swatch);
	}

	// ---- CSS Variable Dialog ----

	private void showCssVariableDialog(View targetView) {
		String[] cssVars = {
			"var(--primary-color)", "var(--secondary-color)", "var(--accent-color)",
			"var(--body-background)", "var(--body-color)", "var(--link-color)",
			"var(--border-color)", "var(--card-background)", "var(--font-family)"
		};

		new MaterialAlertDialogBuilder(this)
			.setTitle("Apply CSS Variable")
			.setItems(cssVars, (dialog, which) -> {
				String varName = cssVars[which];
				// Ask which property to apply it to
				String[] properties = {"color", "backgroundColor", "borderColor", "fontFamily"};
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

	private void showGravityDialog(View selected) {
		String[] options = {"Left", "Center", "Right", "Top", "Bottom"};
		new MaterialAlertDialogBuilder(this)
			.setTitle("Set Gravity")
			.setItems(options, (dialog, which) -> {
				Map<String, Object> style = new HashMap<>();
				style.put("textAlign", options[which].toLowerCase());
				widgetUpdater.updateWidget(selected, "", style);
				saveUndoState();
			})
			.show();
	}

	private void showChoiceDialog(String title, String[] options, OnStyleConfirmed callback) {
		new MaterialAlertDialogBuilder(this)
			.setTitle(title)
			.setItems(options, (dialog, which) -> {
				callback.onConfirmed(options[which]);
			})
			.show();
	}

	private void showStyleDialog(String title, String hint, OnStyleConfirmed callback) {
		View alertLayout = getLayoutInflater().inflate(R.layout.dial, null);
		MaterialAlertDialogBuilder m = new MaterialAlertDialogBuilder(this);
		m.setView(alertLayout);

		TextInputLayout inputLayout = alertLayout.findViewById(R.id.UserNameEditText);
		TextInputEditText inputField = alertLayout.findViewById(R.id.UserNameValue);
		inputLayout.setHint(hint);
		m.setTitle(title);

		m.setPositiveButton("Apply", (dialog, which) -> {
			String value = inputField.getText().toString().trim();
			if (value.isEmpty()) {
				Toast.makeText(this, "Please enter a value.", Toast.LENGTH_SHORT).show();
				return;
			}
			callback.onConfirmed(value);
		});

		m.setNegativeButton("Cancel", null);
		m.setCancelable(true);
		m.show();
	}

	interface OnStyleConfirmed {
		void onConfirmed(String value);
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
			textview1.setText(editType);

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
			case "ImageSrc": return R.drawable.default_image;
			case "TextSize": return R.drawable.textsize;
			case "TextAlign": return R.drawable.focus_centered;
			case "Color": return R.drawable.textcolor;
			case "Font": return R.drawable.alphabet_latin;
			case "Background": return R.drawable.background;
			case "BorderRadius": return R.drawable.border_radius;
			case "BorderWidth": return R.drawable.border_style;
			case "BorderColor": return R.drawable.freezerowcolumn;
			case "Padding": return R.drawable.box_padding;
			case "Margin": return R.drawable.box_margin;
			case "Elevation": return R.drawable.emphasis;
			case "Gravity": return R.drawable.focus_centered;
			case "Opacity": return R.drawable.droplet;
			case "Rotation": return R.drawable.rotate;
			case "Width":
			case "Height": return R.drawable.border_sides;
			case "FlexDir":
			case "JustifyContent":
			case "AlignItems":
			case "Gap": return R.drawable.freezerowcolumn;
			case "Display":
			case "Position": return R.drawable.box_padding;
			case "Overflow": return R.drawable.resize;
			case "BoxShadow": return R.drawable.emphasis;
			case "TextDecor": return R.drawable.cursor_text;
			case "LineHeight":
			case "LetterSpace": return R.drawable.textsize;
			case "ZIndex": return R.drawable.emphasis;
			case "BorderTop":
			case "BorderRight":
			case "BorderBottom":
			case "BorderLeft": return R.drawable.border_style;
			case "RadiusTL":
			case "RadiusTR":
			case "RadiusBL":
			case "RadiusBR": return R.drawable.border_radius;
			case "Gradient": return R.drawable.background;
			case "CssVar": return R.drawable.icon_design_services_round;
			case "OnClick":
			case "OnHover":
			case "OnInput":
			case "OnLoad": return R.drawable.icon_build_round;
			case "Animate":
			case "Navigate":
			case "ShowHide":
			case "SetText": return R.drawable.icon_design_services_round;
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
			LinearLayout linear1 = view.findViewById(R.id.linear1);
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

			view.setOnTouchListener((v, event) -> {
				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					ClipData.Item itemData = new ClipData.Item(String.valueOf(position));
					ClipData dragData = new ClipData("widget", new String[]{"text/plain"}, itemData);
					View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
					v.startDragAndDrop(dragData, shadowBuilder, v, 0);
					return true;
				}
				return false;
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
