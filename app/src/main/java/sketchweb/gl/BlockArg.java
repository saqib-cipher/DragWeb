package sketchweb.gl;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout.LayoutParams;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import android.content.DialogInterface;

public class BlockArg extends BlockBase {
    private Object argValue = "";
    private ViewGroup content;
    private int defaultArgWidth = 20;
    private boolean isEditable = false;
    private Context mContext;
    private AlertDialog mDlg;
    private String mMenuName = "";
    private TextView mTextView;
    private int paddingText = 4;

    public BlockArg(Context var1, String var2, int var3, String var4) {
        super(var1, var2, true);
        this.mContext = var1;
        this.mMenuName = var4;
        this.init(var1);
    }

    // $FF: synthetic method
    static AlertDialog access$000(BlockArg var0) {
        return var0.mDlg;
    }

    // $FF: synthetic method
    static ViewGroup access$100(BlockArg var0) {
        return var0.content;
    }

    private RadioButton createImageRadioButton(String var1) {
        RadioButton var2 = new RadioButton(this.getContext());
        var2.setText("");
        var2.setTag(var1);
        LinearLayout.LayoutParams var3 = new LinearLayout.LayoutParams(-2, (int)(60.0F * LayoutUtil.getDip(this.getContext(), 1.0F)));
        var2.setGravity(19);
        var2.setLayoutParams(var3);
        return var2;
    }

    private RadioButton createPairItem(String var1, String var2) {
        RadioButton var3 = new RadioButton(this.getContext());
        var3.setText(var1 + " : " + var2);
        var3.setTag(var2);
        LinearLayout.LayoutParams var4 = new LinearLayout.LayoutParams(-1, (int)(40.0F * LayoutUtil.getDip(this.getContext(), 1.0F)));
        var3.setGravity(19);
        var3.setLayoutParams(var4);
        return var3;
    }

    private RadioButton createRadioButton(String var1) {
        RadioButton var2 = new RadioButton(this.getContext());
        var2.setText("");
        var2.setTag(var1);
        LinearLayout.LayoutParams var3 = new LinearLayout.LayoutParams(-2, (int)(40.0F * LayoutUtil.getDip(this.getContext(), 1.0F)));
        var2.setGravity(19);
        var2.setLayoutParams(var3);
        return var2;
    }

    private LinearLayout createRadioImage(String var1, boolean var2) {
        float var3 = LayoutUtil.getDip(this.getContext(), 1.0F);
        LinearLayout var4 = new LinearLayout(this.getContext());
        var4.setLayoutParams(new LinearLayout.LayoutParams(-1, (int)(60.0F * var3)));
        var4.setGravity(19);
        var4.setOrientation(0);
        TextView var5 = new TextView(this.getContext());
        LinearLayout.LayoutParams var6 = new LinearLayout.LayoutParams(0, -2);
        var6.weight = 1.0F;
        var5.setLayoutParams(var6);
        var5.setText(var1);
        var4.addView(var5);
        ImageView var7 = new ImageView(this.getContext());
        var7.setScaleType(ScaleType.CENTER_CROP);
        var7.setLayoutParams(new LinearLayout.LayoutParams((int)(48.0F * var3), (int)(48.0F * var3)));
        if(var2) {
            var7.setImageResource(this.getContext().getResources().getIdentifier(var1, "drawable", this.getContext().getPackageName()));
        }/* else {
            Uri var8 = Uri.fromFile(new File(DesignActivity.resourceManager.getImagePathFromName(var1)));
            DrawableTypeRequest var9 = Glide.with(this.getContext()).load(var8);
            ResourceManager var10000 = DesignActivity.resourceManager;
            var9.signature(ResourceManager.getSignature()).error(R.drawable.ic_remove_grey600_24dp).into(var7);
        }*/

        var7.setBackgroundColor(-4342339);
        var4.addView(var7);
        return var4;
    }

    private RadioButton createSingleItem(String var1) {
        RadioButton var2 = new RadioButton(this.getContext());
        var2.setText(var1);
        LinearLayout.LayoutParams var3 = new LinearLayout.LayoutParams(-1, (int)(40.0F * LayoutUtil.getDip(this.getContext(), 1.0F)));
        var2.setGravity(19);
        var2.setLayoutParams(var3);
        return var2;
    }

    private int getLabelWidth() {
        Rect var1 = new Rect();
        this.mTextView.getPaint().getTextBounds(this.mTextView.getText().toString(), 0, this.mTextView.getText().length(), var1);
        return var1.width() + this.paddingText;
    }

