package glab.dragweb.ui;

import glab.dragweb.R;
import glab.dragweb.activities.*;
import glab.dragweb.fragments.*;
import glab.dragweb.engine.*;
import glab.dragweb.ui.*;
import glab.dragweb.logic.*;
import glab.dragweb.codegen.*;
import glab.dragweb.data.*;
import glab.dragweb.adapters.*;
import glab.dragweb.models.*;
import glab.dragweb.util.*;
import glab.dragweb.colorpicker.*;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * Modern Material 3 Expressive dialog system shared across the app.
 *
 * <p>Uses professional XML layouts styled via ThemeOverlay.Dragweb.AlertDialog
 * and Widget.Dragweb.TextInputLayout. Avoids programmatic view building and
 * respects the Material 3 Expressive corner shapes and animations.</p>
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

    private static MaterialAlertDialogBuilder createBuilder(Context ctx) {
        return new MaterialAlertDialogBuilder(ctx, R.style.ThemeOverlay_Dragweb_AlertDialog);
    }

    /** Material 3 Expressive loading dialog with circular progress indicator. */
    public static AlertDialog loading(Context ctx, String message) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_progress_material, null);
        TextView tv = v.findViewById(R.id.progress_message);
        if (tv != null && message != null) {
            tv.setText(message);
        }
        AlertDialog dialog = createBuilder(ctx)
            .setView(v)
            .setCancelable(false)
            .create();
        dialog.show();
        return dialog;
    }

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
        View root = LayoutInflater.from(ctx).inflate(R.layout.dialog_input_text, null);
        TextView tvMsg = root.findViewById(R.id.tvDialogMessage);
        TextInputLayout til = root.findViewById(R.id.tilInput);
        TextInputEditText edit = root.findViewById(R.id.etInput);

        if (message != null && !message.trim().isEmpty()) {
            tvMsg.setText(message);
            tvMsg.setVisibility(View.VISIBLE);
        } else {
            tvMsg.setVisibility(View.GONE);
        }

        if (hint != null) til.setHint(hint);
        if (initial != null) edit.setText(initial);

        if (multiline) {
            edit.setSingleLine(false);
            edit.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            edit.setMinLines(6);
            edit.setMaxLines(14);
            edit.setGravity(Gravity.TOP | Gravity.START);
        } else {
            edit.setSingleLine(true);
        }

        createBuilder(ctx)
            .setTitle(title)
            .setView(root)
            .setPositiveButton("OK", (d, w) -> {
                if (onResult != null) onResult.onText(edit.getText() == null ? "" : edit.getText().toString());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Autocomplete drop-down (variable picker, font picker, etc.). */
    public static void autocompleteInput(Context ctx, String title, String hint,
                                         String initial, List<String> suggestions,
                                         OnTextResult onResult) {
        View root = LayoutInflater.from(ctx).inflate(R.layout.dialog_input_autocomplete, null);
        TextInputLayout til = root.findViewById(R.id.tilAutocomplete);
        MaterialAutoCompleteTextView edit = root.findViewById(R.id.actvInput);

        if (hint != null) til.setHint(hint);
        if (initial != null) edit.setText(initial);
        if (suggestions != null && !suggestions.isEmpty()) {
            edit.setAdapter(new ArrayAdapter<>(ctx,
                android.R.layout.simple_list_item_1, suggestions));
            edit.setThreshold(1);
        }

        createBuilder(ctx)
            .setTitle(title)
            .setView(root)
            .setPositiveButton("OK", (d, w) -> {
                if (onResult != null) onResult.onText(edit.getText() == null ? "" : edit.getText().toString());
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Single-choice list dialog with rounded surface. */
    public static void singleChoice(Context ctx, String title, String[] items,
                                    OnChoiceResult onResult) {
        createBuilder(ctx)
            .setTitle(title)
            .setItems(items, (d, which) -> {
                if (onResult != null) onResult.onChoice(which, items[which]);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Confirm/cancel dialog with optional destructive styling. */
    public static void confirm(Context ctx, String title, String message,
                               String confirmLabel, boolean destructive,
                               OnConfirmResult onResult) {
        MaterialAlertDialogBuilder b = createBuilder(ctx)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(confirmLabel == null ? "OK" : confirmLabel, (d, w) -> {
                if (onResult != null) onResult.onConfirm();
            });
        AlertDialog dlg = b.show();
        if (destructive) {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFD32F2F);
        }
    }

    /** Numeric value with a unit suffix (px / % / em / rem / vw / vh). */
    public static void numberWithUnit(Context ctx, String title, float initial,
                                      String initialUnit, OnNumberUnitResult onResult) {
        View root = LayoutInflater.from(ctx).inflate(R.layout.dialog_input_number_unit, null);
        TextInputEditText edit = root.findViewById(R.id.etNumber);
        Spinner spUnit = root.findViewById(R.id.spUnit);

        edit.setText(formatFloat(initial));

        String[] units = new String[]{"px", "%", "em", "rem", "vw", "vh", "pt", "fr", "auto"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, units);
        spUnit.setAdapter(adapter);

        if (initialUnit != null) {
            for (int i = 0; i < units.length; i++) {
                if (units[i].equalsIgnoreCase(initialUnit.trim())) {
                    spUnit.setSelection(i);
                    break;
                }
            }
        }

        createBuilder(ctx)
            .setTitle(title)
            .setView(root)
            .setPositiveButton("OK", (d, w) -> {
                float v = 0f;
                try {
                    v = Float.parseFloat(edit.getText().toString().trim());
                } catch (Exception ignore) {}
                String u = spUnit.getSelectedItem() != null ? spUnit.getSelectedItem().toString() : "px";
                if (onResult != null) onResult.onValue(v, u);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Color picker dialog utilizing ColorPickerDialogFragment or MaterialAlertDialogBuilder.
     */
    public static void colorPicker(Context ctx, String title, String initialHex,
                                   List<String> suggestions, OnColorResult onResult) {
        if (ctx instanceof androidx.fragment.app.FragmentActivity) {
            androidx.fragment.app.FragmentActivity activity = (androidx.fragment.app.FragmentActivity) ctx;
            ColorPickerDialogFragment dialog = new ColorPickerDialogFragment();
            Bundle args = new Bundle();
            args.putString("initialHex", initialHex != null ? initialHex : "");
            dialog.setArguments(args);
            dialog.setOnColorSelectedListener(hex -> {
                if (onResult != null) onResult.onColor(hex);
            });
            dialog.show(activity.getSupportFragmentManager(), "color_picker");
            return;
        }

        // Fallback for non-FragmentActivity contexts
        textInput(ctx, title, "Enter color hex (e.g. #FF0000)", initialHex, val -> {
            if (onResult != null) onResult.onColor(val);
        });
    }
}
