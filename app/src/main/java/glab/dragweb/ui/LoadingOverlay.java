package glab.dragweb.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import glab.dragweb.R;

/**
 * Reusable dimmed Material 3 Expressive loading indicator for screens and blocking tasks.
 */
public final class LoadingOverlay {

    private LoadingOverlay() {}

    public static void show(View overlayView, String message) {
        if (overlayView == null) return;
        TextView tv = overlayView.findViewById(R.id.tvLoadingMessage);
        if (tv != null && message != null && !message.isEmpty()) {
            tv.setText(message);
        }
        overlayView.setVisibility(View.VISIBLE);
        overlayView.bringToFront();
    }

    public static void hide(View overlayView) {
        if (overlayView == null) return;
        overlayView.setVisibility(View.GONE);
    }

    public static AlertDialog showDialog(Context context, String message) {
        if (context == null) return null;
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_progress_material, null);
        TextView tv = view.findViewById(R.id.progress_message);
        if (tv != null && message != null && !message.isEmpty()) {
            tv.setText(message);
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(false)
                .create();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setDimAmount(0.55f);
        }
        dialog.show();
        return dialog;
    }

    public static void dismissDialog(AlertDialog dialog) {
        if (dialog != null && dialog.isShowing()) {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {}
        }
    }
}
