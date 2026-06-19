package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * One reusable Material 3 dialog system shared across the app.
 *
 * <p>Replaces the dozen ad-hoc {@code MaterialAlertDialogBuilder} call sites
 * scattered through {@link MainActivity}, {@link LogicBlockActivity}, etc.
 * Every dialog produced here uses the same rounded-card surface, tonal accent,
 * keyboard-friendly spacing, and edge-to-edge insets.
 */
public final class UniversalDialog {

    public interface OnTextResult { void onText(String value); }
    public interface OnChoiceResult { void onChoice(int index, String label); }
    public interface OnConfirmResult { void onConfirm(); }
    public interface OnNumberUnitResult { void onValue(float value, String unit); }
    public interface OnColorResult { void onColor(String hex); }

    private static String formatFloat(float f) {
        if (f == (int) f) return String.valueOf((int) f);
        return String.valueOf(f);
    }

    private UniversalDialog() {}

    // -------------------------------------------------------------------
    // Public builders
    // -------------------------------------------------------------------

    /** Single-line text input dialog. */
    public static void textInput(Context ctx, String title, String hint, String initial,
                                 OnTextResult onResult) {
        textInput(ctx, title, null, hint, initial, false, onResult);
    }

    /** Multi-line text input (e.g. JSON, custom JS) with rounded card surface. */
    public static void multilineInput(Context ctx, String title, String hint, String initial,
                                      OnTextResult onResult) {
        textInput(ctx, title, null, hint, initial, true, onResult);
    }

    public static void textInput(Context ctx, String title, String message, String hint,
                                 String initial, boolean multiline, OnTextResult onResult) {
        TextInputLayout til = makeInputLayout(ctx, hint);
        TextInputEditText edit = new TextInputEditText(til.getContext());
        if (multiline) {
            edit.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            edit.setMinLines(4);
            edit.setMaxLines(12);
            edit.setGravity(Gravity.TOP | Gravity.START);
            applyOutlinedFieldStyling(ctx, edit);
        } else {
            edit.setSingleLine(true);
            applyOutlinedFieldStyling(ctx, edit);
        }
        if (initial != null) edit.setText(initial);
        til.addView(edit);

        FrameWrap container = wrap(ctx, message, til);

        new MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(container.root)
            .setPositiveButton("OK", (d, w) -> {
                if (onResult != null) onResult.onText(edit.getText() == null ? "" : edit.getText().toString());
            })
            .setNegativeButton("Cancel", null)
            .setBackgroundInsetStart(dp(ctx, 24))
            .setBackgroundInsetEnd(dp(ctx, 24))
            .show();
    }

    /** Autocomplete drop-down (variable picker, font picker, etc.). */
    public static void autocompleteInput(Context ctx, String title, String hint,
                                         String initial, List<String> suggestions,
                                         OnTextResult onResult) {
        TextInputLayout til = makeInputLayout(ctx, hint);
        MaterialAutoCompleteTextView edit = new MaterialAutoCompleteTextView(til.getContext());
        edit.setSingleLine(true);
        applyOutlinedFieldStyling(ctx, edit);
        if (initial != null) edit.setText(initial);
        if (suggestions != null && !suggestions.isEmpty()) {
            edit.setAdapter(new android.widget.ArrayAdapter<>(ctx,
                android.R.layout.simple_list_item_1, suggestions));
            edit.setThreshold(1);
        }
        til.addView(edit);

        FrameWrap container = wrap(ctx, null, til);

        new MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(container.root)
            .setPositiveButton("OK", (d, w) -> {
                if (onResult != null) onResult.onText(edit.getText() == null ? "" : edit.getText().toString());
            })
            .setNegativeButton("Cancel", null)
            .setBackgroundInsetStart(dp(ctx, 24))
            .setBackgroundInsetEnd(dp(ctx, 24))
            .show();
    }

    /** Single-choice list dialog with rounded surface. */
    public static void singleChoice(Context ctx, String title, String[] items,
                                    OnChoiceResult onResult) {
        new MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setItems(items, (d, which) -> {
                if (onResult != null) onResult.onChoice(which, items[which]);
            })
            .setNegativeButton("Cancel", null)
            .setBackgroundInsetStart(dp(ctx, 24))
            .setBackgroundInsetEnd(dp(ctx, 24))
            .show();
    }

