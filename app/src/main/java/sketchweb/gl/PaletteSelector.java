package sketchweb.gl;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import android.graphics.Color;
import android.widget.LinearLayout.LayoutParams;

/* SUPPORT @R7ia4917 */

public class PaletteSelector extends LinearLayout implements OnClickListener {

    private Context mContext;
    private OnBlockCategorySelectListener mListener;
    
    /*for load sketchware blocks by path and save into string
 
  *  private String path = "/storage/emulated/0/.sketchware/resources/block/My Block/palette.json";
    */
    public PaletteSelector(Context context) {
        super(context);
        init(context);
    }

    public PaletteSelector(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init(context);
    }

    public static class CategoryItem {
        public int index;
        public String name;
        public int color;
        public int type; // 0: variable, 1: list, 2: blocks.json, 3: saved collection, 4: moreblock
        public String blockJsonPath; // For saved collection blocks
        public String originalCategory; // For matching in blocks.json

        public CategoryItem(int index, String name, int color, int type) {
            this.index = index;
            this.name = name;
            this.color = color;
            this.type = type;
        }
    }

    public static java.util.List<CategoryItem> categoriesList = new java.util.ArrayList<>();

    public void refreshCategories() {
        addCategory();
    }

    private void addCategory() {
        categoriesList.clear();
        removeAllViews();

        int index = 0;
        boolean isCss = LogicBlockActivity.isCssEvent();
        
        if (!isCss) {
            // 1. Variables
            CategoryItem varCat = new CategoryItem(index, getResources().getString(R.string.title_bl_cate_var), -1147626, 0);
            categoriesList.add(varCat);
            addCategoryItem(index++, varCat.name, varCat.color);

            // 2. Lists
            CategoryItem listCat = new CategoryItem(index, getResources().getString(R.string.title_bl_cate_list), -3384542, 1);
            categoriesList.add(listCat);
            addCategoryItem(index++, listCat.name, listCat.color);
        }

        // 3. Dynamic categories from categories.json
        List<CategoryDef> categoryDefs = CategoryDef.getCategories(this.mContext);
        for (CategoryDef catDef : categoryDefs) {
            String catType = catDef.type != null ? catDef.type.toLowerCase() : "common";
            if (isCss) {
                if (!catType.equals("css") && !catType.equals("common")) continue;
            } else {
                if (!catType.equals("js") && !catType.equals("common")) continue;
            }
            int color;
            try {
                color = (catDef.catColor != null && !catDef.catColor.isEmpty()) ? android.graphics.Color.parseColor(catDef.catColor) : BlockCategoryPalette.colorIntForCategory(catDef.id);
            } catch (Exception ex) {
                color = BlockCategoryPalette.colorIntForCategory(catDef.id);
            }
            CategoryItem catItem = new CategoryItem(index, catDef.name, color, 2);
            catItem.originalCategory = catDef.id;
            categoriesList.add(catItem);
            addCategoryItem(index++, catDef.name, color);
        }

        // 4. Saved collections from SD card (/.dragweb/resources/block/)
        java.io.File rootDir = new java.io.File(android.os.Environment.getExternalStorageDirectory(), ".dragweb/resources/block");
        if (rootDir.exists() && rootDir.isDirectory()) {
            java.io.File[] files = rootDir.listFiles();
            if (files != null) {
                for (java.io.File subDir : files) {
                    if (subDir.isDirectory()) {
                        java.io.File paletteFile = new java.io.File(subDir, "palette.json");
                        java.io.File blockFile = new java.io.File(subDir, "block.json");
                        if (paletteFile.exists() && blockFile.exists()) {
                            try {
                                java.io.InputStream is = new java.io.FileInputStream(paletteFile);
                                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                                byte[] buf = new byte[1024];
                                int len;
                                while ((len = is.read(buf)) != -1) {
                                    bos.write(buf, 0, len);
                                }
                                is.close();
                                String json = bos.toString("UTF-8");
                                String name = subDir.getName();
                                String colorHex = "#FF2196F3";
                                if (json.trim().startsWith("[")) {
                                    org.json.JSONArray arr = new org.json.JSONArray(json);
                                    if (arr.length() > 0) {
                                        org.json.JSONObject obj = arr.getJSONObject(0);
                                        name = obj.optString("name", name);
                                        colorHex = obj.optString("color", colorHex);
                                    }
                                } else {
                                    org.json.JSONObject obj = new org.json.JSONObject(json);
                                    name = obj.optString("name", name);
                                    colorHex = obj.optString("color", colorHex);
                                }
                                int color = Color.parseColor(colorHex);
                                CategoryItem savedCat = new CategoryItem(index, name, color, 3);
                                savedCat.blockJsonPath = blockFile.getAbsolutePath();
                                categoriesList.add(savedCat);
                                addCategoryItem(index++, name, color);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }

        if (!isCss) {
            // 5. More Blocks (custom blocks)
            CategoryItem moreCat = new CategoryItem(index, getResources().getString(R.string.title_bl_cate_moreblock), -7711273, 4);
            categoriesList.add(moreCat);
            addCategoryItem(index++, moreCat.name, moreCat.color);
        }
    }

    private String formatCategoryName(String cat) {
        if (cat == null || cat.isEmpty()) return "";
        if (cat.equalsIgnoreCase("css")) return "CSS";
        if (cat.equalsIgnoreCase("asd")) return "ASD";
        String[] parts = cat.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            if (part.equalsIgnoreCase("js")) {
                sb.append("JS");
            } else if (part.equalsIgnoreCase("dom")) {
                sb.append("DOM");
            } else {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private void addCategoryItem(int i, String str, int i2) {
        PaletteSelectorItem paletteSelectorItem = new PaletteSelectorItem(this.mContext, i, str, i2);
        paletteSelectorItem.setOnClickListener(this);
        addView(paletteSelectorItem);
        if (i == 0) {
            paletteSelectorItem.setSelected(true);
        }
    }

    private void clearSelection() {
        for (int i = 0; i < getChildCount(); i++) {
            View childAt = getChildAt(i);
            if (childAt instanceof PaletteSelectorItem) {
                ((PaletteSelectorItem) childAt).setSelected(false);
            }
        }
    }

    private void init(Context context) {
        this.mContext = context;
        setOrientation(1);
        setLayoutParams(new LayoutParams(-1, -2));
        int dip = (int) LayoutUtil.getDip(context, 8.0f);
        int dip2 = (int) LayoutUtil.getDip(context, 4.0f);
        setPadding(dip, dip2, dip, dip2);
        addCategory();
    }

    public void onClick(View view) {
        if (view instanceof PaletteSelectorItem) {
            clearSelection();
            PaletteSelectorItem paletteSelectorItem = (PaletteSelectorItem) view;
            paletteSelectorItem.setSelected(true);
            this.mListener.onBlockCategorySelect(paletteSelectorItem.getId(), paletteSelectorItem.getColor());
        }
    }

    public void setOnBlockCategorySelectListener(OnBlockCategorySelectListener onBlockCategorySelectListener) {
        this.mListener = onBlockCategorySelectListener;
    }

    public void refresh() {
        init(getContext());
    }
}