    private void init(Context var1) {
        byte var3;
        label48: {
            String var2 = this.mType;
            switch(var2.hashCode()) {
                case 98:
                    if(var2.equals("b")) {
                        var3 = 0;
                        break label48;
                    }
                    break;
                case 100:
                    if(var2.equals("d")) {
                        var3 = 1;
                        break label48;
                    }
                    break;
                case 109:
                    if(var2.equals("m")) {
                        var3 = 4;
                        break label48;
                    }
                    break;
                case 110:
                    if(var2.equals("n")) {
                        var3 = 2;
                        break label48;
                    }
                    break;
                case 115:
                    if(var2.equals("s")) {
                        var3 = 3;
                        break label48;
                    }
            }

            var3 = -1;
        }

        switch(var3) {
            case 0:
                this.mColor = 1342177280;
                this.defaultArgWidth = 25;
                break;
            case 1:
                this.mColor = -657931;
                break;
            case 2:
                this.mColor = -3155748;
                break;
            case 3:
                this.mColor = -1;
                break;
            case 4:
                this.mColor = 805306368;
        }

        this.defaultArgWidth = (int)((float)this.defaultArgWidth * this.dip);
        this.paddingText = (int)((float)this.paddingText * this.dip);
        if(this.mType.equals("m") || this.mType.equals("d") || this.mType.equals("n") || this.mType.equals("s")) {
            this.mTextView = this.makeEditText("");
            this.addView(this.mTextView);
        }

        this.setWidthAndTopHeight((float)this.defaultArgWidth, (float)this.labelAndArgHeight, false);
    }

    private TextView makeEditText(String var1) {
        TextView var2 = new TextView(this.mContext);
        var2.setText(var1);
        var2.setTextSize(9.0F);
        android.widget.RelativeLayout.LayoutParams var3 = new android.widget.RelativeLayout.LayoutParams(this.defaultArgWidth, this.labelAndArgHeight);
        var3.setMargins(0, 0, 0, 0);
        var2.setPadding(5, 0, 0, 0);
        var2.setLayoutParams(var3);
        var2.setBackgroundColor(0);
        var2.setSingleLine();
        var2.setGravity(17);
        if(!this.mType.equals("m")) {
            var2.setTextColor(-268435456);
            return var2;
        } else {
            var2.setTextColor(-251658241);
            return var2;
        }
    }

   /* private void showColorPopup() {
        View var1 = LayoutUtil.inflate(this.getContext(), R.layout.color_picker);
        var1.setAnimation(AnimationUtils.loadAnimation(this.getContext(), R.anim.abc_fade_in));
        Object var2 = this.argValue;
        int var3 = 0;
        if(var2 != null) {
            int var5 = this.argValue.toString().length();
            var3 = 0;
            if(var5 > 0) {
                int var6 = this.argValue.toString().indexOf("0xFF");
                var3 = 0;
                if(var6 == 0) {
                    var3 = Color.parseColor(this.argValue.toString().replace("0xFF", "#"));
                }
            }
        }

        ColorPickerPopup var4 = new ColorPickerPopup(var1, (Activity)this.getContext(), var3, true, false);
        var4.setColorSelectedListener(new 9(this));
        var4.setAnimationStyle(R.anim.abc_fade_in);
        var4.showAtLocation(var1, 17, 0, 0);
    }*/

 /*   private void showImagePopup() {
        View var1 = LayoutUtil.inflate(this.getContext(), R.layout.property_popup_selector_color);
        Builder var2 = new Builder(this.getContext());
        var2.setView(var1);
        var2.setTitle(this.getResources().getString(R.string.title_popup_select_image));
        RadioGroup var5 = (RadioGroup)var1.findViewById(R.id.rg);
        this.content = (LinearLayout)var1.findViewById(R.id.content);
        ArrayList var6 = DesignActivity.resourceManager.getImageNames();
        if(ScDefine.isCustomEditMode(DesignActivity.getScId())) {
            var6.add(0, "default_image");
        }

        Iterator var7 = var6.iterator();

        while(var7.hasNext()) {
            String var10 = (String)var7.next();
            RadioButton var11 = this.createImageRadioButton(var10);
            var5.addView(var11);
            if(var10.equals(this.argValue)) {
                var11.setChecked(true);
            }

            LinearLayout var12;
            if(ScDefine.isCustomEditMode(DesignActivity.getScId())) {
                if(var10.equals("default_image")) {
                    var12 = this.createRadioImage(var10, true);
                } else {
                    var12 = this.createRadioImage(var10, false);
                }
            } else {
                var12 = this.createRadioImage(var10, true);
            }

            var12.setOnClickListener(new 10(this, var5));
            this.content.addView(var12);
        }

        var2.setNegativeButton(R.string.btn_cancel, new 11(this));
        var2.setPositiveButton(R.string.btn_accept, new 12(this, var5));
        this.mDlg = var2.create();
        this.mDlg.show();
    }*/

    public Object getArgValue() {
        return !this.mType.equals("d") && !this.mType.equals("m") && !this.mType.equals("s")?this.argValue:this.mTextView.getText();
    }

