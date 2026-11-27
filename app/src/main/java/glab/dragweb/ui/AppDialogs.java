package glab.dragweb.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import glab.dragweb.R;
import glab.dragweb.colorpicker.ColorPickerDialogFragment;
import glab.dragweb.data.DesignDataManager;
import glab.dragweb.databinding.DialogChoiceListBinding;
import glab.dragweb.databinding.DialogImageSelectorBinding;
import glab.dragweb.databinding.DialogInputNumberUnitBinding;
import glab.dragweb.databinding.DialogInputTextBinding;
import glab.dragweb.databinding.DialogSelectorPickerBinding;
import glab.dragweb.databinding.DialogSetupListBinding;
import glab.dragweb.databinding.DialogSetupWidgetItemsBinding;
import glab.dragweb.databinding.DialogUnitFourBinding;
import glab.dragweb.databinding.DialogVariableSelectorBinding;
import glab.dragweb.databinding.ItemImageAssetBinding;
import glab.dragweb.util.FileUtil;

/**
 * Custom-layout Material 3 dialogs backed by ViewBinding and rounded-corner TextInputLayouts.
 */
public final class AppDialogs {

    public interface OnText {
        void onText(String value);
    }

    public interface OnNeutralAction {
        void onNeutral(String currentText);
    }

    public interface OnIndex {
        void onSelected(int index, String label);
    }

    public interface Validator {
        String validate(String value);
    }

    public interface OnWidgetListItems {
        void onItems(List<String> items, List<String> icons);
    }

    private AppDialogs() {}

