package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
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
                view = new TextView(context);
                break;
            case "button":
                view = new Button(context);
                break;
            case "img":
                view = new ImageView(context);
                break;
            case "input":
                view = new TextInputEditText(context);
                break;
            case "div":
            case "ul":
            case "ol":
            case "li":
            case "hr":
                view = new LinearLayout(context);
                ((LinearLayout) view).setOrientation(LinearLayout.VERTICAL);
                break;
            default:
                view = new TextView(context);
                break;
        }

        if (view != null) {
            Map<String, Object> tagData = new HashMap<>();
            tagData.put("tag", tag);
            view.setTag(tagData);
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
            // A basic placeholder handling for image source, real app might load from url
            iv.setImageResource(R.drawable.default_image); // Default placeholder
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
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        if (style.containsKey("width")) {
            String widthStr = style.get("width").toString();
            if (widthStr.equals("100%")) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            } else if (widthStr.equals("auto")) {
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else {
                params.width = parseDimension(widthStr);
            }
        }

        if (style.containsKey("height")) {
            String heightStr = style.get("height").toString();
            if (heightStr.equals("100%")) {
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            } else if (heightStr.equals("auto")) {
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

        // Padding
        if (style.containsKey("padding")) {
            int padding = parseDimension(style.get("padding").toString());
            view.setPadding(padding, padding, padding, padding);
        }

        // Background Color
        if (style.containsKey("backgroundColor")) {
            try {
                view.setBackgroundColor(Color.parseColor(style.get("backgroundColor").toString()));
            } catch (Exception e) {
                // Ignore parse errors
            }
        }

        // TextView specific styles
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (style.containsKey("color")) {
                try {
                    tv.setTextColor(Color.parseColor(style.get("color").toString()));
                } catch (Exception e) {}
            }
            if (style.containsKey("fontSize")) {
                int size = parseDimension(style.get("fontSize").toString());
                tv.setTextSize(size);
            }
            if (style.containsKey("textAlign")) {
                 String align = style.get("textAlign").toString();
                 if (align.equals("center")) tv.setGravity(Gravity.CENTER);
                 else if (align.equals("right")) tv.setGravity(Gravity.RIGHT);
                 else tv.setGravity(Gravity.LEFT);
            }
        }
    }

    private int parseDimension(String dimStr) {
        dimStr = dimStr.replaceAll("[^0-9]", ""); // Keep only numbers
        try {
            return Integer.parseInt(dimStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
