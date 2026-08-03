package sketchweb.gl.colorpicker;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputFilter;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import sketchweb.gl.R;
import sketchweb.gl.ThemeManager;
import sketchweb.gl.ProjectAssetManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ColorPickerDialogFragment extends DialogFragment {
	
	public interface OnColorSelectedListener {
		void onColorSelected(String color);
	}
	
	private OnColorSelectedListener colorSelectedListener;
	private boolean isHexOnlyMode = false;
	
	public void setOnColorSelectedListener(OnColorSelectedListener listener) {
		this.colorSelectedListener = listener;
	}

	public void setHexOnlyMode(boolean hexOnly) {
		this.isHexOnlyMode = hexOnly;
	}
	
	private ColorViewModel viewModel;    
	private EditText inputHex;    
	private View previewBox;    
	private MaterialCardView addButton;    
	private RecyclerView categoryRecycler;    
	private RecyclerView colorRecycler;    
	private CategoryColorAdapter categoryColorAdapter;    
	private ColorAdapter colorAdapter;    
	private MaterialButton btnCopy;    
	private MaterialButton btnClose;    
	private MaterialButton btnApply;
	private MaterialButton btnAdd;
	
	private com.google.android.material.slider.Slider alphaSlider;
	private TextView tvAlphaVal;
	private boolean isUpdatingFromSlider = false;
	
	private ChipGroup colorMenu;
	private Chip chipHex;
	private Chip chipHexad;
	private Chip chipRgb;
	private Chip chipRgba;
	
	private boolean isFullScreenNoMargins = false;
	private String currentFormatKey = "hex";     
	
	@NonNull    
	@Override    
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {    
		Context context = requireContext();    
		viewModel = new ViewModelProvider(requireActivity()).get(ColorViewModel.class);    
		
		View view = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null);    
		
		inputHex = view.findViewById(R.id.input_hex);    
		previewBox = view.findViewById(R.id.color_preview);    
		categoryRecycler = view.findViewById(R.id.recycler_categories);    
		colorRecycler = view.findViewById(R.id.recycler_colors);    
		
		btnAdd = view.findViewById(R.id.btn_add);
		btnCopy = view.findViewById(R.id.btn_copy);    
		btnClose = view.findViewById(R.id.btn_close);    
		btnApply = view.findViewById(R.id.btn_apply);
		
		alphaSlider = view.findViewById(R.id.alpha_slider);
		tvAlphaVal = view.findViewById(R.id.tv_alpha_val);

		if (alphaSlider != null) {
			alphaSlider.addOnChangeListener((slider, value, fromUser) -> {
				if (!fromUser) return;
				int a = Math.round(value);
				if (tvAlphaVal != null) {
					int pct = Math.round(a * 100f / 255f);
					tvAlphaVal.setText(pct + "%");
				}

				String curHex = viewModel.getSelectedColor().getValue();
				if (curHex == null || curHex.isEmpty()) {
					curHex = inputHex.getText() != null ? inputHex.getText().toString() : "#FFFFFF";
				}

				if ("transparent".equalsIgnoreCase(curHex)) {
					if (a > 0) {
						curHex = "#000000";
					} else {
						return;
					}
				}

				int colorInt = resolveColorHex(curHex);
				int r = Color.red(colorInt);
				int g = Color.green(colorInt);
				int b = Color.blue(colorInt);

				isUpdatingFromSlider = true;
				if (a == 0) {
					viewModel.selectColor("transparent");
					if (inputHex != null) inputHex.setText("transparent");
					previewBox.setBackgroundColor(Color.TRANSPARENT);
				} else {
					String argbHex = String.format("#%02X%02X%02X%02X", r, g, b, a);
					String formatted = ColorUtils.formatColor(argbHex, (currentFormatKey != null) ? currentFormatKey : "hex");
					viewModel.selectColor(formatted);
					if (inputHex != null) inputHex.setText(formatted);
					previewBox.setBackgroundColor(Color.argb(a, r, g, b));
				}
				isUpdatingFromSlider = false;
			});
		}

		colorMenu = view.findViewById(R.id.color_menu);
		chipHex = view.findViewById(R.id.chip_hex);
		chipHexad = view.findViewById(R.id.chip_hexad);
		chipRgb = view.findViewById(R.id.chip_rgb);
		chipRgba = view.findViewById(R.id.chip_rgba);
		
		categoryColorAdapter = new CategoryColorAdapter(viewModel);    
		colorAdapter = new ColorAdapter(viewModel);    
		
		categoryRecycler.setLayoutManager(new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false));    
		categoryRecycler.setAdapter(categoryColorAdapter);    
		
		colorRecycler.setLayoutManager(new LinearLayoutManager(context));    
		colorRecycler.setAdapter(colorAdapter);    
		
		if (colorMenu != null) {
			colorMenu.setOnCheckedStateChangeListener((group, checkedIds) -> {
				if (checkedIds.isEmpty()) return;
				int checkedId = checkedIds.get(0);
				if (checkedId == R.id.chip_hex) {
					currentFormatKey = "hex";
				} else if (checkedId == R.id.chip_hexad) {
					currentFormatKey = "hexad";
				} else if (checkedId == R.id.chip_rgb) {
					currentFormatKey = "rgb";
				} else if (checkedId == R.id.chip_rgba) {
					currentFormatKey = "rgba";
				}
				
				String selHex = viewModel.getSelectedColor().getValue();
				if (selHex != null && !selHex.startsWith("var(") && inputHex != null) {
					inputHex.setText(ColorUtils.formatColor(selHex, currentFormatKey));
				}
			});
		}
		
		if (isHexOnlyMode) {
			if (colorMenu != null) colorMenu.setVisibility(View.GONE);
			if (categoryColorAdapter != null) categoryColorAdapter.setHexOnlyMode(true);
			if ("theme".equalsIgnoreCase(viewModel.getSelectedCategory().getValue())) {
				viewModel.selectCategory("red");
			}
		}

		viewModel.getSelectedCategory().observe(this, category -> {
			if (isHexOnlyMode) {
				if (colorMenu != null) colorMenu.setVisibility(View.GONE);
				view.findViewById(R.id.card_view_colors).setVisibility(View.VISIBLE);
				view.findViewById(R.id.card_view_theme).setVisibility(View.GONE);
				colorAdapter.setColors(viewModel.getColorsForCategory(category));
				return;
			}
			boolean hideChips = "theme".equalsIgnoreCase(category) 
							|| "transparent".equalsIgnoreCase(category)
							|| "none".equalsIgnoreCase(category);
			if (colorMenu != null) {
				colorMenu.setVisibility(hideChips ? View.GONE : View.VISIBLE);
			}
			View alphaContainer = view.findViewById(R.id.alpha_container);
			if (alphaContainer != null) {
				alphaContainer.setVisibility(hideChips ? View.GONE : View.VISIBLE);
			}
			if ("theme".equalsIgnoreCase(category)) {
				view.findViewById(R.id.card_view_colors).setVisibility(View.GONE);
				view.findViewById(R.id.card_view_theme).setVisibility(View.VISIBLE);
				
				RecyclerView recyclerTheme = view.findViewById(R.id.recycler_theme_colors);
				recyclerTheme.setLayoutManager(new LinearLayoutManager(context));
				recyclerTheme.setAdapter(new ThemeColorAdapter());
			} else {
				view.findViewById(R.id.card_view_colors).setVisibility(View.VISIBLE);
				view.findViewById(R.id.card_view_theme).setVisibility(View.GONE);
				colorAdapter.setColors(viewModel.getColorsForCategory(category));
			}
		});
		
		viewModel.getSelectedColor().observe(this, hex -> {    
			if (hex != null) {    
				int colorInt = resolveColorHex(hex);
				previewBox.setBackgroundColor(colorInt);    
				updateColorFormatChips(hex);
				
				if (!isUpdatingFromSlider && alphaSlider != null) {
					int a = Color.alpha(colorInt);
					if ("transparent".equalsIgnoreCase(hex)) {
						a = 0;
					}
					alphaSlider.setValue(a);
					if (tvAlphaVal != null) {
						int pct = Math.round(a * 100f / 255f);
						tvAlphaVal.setText(pct + "%");
					}
				}

				if ("theme".equalsIgnoreCase(viewModel.getSelectedCategory().getValue())) {
					RecyclerView recyclerTheme = view.findViewById(R.id.recycler_theme_colors);
					if (recyclerTheme != null && recyclerTheme.getAdapter() != null) {
						recyclerTheme.getAdapter().notifyDataSetChanged();
					}
				}
			}    
		});    
		
		btnAdd.setOnClickListener(v -> {
			String hex = inputHex.getText().toString().trim();    
			if (!hex.startsWith("#")) hex = "#" + hex;    
			
			if (!ColorUtils.isValidHexColor(hex)) {    
				Toast.makeText(context, getString(R.string.invalid_hex_color), Toast.LENGTH_SHORT).show();
				return;    
			}    
			
			if (viewModel.addCustomColor(hex)) {    
				Toast.makeText(context, getString(R.string.color_added), Toast.LENGTH_SHORT).show();
				inputHex.setText("");    
			} else {    
				Toast.makeText(context, getString(R.string.color_already_exists), Toast.LENGTH_SHORT).show();
			}    
		});    
		
		btnClose.setOnClickListener(v -> {    
			dismiss();    
		});    
		
		btnApply.setOnClickListener(v -> {
			String hex = viewModel.getSelectedColor().getValue();
			
			if (hex != null && colorSelectedListener != null) {
				if (!hex.startsWith("var(")) {
					String formatToUse = (currentFormatKey != null) ? currentFormatKey : "hex";
					hex = ColorUtils.formatColor(hex, formatToUse);
				}
				colorSelectedListener.onColorSelected(hex);
			}
			dismiss();
		});
		
		btnCopy.setOnClickListener(v -> {    
			String hex = viewModel.getSelectedColor().getValue();    
			if (hex == null) {    
				Toast.makeText(getContext(), getString(R.string.color_not_selected), Toast.LENGTH_SHORT).show();
				return;    
			}    
			
			String formatToUse = (currentFormatKey != null) ? currentFormatKey : "hex";    
			String formatted = hex;
			if (!hex.startsWith("var(")) {
				formatted = ColorUtils.formatColor(hex, formatToUse);    
			}
			
			android.content.ClipboardManager clipboard = (android.content.ClipboardManager)    
			v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);    
			android.content.ClipData clip = android.content.ClipData.newPlainText("Color", formatted);    
			clipboard.setPrimaryClip(clip);    
			
			Toast.makeText(getContext(), getString(R.string.copied_value, formatted), Toast.LENGTH_SHORT).show();
		});    
		
		inputHex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});    
		
		inputHex.addTextChangedListener(new TextWatcher() {    
			private boolean isEditing = false;    
			
			@Override    
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}    
			
			@Override    
			public void onTextChanged(CharSequence s, int start, int before, int count) {    
				if (isEditing) return;    
				isEditing = true;    
				
				String text = s != null ? s.toString() : "";    
				
				if (!text.isEmpty()) {    
					if ("transparent".equalsIgnoreCase(text) || text.toLowerCase().startsWith("rgb") || text.toLowerCase().startsWith("0x")) {
						// Keep formatted string as is
					} else {
						if (!text.startsWith("#")) {    
							text = "#" + text;    
						}    
						text = text.toUpperCase(Locale.ROOT);    
						
						inputHex.setText(text);    
						int sel = Math.min(text.length(), inputHex.getText().length());    
						inputHex.setSelection(sel);    
					}
				} else {    
					inputHex.setSelection(0);    
				}    
				
				int color = resolveColorHex(inputHex.getText().toString());    
				previewBox.setBackgroundColor(color);    
				
				isEditing = false;    
			}    
			
			@Override    
			public void afterTextChanged(Editable s) {}    
		});    
		
		// Apply initial hex from arguments after all views are initialized
		Bundle args = getArguments();
		String initialHex = (args != null) ? args.getString("initialHex", "") : "";
		if (initialHex != null && !initialHex.isEmpty()) {
			applyInitialHex(initialHex);
		}

		Dialog dialog = new MaterialAlertDialogBuilder(context)
		.setView(view)
		.create();
		
		dialog.setOnShowListener(d -> {
			Window window = dialog.getWindow();
			if (window != null) {
				if (isFullScreenNoMargins) {
					window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
				}
			}
		});
		
		return dialog;
	}
	

	
	private void applyInitialHex(String initialHex) {
		if (initialHex == null || initialHex.isEmpty()) return;
		if ("transparent".equalsIgnoreCase(initialHex)) {
			viewModel.selectCategory("transparent");
			viewModel.selectColor("transparent");
			if (inputHex != null) {
				inputHex.setText("transparent");
			}
			return;
		}

		int initColorInt = ColorUtils.parseHexColorSafe(initialHex);
		int initR = Color.red(initColorInt);
		int initG = Color.green(initColorInt);
		int initB = Color.blue(initColorInt);

		String foundCategory = null;
		Set<String> categories = viewModel.getColorCategories();
		if (categories != null) {
			for (String category : categories) {
				if ("custom".equalsIgnoreCase(category) || "theme".equalsIgnoreCase(category)) continue;
				List<String> colors = viewModel.getColorsForCategory(category);
				if (colors != null) {
					for (String c : colors) {
						int cInt = ColorUtils.parseHexColorSafe(c);
						if (Color.red(cInt) == initR && Color.green(cInt) == initG && Color.blue(cInt) == initB) {
							foundCategory = category;
							break;
						}
					}
				}
				if (foundCategory != null) break;
			}
		}

		if (foundCategory != null) {
			viewModel.selectCategory(foundCategory);
			viewModel.selectColor(initialHex);
		} else {
			viewModel.selectCategory("custom");
			viewModel.selectColor(initialHex);
		}
		if (inputHex != null) {
			inputHex.setText(ColorUtils.formatColor(initialHex, (currentFormatKey != null) ? currentFormatKey : "hex"));
		}
	}

	private int resolveColorHex(String hexOrVar) {
		if (hexOrVar == null) return Color.TRANSPARENT;
		String hex = hexOrVar;
		if (hexOrVar.startsWith("var(")) {
			String varName = hexOrVar.substring(4, hexOrVar.length() - 1).trim();
			ThemeManager tm = ProjectAssetManager.getInstance().getThemeManager();
			if (tm != null) {
				String matchedKey = null;
				for (String key : tm.getAllStyles().keySet()) {
					if (("--" + ThemeManager.camelToKebab(key)).equals(varName)) {
						matchedKey = key;
						break;
					}
				}
				String colorVal = null;
				if (matchedKey != null) {
					colorVal = tm.getStyleForTheme("light", matchedKey);
				} else {
					String cleanVar = varName.startsWith("--") ? varName.substring(2) : varName;
					colorVal = tm.getCustomCssVars().get(cleanVar);
					if (colorVal != null && colorVal.contains("|")) {
						colorVal = colorVal.split("\\|", 2)[0];
					}
				}
				if (colorVal != null) {
					hex = colorVal;
				}
			}
		}
		return ColorUtils.parseHexColorSafe(hex);
	}
	
	private String formatThemeKeyAsCssVar(String key) {
		if (key.startsWith("var(")) {
			return key;
		}
		String cleanKey = key;
		if (cleanKey.startsWith("--")) {
			cleanKey = cleanKey.substring(2);
		}
		return "var(--" + ThemeManager.camelToKebab(cleanKey) + ")";
	}
	
	private void updateColorFormatChips(String hexOrVar) {
		if (hexOrVar == null) return;
		String hex = hexOrVar;
		if (hexOrVar.startsWith("var(")) {
			String varName = hexOrVar.substring(4, hexOrVar.length() - 1).trim();
			ThemeManager tm = ProjectAssetManager.getInstance().getThemeManager();
			if (tm != null) {
				String matchedKey = null;
				for (String key : tm.getAllStyles().keySet()) {
					if (("--" + ThemeManager.camelToKebab(key)).equals(varName)) {
						matchedKey = key;
						break;
					}
				}
				String colorVal = null;
				if (matchedKey != null) {
					colorVal = tm.getStyleForTheme("light", matchedKey);
				} else {
					String cleanVar = varName.startsWith("--") ? varName.substring(2) : varName;
					colorVal = tm.getCustomCssVars().get(cleanVar);
					if (colorVal != null && colorVal.contains("|")) {
						colorVal = colorVal.split("\\|", 2)[0];
					}
				}
				if (colorVal != null) {
					hex = colorVal;
				}
			}
		}
		
		if (chipHex != null) {
			chipHex.setText(ColorUtils.formatColor(hex, "hex"));
		}
		if (chipHexad != null) {
			chipHexad.setText(ColorUtils.formatColor(hex, "hexad"));
		}
		if (chipRgb != null) {
			chipRgb.setText(ColorUtils.formatColor(hex, "rgb"));
		}
		if (chipRgba != null) {
			chipRgba.setText(ColorUtils.formatColor(hex, "rgba"));
		}
	}
	
	public void setFullScreenNoMargins(boolean isFullScreenNoMargins) {
		this.isFullScreenNoMargins = isFullScreenNoMargins;
	}
	
	@Override
	public void onStart() {
		super.onStart();
		Dialog dialog = getDialog();
		if (dialog != null && dialog.getWindow() != null) {
			Window window = dialog.getWindow();
			window.setLayout(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
			);
		}
	}
	
	private class ThemeColorAdapter extends RecyclerView.Adapter<ThemeColorAdapter.ViewHolder> {
		private final List<String> themeKeys = new ArrayList<>();
		private final ThemeManager themeManager;
		
		public ThemeColorAdapter() {
			this.themeManager = ProjectAssetManager.getInstance().getThemeManager();
			
			themeKeys.add("primaryColor");
			themeKeys.add("secondaryColor");
			themeKeys.add("accentColor");
			themeKeys.add("bodyBackground");
			themeKeys.add("bodyColor");
			themeKeys.add("linkColor");
			themeKeys.add("borderColor");
			
			if (themeManager != null) {
				themeKeys.addAll(themeManager.getCustomCssVars().keySet());
			}
		}
		
		@NonNull
		@Override
		public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attr, parent, false);
			return new ViewHolder(view);
		}
		
		@Override
		public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
			String key = themeKeys.get(position);
			
			String lightColorHex = "#FFFFFF";
			String darkColorHex = "#121212";
			
			if (themeManager != null) {
				if (themeManager.getAllStyles().containsKey(key)) {
					lightColorHex = themeManager.getStyleForTheme("light", key);
					darkColorHex = themeManager.getStyleForTheme("dark", key);
				} else {
					String val = themeManager.getCustomCssVars().get(key);
					if (val != null && val.contains("|")) {
						String[] parts = val.split("\\|", 2);
						lightColorHex = parts[0];
						darkColorHex = parts[1];
					} else {
						lightColorHex = val != null ? val : "#FFFFFF";
						darkColorHex = lightColorHex;
					}
				}
			}
			
			holder.tvAttrName.setText(key);
			int lightColor = ColorUtils.parseHexColorSafe(lightColorHex);
			int darkColor = ColorUtils.parseHexColorSafe(darkColorHex);
			
			holder.lightContainer.setBackgroundColor(lightColor);
			holder.darkContainer.setBackgroundColor(darkColor);
			
			holder.lightHex.setText(lightColorHex);
			holder.darkHex.setText(darkColorHex);
			
			int lightTextColor = ColorUtils.getContrastingTextColor(lightColor);
			int darkTextColor = ColorUtils.getContrastingTextColor(darkColor);
			
			holder.lightHex.setTextColor(lightTextColor);
			holder.lightTtl.setTextColor(lightTextColor);
			holder.darkHex.setTextColor(darkTextColor);
			holder.darkTtl.setTextColor(darkTextColor);
			
			String cssVar = formatThemeKeyAsCssVar(key);
			String selectedColorVal = viewModel.getSelectedColor().getValue();
			boolean isSelected = cssVar.equalsIgnoreCase(selectedColorVal);
			
			holder.checkedImg.setVisibility(isSelected ? View.VISIBLE : View.GONE);
			
			holder.itemView.setOnClickListener(v -> {
				viewModel.selectColor(cssVar);
			});
		}
		
		@Override
		public int getItemCount() {
			return themeKeys.size();
		}
		
		class ViewHolder extends RecyclerView.ViewHolder {
			View lightContainer;
			View darkContainer;
			TextView tvAttrName;
			TextView lightHex;
			TextView darkHex;
			TextView lightTtl;
			TextView darkTtl;
			ImageView checkedImg;
			
			public ViewHolder(@NonNull View itemView) {
				super(itemView);
				lightContainer = itemView.findViewById(R.id.light_container);
				darkContainer = itemView.findViewById(R.id.dark_container);
				tvAttrName = itemView.findViewById(R.id.tvAttrName);
				lightHex = itemView.findViewById(R.id.light_hex);
				darkHex = itemView.findViewById(R.id.dark_hex);
				lightTtl = itemView.findViewById(R.id.light_ttl);
				darkTtl = itemView.findViewById(R.id.dark_ttl);
				checkedImg = itemView.findViewById(R.id.checked_img);
			}
		}
	}
}
