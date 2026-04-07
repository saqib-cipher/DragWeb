package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

import java.util.Map;

public class WidgetSelector {

    public interface OnWidgetSelectedListener {
        void onWidgetSelected(String widgetId);
    }

    private Context context;
    private View selectedView = null;
    private OnWidgetSelectedListener listener;
    private Drawable originalBackground;

    public WidgetSelector(Context context) {
        this.context = context;
    }

    public void setOnWidgetSelectedListener(OnWidgetSelectedListener listener) {
        this.listener = listener;
    }

    public void attachTo(View screen) {
        if (screen instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) screen;
            for (int i = 0; i < vg.getChildCount(); i++) {
                registerView(vg.getChildAt(i));
            }
        }
    }

    public View getSelectedView() {
        return selectedView;
    }

    public void clearSelection() {
        if (selectedView != null) {
            // Restore original alpha
            selectedView.setAlpha(1.0f);
            selectedView = null;
        }
    }

    public void registerView(View view) {
        if (view == null) return;
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectView(v);
            }
        });

        // Recursively register children if it's a layout
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                registerView(vg.getChildAt(i));
            }
        }
    }

    private void selectView(View view) {
        clearSelection();

        selectedView = view;

        // Highlight selected view with a fade effect
        view.setAlpha(0.5f);

        if (listener != null) {
            String widgetName = "Unknown";
            Object tagObj = view.getTag();
            if (tagObj instanceof Map) {
                Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
                if (widgetMap.containsKey("tag")) {
                    widgetName = widgetMap.get("tag").toString();
                }
            }
            listener.onWidgetSelected(widgetName + " Widget");
        }
    }
}
