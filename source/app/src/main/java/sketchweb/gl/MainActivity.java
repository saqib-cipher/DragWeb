package sketchweb.gl;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
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
	private LinearLayout bottomPanel;
	private LinearLayout eventPanel;
	private NestedScrollView vscroll2;
	private Button button5, button4, delete, btnImportWidgets, btnImportImage;
	private Button btnDrawer, btnBack, btnUndo, btnRedo, btnTheme, btnExport, btnViewStyles;
	private Button btnAddLogicBlock, btnViewAllBlocks;
	private TextView textview2, tvProjectTitle;
	private RecyclerView recyclerview3, recyclerview1, recyclerviewRightPanel;
	private android.widget.Spinner widgetSpinner;
	private TabLayout tabLayout, tabWidgetCategories;
	private Chip chipBasic, chipStyles, chipEvent;

	private ActivityResultLauncher<android.content.Intent> importWidgetLauncher;
	private ActivityResultLauncher<android.content.Intent> importImageLauncher;

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
		bottomPanel = findViewById(R.id.bottomPanel);
		eventPanel = findViewById(R.id.eventPanel);
		vscroll2 = findViewById(R.id.vscroll2);
		button5 = findViewById(R.id.button5);
		button4 = findViewById(R.id.button4);
		delete = findViewById(R.id.delete);
		btnImportWidgets = findViewById(R.id.btnImportWidgets);
		btnImportImage = findViewById(R.id.btnImportImage);
		textview2 = findViewById(R.id.textview2);
		recyclerview3 = findViewById(R.id.recyclerview3);
		recyclerview1 = findViewById(R.id.recyclerview1);
		recyclerviewRightPanel = findViewById(R.id.recyclerviewRightPanel);
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
		tvProjectTitle = findViewById(R.id.tvProjectTitle);
		tabLayout = findViewById(R.id.tabLayout);
		tabWidgetCategories = findViewById(R.id.tabWidgetCategories);
		chipBasic = findViewById(R.id.chipBasic);
		chipStyles = findViewById(R.id.chipStyles);
		chipEvent = findViewById(R.id.chipEvent);

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
			});
		});

		btnViewAllBlocks.setOnClickListener(v -> logicBlockManager.showBlocksDialog());

		// Setup top tab layout (View / Event / Component)
		setupTabLayout();

		// Setup widget category tabs
		setupWidgetCategoryTabs();

		// Setup bottom chips
		setupBottomChips();
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
			updateWidgetSpinner(widgetId);
			if (hierarchyAdapter != null) {
				hierarchyAdapter.setSelectedView(selector.getSelectedView());
			}
		});
		selector.attachTo(screen);

		// Setup initial empty spinner
		updateWidgetSpinner(null);

		// Load widgets from JSON asset registry
		widgetRegistry = new WidgetRegistry(this);
		widgets = widgetRegistry.getAllWidgets();
		filteredWidgets = new ArrayList<>(widgets);

		recyclerview1.setAdapter(new Recyclerview1Adapter(filteredWidgets));
		recyclerview1.setLayoutManager(new GridLayoutManager(this, 4));

		// Setup hierarchy tree
		hierarchyAdapter = new HierarchyTreeAdapter(this);
		hierarchyAdapter.setOnItemClickListener(widgetView -> {
			selector.clearSelection();
			widgetView.performClick();
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
					case 2: // Component
						showComponentMode();
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
		bottomPanel.setVisibility(View.VISIBLE);
		rightPanel.setVisibility(View.VISIBLE);
	}

	private void showEventMode() {
		LinearLayout scre = findViewById(R.id.scre);
		if (scre != null) scre.setVisibility(View.GONE);
		eventPanel.setVisibility(View.VISIBLE);
		bottomPanel.setVisibility(View.GONE);
		rightPanel.setVisibility(View.GONE);
	}

	private void showComponentMode() {
		LinearLayout scre = findViewById(R.id.scre);
		if (scre != null) scre.setVisibility(View.VISIBLE);
		eventPanel.setVisibility(View.GONE);
		bottomPanel.setVisibility(View.VISIBLE);
		rightPanel.setVisibility(View.VISIBLE);
	}

	private void setupWidgetCategoryTabs() {
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
		if (tabWidgetCategories.getSelectedTabPosition() == 0) {
			filteredWidgets.clear();
			filteredWidgets.addAll(widgets);
		}
		if (recyclerview1.getAdapter() != null) {
			recyclerview1.getAdapter().notifyDataSetChanged();
		}
	}

	private void refreshHierarchy() {
		if (hierarchyAdapter != null) {
			hierarchyAdapter.buildTree(screen);
		}
	}

	private void updateWidgetSpinner(String widgetId) {
		List<String> items = new ArrayList<>();
		if (widgetId == null) {
			items.add("No selection");
		} else {
			items.add(widgetId);
			View selectedView = selector.getSelectedView();
			if (selectedView != null) {
				View parent = (View) selectedView.getParent();
				while (parent != null && parent != screen && parent.getId() != R.id.scre) {
					Object tagDataObj = parent.getTag();
					if (tagDataObj instanceof Map) {
						Map<String, Object> tagData = (Map<String, Object>) tagDataObj;
						if (tagData.containsKey("id")) {
							items.add(0, (String) tagData.get("id"));
						} else if (tagData.containsKey("tag")) {
							items.add(0, "<" + tagData.get("tag") + ">");
						}
					}
					if (parent.getParent() instanceof View) {
						parent = (View) parent.getParent();
					} else {
						break;
					}
				}
				items.add(0, "body (screen)");
			}
		}

		android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
			this,
			android.R.layout.simple_spinner_item,
			items
		);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		widgetSpinner.setAdapter(adapter);
		widgetSpinner.setSelection(items.size() - 1);
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
		// Visual feedback for drop position (minimal for performance)
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

	// ---- Responsive Design Tester ----

	private void showResponsiveTestDialog() {
		View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_responsive_test, null);
		WebView webView = dialogView.findViewById(R.id.webViewResponsive);
		Chip chipMobile = dialogView.findViewById(R.id.chipMobile);
		Chip chipTablet = dialogView.findViewById(R.id.chipTablet);
		Chip chipDesktop = dialogView.findViewById(R.id.chipDesktop);

		WebSettings settings = webView.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setUseWideViewPort(true);
		settings.setLoadWithOverviewMode(true);

		String html = new PageCodeGenerator().generateFullCode(screen, themeManager, logicBlockManager);

		// Default mobile
		loadResponsivePreview(webView, html, 375);

		chipMobile.setOnCheckedChangeListener((btn, checked) -> {
			if (checked) loadResponsivePreview(webView, html, 375);
		});
		chipTablet.setOnCheckedChangeListener((btn, checked) -> {
			if (checked) loadResponsivePreview(webView, html, 768);
		});
		chipDesktop.setOnCheckedChangeListener((btn, checked) -> {
			if (checked) loadResponsivePreview(webView, html, 1024);
		});

		new MaterialAlertDialogBuilder(this)
			.setView(dialogView)
			.setPositiveButton("Close", null)
			.show();
	}

	private void loadResponsivePreview(WebView webView, String html, int widthPx) {
		String wrapped = html.replace("<meta name=\"viewport\"",
			"<meta name=\"viewport\" data-width=\"" + widthPx + "\"");
		webView.loadDataWithBaseURL(null, wrapped, "text/html", "UTF-8", null);
	}

	// ---- Project Save/Load ----

	private void saveProject() {
		projectDataManager.saveProject(screen, projectName);

		File dir = new File(getFilesDir(), "projects");
		File themeFile = new File(dir, projectName + ".theme");
		FileUtil.writeFile(themeFile.getAbsolutePath(), themeManager.toJson());

		File logicFile = new File(dir, projectName + ".logic");
		FileUtil.writeFile(logicFile.getAbsolutePath(), logicBlockManager.toJson());

		Toast.makeText(this, "Project saved", Toast.LENGTH_SHORT).show();
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

		String[] extraOptions = {"Responsive Preview"};
		builder.setNeutralButton("Responsive", (dialog, which) -> {
			showResponsiveTestDialog();
		});

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
		String[] items = {
			"Edittext", "Color", "TextSize", "Font", "Background",
			"BorderRadius", "BorderWidth", "BorderColor",
			"Padding", "Margin", "Width", "Height"
		};
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
			"TextDecor", "LineHeight", "LetterSpace", "ZIndex"
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

	public void handleDesignItemClick(int position) {
		View selected = selector.getSelectedView();
		if (selected == null) {
			Toast.makeText(this, "Select a widget first", Toast.LENGTH_SHORT).show();
			return;
		}

		String editType = design.get(position).get("edit").toString();

		// Get target tag for logic blocks
		String targetTag = "div";
		Object tagObj = selected.getTag();
		if (tagObj instanceof Map) {
			Map<String, Object> wm = (Map<String, Object>) tagObj;
			if (wm.containsKey("tag")) targetTag = wm.get("tag").toString();
		}
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
			case "TextSize":
				showStyleDialog("Change Text Size", "Text size (px)", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("fontSize", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "Color":
				showStyleDialog("Set Color", "#333333", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("color", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "Font":
				showStyleDialog("Set Font Weight", "bold, italic, normal", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("fontWeight", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "Background":
				showStyleDialog("Set Background Color", "#FFFFFF", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("backgroundColor", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "BorderRadius":
				showStyleDialog("Set Border Radius", "12px", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("borderRadius", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "BorderWidth":
				showStyleDialog("Set Border Width", "2px", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("borderWidth", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "BorderColor":
				showStyleDialog("Set Border Color", "#000000", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("borderColor", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "Padding":
				showStyleDialog("Set Padding", "12px", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("padding", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "Margin":
				showStyleDialog("Set Margin", "12px", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("margin", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "Width":
				showStyleDialog("Set Width", "100px, 100%, auto", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("width", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "Height":
				showStyleDialog("Set Height", "100px, auto", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("height", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
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
				showStyleDialog("Set Gap", "8px", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("gap", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
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
				showStyleDialog("Set Letter Spacing", "1px", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("letterSpacing", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			case "ZIndex":
				showStyleDialog("Set Z-Index", "10", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("zIndex", value);
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				});
				break;
			// Event items
			case "OnClick":
				logicBlockManager.showAddBlockDialog(fTargetTag, block -> {
					Toast.makeText(this, "Click event added", Toast.LENGTH_SHORT).show();
				});
				break;
			case "OnHover":
				logicBlockManager.showAddBlockDialog(fTargetTag, block -> {
					Toast.makeText(this, "Hover event added", Toast.LENGTH_SHORT).show();
				});
				break;
			case "OnInput":
				logicBlockManager.showAddBlockDialog(fTargetTag, block -> {
					Toast.makeText(this, "Input event added", Toast.LENGTH_SHORT).show();
				});
				break;
			case "OnLoad":
				logicBlockManager.showAddBlockDialog(fTargetTag, block -> {
					Toast.makeText(this, "Load event added", Toast.LENGTH_SHORT).show();
				});
				break;
			case "Animate":
			case "Navigate":
			case "ShowHide":
			case "SetText":
				logicBlockManager.showAddBlockDialog(fTargetTag, block -> {
					Toast.makeText(this, "Action added", Toast.LENGTH_SHORT).show();
				});
				break;
		}
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
			lp.setMargins(4, 0, 4, 0);
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
			case "TextSize": return R.drawable.textsize;
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
			View v = getLayoutInflater().inflate(R.layout.widgets, null);
			RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			v.setLayoutParams(lp);
			return new ViewHolder(v);
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, final int position) {
			View view = holder.itemView;
			LinearLayout linear1 = view.findViewById(R.id.linear1);
			TextView textview1 = view.findViewById(R.id.textview1);

			Map<String, Object> item = data.get(position);
			String name = item.get("name").toString();
			String tag = item.get("tag").toString();
			String color = item.get("color").toString();

			textview1.setText(name + "\n<" + tag + ">");
			textview1.setTextSize(10);
			try {
				linear1.setBackgroundColor(Color.parseColor(color));
			} catch (Exception e) {
				linear1.setBackgroundColor(Color.LTGRAY);
			}

			linear1.setOnTouchListener((v, event) -> {
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
