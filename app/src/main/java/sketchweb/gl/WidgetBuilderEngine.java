package sketchweb.gl;

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

    public WidgetBuilderEngine(Context context) {
        this.context = context;
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
                view = new TextView(context);
                break;
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
            case "li":
            case "a": {
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

            // Set default layout params
            LinearLayout.LayoutParams defaultParams;
            if ("hr".equals(tag)) {
                defaultParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(2));
                defaultParams.setMargins(0, dpToPx(12), 0, dpToPx(12));
            } else if (isBlockTag(tag)) {
                defaultParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
                if (isLayoutTag(tag)) {
                    defaultParams.setMargins(0, dpToPx(4), 0, dpToPx(4));
                    view.setMinimumHeight(dpToPx(60));
                } else {
                    defaultParams.setMargins(0, dpToPx(2), 0, dpToPx(2));
                }
            } else {
                defaultParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            view.setLayoutParams(defaultParams);
        }

        return view;
    }

    private boolean isBlockTag(String tag) {
        switch (tag) {
            case "p":
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
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
            case "li":
            case "blockquote":
            case "pre":
            case "hr":
            case "table":
                return true;
            default:
                return false;
        }
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
            } else if ("i".equals(widgetMap.get("tag")) && function.containsKey("class")) {
                tv.setText("[" + function.get("class").toString() + "]");
            }
        }

        if (view instanceof ImageView) {
            ImageView iv = (ImageView) view;
            if (function.containsKey("src")) {
                String src = function.get("src").toString();
                if (src.startsWith("data:")) {
                    try {
                        String base64 = src.substring(src.indexOf(",") + 1);
                        byte[] decoded = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                        if (bmp != null) {
                            iv.setImageBitmap(bmp);
                        } else {
                            iv.setImageResource(R.drawable.photo);
                        }
                    } catch (Exception e) {
                        iv.setImageResource(R.drawable.photo);
                    }
                } else {
                    try {
                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(src);
                        if (bmp != null) {
                            iv.setImageBitmap(bmp);
                        } else {
                            iv.setImageResource(R.drawable.photo);
                        }
                    } catch (Exception e) {
                        iv.setImageResource(R.drawable.photo);
                    }
                }
            } else {
                iv.setImageResource(R.drawable.photo);
            }
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
        // Create a copy of style map with !important stripped from values to prevent Android view parse exceptions
        Map<String, Object> cleanStyle = new HashMap<>();
        for (Map.Entry<String, Object> entry : style.entrySet()) {
            Object val = entry.getValue();
            if (val != null) {
                String valStr = val.toString().replaceAll("(?i)\\s*!\\s*important\\s*$", "").trim();
                cleanStyle.put(entry.getKey(), valStr);
            } else {
                cleanStyle.put(entry.getKey(), null);
            }
        }
        style = cleanStyle;

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
                String marginStr = style.get("margin").toString().trim();
                int[] margins = parseShorthandDimension(marginStr);
                marginParams.setMargins(margins[3], margins[0], margins[1], margins[2]); // left, top, right, bottom
            }
        }

        view.setLayoutParams(params);

        // Build GradientDrawable for background with border and radius
        GradientDrawable shape = new GradientDrawable();

        // Extract widget tag
        String tag = "";
        if (view.getTag() instanceof Map) {
            Map<?, ?> tagData = (Map<?, ?>) view.getTag();
            if (tagData.containsKey("tag")) {
                tag = tagData.get("tag").toString();
            }
        }

        int defaultBgColor = Color.TRANSPARENT;
        int defaultBorderWidth = 0;
        int defaultBorderColor = Color.TRANSPARENT;
        float defaultCornerRadius = 0;

        boolean isLayoutContainer = "div".equalsIgnoreCase(tag) || "section".equalsIgnoreCase(tag) || "header".equalsIgnoreCase(tag)
                || "footer".equalsIgnoreCase(tag) || "nav".equalsIgnoreCase(tag) || "main".equalsIgnoreCase(tag)
                || "aside".equalsIgnoreCase(tag) || "form".equalsIgnoreCase(tag) || "article".equalsIgnoreCase(tag);

        if ("button".equalsIgnoreCase(tag)) {
            defaultBgColor = Color.parseColor("#EFEFEF");
            defaultBorderWidth = dpToPx(1);
            defaultBorderColor = Color.parseColor("#B0BEC5");
            defaultCornerRadius = dpToPx(4);
        } else if ("input".equalsIgnoreCase(tag) || "textarea".equalsIgnoreCase(tag)) {
            defaultBgColor = Color.parseColor("#FFFFFF");
            defaultBorderWidth = dpToPx(1);
            defaultBorderColor = Color.parseColor("#B0BEC5");
            defaultCornerRadius = dpToPx(4);
        } else if (isLayoutContainer) {
            defaultBorderWidth = dpToPx(1);
            defaultBorderColor = Color.parseColor("#CCCCCC");
        }

        // Background color
        int bgColor = defaultBgColor;
        if (style.containsKey("backgroundColor")) {
            try {
                String bgStr = style.get("backgroundColor").toString();
                if (!bgStr.startsWith("var(")) {
                    bgColor = Color.parseColor(bgStr);
                }
            } catch (Exception e) {
                // ignore invalid color
            }
        }
        shape.setColor(bgColor);

        // Border radius
        float radius = defaultCornerRadius;
        if (style.containsKey("borderRadius")) {
            radius = parseDimension(style.get("borderRadius").toString());
        }
        shape.setCornerRadius(radius);

        // Per-corner radius
        if (style.containsKey("borderTopLeftRadius") || style.containsKey("borderTopRightRadius")
            || style.containsKey("borderBottomLeftRadius") || style.containsKey("borderBottomRightRadius")) {
            float tl = style.containsKey("borderTopLeftRadius") ? parseDimension(style.get("borderTopLeftRadius").toString()) : radius;
            float tr = style.containsKey("borderTopRightRadius") ? parseDimension(style.get("borderTopRightRadius").toString()) : radius;
            float br = style.containsKey("borderBottomRightRadius") ? parseDimension(style.get("borderBottomRightRadius").toString()) : radius;
            float bl = style.containsKey("borderBottomLeftRadius") ? parseDimension(style.get("borderBottomLeftRadius").toString()) : radius;
            shape.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
        }

        // Border width and color
        int borderWidth = defaultBorderWidth;
        int borderColor = defaultBorderColor;
        if (style.containsKey("borderWidth")) {
            borderWidth = parseDimension(style.get("borderWidth").toString());
        }
        if (style.containsKey("borderColor")) {
            try {
                String bc = style.get("borderColor").toString();
                if (!bc.startsWith("var(")) {
                    borderColor = Color.parseColor(bc);
                }
            } catch (Exception e) {
                // ignore
            }
        }

        if (isLayoutContainer && !style.containsKey("border") && !style.containsKey("borderWidth")) {
            shape.setStroke(borderWidth, borderColor, dpToPx(4), dpToPx(3));
        } else {
            shape.setStroke(borderWidth, borderColor);
        }

        view.setBackground(shape);

        // Padding
        if (style.containsKey("padding")) {
            String paddingStr = style.get("padding").toString().trim();
            int[] paddings = parseShorthandDimension(paddingStr);
            view.setPadding(paddings[3], paddings[0], paddings[1], paddings[2]); // left, top, right, bottom
        } else {
            // Respect tag-specific default paddings when no custom padding is defined
            if ("button".equals(tag)) {
                view.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
            } else if ("input".equals(tag)) {
                view.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
            } else if ("textarea".equals(tag)) {
                view.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            } else {
                int defaultPadding = dpToPx(8);
                view.setPadding(defaultPadding, defaultPadding, defaultPadding, defaultPadding);
            }
        }

        // Opacity
        if (style.containsKey("opacity")) {
            try {
                float opacity = Float.parseFloat(style.get("opacity").toString());
                view.setAlpha(opacity);
            } catch (Exception e) {
                // ignore
            }
        }

        // Elevation (shadow)
        if (style.containsKey("elevation")) {
            try {
                float elevation = Float.parseFloat(style.get("elevation").toString());
                view.setElevation(elevation);
            } catch (Exception e) {
                // ignore
            }
        }

        // Rotation
        if (style.containsKey("transform")) {
            String transform = style.get("transform").toString();
            if (transform.contains("rotate(")) {
                try {
                    String deg = transform.replaceAll("[^0-9.-]", "");
                    view.setRotation(Float.parseFloat(deg));
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        // LinearLayout orientation for flex containers
        if (view instanceof LinearLayout && style.containsKey("flexDirection")) {
            String dir = style.get("flexDirection").toString();
            if ("row".equals(dir) || "row-reverse".equals(dir)) {
                ((LinearLayout) view).setOrientation(LinearLayout.HORIZONTAL);
            } else {
                ((LinearLayout) view).setOrientation(LinearLayout.VERTICAL);
            }
        }

        // LinearLayout gravity for justify/align
        if (view instanceof LinearLayout) {
            int gravity = Gravity.NO_GRAVITY;
            if (style.containsKey("justifyContent")) {
                String jc = style.get("justifyContent").toString();
                switch (jc) {
                    case "center": gravity |= Gravity.CENTER; break;
                    case "flex-end": gravity |= Gravity.END; break;
                    case "space-between":
                    case "space-around":
                    case "space-evenly":
                    case "flex-start":
                    default: gravity |= Gravity.START; break;
                }
            }
            if (style.containsKey("alignItems")) {
                String ai = style.get("alignItems").toString();
                switch (ai) {
                    case "center": gravity |= Gravity.CENTER_VERTICAL; break;
                    case "flex-end": gravity |= Gravity.BOTTOM; break;
                    default: gravity |= Gravity.TOP; break;
                }
            }
            if (gravity != Gravity.NO_GRAVITY) {
                ((LinearLayout) view).setGravity(gravity);
            }
        }

        // TextView specific styles
        if (view instanceof TextView) {
            TextView tv = (TextView) view;

            if (style.containsKey("color")) {
                try {
                    String color = style.get("color").toString();
                    if (!color.startsWith("var(")) {
                        tv.setTextColor(Color.parseColor(color));
                    } else {
                        tv.setTextColor(Color.parseColor("#37474F"));
                    }
                } catch (Exception e) {
                    tv.setTextColor(Color.parseColor("#37474F"));
                }
            } else {
                tv.setTextColor(Color.parseColor("#37474F"));
            }

            if (style.containsKey("fontSize")) {
                try {
                    float size = parseDimension(style.get("fontSize").toString());
                    if (size > 0 && size < dpToPx(200)) {
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
                    } else {
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    }
                } catch (Exception e) {
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                }
            } else {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            }

            if (style.containsKey("fontWeight")) {
                String weight = style.get("fontWeight").toString();
                if ("bold".equals(weight) || "700".equals(weight) || "800".equals(weight) || "900".equals(weight)) {
                    tv.setTypeface(null, android.graphics.Typeface.BOLD);
                } else if ("italic".equals(weight)) {
                    tv.setTypeface(null, android.graphics.Typeface.ITALIC);
                } else {
                    tv.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            }

            if (style.containsKey("textAlign")) {
                String align = style.get("textAlign").toString();
                switch (align) {
                    case "center": tv.setGravity(Gravity.CENTER); break;
                    case "right": tv.setGravity(Gravity.END); break;
                    case "justify":
                    default: tv.setGravity(Gravity.START); break;
                }
            }

            if (style.containsKey("textDecoration")) {
                String decor = style.get("textDecoration").toString();
                if ("underline".equals(decor)) {
                    tv.setPaintFlags(tv.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
                } else if ("line-through".equals(decor)) {
                    tv.setPaintFlags(tv.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                }
            }

            if (style.containsKey("lineHeight")) {
                try {
                    float lineHeight = Float.parseFloat(style.get("lineHeight").toString());
                    tv.setLineSpacing(0, lineHeight);
                } catch (Exception e) {
                    // ignore
                }
            }

            if (style.containsKey("letterSpacing")) {
                try {
                    float spacing = parseDimension(style.get("letterSpacing").toString());
                    tv.setLetterSpacing(spacing / tv.getTextSize());
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    private boolean isLayoutTag(String tag) {
        switch (tag) {
            case "div": case "section": case "nav": case "header": case "footer":
            case "main": case "article": case "aside": case "form":
            case "ul": case "ol": case "li": case "a": case "table":
                return true;
            default:
                return false;
        }
    }

    private int parseDimension(String dimStr) {
        dimStr = dimStr.replaceAll("[^0-9.]", "");
        try {
            float val = Float.parseFloat(dimStr);
            return dpToPx(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int[] parseShorthandDimension(String shStr) {
        // Returns [top, right, bottom, left]
        int[] result = new int[4];
        if (shStr == null || shStr.trim().isEmpty()) {
            return result;
        }
        String[] parts = shStr.trim().split("\\s+");
        if (parts.length == 1) {
            int val = parseDimension(parts[0]);
            result[0] = val; // top
            result[1] = val; // right
            result[2] = val; // bottom
            result[3] = val; // left
        } else if (parts.length == 2) {
            int valV = parseDimension(parts[0]); // top & bottom
            int valH = parseDimension(parts[1]); // left & right
            result[0] = valV;
            result[1] = valH;
            result[2] = valV;
            result[3] = valH;
        } else if (parts.length == 3) {
            int top = parseDimension(parts[0]);
            int lr = parseDimension(parts[1]);
            int bottom = parseDimension(parts[2]);
            result[0] = top;
            result[1] = lr;
            result[2] = bottom;
            result[3] = lr;
        } else if (parts.length >= 4) {
            result[0] = parseDimension(parts[0]); // top
            result[1] = parseDimension(parts[1]); // right
            result[2] = parseDimension(parts[2]); // bottom
            result[3] = parseDimension(parts[3]); // left
        }
        return result;
    }
}
