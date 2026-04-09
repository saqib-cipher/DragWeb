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
            case "pre":
            case "blockquote":
            case "li":
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
            case "textarea":
                TextInputEditText textArea = new TextInputEditText(context);
                textArea.setMinLines(3);
                textArea.setGravity(Gravity.TOP | Gravity.START);
                view = textArea;
                break;
            case "div":
            case "section":
            case "nav":
            case "header":
            case "footer":
            case "main":
            case "article":
            case "aside":
            case "form":
                LinearLayout layoutView = new LinearLayout(context);
                layoutView.setOrientation(LinearLayout.VERTICAL);
                layoutView.setMinimumHeight(60);
                // Ensure layout containers have padding so nested drops work easily
                layoutView.setPadding(8, 8, 8, 8);
                view = layoutView;
                break;
            case "ul":
            case "ol":
                view = new LinearLayout(context);
                ((LinearLayout) view).setOrientation(LinearLayout.VERTICAL);
                break;
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
            case "table":
                LinearLayout tableView = new LinearLayout(context);
                tableView.setOrientation(LinearLayout.VERTICAL);
                tableView.setMinimumHeight(60);
                tableView.setPadding(8, 8, 8, 8);
                view = tableView;
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
            } else if (isLayoutTag(tag)) {
                // Layout containers get full width by default
                defaultParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
                defaultParams.setMargins(0, 4, 0, 4);
                // Set minimum height for layout containers so they can receive drops
                view.setMinimumHeight(60);
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
                            iv.setImageResource(R.drawable.default_image);
                        }
                    } catch (Exception e) {
                        iv.setImageResource(R.drawable.default_image);
                    }
                } else {
                    try {
                        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(src);
                        if (bmp != null) {
                            iv.setImageBitmap(bmp);
                        } else {
                            iv.setImageResource(R.drawable.default_image);
                        }
                    } catch (Exception e) {
                        iv.setImageResource(R.drawable.default_image);
                    }
                }
            } else {
                iv.setImageResource(R.drawable.default_image);
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

        // Build GradientDrawable for background with border and radius
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.TRANSPARENT);
        shape.setStroke(1, Color.parseColor("#B0BEC5"));
        shape.setCornerRadius(4);

        // Background color
        if (style.containsKey("backgroundColor")) {
            try {
                String bgColor = style.get("backgroundColor").toString();
                if (!bgColor.startsWith("var(")) {
                    shape.setColor(Color.parseColor(bgColor));
                }
            } catch (Exception e) {
                // ignore invalid color
            }
        }

        // Border radius
        if (style.containsKey("borderRadius")) {
            float radius = parseDimension(style.get("borderRadius").toString());
            shape.setCornerRadius(radius);
        }

        // Per-corner radius
        float[] radii = null;
        if (style.containsKey("borderTopLeftRadius") || style.containsKey("borderTopRightRadius")
            || style.containsKey("borderBottomLeftRadius") || style.containsKey("borderBottomRightRadius")) {
            float tl = style.containsKey("borderTopLeftRadius") ? parseDimension(style.get("borderTopLeftRadius").toString()) : 4;
            float tr = style.containsKey("borderTopRightRadius") ? parseDimension(style.get("borderTopRightRadius").toString()) : 4;
            float br = style.containsKey("borderBottomRightRadius") ? parseDimension(style.get("borderBottomRightRadius").toString()) : 4;
            float bl = style.containsKey("borderBottomLeftRadius") ? parseDimension(style.get("borderBottomLeftRadius").toString()) : 4;
            shape.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
        }

        // Border width and color
        int borderWidth = 1;
        int borderColor = Color.parseColor("#B0BEC5");
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
        shape.setStroke(borderWidth, borderColor);

        view.setBackground(shape);

        // Padding
        int paddingPx = 8;
        if (style.containsKey("padding")) {
            paddingPx = parseDimension(style.get("padding").toString());
        }
        view.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

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
                    if (size > 0 && size < 200) {
                        tv.setTextSize(size);
                    } else {
                        tv.setTextSize(13);
                    }
                } catch (Exception e) {
                    tv.setTextSize(13);
                }
            } else {
                tv.setTextSize(13);
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
            case "ul": case "ol": case "table":
                return true;
            default:
                return false;
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
