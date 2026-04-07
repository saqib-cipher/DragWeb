package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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

    public WidgetBuilderEngine(Context context) {
        this.context = context;
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
            case "a":
            case "span":
            case "label":
                view = new TextView(context);
                break;
            case "button":
                view = new Button(context);
                break;
            case "img":
                view = new ImageView(context);
                ((ImageView) view).setScaleType(ImageView.ScaleType.FIT_CENTER);
                break;
            case "input":
                view = new TextInputEditText(context);
                break;
            case "div":
            case "section":
            case "nav":
            case "header":
            case "footer":
            case "main":
            case "article":
            case "aside":
                view = new LinearLayout(context);
                ((LinearLayout) view).setOrientation(LinearLayout.VERTICAL);
                // Set min height so empty containers are visible/droppable
                view.setMinimumHeight(40);
                break;
            case "ul":
            case "ol":
                view = new LinearLayout(context);
                ((LinearLayout) view).setOrientation(LinearLayout.VERTICAL);
                break;
            case "li":
                view = new TextView(context);
                break;
            case "hr":
                view = new View(context);
                view.setBackgroundColor(Color.parseColor("#CCCCCC"));
                break;
            case "br":
                view = new View(context);
                break;
            default:
                view = new TextView(context);
                break;
        }

        if (view != null) {
            Map<String, Object> tagData = new HashMap<>();
            tagData.put("tag", tag);
            tagData.put("function", new HashMap<String, Object>());
            view.setTag(tagData);

            // Set default layout params
            LinearLayout.LayoutParams defaultParams;
            if ("hr".equals(tag)) {
                defaultParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 2);
                defaultParams.setMargins(0, 12, 0, 12);
            } else {
                defaultParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            view.setLayoutParams(defaultParams);
        }

        return view;
    }

    public void applyPropertiesToView(View view, Map<String, Object> widgetMap) {
        if (view == null || widgetMap == null) return;

        Map<String, Object> function = (Map<String, Object>) widgetMap.get("function");
        if (function == null) return;

        // Apply base attributes
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (function.containsKey("text")) {
                tv.setText(function.get("text").toString());
            }
        }

        if (view instanceof ImageView) {
            ImageView iv = (ImageView) view;
            iv.setImageResource(R.drawable.default_image);
        }

        if (view instanceof TextInputEditText) {
            TextInputEditText input = (TextInputEditText) view;
            if (function.containsKey("placeholder")) {
                input.setHint(function.get("placeholder").toString());
            }
        }

        // Apply styles
        Map<String, Object> style = (Map<String, Object>) function.get("style");
        if (style != null) {
            applyStyles(view, style);
        }
    }

    private void applyStyles(View view, Map<String, Object> style) {
        // Layout params
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) {
            params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        if (style.containsKey("width")) {
            String widthStr = style.get("width").toString();
            if ("100%".equals(widthStr) || "match_parent".equals(widthStr)) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            } else if ("auto".equals(widthStr) || "wrap_content".equals(widthStr)) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else {
                params.width = parseDimension(widthStr);
            }
        }

        if (style.containsKey("height")) {
            String heightStr = style.get("height").toString();
            if ("100%".equals(heightStr) || "match_parent".equals(heightStr)) {
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            } else if ("auto".equals(heightStr) || "wrap_content".equals(heightStr)) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else {
                params.height = parseDimension(heightStr);
            }
        }

        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
            if (style.containsKey("margin")) {
                int margin = parseDimension(style.get("margin").toString());
                marginParams.setMargins(margin, margin, margin, margin);
            }
        }

        view.setLayoutParams(params);

        // Default Block Styling for Canvas (Android Preview)
        android.graphics.drawable.GradientDrawable defaultShape = new android.graphics.drawable.GradientDrawable();
        defaultShape.setColor(android.graphics.Color.TRANSPARENT);
        defaultShape.setStroke(2, android.graphics.Color.parseColor("#B0BEC5")); // Light gray border
        defaultShape.setCornerRadius(8);
        view.setBackground(defaultShape);

        // Add default padding so children are not squished against the border
        view.setPadding(16, 16, 16, 16);

        // LinearLayout orientation
        if (view instanceof LinearLayout && style.containsKey("flexDirection")) {
            String dir = style.get("flexDirection").toString();
            if ("row".equals(dir)) {
                ((LinearLayout) view).setOrientation(LinearLayout.HORIZONTAL);
            } else {
                ((LinearLayout) view).setOrientation(LinearLayout.VERTICAL);
            }
        }

        // TextView specific styles - Apply ONLY minimal styles for visibility
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            tv.setTextColor(android.graphics.Color.parseColor("#37474F")); // Default dark text
            tv.setTextSize(14); // Default text size
            if (style.containsKey("textAlign")) {
                String align = style.get("textAlign").toString();
                switch (align) {
                    case "center": tv.setGravity(android.view.Gravity.CENTER); break;
                    case "right": tv.setGravity(android.view.Gravity.END); break;
                    default: tv.setGravity(android.view.Gravity.START); break;
                }
            }
        }
    }

    private int parseDimension(String dimStr) {
        dimStr = dimStr.replaceAll("[^0-9.]", "");
        try {
            return (int) Float.parseFloat(dimStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
