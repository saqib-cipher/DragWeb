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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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

	private ArrayList<HashMap<String, Object>> widgets = new ArrayList<>();
	private ArrayList<HashMap<String, Object>> design = new ArrayList<>();

	private String projectName = "Untitled";

	// Views
	private LinearLayout main;
	private LinearLayout topBar;
	private LinearLayout screen;
	private NestedScrollView vscroll2;
	private Button button5, button4, delete, btnImportWidgets;
	private Button btnBack, btnUndo, btnRedo, btnTheme, btnExport;
	private TextView textview2, tvProjectTitle;
	private RecyclerView recyclerview3, recyclerview1, recyclerviewRightPanel;
	private android.widget.Spinner widgetSpinner;

	private ActivityResultLauncher<android.content.Intent> importWidgetLauncher;

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
		vscroll2 = findViewById(R.id.vscroll2);
		button5 = findViewById(R.id.button5);
		button4 = findViewById(R.id.button4);
		delete = findViewById(R.id.delete);
		btnImportWidgets = findViewById(R.id.btnImportWidgets);
		textview2 = findViewById(R.id.textview2);
		recyclerview3 = findViewById(R.id.recyclerview3);
		recyclerview1 = findViewById(R.id.recyclerview1);
		recyclerviewRightPanel = findViewById(R.id.recyclerviewRightPanel);
		widgetSpinner = findViewById(R.id.widgetSpinner);
		btnBack = findViewById(R.id.btnBack);
		btnUndo = findViewById(R.id.btnUndo);
		btnRedo = findViewById(R.id.btnRedo);
		btnTheme = findViewById(R.id.btnTheme);
		btnExport = findViewById(R.id.btnExport);
		tvProjectTitle = findViewById(R.id.tvProjectTitle);

		// Get project name from intent
		if (getIntent().hasExtra("project_name")) {
			projectName = getIntent().getStringExtra("project_name");
		}
		tvProjectTitle.setText(projectName);

		// Preview button
		button5.setOnClickListener(v -> {
			PageCodeGenerator codeGen = new PageCodeGenerator();
			String finalHtml = codeGen.generateAllCode(screen);
			Bundle bundle = new Bundle();
			bundle.putString("finalCode", finalHtml);
			PreviewBottomdialogFragmentActivity fragment = new PreviewBottomdialogFragmentActivity();
			fragment.setArguments(bundle);
			fragment.show(getSupportFragmentManager(), "fragment");
		});

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
			}
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
							if (recyclerview1.getAdapter() != null) {
								recyclerview1.getAdapter().notifyDataSetChanged();
							}
							Toast.makeText(this, "Widgets imported!", Toast.LENGTH_SHORT).show();
						} catch (Exception e) {
							Toast.makeText(this, "Failed to import widgets.", Toast.LENGTH_SHORT).show();
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
			}
		});

		// Redo button
		btnRedo.setOnClickListener(v -> {
			List<Map<String, Object>> state = undoRedoManager.redo();
			if (state != null) {
				restoreState(state);
			}
		});

		// Theme button
		btnTheme.setOnClickListener(v -> showThemeDialog());

		// Export button
		btnExport.setOnClickListener(v -> showExportDialog());
	}

	private void initializeLogic() {
		// Initialize engines
		engine = new WidgetBuilderEngine(this);
		widgetUpdater = new WidgetUpdater(this, engine);
		codeGenerator = new PageCodeGenerator();
		projectDataManager = new ProjectDataManager(this);
		themeManager = new ThemeManager();
		exportManager = new ExportManager(this, themeManager);

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
		});
		selector.attachTo(screen);

		// Setup initial empty spinner
		updateWidgetSpinner(null);

		// Load widgets from JSON asset registry
		widgetRegistry = new WidgetRegistry(this);
		widgets = widgetRegistry.getAllWidgets();

		recyclerview1.setAdapter(new Recyclerview1Adapter(widgets));
		recyclerview1.setLayoutManager(new GridLayoutManager(this, 4));

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
			}
		});

		// Setup design list
		buildDesignList();

		// Load project if it exists
		loadProject();

		// Save initial undo state
		saveUndoState();
	}

	private void updateWidgetSpinner(String widgetId) {
		List<String> items = new ArrayList<>();
		if (widgetId == null) {
			items.add("No selection");
		} else {
			items.add(widgetId);
			// Further hierarchy can be added here if needed, like fetching parent IDs
			View selectedView = selector.getSelectedView();
			if (selectedView != null) {
				View parent = (View) selectedView.getParent();
				while (parent != null && parent != screen && parent.getId() != R.id.scre) {
					Object tagDataObj = parent.getTag();
					if (tagDataObj instanceof Map) {
						Map<String, Object> tagData = (Map<String, Object>) tagDataObj;
						if (tagData.containsKey("id")) {
							items.add(0, (String) tagData.get("id"));
						} else {
							items.add(0, parent.getClass().getSimpleName());
						}
					}
					parent = (View) parent.getParent();
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
		widgetSpinner.setSelection(items.size() - 1); // Select the actual element by default
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
							// Check if it's a reorder drag (prefixed with "reorder:")
							if (dragText.startsWith("reorder:")) {
								handleReorderDrop(dragText, event);
								return true;
							}

							int pos = Integer.parseInt(dragText);
							Map<String, Object> widgetDefinition = widgets.get(pos);
							View newWidgetView = engine.createWidget(widgetDefinition.get("tag").toString());

							if (newWidgetView != null) {
								applyWidgetDefaults(newWidgetView, widgetDefinition);

								// Position drop near closest child
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
					// Show drop indicator
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
		// Reset background of all children
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			Object tag = child.getTag();
			if (tag instanceof Map) {
				// Don't clear highlight from selected widget
				if (child != selector.getSelectedView()) {
					// preserve original background
				}
			}
		}
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
		// Find the dragged view by hashcode
		int hash = Integer.parseInt(dragText.replace("reorder:", ""));
		View draggedView = findViewByHash(screen, hash);
		if (draggedView != null && draggedView.getParent() instanceof ViewGroup) {
			ViewGroup parent = (ViewGroup) draggedView.getParent();
			parent.removeView(draggedView);
			int newIndex = findDropIndex(screen, event.getY());
			screen.addView(draggedView, Math.min(newIndex, screen.getChildCount()));
			saveUndoState();
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

	// ---- Project Save/Load ----

	private void saveProject() {
		projectDataManager.saveProject(screen, projectName);

		// Save theme
		File dir = new File(getFilesDir(), "projects");
		File themeFile = new File(dir, projectName + ".theme");
		FileUtil.writeFile(themeFile.getAbsolutePath(), themeManager.toJson());

		Toast.makeText(this, "Project saved", Toast.LENGTH_SHORT).show();
	}

	private void loadProject() {
		projectDataManager.loadProject(screen, projectName, engine, selector, dropZoneManager);

		// Register reorder drag for loaded widgets
		for (int i = 0; i < screen.getChildCount(); i++) {
			setupWidgetReorderDrag(screen.getChildAt(i));
		}

		// Load theme
		File dir = new File(getFilesDir(), "projects");
		File themeFile = new File(dir, projectName + ".theme");
		if (themeFile.exists()) {
			String themeJson = FileUtil.readFile(themeFile.getAbsolutePath());
			themeManager.fromJson(themeJson);
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

		// Pre-fill with current theme values
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
			ExportManager.ExportResult result = exportManager.generateExportFiles(screen, projectName);
			if (result.success) {
				Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
			}
			dialog.dismiss();
		});

		dialogView.findViewById(R.id.cardExportZip).setOnClickListener(v -> {
			try {
				File zipFile = exportManager.exportAsZip(screen, projectName);
				Toast.makeText(this, "ZIP exported: " + zipFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
			} catch (Exception e) {
				Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
			}
			dialog.dismiss();
		});

		dialogView.findViewById(R.id.cardExportPreview).setOnClickListener(v -> {
			PageCodeGenerator gen = new PageCodeGenerator();
			String html = gen.generateAllCode(screen);
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
			"Padding", "Margin", "Elevation", "Gravity",
			"Opacity", "Rotation", "ScaleX", "ScaleY",
			"Width", "Height", "Orientation"
		};
		for (String item : items) {
			HashMap<String, Object> map = new HashMap<>();
			map.put("edit", item);
			design.add(map);
		}
		recyclerview3.setAdapter(new Recyclerview3Adapter(design));
		recyclerview3.setLayoutManager(new GridLayoutManager(this, 4));
	}

	public void handleDesignItemClick(int position) {
		View selected = selector.getSelectedView();
		if (selected == null) {
			Toast.makeText(this, "Select a widget first", Toast.LENGTH_SHORT).show();
			return;
		}

		String editType = design.get(position).get("edit").toString();
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
					style.put("rotation", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "ScaleX":
				showStyleDialog("Set ScaleX", "1.0", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("scaleX", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "ScaleY":
				showStyleDialog("Set ScaleY", "1.0", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("scaleY", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "Width":
				showStyleDialog("Set Width", "100px, match_parent, wrap_content", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("width", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "Height":
				showStyleDialog("Set Height", "100px, match_parent, wrap_content", value -> {
					Map<String, Object> style = new HashMap<>();
					style.put("height", value);
					widgetUpdater.updateWidget(selected, value, style);
					saveUndoState();
				});
				break;
			case "Orientation":
				showOrientationDialog(selected);
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

	private void showOrientationDialog(View selected) {
		String[] options = {"Vertical", "Horizontal"};
		new MaterialAlertDialogBuilder(this)
			.setTitle("Set Orientation")
			.setItems(options, (dialog, which) -> {
				if (selected instanceof LinearLayout) {
					((LinearLayout) selected).setOrientation(
						which == 0 ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
					Map<String, Object> style = new HashMap<>();
					style.put("flexDirection", which == 0 ? "column" : "row");
					widgetUpdater.updateWidget(selected, "", style);
					saveUndoState();
				}
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
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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

			// Set icon based on type
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
			case "ScaleX":
			case "ScaleY": return R.drawable.resize;
			case "Width":
			case "Height": return R.drawable.border_sides;
			case "Orientation": return R.drawable.freezerowcolumn;
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

			textview1.setText(name + " <" + tag + ">");
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
