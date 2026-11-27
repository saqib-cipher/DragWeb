package glab.dragweb.engine;

import glab.dragweb.R;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
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
    private Drawable originalForeground;

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                selectedView.setForeground(originalForeground);
            } else {
                selectedView.setAlpha(1.0f);
            }
            selectedView = null;
        }
    }

    public void registerView(View view) {
        if (view == null) return;
        boolean isInternal = Boolean.TRUE.equals(view.getTag(R.id.tag_internal_preview));
        boolean hasWidgetMap = view.getTag() instanceof Map;
        if (isInternal || !hasWidgetMap) {
            view.setOnClickListener(null);
            view.setClickable(false);
            view.setFocusable(false);
            if (view instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) view;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    registerView(vg.getChildAt(i));
                }
            }
            return;
        }

        view.setOnClickListener(v -> selectView(v));

        // Recursively register child widgets
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                registerView(vg.getChildAt(i));
            }
        }
    }

    private View resolveSelectableWidget(View view) {
        View cur = view;
        while (cur != null) {
            if (!Boolean.TRUE.equals(cur.getTag(R.id.tag_internal_preview))
                    && cur.getTag() instanceof Map) {
                return cur;
            }
            if (cur.getParent() instanceof View) {
                cur = (View) cur.getParent();
            } else {
                break;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public void selectView(View view) {
        View target = resolveSelectableWidget(view);
        if (target == null) return;

        clearSelection();
        selectedView = target;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            originalForeground = target.getForeground();
            GradientDrawable highlight = new GradientDrawable();
            highlight.setColor(Color.parseColor("#1A000000"));
            highlight.setStroke(4, Color.parseColor("#80000000"));
            target.setForeground(highlight);
        } else {
            target.setAlpha(0.6f);
        }

        if (listener != null) {
            String widgetName = "Unknown";
            String widgetId = "";
            Object tagObj = target.getTag();
            if (tagObj instanceof Map) {
                Map<String, Object> widgetMap = (Map<String, Object>) tagObj;
                if (widgetMap.containsKey("tag")) {
                    widgetName = widgetMap.get("tag").toString();
                }
                Map<String, Object> fn = (Map<String, Object>) widgetMap.get("function");
                if (fn != null && fn.containsKey("id")) {
                    widgetId = fn.get("id").toString();
                }
            }
            String display = widgetName + " Widget";
            if (!widgetId.isEmpty()) {
                display = widgetName + " #" + widgetId;
            }
            listener.onWidgetSelected(display);
        }
    }
}
