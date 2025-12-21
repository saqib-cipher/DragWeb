package sketchweb.gl;

import sketchweb.gl.SplashActivity;
import android.animation.*;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.view.View;
import android.view.View.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.*;
import org.json.*;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import 
com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity {
	
	private WidgetBuilderEngine engine;
	private WidgetUpdater widgetUpdater;
	private PageCodeGenerator codeGenerator;
	private ProjectDataManager projectDataManager;
	private WidgetSelector selector;
	private DropZoneManager dropZoneManager;
	private HashMap<String, Object> item = new HashMap<>();
	
	private ArrayList<HashMap<String, Object>> widgets = new ArrayList<>();
	private ArrayList<HashMap<String, Object>> design = new ArrayList<>();
	
	private LinearLayout main;
	private LinearLayout topBar;
	private LinearLayout linear8;
	private LinearLayout linear3;
	private LinearLayout linear9;
	private LinearLayout scre;
	private LinearLayout linear10;
	private Button button5;
	private Button button4;
	private NestedScrollView vscroll2;
	private LinearLayout screen;
	private Button delete;
	private MaterialButtonToggleGroup linear7;
	private TextView textview2;
	private RecyclerView recyclerview3;
	private RecyclerView recyclerview1;
	private Button button1;
	private Button button2;
	private Button button3;
	
	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		setContentView(R.layout.main);
		initialize(_savedInstanceState);
		initializeLogic();
	}
	
	private void initialize(Bundle _savedInstanceState) {
		main = findViewById(R.id.main);
		topBar = findViewById(R.id.topBar);
		linear8 = findViewById(R.id.linear8);
		linear3 = findViewById(R.id.linear3);
		linear9 = findViewById(R.id.linear9);
		scre = findViewById(R.id.scre);
		linear10 = findViewById(R.id.linear10);
		button5 = findViewById(R.id.button5);
		button4 = findViewById(R.id.button4);
		vscroll2 = findViewById(R.id.vscroll2);
		screen = findViewById(R.id.screen);
		delete = findViewById(R.id.delete);
		linear7 = findViewById(R.id.linear7);
		textview2 = findViewById(R.id.textview2);
		recyclerview3 = findViewById(R.id.recyclerview3);
		recyclerview1 = findViewById(R.id.recyclerview1);
		button1 = findViewById(R.id.button1);
		button2 = findViewById(R.id.button2);
		button3 = findViewById(R.id.button3);
		
		button5.setOnClickListener(_v -> {
			PageCodeGenerator codeGen = new PageCodeGenerator();
			String finalHtml = codeGen.generateAllCode(screen);  
			Bundle bundle = new Bundle();
			bundle.putString("finalCode", finalHtml); 
			PreviewBottomdialogFragmentActivity _fragment_ = new PreviewBottomdialogFragmentActivity();
			_fragment_.setArguments(bundle);
			_fragment_.show(getSupportFragmentManager(), "fragment");
		});
		
		button4.setOnClickListener(_v -> {
			String newValue = button1.getText().toString().trim();
			if (newValue.isEmpty()) {
				Toast.makeText(MainActivity.this, "Please enter a value to update.", Toast.LENGTH_SHORT).show();
				return;
			}
			View selected = selector.getSelectedView();
			if (selected != null) {
				Toast.makeText(MainActivity.this, selected + "selected", Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(MainActivity.this, "No widget selected.", Toast.LENGTH_SHORT).show();
			}
		});
		
		delete.setOnClickListener(_v -> {
			View selected = selector.getSelectedView();
			if (selected != null) {
				((ViewGroup) selected.getParent()).removeView(selected);
				selected.setBackgroundColor(Color.TRANSPARENT);
				selector.clearSelection();
				textview2.setText("No widget selected");
				delete.setEnabled(false);
			}
		});
		
		button1.setOnClickListener(_v -> {
			recyclerview1.setVisibility(View.VISIBLE);
			recyclerview3.setVisibility(View.GONE);
			
		});
		
		button2.setOnClickListener(_v -> {
			recyclerview3.setVisibility(View.VISIBLE);
			recyclerview1.setVisibility(View.GONE);
		});
		
		button3.setOnClickListener(_v -> {
			recyclerview1.setVisibility(View.GONE);
			recyclerview3.setVisibility(View.GONE);
		});
	}
	
	private void initializeLogic() {
		button1.performClick();
		engine = new WidgetBuilderEngine(this);
		widgetUpdater = new WidgetUpdater(this, engine);
		codeGenerator = new PageCodeGenerator();
		projectDataManager = new ProjectDataManager(this);
		
		selector = new WidgetSelector(this);
		
		
		selector.setOnWidgetSelectedListener(new WidgetSelector.OnWidgetSelectedListener() {
			@Override
			public void onWidgetSelected(String widgetId) {
				textview2.setText("Selected: " + widgetId);
				delete.setEnabled(true);
			}
		});
		
		selector.attachTo(screen);
		
		
		widgets.clear();
		widgets = new Gson().fromJson("[\n  {\n    \"name\": \"Text\",\n    \"tag\": \"p\",\n    \"color\": \"#333333\",\n    \"function\": {\n      \"text\": \"Hello, world!\",\n      \"style\": {\n        \"fontSize\": \"16px\",\n        \"color\": \"#333333\"\n      }\n    }\n  },\n  {\n    \"name\": \"Heading\",\n    \"tag\": \"h1\",\n    \"color\": \"#000000\",\n    \"function\": {\n      \"text\": \"Your Page Title\",\n      \"style\": {\n        \"fontSize\": \"32px\",\n        \"fontWeight\": \"bold\",\n        \"textAlign\": \"center\"\n      }\n    }\n  },\n  {\n    \"name\": \"Button\",\n    \"tag\": \"button\",\n    \"color\": \"#FFBB33\",\n    \"function\": {\n      \"text\": \"Click Me\",\n      \"style\": {\n        \"padding\": \"10px 20px\",\n        \"backgroundColor\": \"#FFBB33\",\n        \"border\": \"none\",\n        \"borderRadius\": \"5px\"\n      }\n    }\n  },\n  {\n    \"name\": \"Image\",\n    \"tag\": \"img\",\n    \"color\": \"#CCCCCC\",\n    \"function\": {\n      \"src\": \"https://example.com/image.jpg\",\n      \"style\": {\n        \"width\": \"100%\",\n        \"height\": \"auto\"\n      }\n    }\n  },\n  {\n    \"name\": \"Input\",\n    \"tag\": \"input\",\n    \"color\": \"#FFFFFF\",\n    \"function\": {\n      \"type\": \"text\",\n      \"placeholder\": \"Enter text\",\n      \"style\": {\n        \"width\": \"100%\",\n        \"padding\": \"8px\",\n        \"border\": \"1px solid #999999\"\n      }\n    }\n  },\n  {\n    \"name\": \"Link\",\n    \"tag\": \"a\",\n    \"color\": \"#2196F3\",\n    \"function\": {\n      \"text\": \"Visit\",\n      \"href\": \"https://example.com\",\n      \"style\": {\n        \"color\": \"#2196F3\",\n        \"textDecoration\": \"none\"\n      }\n    }\n  },\n  {\n    \"name\": \"List\",\n    \"tag\": \"ul\",\n    \"color\": \"#444444\",\n    \"function\": {\n      \"items\": [\"Item 1\", \"Item 2\", \"Item 3\"],\n      \"style\": {\n        \"paddingLeft\": \"20px\"\n      }\n    }\n  },\n  {\n    \"name\": \"Divider\",\n    \"tag\": \"hr\",\n    \"color\": \"#CCCCCC\",\n    \"function\": {\n      \"style\": {\n        \"border\": \"1px solid #ccc\",\n        \"margin\": \"12px 0\"\n      }\n    }\n  },\n  {\n    \"name\": \"Spacer\",\n    \"tag\": \"div\",\n    \"color\": \"#F0F0F0\",\n    \"function\": {\n      \"style\": {\n        \"height\": \"20px\"\n      }\n    }\n  },\n  {\n    \"name\": \"Container\",\n    \"tag\": \"div\",\n    \"color\": \"#F5F5F5\",\n    \"function\": {\n      \"style\": {\n        \"padding\": \"16px\",\n        \"backgroundColor\": \"#F5F5F5\"\n      }\n    }\n  }\n]", new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
		for(HashMap<String, Object> widgetDef : widgets) {
			if ("img".equals(widgetDef.get("tag"))) {
				Map<String, Object> function = (Map<String, Object>) widgetDef.get("function");
				if (function != null) {
					if (function.containsKey("src") && function.get("src") instanceof String) {
						String currentSrc = function.get("src").toString();
						if (currentSrc.startsWith("http") || currentSrc.startsWith("https")) {
							function.put("src", "android.R.drawable.ic_menu_gallery");
						}
					}
				}
			}
		}
		
		recyclerview1.setAdapter(new Recyclerview1Adapter(widgets));
		recyclerview1.setLayoutManager(new GridLayoutManager(MainActivity.this, 4));
		DropZoneManager dropZoneManager = new DropZoneManager(
		MainActivity.this, screen, widgets, engine, selector
		);
		screen.setOnDragListener(new View.OnDragListener() {
			@Override
			public boolean onDrag(View v, DragEvent event) {
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
					v.setBackgroundColor(Color.parseColor("#B0BEC5"));      ClipData data = event.getClipData();
					
					if (data != null && data.getItemCount() > 0) {
						try {
							int pos = Integer.parseInt(data.getItemAt(0).getText().toString());
							Map<String, Object> widgetDefinition = widgets.get(pos);
							View newWidgetView = engine.createWidget(widgetDefinition.get("tag").toString());
							
							if (newWidgetView != null) {
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
								
								engine.applyPropertiesToView(newWidgetView, newWidgetMap);
								
								// ✅ Position drop near closest child view
								float dropY = event.getY();
								int targetIndex = -1;
								float minDistance = Float.MAX_VALUE;
								
								for (int i = 0; i < screen.getChildCount(); i++) {
									View child = screen.getChildAt(i);
									float centerY = child.getY() + (child.getHeight() / 2);
									float distance = Math.abs(dropY - centerY);
									if (distance < minDistance) {
										minDistance = distance;
										targetIndex = i;
									}
								}
								
								if (targetIndex != -1) {
									View targetView = screen.getChildAt(targetIndex);
									float centerY = targetView.getY() + (targetView.getHeight() / 2);
									if (dropY < centerY) {
										screen.addView(newWidgetView, targetIndex);
									} else {
										screen.addView(newWidgetView, targetIndex + 1);
									}
								} else {
									screen.addView(newWidgetView); // fallback to end
								}
								
								selector.registerView(newWidgetView);  // ✨ This makes the new widget selectable
								newWidgetView.performClick();          // 👉 Immediately triggers selection
								
								dropZoneManager.registerWidgetAsDropZoneIfContainer(newWidgetView); // 🔥 Enable drop if it's a container
							}
						} catch (NumberFormatException e) {
							Toast.makeText(MainActivity.this, "Error parsing widget position.", Toast.LENGTH_SHORT).show();
							Log.e("MainActivity", "Drag data error: " + e.getMessage());
						} catch (Exception e) {
							Toast.makeText(MainActivity.this, "Error creating widget.", Toast.LENGTH_SHORT).show();
							Log.e("MainActivity", "Error creating widget on drop: " + e.getMessage());
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
			}
		});
		main.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				View selected = selector.getSelectedView();
				if (selected != null) {
					selected.setBackgroundColor(Color.TRANSPARENT);
					selector.clearSelection();
					
					// Optionally clear selected label in your UI
					textview2.setText("No widget selected");
					delete.setEnabled(false);
				}
			}
		});
		_designList();
	}
	
	public void _designList() {
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "Orientation");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "Edittext");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "Color");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "TextSize");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "Font");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "Background");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "BorderRadius");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "BorderWidth");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "BorderColor");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "Padding");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "Margin");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "Elevation");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "Gravity");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "Opacity");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "Rotation");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "ScaleX");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "ScaleY");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "Width");
			design.add(_item);
		}
		{
			HashMap<String, Object> _item = new HashMap<>();
			_item.put("edit", "Height");
			design.add(_item);
		}
		recyclerview3.setAdapter(new Recyclerview3Adapter(design));
		recyclerview3.setLayoutManager(new GridLayoutManager(MainActivity.this, 4));
	}
	
	
	public void _ShwDial(final double _posi, final ArrayList<HashMap<String, Object>> _listmap) {
		if (design.get((int) _posi).get("edit").toString().equals("Edittext")) {
			showStyleDialog("Set New Value", "New Value", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("text", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("TextSize")) {
			showStyleDialog("Change Text Size", "Text size (px)", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("fontSize", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), "", style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("Color")) {
			showStyleDialog("Set Color", "#FFFFF", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("color", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("Font")) {
			showStyleDialog("Set Font Weight", "Bold, Italic", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("fontWeight", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("Background")) {
			showStyleDialog("Set Background Color", "#FFFFF", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("backgroundColor", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("BorderRadius")) {
			showStyleDialog("Set Border Radius", "12px", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("borderRadius", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("BorderWidth")) {
			showStyleDialog("Set Border Width", "12px", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("borderWidth", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("BorderColor")) {
			showStyleDialog("Set Border Color", "#FFFFFF", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("borderColor", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("Padding")) {
			showStyleDialog("Set Padding", "12px", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("padding", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("Margin")) {
			showStyleDialog("Set Padding", "12px", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("margin", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("ScaleX")) {
			showStyleDialog("Set ScaleX", ".12", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("scaleX", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("ScaleY")) {
			showStyleDialog("Set ScaleY", "1.2", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("scaleY", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("Elevation")) {
			showStyleDialog("Set Padding", "4", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("elevation", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("Rotation")) {
			showStyleDialog("Set Rotation", "4", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("rotation", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("Height")) {
			showStyleDialog("Set Height", "100px, match_parent, wrap_content", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("height", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("Opacity")) {
			showStyleDialog("Set Padding", "0.6", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("opacity", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
		else if (design.get((int) _posi).get("edit").toString().equals("Width")) {
			showStyleDialog("Set Width", "100px, match_parent, wrap_content", value -> {
				Map<String, Object> style = new HashMap<>();
				style.put("width", value);
				widgetUpdater.updateWidget(selector.getSelectedView(), value, style);
			});
		}
	}
	
	
	public void _ShowDialo() {
	} private void showStyleDialog(String title, String hint, OnStyleConfirmed callback) {
		View alertLayout = getLayoutInflater().inflate(R.layout.dial, null);
		MaterialAlertDialogBuilder m = new MaterialAlertDialogBuilder(MainActivity.this);
		m.setView(alertLayout);
		
		TextInputLayout inputLayout = alertLayout.findViewById(R.id.UserNameEditText);
		TextInputEditText inputField = alertLayout.findViewById(R.id.UserNameValue);
		inputLayout.setHint(hint);
		m.setTitle(title);
		
		m.setPositiveButton("Apply", (dialog, which) -> {
			String value = inputField.getText().toString().trim();
			if (value.isEmpty()) {
				Toast.makeText(MainActivity.this, "Please enter a value.", Toast.LENGTH_SHORT).show();
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
	{
	}
	
	public class Recyclerview3Adapter extends RecyclerView.Adapter<Recyclerview3Adapter.ViewHolder> {
		
		ArrayList<HashMap<String, Object>> _data;
		
		public Recyclerview3Adapter(ArrayList<HashMap<String, Object>> _arr) {
			_data = _arr;
		}
		
		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			LayoutInflater _inflater = getLayoutInflater();
			View _v = _inflater.inflate(R.layout.design, null);
			RecyclerView.LayoutParams _lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			_v.setLayoutParams(_lp);
			return new ViewHolder(_v);
		}
		
		@Override
		public void onBindViewHolder(ViewHolder _holder, final int _position) {
			View _view = _holder.itemView;
			
			final com.google.android.material.card.MaterialCardView cardview1 = _view.findViewById(R.id.cardview1);
			final LinearLayout linear19 = _view.findViewById(R.id.linear19);
			final ImageView imageview1 = _view.findViewById(R.id.imageview1);
			final TextView textview1 = _view.findViewById(R.id.textview1);
			
			textview1.setText(design.get((int) _position).get("edit").toString());
			if (design.get((int) _position).get("edit").toString().equals("Edittext")) {
				imageview1.setImageResource(R.drawable.cursor_text);
			}
			else if (design.get((int) _position).get("edit").toString().equals("TextSize")) {
				imageview1.setImageResource(R.drawable.textsize);
			}
			else if (design.get((int) _position).get("edit").toString().equals("Color")) {
				imageview1.setImageResource(R.drawable.textcolor);
			}
			else if (design.get((int) _position).get("edit").toString().equals("Font")) {
				imageview1.setImageResource(R.drawable.alphabet_latin);
			}
			else if (design.get((int) _position).get("edit").toString().equals("Background")) {
				imageview1.setImageResource(R.drawable.background);
			}
			else if (design.get((int) _position).get("edit").toString().equals("BorderRadius")) {
				imageview1.setImageResource(R.drawable.border_radius);
			}
			else if (design.get((int) _position).get("edit").toString().equals("BorderWidth")) {
				imageview1.setImageResource(R.drawable.border_style);
			}
			else if (design.get((int) _position).get("edit").toString().equals("BorderColor")) {
				imageview1.setImageResource(R.drawable.freezerowcolumn);
			}
			else if (design.get((int) _position).get("edit").toString().equals("Padding")) {
				imageview1.setImageResource(R.drawable.box_padding);
			}
			else if (design.get((int) _position).get("edit").toString().equals("Margin")) {
				imageview1.setImageResource(R.drawable.box_margin);
			}
			else if (design.get((int) _position).get("edit").toString().equals("Width/Height")) {
				imageview1.setImageResource(R.drawable.border_sides);
			}
			else if (design.get((int) _position).get("edit").toString().equals("Elevation")) {
				imageview1.setImageResource(R.drawable.emphasis);
			}
			else if (design.get((int) _position).get("edit").toString().equals("Gravity")) {
				imageview1.setImageResource(R.drawable.focus_centered);
			}
			else if (design.get((int) _position).get("edit").toString().equals("ScaleX/Y")) {
				imageview1.setImageResource(R.drawable.resize);
			}
			else if (design.get((int) _position).get("edit").toString().equals("Opacity")) {
				imageview1.setImageResource(R.drawable.droplet);
			}
			else if (design.get((int) _position).get("edit").toString().equals("Rotation")) {
				imageview1.setImageResource(R.drawable.rotate);
			}
			cardview1.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View _view) {
					_ShwDial(_position, design);
				}
			});
		}
		
		@Override
		public int getItemCount() {
			return _data.size();
		}
		
		public class ViewHolder extends RecyclerView.ViewHolder {
			public ViewHolder(View v) {
				super(v);
			}
		}
	}
	
	public class Recyclerview1Adapter extends RecyclerView.Adapter<Recyclerview1Adapter.ViewHolder> {
		
		ArrayList<HashMap<String, Object>> _data;
		
		public Recyclerview1Adapter(ArrayList<HashMap<String, Object>> _arr) {
			_data = _arr;
		}
		
		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			LayoutInflater _inflater = getLayoutInflater();
			View _v = _inflater.inflate(R.layout.widgets, null);
			RecyclerView.LayoutParams _lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			_v.setLayoutParams(_lp);
			return new ViewHolder(_v);
		}
		
		@Override
		public void onBindViewHolder(ViewHolder _holder, final int _position) {
			View _view = _holder.itemView;
			
			final LinearLayout linear1 = _view.findViewById(R.id.linear1);
			final TextView textview1 = _view.findViewById(R.id.textview1);
			
			Map<String, Object> item = _data.get((int)_position);
			
			String name = item.get("name").toString();
			String tag = item.get("tag").toString();
			String color = item.get("color").toString();
			
			textview1.setText(name + " <" + tag + ">");
			linear1.setBackgroundColor(Color.parseColor(color));
			
			
			linear1.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View view, MotionEvent event) {
					if (event.getAction() == MotionEvent.ACTION_DOWN) {
						ClipData.Item itemData = new ClipData.Item(String.valueOf(_position));
						
						ClipData dragData = new ClipData("widget", new String[]{"text/plain"}, itemData);
						View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view);
						
						view.startDragAndDrop(dragData, shadowBuilder, view, 0);
						return true;
					}
					return false;
				}
			});
		}
		
		@Override
		public int getItemCount() {
			return _data.size();
		}
		
		public class ViewHolder extends RecyclerView.ViewHolder {
			public ViewHolder(View v) {
				super(v);
			}
		}
	}
	
	@Deprecated
	public void showMessage(String _s) {
		Toast.makeText(getApplicationContext(), _s, Toast.LENGTH_SHORT).show();
	}
	
	@Deprecated
	public int getLocationX(View _v) {
		int _location[] = new int[2];
		_v.getLocationInWindow(_location);
		return _location[0];
	}
	
	@Deprecated
	public int getLocationY(View _v) {
		int _location[] = new int[2];
		_v.getLocationInWindow(_location);
		return _location[1];
	}
	
	@Deprecated
	public int getRandom(int _min, int _max) {
		Random random = new Random();
		return random.nextInt(_max - _min + 1) + _min;
	}
	
	@Deprecated
	public ArrayList<Double> getCheckedItemPositionsToArray(ListView _list) {
		ArrayList<Double> _result = new ArrayList<Double>();
		SparseBooleanArray _arr = _list.getCheckedItemPositions();
		for (int _iIdx = 0; _iIdx < _arr.size(); _iIdx++) {
			if (_arr.valueAt(_iIdx)) _result.add((double)_arr.keyAt(_iIdx));
		}
		return _result;
	}
	
	@Deprecated
	public float getDip(int _input) {
		return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, _input, getResources().getDisplayMetrics());
	}
	
	@Deprecated
	public int getDisplayWidthPixels() {
		return getResources().getDisplayMetrics().widthPixels;
	}
	
	@Deprecated
	public int getDisplayHeightPixels() {
		return getResources().getDisplayMetrics().heightPixels;
	}
}