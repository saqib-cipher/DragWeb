package glab.dragweb.engine;

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
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.textfield.TextInputEditText;
import java.util.HashMap;
import java.util.Map;

public class WidgetBuilderEngine {
    private Context context;
    private WidgetCanvasStyler styler;

    public WidgetBuilderEngine(Context context) {
        this.context = context;
        this.styler = WidgetCanvasStyler.getInstance(context);
    }

    public void setThemeManager(ThemeManager tm) {
        if (this.styler != null) {
            this.styler.setThemeManager(tm);
        }
    }

    public WidgetCanvasStyler getStyler() {
        return this.styler;
    }

    private int dpToPx(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    public View createWidget(String tag) {
        View view = null;
        switch (tag) {
            case "p":
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
            case "span":
            case "label":
            case "pre":
            case "blockquote":
            case "i":
            case "a": {
                TextView linkView = new TextView(context);
                linkView.setTextColor(Color.parseColor("#2196F3"));
                linkView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                view = linkView;
                break;
            }
            case "button": {
                Button btn = new Button(context);
                btn.setTransformationMethod(null);
                btn.setMinimumWidth(0);
                btn.setMinimumHeight(0);
                btn.setMinWidth(0);
                btn.setMinHeight(0);
                btn.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
                btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                view = btn;
                break;
            }
            case "img":
                view = new ImageView(context);
                ((ImageView) view).setScaleType(ImageView.ScaleType.FIT_CENTER);
                break;
            case "input": {
                TextInputEditText input = new TextInputEditText(context);
                input.setMinimumWidth(0);
                input.setMinimumHeight(0);
                input.setMinWidth(0);
                input.setMinHeight(0);
                input.setBackground(null);
                input.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
                input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                view = input;
                break;
            }
            case "textarea": {
                TextInputEditText textArea = new TextInputEditText(context);
                textArea.setMinLines(3);
                textArea.setGravity(Gravity.TOP | Gravity.START);
                textArea.setMinimumWidth(0);
                textArea.setMinimumHeight(0);
                textArea.setMinWidth(0);
                textArea.setMinHeight(0);
                textArea.setBackground(null);
                textArea.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
                textArea.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                view = textArea;
                break;
            }
            case "div":
            case "section":
            case "nav":
            case "header":
            case "footer":
            case "main":
            case "article":
            case "aside":
            case "form":
            case "ul":
            case "ol":
            case "li": {
                LinearLayout layoutView = new LinearLayout(context);
                layoutView.setOrientation(LinearLayout.VERTICAL);
                layoutView.setMinimumHeight(dpToPx(60));
                layoutView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
                view = layoutView;
                break;
            }

            case "hr":
                view = new View(context);
                view.setBackgroundColor(Color.parseColor("#CCCCCC"));
                break;
            case "br":
                view = new View(context);
                break;
            case "video":
            case "audio":
                TextView mediaView = new TextView(context);
                mediaView.setText("[" + tag.toUpperCase() + " Player]");
                mediaView.setGravity(Gravity.CENTER);
                mediaView.setBackgroundColor(Color.parseColor("#1A000000"));
                view = mediaView;
                break;
            case "canvas":
                TextView canvasView = new TextView(context);
                canvasView.setText("[Canvas]");
                canvasView.setGravity(Gravity.CENTER);
                canvasView.setBackgroundColor(Color.parseColor("#FFF8E1"));
                view = canvasView;
                break;
            case "svg":
                TextView svgView = new TextView(context);
                svgView.setText("[SVG]");
                svgView.setGravity(Gravity.CENTER);
                view = svgView;
                break;
            case "iframe":
                TextView iframeView = new TextView(context);
                iframeView.setText("[IFrame]");
                iframeView.setGravity(Gravity.CENTER);
                iframeView.setBackgroundColor(Color.parseColor("#1A000000"));
                view = iframeView;
                break;
            case "table": {
                LinearLayout tableView = new LinearLayout(context);
                tableView.setOrientation(LinearLayout.VERTICAL);
                tableView.setMinimumHeight(dpToPx(60));
                tableView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
                view = tableView;
                break;
            }
            default:
                view = new TextView(context);
                break;
        }

        if (view != null) {
            Map<String, Object> tagData = new HashMap<>();
            tagData.put("tag", tag);
            tagData.put("function", new HashMap<String, Object>());
            view.setTag(tagData);

            // Apply default initial styles & LayoutParams via styler
            styler.applyProperties(view, tagData);
        }

        return view;
    }

    public boolean isBlockTag(String tag) {
        return styler != null ? styler.isBlockTag(tag) : false;
    }

    public boolean isLayoutTag(String tag) {
        return styler != null ? styler.isLayoutContainer(tag) : false;
    }

    public void applyPropertiesToView(View view, Map<String, Object> widgetMap) {
        if (view == null || widgetMap == null) return;
        styler.applyProperties(view, widgetMap);
    }

    public ViewGroup.LayoutParams generateLayoutParams(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) {
            params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return params;
    }

    public void applyStyles(View view, Map<String, Object> style) {
        if (view == null || style == null) return;
        String tag = "";
        if (view.getTag() instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) view.getTag();
            if (map.containsKey("tag") && map.get("tag") != null) {
                tag = map.get("tag").toString();
            }
        }
        styler.applyStyles(view, tag, style);
    }

    public int parseDimension(String dimStr) {
        return styler != null ? styler.parseDimension(dimStr, 0, 0) : 0;
    }

    public int[] parseShorthandDimension(String shStr) {
        return styler != null ? styler.parseShorthandDimensions(shStr, 0) : new int[4];
    }
}