    public static void confirm(Context context, String title, String message, String positiveLabel, Runnable onConfirm) {
        if (context == null) return;
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveLabel != null ? positiveLabel : "OK", (d, w) -> {
                    if (onConfirm != null) onConfirm.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void singleChoice(Context context, String title, String[] options, OnIndex callback) {
        if (context == null || options == null) return;
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setItems(options, (d, which) -> {
                    if (callback != null && which >= 0 && which < options.length) {
                        callback.onSelected(which, options[which]);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void textInput(Context context, String title, String hint, String initialValue, OnText callback) {
        showTextInput(context, title, hint, initialValue, false, false, null, null, null, null, callback);
    }

    public static void showTextInput(
            Context context,
            String title,
            String hint,
            String initialValue,
            boolean isNumeric,
            boolean multiline,
            List<String> suggestions,
            String neutralText,
            OnNeutralAction neutralAction,
            Validator validator,
            OnText callback
    ) {
        if (context == null) return;
        DialogInputTextBinding b = DialogInputTextBinding.inflate(LayoutInflater.from(context));
        b.tilInput.setHint(hint != null ? hint : "");
        b.etInput.setText(initialValue != null ? initialValue : "");

        if (isNumeric) {
            b.etInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            b.etInput.setSingleLine(true);
        } else if (multiline) {
            b.etInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            b.etInput.setMinLines(4);
            b.etInput.setMaxLines(10);
            b.etInput.setGravity(Gravity.TOP | Gravity.START);
        } else {
            b.etInput.setSingleLine(true);
        }

        if (suggestions != null && !suggestions.isEmpty()) {
            b.scrollSuggestions.setVisibility(View.VISIBLE);
            for (String s : suggestions) {
                if (s == null || s.trim().isEmpty()) continue;
                Chip chip = new Chip(context);
                chip.setText(s);
                chip.setOnClickListener(v -> {
                    b.etInput.setText(s);
                    if (b.etInput.getText() != null) {
                        b.etInput.setSelection(b.etInput.getText().length());
                    }
                });
                b.chipGroupSuggestions.addView(chip);
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(title != null ? title : "")
                .setView(b.getRoot())
                .setPositiveButton("OK", null)
                .setNegativeButton("Cancel", null);

        if (neutralText != null && neutralAction != null) {
            builder.setNeutralButton(neutralText, (d, w) -> {
                String txt = b.etInput.getText() != null ? b.etInput.getText().toString() : "";
                neutralAction.onNeutral(txt);
            });
        }

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String val = b.etInput.getText() != null ? b.etInput.getText().toString() : "";
            if (validator != null) {
                String err = validator.validate(val);
                if (err != null) {
                    b.tilInput.setError(err);
                    return;
                }
            }
            b.tilInput.setError(null);
            dialog.dismiss();
            if (callback != null) callback.onText(val);
        }));
        dialog.show();
        requestFocusAndKeyboard(dialog, b.etInput);
    }

    public static void showChoiceInput(
            Context context,
            String title,
            String[] options,
            String initialValue,
            String neutralText,
            OnNeutralAction neutralAction,
            OnText callback
    ) {
        if (context == null) return;
        DialogChoiceListBinding b = DialogChoiceListBinding.inflate(LayoutInflater.from(context));
        final String[] safeOptions = options != null ? options : new String[0];
        final String init = initialValue != null ? initialValue : "";
        b.etFilter.setText(init);

        final AlertDialog[] holder = new AlertDialog[1];
        Runnable rebuildList = () -> {
            String filter = b.etFilter.getText() != null ? b.etFilter.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
            b.radioGroupOptions.removeAllViews();
            for (String opt : safeOptions) {
                if (!filter.isEmpty() && !opt.toLowerCase(Locale.ROOT).contains(filter) && !opt.equals(init)) {
                    continue;
                }
                RadioButton rb = new RadioButton(context);
                rb.setText(opt);
                rb.setChecked(opt.equals(init));
                int pad = dp(context, 10);
                rb.setPadding(pad, pad, pad, pad);
                rb.setOnClickListener(v -> {
                    if (holder[0] != null) holder[0].dismiss();
                    if (callback != null) callback.onText(opt);
                });
                b.radioGroupOptions.addView(rb);
            }
        };
        rebuildList.run();

        b.etFilter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { rebuildList.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(title != null ? title : "Select Option")
                .setView(b.getRoot())
                .setPositiveButton("OK", (d, w) -> {
                    String typed = b.etFilter.getText() != null ? b.etFilter.getText().toString().trim() : "";
                    if (callback != null) callback.onText(typed);
                })
                .setNegativeButton("Cancel", null);

        if (neutralText != null && neutralAction != null) {
            builder.setNeutralButton(neutralText, (d, w) -> {
                String typed = b.etFilter.getText() != null ? b.etFilter.getText().toString().trim() : "";
                neutralAction.onNeutral(typed);
            });
        }

        holder[0] = builder.create();
        holder[0].show();
    }

    public static void showSelectorPicker(
            Context context,
            String title,
            String hint,
            String initialValue,
            Map<String, List<String>> sections,
            boolean spaceSeparatedChips,
            OnText callback
    ) {
        if (context == null) return;
        DialogSelectorPickerBinding b = DialogSelectorPickerBinding.inflate(LayoutInflater.from(context));
        b.tilSelector.setHint(hint != null ? hint : "Value");
        b.etSelector.setText(initialValue != null ? initialValue : "");

        if (sections != null) {
            for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
                List<String> items = entry.getValue();
                if (items == null || items.isEmpty()) continue;

                TextView header = new TextView(context);
                header.setText(entry.getKey());
                header.setTextSize(12f);
                header.setTypeface(null, android.graphics.Typeface.BOLD);
                header.setTextColor(MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.GRAY));
                int topPad = dp(context, 10);
                header.setPadding(0, topPad, 0, dp(context, 4));
                b.sectionsContainer.addView(header);

                ChipGroup group = new ChipGroup(context);
                for (String item : items) {
                    if (item == null || item.trim().isEmpty()) continue;
                    Chip chip = new Chip(context);
                    chip.setText(item);
                    chip.setOnClickListener(v -> {
                        if (spaceSeparatedChips) {
                            String curr = b.etSelector.getText() != null ? b.etSelector.getText().toString().trim() : "";
                            List<String> tokens = new ArrayList<>(Arrays.asList(curr.isEmpty() ? new String[0] : curr.split("\\s+")));
                            if (!tokens.contains(item)) {
                                tokens.add(item);
                                b.etSelector.setText(String.join(" ", tokens));
                            }
                        } else {
                            b.etSelector.setText(item);
                        }
                        if (b.etSelector.getText() != null) {
                            b.etSelector.setSelection(b.etSelector.getText().length());
                        }
                    });
                    group.addView(chip);
                }
                b.sectionsContainer.addView(group);
            }
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title != null ? title : "Select")
                .setView(b.getRoot())
                .setPositiveButton("OK", (d, w) -> {
                    String val = b.etSelector.getText() != null ? b.etSelector.getText().toString().trim() : "";
                    if (callback != null) callback.onText(val);
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
        requestFocusAndKeyboard(dialog, b.etSelector);
    }

    public static void showIconPicker(Context context, String initialValue, OnText callback) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        sections.put("Material Icons", Arrays.asList(
                "home", "menu", "search", "settings", "add", "edit", "delete", "close",
                "check", "arrow_back", "arrow_forward", "favorite", "star", "share", "person", "email"
        ));
        sections.put("FontAwesome / Bootstrap", Arrays.asList(
                "fa-solid fa-house", "fa-solid fa-user", "fa-solid fa-gear", "fa-solid fa-magnifying-glass",
                "bi bi-house", "bi bi-person", "bi bi-gear", "bi bi-search"
        ));
        showSelectorPicker(context, "Pick Icon", "Icon name or class", initialValue, sections, false, callback);
    }

    public static void showImageSelector(Context context, String projectId, String initialValue, OnText callback) {
        if (context == null) return;
        DialogImageSelectorBinding b = DialogImageSelectorBinding.inflate(LayoutInflater.from(context));
        b.etImagePath.setText(initialValue != null ? initialValue : "");

        final List<java.io.File> imageFiles = new ArrayList<>();
        final List<String> relativePaths = new ArrayList<>();
        if (projectId != null && !projectId.trim().isEmpty()) {
            java.io.File assetsDir = new java.io.File(FileUtil.getDragWebDir(context), "projects/" + projectId + "/assets");
            collectAssetImages(assetsDir, "", imageFiles, relativePaths);
        }

        if (imageFiles.isEmpty()) {
            b.tvEmptyImages.setVisibility(View.VISIBLE);
            b.rvAssetImages.setVisibility(View.GONE);
        } else {
            b.tvEmptyImages.setVisibility(View.GONE);
            b.rvAssetImages.setVisibility(View.VISIBLE);
            b.rvAssetImages.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(context, 3));
        }

        final AlertDialog[] holder = new AlertDialog[1];
        androidx.recyclerview.widget.RecyclerView.Adapter<?> adapter = new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                ItemImageAssetBinding ib = ItemImageAssetBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(ib.getRoot()) {};
            }

            @Override
            public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder vh, int position) {
                ItemImageAssetBinding ib = ItemImageAssetBinding.bind(vh.itemView);
                java.io.File file = imageFiles.get(position);
                String rel = relativePaths.get(position);
                ib.tvImageName.setText(rel);

                android.graphics.Bitmap thumb = decodeSampledBitmap(file.getAbsolutePath(), 180, 180);
                if (thumb != null) {
                    ib.imgThumb.setImageBitmap(thumb);
                    ib.imgThumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                } else {
                    ib.imgThumb.setImageResource(R.drawable.file_code);
                    ib.imgThumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                }

                ib.cardImage.setOnClickListener(v -> {
                    if (holder[0] != null) holder[0].dismiss();
                    if (callback != null) callback.onText(rel);
                });
            }

            @Override
            public int getItemCount() {
                return imageFiles.size();
            }
        };
        b.rvAssetImages.setAdapter(adapter);

        holder[0] = new MaterialAlertDialogBuilder(context)
                .setTitle("Select Image from Assets")
                .setView(b.getRoot())
                .setPositiveButton("OK", (d, w) -> {
                    String path = b.etImagePath.getText() != null ? b.etImagePath.getText().toString().trim() : "";
                    if (callback != null) callback.onText(path);
                })
                .setNegativeButton("Cancel", null)
                .create();
        holder[0].show();
    }

    private static void collectAssetImages(java.io.File dir, String relPrefix, List<java.io.File> outFiles, List<String> outRelPaths) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        java.io.File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (java.io.File f : children) {
            String rel = relPrefix.isEmpty() ? f.getName() : relPrefix + "/" + f.getName();
            if (f.isDirectory()) {
                collectAssetImages(f, rel, outFiles, outRelPaths);
            } else if (f.isFile()) {
                String lower = f.getName().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                        || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".svg")
                        || lower.endsWith(".ico") || lower.endsWith(".bmp")) {
                    outFiles.add(f);
                    outRelPaths.add(rel);
                }
            }
        }
    }

    private static android.graphics.Bitmap decodeSampledBitmap(String path, int reqWidth, int reqHeight) {
        try {
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(path, opts);
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;
            int inSampleSize = 1;
            while ((opts.outHeight / inSampleSize) > reqHeight && (opts.outWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
            opts.inSampleSize = inSampleSize;
            opts.inJustDecodeBounds = false;
            return android.graphics.BitmapFactory.decodeFile(path, opts);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void showUnitInput(Context context, String title, String initialValue, String[] units, OnText callback) {
        if (context == null) return;
        DialogInputNumberUnitBinding b = DialogInputNumberUnitBinding.inflate(LayoutInflater.from(context));
        String[] safeUnits = (units != null && units.length > 0) ? units : new String[]{"px", "%", "em", "rem", "vw", "vh", "auto"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, safeUnits);
        b.spUnit.setAdapter(adapter);

        String init = initialValue != null ? initialValue.trim() : "";
        String numPart = init;
        int unitIdx = 0;
        if ("auto".equalsIgnoreCase(init)) {
            numPart = "";
            for (int i = 0; i < safeUnits.length; i++) {
                if ("auto".equalsIgnoreCase(safeUnits[i])) unitIdx = i;
            }
        } else {
            Matcher m = Pattern.compile("^([-+]?\\d*\\.?\\d+)\\s*([a-zA-Z%]+)?$").matcher(init);
            if (m.matches()) {
                numPart = m.group(1) != null ? m.group(1) : "";
                String u = m.group(2) != null ? m.group(2) : "";
                for (int i = 0; i < safeUnits.length; i++) {
                    if (safeUnits[i].equalsIgnoreCase(u)) {
                        unitIdx = i;
                        break;
                    }
                }
            }
        }
        b.etNumber.setText(numPart);
        b.spUnit.setSelection(unitIdx);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title != null ? title : "Size")
                .setView(b.getRoot())
                .setPositiveButton("OK", (d, w) -> {
                    String u = b.spUnit.getSelectedItem() != null ? b.spUnit.getSelectedItem().toString() : "px";
                    String n = b.etNumber.getText() != null ? b.etNumber.getText().toString().trim() : "";
                    String out = "auto".equalsIgnoreCase(u) ? "auto" : (n.isEmpty() ? "0" + u : n + u);
                    if (callback != null) callback.onText(out);
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
        requestFocusAndKeyboard(dialog, b.etNumber);
    }

    public static void showFourValueInput(Context context, String title, String initialValue, String[] units, OnText callback) {
        if (context == null) return;
        DialogUnitFourBinding b = DialogUnitFourBinding.inflate(LayoutInflater.from(context));
        String[] safeUnits = (units != null && units.length > 0) ? units : new String[]{"px", "rem", "%", "em", "auto"};
        final String[] selectedUnit = {safeUnits[0]};

        String[] parts = initialValue != null ? initialValue.trim().split("\\s+") : new String[0];
        String[] nums = {"0", "0", "0", "0"};
        if (parts.length == 1 && !parts[0].isEmpty()) {
            String[] p = splitNumUnit(parts[0]);
            Arrays.fill(nums, p[0]);
            if (!p[1].isEmpty()) selectedUnit[0] = p[1];
        } else if (parts.length == 2) {
            String[] tb = splitNumUnit(parts[0]);
            String[] lr = splitNumUnit(parts[1]);
            nums[0] = nums[2] = tb[0];
            nums[1] = nums[3] = lr[0];
            if (!tb[1].isEmpty()) selectedUnit[0] = tb[1];
        } else if (parts.length >= 4) {
            for (int i = 0; i < 4; i++) {
                String[] p = splitNumUnit(parts[i]);
                nums[i] = p[0];
                if (i == 0 && !p[1].isEmpty()) selectedUnit[0] = p[1];
            }
        }
        b.etTop.setText(nums[0]);
        b.etRight.setText(nums[1]);
        b.etBottom.setText(nums[2]);
        b.etLeft.setText(nums[3]);

        for (String u : safeUnits) {
            Chip chip = new Chip(context);
            chip.setText(u);
            chip.setCheckable(true);
            chip.setChecked(u.equalsIgnoreCase(selectedUnit[0]));
            chip.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) selectedUnit[0] = u;
            });
            b.chipGroupUnits.addView(chip);
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(title != null ? title : "Spacing")
                .setView(b.getRoot())
                .setPositiveButton("OK", (d, w) -> {
                    String u = selectedUnit[0];
                    String t = formatNumUnit(b.etTop.getText(), u);
                    String r = formatNumUnit(b.etRight.getText(), u);
                    String bt = formatNumUnit(b.etBottom.getText(), u);
                    String l = formatNumUnit(b.etLeft.getText(), u);
                    String out = (t.equals(r) && r.equals(bt) && bt.equals(l)) ? t : (t + " " + r + " " + bt + " " + l);
                    if (callback != null) callback.onText(out);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void showColorInput(Context context, String title, String initialValue, List<String> suggestions, OnText callback) {
        if (context instanceof FragmentActivity) {
            ColorPickerDialogFragment frag = new ColorPickerDialogFragment();
            frag.setOnColorSelectedListener(color -> {
                if (callback != null) callback.onText(color);
            });
            frag.show(((FragmentActivity) context).getSupportFragmentManager(), "ColorPickerDialog");
            return;
        }
        List<String> colors = (suggestions != null && !suggestions.isEmpty())
                ? suggestions
                : Arrays.asList("#000000", "#FFFFFF", "#F44336", "#2196F3", "#4CAF50", "#FF9800", "transparent");
        showTextInput(context, title != null ? title : "Select Color", "#RRGGBB", initialValue, false, false, colors, null, null, null, callback);
    }

    public static void showArrayListBuilder(Context context, String title, String initialJson, OnText callback) {
        if (context == null) return;
        DialogSetupListBinding b = DialogSetupListBinding.inflate(LayoutInflater.from(context));
        final List<String> items = new ArrayList<>();
        if (initialJson != null && initialJson.trim().startsWith("[")) {
            try {
                List<Object> parsed = new Gson().fromJson(initialJson, new TypeToken<List<Object>>() {}.getType());
                if (parsed != null) {
                    for (Object o : parsed) {
                        if (o instanceof String) items.add("\"" + o + "\"");
                        else if (o != null) items.add(String.valueOf(o));
                    }
                }
            } catch (Exception ignored) {}
        }

        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                b.itemsContainer.removeAllViews();
                for (int i = 0; i < items.size(); i++) {
                    final int idx = i;
                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(context, 4), 0, dp(context, 4));

                    TextInputLayout til = new TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
                    til.setBoxCornerRadii(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
                    TextInputEditText et = new TextInputEditText(til.getContext());
                    et.setText(items.get(idx));
                    et.addTextChangedListener(new TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                        @Override public void onTextChanged(CharSequence s, int st, int b1, int c) { items.set(idx, s.toString()); }
                        @Override public void afterTextChanged(Editable s) {}
                    });
                    til.addView(et);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    row.addView(til, lp);

                    ImageButton del = new ImageButton(context);
                    del.setImageResource(R.drawable.ic_mtrl_delete);
                    del.setBackgroundColor(Color.TRANSPARENT);
                    del.setOnClickListener(v -> {
                        items.remove(idx);
                        run();
                    });
                    row.addView(del);
                    b.itemsContainer.addView(row);
                }
            }
        };
        refresh.run();

        b.btnAddString.setOnClickListener(v -> { items.add("\"text\""); refresh.run(); });
        b.btnAddNumber.setOnClickListener(v -> { items.add("0"); refresh.run(); });
        b.btnAddBoolean.setOnClickListener(v -> { items.add("true"); refresh.run(); });
        b.btnAddObject.setOnClickListener(v -> { items.add("{}"); refresh.run(); });

        new MaterialAlertDialogBuilder(context)
                .setTitle(title != null ? title : "Setup List / Array")
                .setView(b.getRoot())
                .setPositiveButton("Save", (d, w) -> {
                    String out = "[" + String.join(", ", items) + "]";
                    if (callback != null) callback.onText(out);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void showWidgetListItemsBuilder(
            Context context,
            String title,
            List<String> initialItems,
            List<String> initialIcons,
            boolean supportsIcons,
            OnWidgetListItems onLive,
            OnWidgetListItems onConfirm
    ) {
        if (context == null) return;
        DialogSetupWidgetItemsBinding b = DialogSetupWidgetItemsBinding.inflate(LayoutInflater.from(context));
        final List<String> items = initialItems != null ? new ArrayList<>(initialItems) : new ArrayList<>();
        final List<String> icons = initialIcons != null ? new ArrayList<>(initialIcons) : new ArrayList<>();
        while (icons.size() < items.size()) icons.add("");

        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                b.itemsContainer.removeAllViews();
                for (int i = 0; i < items.size(); i++) {
                    final int idx = i;
                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(context, 4), 0, dp(context, 4));

                    TextInputLayout til = new TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
                    til.setBoxCornerRadii(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
                    til.setHint("Item " + (idx + 1));
                    TextInputEditText et = new TextInputEditText(til.getContext());
                    et.setText(items.get(idx));
                    et.addTextChangedListener(new TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                        @Override public void onTextChanged(CharSequence s, int st, int b1, int c) {
                            items.set(idx, s.toString());
                            if (onLive != null) onLive.onItems(items, icons);
                        }
                        @Override public void afterTextChanged(Editable s) {}
                    });
                    til.addView(et);
                    row.addView(til, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    if (supportsIcons) {
                        com.google.android.material.button.MaterialButton btnIcon = new com.google.android.material.button.MaterialButton(context);
                        String ic = icons.get(idx);
                        btnIcon.setText(ic.isEmpty() ? "Icon" : ic);
                        btnIcon.setOnClickListener(v -> showIconPicker(context, icons.get(idx), chosen -> {
                            icons.set(idx, chosen);
                            btnIcon.setText(chosen.isEmpty() ? "Icon" : chosen);
                            if (onLive != null) onLive.onItems(items, icons);
                        }));
                        row.addView(btnIcon);
                    }

                    ImageButton del = new ImageButton(context);
                    del.setImageResource(R.drawable.ic_mtrl_delete);
                    del.setBackgroundColor(Color.TRANSPARENT);
                    del.setOnClickListener(v -> {
                        items.remove(idx);
                        if (idx < icons.size()) icons.remove(idx);
                        run();
                        if (onLive != null) onLive.onItems(items, icons);
                    });
                    row.addView(del);
                    b.itemsContainer.addView(row);
                }
            }
        };
        refresh.run();

        b.btnAddItem.setOnClickListener(v -> {
            items.add("Item " + (items.size() + 1));
            icons.add("");
            refresh.run();
            if (onLive != null) onLive.onItems(items, icons);
        });

        new MaterialAlertDialogBuilder(context)
                .setTitle(title != null ? title : "Setup Items")
                .setView(b.getRoot())
                .setPositiveButton("Save", (d, w) -> {
                    if (onConfirm != null) onConfirm.onItems(items, icons);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void showVariableSelector(
            Context context,
            String filename,
            String filterKey,
            boolean isSetter,
            Set<String> usedConstVars,
            String initialValue,
            OnText callback
    ) {
        if (context == null) return;
        DialogVariableSelectorBinding b = DialogVariableSelectorBinding.inflate(LayoutInflater.from(context));
        int defaultType = "varInt".equals(filterKey) ? 1 : ("varBool".equals(filterKey) ? 0 : ("varStr".equals(filterKey) ? 2 : -1));
        final int[] createType = {defaultType >= 0 ? defaultType : 2};

        String[] typeLabels = {"Bool", "Number", "String"};
        int[] typeValues = {0, 1, 2};
        for (int i = 0; i < typeLabels.length; i++) {
            final int tVal = typeValues[i];
            Chip chip = new Chip(context);
            chip.setText(typeLabels[i]);
            chip.setCheckable(true);
            chip.setChecked(tVal == createType[0]);
            chip.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) createType[0] = tVal;
            });
            b.chipGroupVarTypes.addView(chip);
        }

        final AlertDialog[] holder = new AlertDialog[1];
        Runnable reload = new Runnable() {
            @Override
            public void run() {
                b.listContainer.removeAllViews();
                List<String> vars = new ArrayList<>();
                if (defaultType >= 0) {
                    vars.addAll(DesignDataManager.getVariablesByType(filename, defaultType));
                } else {
                    for (android.util.Pair<Integer, String> pair : DesignDataManager.getVariables(filename)) {
                        if (pair != null && pair.second != null) vars.add(pair.second);
                    }
                }
                for (String vName : vars) {
                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(context, 4), dp(context, 6), dp(context, 4), dp(context, 6));

                    RadioButton rb = new RadioButton(context);
                    boolean disabled = isSetter && usedConstVars != null && usedConstVars.contains(vName) && !vName.equals(initialValue);
                    rb.setText(disabled ? vName + " (in use)" : vName);
                    rb.setEnabled(!disabled);
                    rb.setChecked(vName.equals(initialValue));
                    rb.setOnClickListener(v -> {
                        if (holder[0] != null) holder[0].dismiss();
                        if (callback != null) callback.onText(vName);
                    });
                    row.addView(rb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    ImageButton del = new ImageButton(context);
                    del.setImageResource(R.drawable.ic_mtrl_delete);
                    del.setBackgroundColor(Color.TRANSPARENT);
                    del.setOnClickListener(v -> {
                        DesignDataManager.removeVariable(filename, vName);
                        run();
                    });
                    row.addView(del);
                    b.listContainer.addView(row);
                }
            }
        };
        reload.run();

        b.btnAddNew.setOnClickListener(v -> {
            String raw = b.etNewName.getText() != null ? b.etNewName.getText().toString().trim() : "";
            if (raw.isEmpty()) {
                b.tilNewName.setError("Enter variable name");
                return;
            }
            b.tilNewName.setError(null);
            DesignDataManager.addVariable(filename, createType[0], raw);
            b.etNewName.setText("");
            reload.run();
        });

        holder[0] = new MaterialAlertDialogBuilder(context)
                .setTitle("Select Variable")
                .setView(b.getRoot())
                .setNegativeButton("Cancel", null)
                .create();
        holder[0].show();
    }

    public static void showListSelector(Context context, String filename, String initialValue, OnText callback) {
        if (context == null) return;
        DialogVariableSelectorBinding b = DialogVariableSelectorBinding.inflate(LayoutInflater.from(context));
        b.tilNewName.setHint("New list name");
        b.chipGroupVarTypes.setVisibility(View.GONE);

        final AlertDialog[] holder = new AlertDialog[1];
        Runnable reload = new Runnable() {
            @Override
            public void run() {
                b.listContainer.removeAllViews();
                List<String> lists = DesignDataManager.getAllLists(filename);
                for (String lName : lists) {
                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(context, 4), dp(context, 6), dp(context, 4), dp(context, 6));

                    RadioButton rb = new RadioButton(context);
                    rb.setText(lName);
                    rb.setChecked(lName.equals(initialValue));
                    rb.setOnClickListener(v -> {
                        if (holder[0] != null) holder[0].dismiss();
                        if (callback != null) callback.onText(lName);
                    });
                    row.addView(rb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    ImageButton del = new ImageButton(context);
                    del.setImageResource(R.drawable.ic_mtrl_delete);
                    del.setBackgroundColor(Color.TRANSPARENT);
                    del.setOnClickListener(v -> {
                        DesignDataManager.removeList(filename, lName);
                        run();
                    });
                    row.addView(del);
                    b.listContainer.addView(row);
                }
            }
        };
        reload.run();

        b.btnAddNew.setOnClickListener(v -> {
            String raw = b.etNewName.getText() != null ? b.etNewName.getText().toString().trim() : "";
            if (raw.isEmpty()) {
                b.tilNewName.setError("Enter list name");
                return;
            }
            b.tilNewName.setError(null);
            DesignDataManager.addList(filename, 2, raw);
            b.etNewName.setText("");
            reload.run();
        });

        holder[0] = new MaterialAlertDialogBuilder(context)
                .setTitle("Select List")
                .setView(b.getRoot())
                .setNegativeButton("Cancel", null)
                .create();
        holder[0].show();
    }

    private static String[] splitNumUnit(String token) {
        if (token == null) return new String[]{"0", ""};
        Matcher m = Pattern.compile("^([-+]?\\d*\\.?\\d+)\\s*([a-zA-Z%]+)?$").matcher(token.trim());
        if (m.matches()) {
            return new String[]{m.group(1) != null ? m.group(1) : "0", m.group(2) != null ? m.group(2) : ""};
        }
        return new String[]{"0", ""};
    }

    private static String formatNumUnit(Editable ed, String unit) {
        if ("auto".equalsIgnoreCase(unit)) return "auto";
        String n = ed != null ? ed.toString().trim() : "";
        return (n.isEmpty() ? "0" : n) + unit;
    }

    private static void requestFocusAndKeyboard(AlertDialog dialog, View inputView) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        inputView.post(inputView::requestFocus);
    }

    private static int dp(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    public static Builder builder(Context context) {
        return new Builder(context);
    }

    public static final class Builder {
        private final Context context;
        private String title = "";
        private String hint = "";
        private String initialValue = "";
        private String[] options;
        private String[] units;
        private List<String> suggestions;
        private boolean multiline = false;
        private boolean isNumeric = false;
        private String neutralText;
        private OnNeutralAction neutralAction;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(String title) { this.title = title; return this; }
        public Builder setHint(String hint) { this.hint = hint; return this; }
        public Builder setInitialValue(String value) { this.initialValue = value != null ? value : ""; return this; }
        public Builder setOptions(String[] options) { this.options = options; return this; }
        public Builder setUnits(String[] units) { this.units = units; return this; }
        public Builder setSuggestions(List<String> suggestions) { this.suggestions = suggestions; return this; }
        public Builder setMultiline(boolean multiline) { this.multiline = multiline; return this; }
        public Builder setIsNumeric(boolean isNumeric) { this.isNumeric = isNumeric; return this; }
        public Builder setNeutralButton(String text, OnNeutralAction action) {
            this.neutralText = text;
            this.neutralAction = action;
            return this;
        }

        public void showTextInput(OnText callback) {
            AppDialogs.showTextInput(context, title, hint, initialValue, isNumeric, multiline, suggestions, neutralText, neutralAction, null, callback);
        }

        public void showTextInputWithValidation(Validator validator, OnText callback) {
            AppDialogs.showTextInput(context, title, hint, initialValue, isNumeric, multiline, suggestions, neutralText, neutralAction, validator, callback);
        }

        public void showChoiceInput(OnText callback) {
            AppDialogs.showChoiceInput(context, title, options, initialValue, neutralText, neutralAction, callback);
        }

        public void showUnitInput(String propName, OnText callback) {
            String effectiveTitle = (title != null && !title.isEmpty()) ? title : propName;
            AppDialogs.showUnitInput(context, effectiveTitle, initialValue, units, callback);
        }

        public void showFourValueInput(String propName, OnText callback) {
            String effectiveTitle = (title != null && !title.isEmpty()) ? title : propName;
            AppDialogs.showFourValueInput(context, effectiveTitle, initialValue, units, callback);
        }

        public void showColorInput(OnText callback) {
            AppDialogs.showColorInput(context, title, initialValue, suggestions, callback);
        }

        public void showIconPicker(OnText callback) {
            AppDialogs.showIconPicker(context, initialValue, callback);
        }

        public void showClassChipsInput(List<String> classes, OnText callback) {
            Map<String, List<String>> sections = new LinkedHashMap<>();
            sections.put("Project Classes", classes);
            AppDialogs.showSelectorPicker(context, title, hint, initialValue, sections, true, callback);
        }

        public void showConvertViewDialog(List<String> tagOptions, String current, OnText callback) {
            Map<String, List<String>> sections = new LinkedHashMap<>();
            sections.put("HTML Tags", tagOptions);
            AppDialogs.showSelectorPicker(context, title, "HTML tag", current, sections, false, callback);
        }

        public void showIdSelectorInput(List<String> ids, OnText callback) {
            Map<String, List<String>> sections = new LinkedHashMap<>();
            sections.put("Element IDs", ids);
            AppDialogs.showSelectorPicker(context, title, "Element ID", initialValue, sections, false, callback);
        }

        public void showClassSelectorInput(List<String> classes, OnText callback) {
            Map<String, List<String>> sections = new LinkedHashMap<>();
            sections.put("CSS Classes", classes);
            AppDialogs.showSelectorPicker(context, title, "CSS class", initialValue, sections, false, callback);
        }

        public void showSelectorInput(List<String> ids, List<String> classes, List<String> tags, OnText callback) {
            Map<String, List<String>> sections = new LinkedHashMap<>();
            List<String> formattedIds = new ArrayList<>();
            if (ids != null) for (String id : ids) formattedIds.add(id.startsWith("#") ? id : "#" + id);
            List<String> formattedClasses = new ArrayList<>();
            if (classes != null) for (String c : classes) formattedClasses.add(c.startsWith(".") ? c : "." + c);
            sections.put("IDs", formattedIds);
            sections.put("Classes", formattedClasses);
            sections.put("Tags", tags);
            AppDialogs.showSelectorPicker(context, title, "CSS selector", initialValue, sections, false, callback);
        }

        public void showArrayListBuilder(String currentJson, OnText callback) {
            AppDialogs.showArrayListBuilder(context, title, currentJson, callback);
        }

        public void showVariableSelector(String filename, String filterKey, boolean isSetter, Set<String> usedConstVars, OnText callback) {
            AppDialogs.showVariableSelector(context, filename, filterKey, isSetter, usedConstVars, initialValue, callback);
        }

        public void showListSelector(String filename, String current, OnText callback) {
            AppDialogs.showListSelector(context, filename, current, callback);
        }

        public void showWidgetListItemsBuilder(String dialogTitle, List<String> initialItems, List<String> initialIcons, boolean supportsIcons, OnWidgetListItems onLive, OnWidgetListItems onConfirm) {
            AppDialogs.showWidgetListItemsBuilder(context, dialogTitle, initialItems, initialIcons, supportsIcons, onLive, onConfirm);
        }
    }
}