    public void setArgValue(Object var1) {
        this.argValue = var1;
        if(this.mType.equals("d") || this.mType.equals("m") || this.mType.equals("s")) {
            this.mTextView.setText(var1.toString());
            int var2 = Math.max(this.defaultArgWidth, this.getLabelWidth());
            this.mTextView.getLayoutParams().width = var2;
            this.setWidthAndTopHeight((float)var2, (float)this.labelAndArgHeight, true);
        }

    }

    public void setEditable(boolean var1) {
        this.isEditable = var1;
    }

    public void showPopup() {
        if (this.mType.equals("d") || this.mType.equals("s")) {
            boolean isNumber = this.mType.equals("d");
            UniversalM3Dialog dialog = new UniversalM3Dialog(this.getContext());
            dialog.setTitle(isNumber ? "Input Number Value" : "Input String Value")
                  .setInitialValue(this.mTextView.getText().toString());
            dialog.showTextInput(new UniversalM3Dialog.OnText() {
                @Override
                public void onText(String value) {
                    setArgValue(value);
                    parentBlock.recalcWidthToParent();
                    parentBlock.topBlock().fixLayout();
                    parentBlock.pane.calculateWidthHeight();
                }
            });
            return;
        }

        if (this.mType.equals("m")) {
            if (this.mMenuName.equals("selector")) {
                UniversalM3Dialog dialog = new UniversalM3Dialog(this.getContext());
                dialog.setTitle("Pick selector")
                      .setInitialValue(this.argValue.toString());
                java.util.List<String> ids = ProjectAssetManager.getInstance().getIds();
                java.util.List<String> classes = ProjectAssetManager.getInstance().getClasses();
                java.util.List<String> tags = ProjectAssetManager.getInstance().getTags();
                if (ids.isEmpty() && classes.isEmpty()) {
                    DesignDataManager.WidgetSelectorData data = DesignDataManager.getWidgetSelectorData(this.getContext(), LogicBlockActivity.projectId, LogicBlockActivity.pageName);
                    ids = data.ids;
                    classes = data.classes;
                    tags = data.tags;
                }
                dialog.showSelectorInput(ids, classes, tags, new UniversalM3Dialog.OnText() {
                    @Override
                    public void onText(String value) {
                        setArgValue(value);
                        parentBlock.recalcWidthToParent();
                        parentBlock.topBlock().fixLayout();
                        parentBlock.pane.calculateWidthHeight();
                    }
                });
                return;
            }

            if (this.mMenuName.equals("color")) {
                UniversalM3Dialog dialog = new UniversalM3Dialog(this.getContext());
                dialog.setTitle("Select Color")
                      .setInitialValue(this.argValue.toString());
                dialog.showColorInput(new UniversalM3Dialog.OnText() {
                    @Override
                    public void onText(String value) {
                        setArgValue(value);
                        parentBlock.recalcWidthToParent();
                        parentBlock.topBlock().fixLayout();
                        parentBlock.pane.calculateWidthHeight();
                    }
                });
                return;
            }

            final ArrayList<String> options = new ArrayList<>();
            BlockParamTypeManager pManager = new BlockParamTypeManager();
            if (pManager.getAllParamTypes().containsKey(this.mMenuName) || this.mMenuName.equals("selector") || this.mMenuName.equals("unit")) {
                options.addAll(pManager.getOptions(this.mMenuName));
            } else if (this.mMenuName.equals("varInt")) {
                options.addAll(DesignDataManager.getVariablesByType(LogicBlockActivity.filename, 1));
            } else if (this.mMenuName.equals("varBool")) {
                options.addAll(DesignDataManager.getVariablesByType(LogicBlockActivity.filename, 0));
            } else if (this.mMenuName.equals("varStr")) {
                options.addAll(DesignDataManager.getVariablesByType(LogicBlockActivity.filename, 2));
            } else if (this.mMenuName.equals("listInt")) {
                options.addAll(DesignDataManager.getListsByType(LogicBlockActivity.filename, 1));
            } else if (this.mMenuName.equals("listStr")) {
                options.addAll(DesignDataManager.getListsByType(LogicBlockActivity.filename, 2));
            } else if (this.mMenuName.equals("list")) {
                options.addAll(DesignDataManager.getAllLists(LogicBlockActivity.filename));
            }

            UniversalM3Dialog dialog = new UniversalM3Dialog(this.getContext());
            dialog.setTitle("Select " + this.mMenuName)
                  .setOptions(options.toArray(new String[0]))
                  .setInitialValue(this.argValue.toString());
            dialog.showChoiceInput(new UniversalM3Dialog.OnText() {
                @Override
                public void onText(String value) {
                    setArgValue(value);
                    parentBlock.recalcWidthToParent();
                    parentBlock.topBlock().fixLayout();
                    parentBlock.pane.calculateWidthHeight();
                }
            });
        }
    }
}