    /** Confirm/cancel dialog with optional destructive styling. */
    public static void confirm(Context ctx, String title, String message,
                               String confirmLabel, boolean destructive,
                               OnConfirmResult onResult) {
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(confirmLabel == null ? "OK" : confirmLabel, (d, w) -> {
                if (onResult != null) onResult.onConfirm();
            })
            .setBackgroundInsetStart(dp(ctx, 24))
            .setBackgroundInsetEnd(dp(ctx, 24));
        AlertDialog dlg = b.show();
        if (destructive) {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFD32F2F);
        }
    }

    /** Numeric value with a unit suffix (px / % / em / rem / vw / vh). */
    public static void numberWithUnit(Context ctx, String title, float initial,
                                      String initialUnit, OnNumberUnitResult onResult) {
        Context themed = ctx;
        LinearLayout row = new LinearLayout(themed);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextInputLayout til = makeInputLayout(themed, "Value");
        TextInputEditText edit = new TextInputEditText(til.getContext());
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        edit.setText(formatFloat(initial));
        applyOutlinedFieldStyling(themed, edit);
        til.addView(edit);
        LinearLayout.LayoutParams tilLp = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f);
        til.setLayoutParams(tilLp);

        TextInputLayout unitTil = makeInputLayout(themed, "Unit");
        MaterialAutoCompleteTextView unitField = new MaterialAutoCompleteTextView(unitTil.getContext());
        unitField.setSingleLine(true);
        unitField.setText(initialUnit == null ? "px" : initialUnit);
        unitField.setAdapter(new android.widget.ArrayAdapter<>(themed,
            android.R.layout.simple_list_item_1,
            new String[]{"px", "%", "em", "rem", "vw", "vh", "pt", "fr", "auto"}));
        unitField.setThreshold(1);
        applyOutlinedFieldStyling(themed, unitField);
        unitTil.addView(unitField);
        LinearLayout.LayoutParams unitLp = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        unitLp.leftMargin = dp(themed, 8);
        unitTil.setLayoutParams(unitLp);

        row.addView(til);
        row.addView(unitTil);

        FrameWrap container = wrap(ctx, null, row);

        new MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(container.root)
            .setPositiveButton("OK", (d, w) -> {
                float v = 0f;
                try { v = Float.parseFloat(edit.getText().toString().trim()); }
                catch (Exception ignore) {}
                String u = unitField.getText() == null ? "px" : unitField.getText().toString().trim();
                if (u.isEmpty()) u = "px";
                if (onResult != null) onResult.onValue(v, u);
            })
            .setNegativeButton("Cancel", null)
            .setBackgroundInsetStart(dp(ctx, 24))
            .setBackgroundInsetEnd(dp(ctx, 24))
            .show();
    }

    /**
     * Color picker dialog with a hex field, RGB sliders, and a live swatch.
     * No external dependencies — uses only Material 3 + plain widgets so it
     * works wherever this class is dropped.
     */
    public static void colorPicker(Context ctx, String title, String initialHex,
                                   List<String> suggestions, OnColorResult onResult) {
        int[] rgb = parseColor(initialHex);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 24);
        root.setPadding(pad, pad, pad, dp(ctx, 12));

        // 2. Live swatch
        View swatch = new View(ctx);
        GradientDrawable sw = new GradientDrawable();
        sw.setCornerRadius(dp(ctx, 12));
        sw.setColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
        sw.setStroke(dp(ctx, 1), 0x33000000);
        swatch.setBackground(sw);
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 64));
        swLp.bottomMargin = dp(ctx, 12);

        TextInputLayout hexTil = makeInputLayout(ctx, "Hex (#RRGGBB)");
        TextInputEditText hexEdit = new TextInputEditText(hexTil.getContext());
        hexEdit.setSingleLine(true);
        hexEdit.setText(formatHex(rgb));
        applyOutlinedFieldStyling(ctx, hexEdit);
        hexTil.addView(hexEdit);

        SeekBar rBar = newColorSlider(ctx, rgb[0]);
        SeekBar gBar = newColorSlider(ctx, rgb[1]);
        SeekBar bBar = newColorSlider(ctx, rgb[2]);

        // 1. Suggestions Chips
        if (suggestions != null && !suggestions.isEmpty()) {
            android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(ctx);
            scroll.setHorizontalScrollBarEnabled(false);
            com.google.android.material.chip.ChipGroup group = new com.google.android.material.chip.ChipGroup(ctx);
            group.setSingleLine(true);
            group.setSingleSelection(true);
            group.setChipSpacingHorizontal(dp(ctx, 8));
            group.setPadding(0, 0, 0, dp(ctx, 12));

            for (String s : suggestions) {
                com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(ctx);
                chip.setText(s);
                chip.setTextSize(12);
                chip.setCheckable(true);
                if (s.equalsIgnoreCase(initialHex)) chip.setChecked(true);
                chip.setOnClickListener(v -> {
                    hexEdit.setText(s);
                    int[] parsed = parseColor(s);
                    rBar.setProgress(parsed[0]);
                    gBar.setProgress(parsed[1]);
                    bBar.setProgress(parsed[2]);
                    sw.setColor(Color.rgb(parsed[0], parsed[1], parsed[2]));
                    swatch.setBackground(sw);
                });
                group.addView(chip);
            }
            scroll.addView(group);
            root.addView(scroll);

            hexEdit.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    String text = s.toString();
                    for (int i = 0; i < group.getChildCount(); i++) {
                        com.google.android.material.chip.Chip c = (com.google.android.material.chip.Chip) group.getChildAt(i);
                        c.setChecked(c.getText().toString().equalsIgnoreCase(text));
                    }
                }
            });
        }

        root.addView(swatch, swLp);
        root.addView(hexTil);

        root.addView(labeledSlider(ctx, "R", rBar));
        root.addView(labeledSlider(ctx, "G", gBar));
        root.addView(labeledSlider(ctx, "B", bBar));

        Runnable updateSwatch = () -> {
            int r = rBar.getProgress();
            int g = gBar.getProgress();
            int b = bBar.getProgress();
            sw.setColor(Color.rgb(r, g, b));
            swatch.setBackground(sw);
            hexEdit.setText(formatHex(new int[]{r, g, b}));
        };
        SeekBar.OnSeekBarChangeListener barListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { if (u) updateSwatch.run(); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };
        rBar.setOnSeekBarChangeListener(barListener);
        gBar.setOnSeekBarChangeListener(barListener);
        bBar.setOnSeekBarChangeListener(barListener);

        hexEdit.setOnFocusChangeListener((v, has) -> {
            if (!has) {
                int[] parsed = parseColor(hexEdit.getText() == null ? "" : hexEdit.getText().toString());
                rBar.setProgress(parsed[0]);
                gBar.setProgress(parsed[1]);
                bBar.setProgress(parsed[2]);
                sw.setColor(Color.rgb(parsed[0], parsed[1], parsed[2]));
                swatch.setBackground(sw);
            }
        });

        new MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(root)
            .setPositiveButton("OK", (d, w) -> {
                if (onResult != null) onResult.onColor(hexEdit.getText().toString().trim());
            })
            .setNegativeButton("Cancel", null)
            .setBackgroundInsetStart(dp(ctx, 24))
            .setBackgroundInsetEnd(dp(ctx, 24))
            .show();
    }

    // -------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------

    private static TextInputLayout makeInputLayout(Context ctx, String hint) {
        TextInputLayout til = new TextInputLayout(ctx, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint(hint);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        int dp12 = dp(ctx, 12);
        til.setBoxCornerRadii(dp12, dp12, dp12, dp12);
        return til;
    }

    private static void applyOutlinedFieldStyling(Context ctx, android.widget.EditText edit) {
        edit.setMinimumHeight(dp(ctx, 56));
        int hp = dp(ctx, 16);
        int vp = dp(ctx, 12);
        edit.setPadding(hp, vp, hp, vp);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
    }

    private static SeekBar newColorSlider(Context ctx, int initial) {
        SeekBar sb = new SeekBar(ctx);
        sb.setMax(255);
        sb.setProgress(Math.max(0, Math.min(255, initial)));
        return sb;
    }

    private static View labeledSlider(Context ctx, String label, SeekBar bar) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(ctx, 24),
            ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(t, tlp);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(bar, blp);
        return row;
    }

    private static FrameWrap wrap(Context ctx, String message, View body) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 24);
        root.setPadding(pad, pad, pad, dp(ctx, 12));

        if (message != null && !message.isEmpty()) {
            TextView msg = new TextView(ctx);
            msg.setText(message);
            msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            msg.setPadding(0, 0, 0, dp(ctx, 12));
            root.addView(msg);
        }
        root.addView(body, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new FrameWrap(root);
    }

    private static int[] parseColor(String hex) {
        int[] out = new int[]{255, 255, 255};
        if (hex == null) return out;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 3) {
                out[0] = Integer.parseInt(String.valueOf(s.charAt(0)) + s.charAt(0), 16);
                out[1] = Integer.parseInt(String.valueOf(s.charAt(1)) + s.charAt(1), 16);
                out[2] = Integer.parseInt(String.valueOf(s.charAt(2)) + s.charAt(2), 16);
            } else if (s.length() == 6) {
                out[0] = Integer.parseInt(s.substring(0, 2), 16);
                out[1] = Integer.parseInt(s.substring(2, 4), 16);
                out[2] = Integer.parseInt(s.substring(4, 6), 16);
            } else if (s.length() == 8) {
                out[0] = Integer.parseInt(s.substring(2, 4), 16);
                out[1] = Integer.parseInt(s.substring(4, 6), 16);
                out[2] = Integer.parseInt(s.substring(6, 8), 16);
            }
        } catch (NumberFormatException ignore) {}
        return out;
    }

    private static String formatHex(int[] rgb) {
        return String.format("#%02X%02X%02X",
            Math.max(0, Math.min(255, rgb[0])),
            Math.max(0, Math.min(255, rgb[1])),
            Math.max(0, Math.min(255, rgb[2])));
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }

    private static final class FrameWrap {
        final View root;
        FrameWrap(View root) { this.root = root; }
    }
}
