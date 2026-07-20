package sketchweb.gl;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;

import java.util.ArrayList;

public class BlockArg extends BlockBase {
    private Object argValue = "";
    private ViewGroup content;
    private int defaultArgWidth = 20;
    private boolean isEditable = true;
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
        if (var2) {
            var7.setImageResource(this.getContext().getResources().getIdentifier(var1, "drawable", this.getContext().getPackageName()));
        }

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

    public int getBaseW() {
        if (this.mTextView == null || this.mTextView.getText() == null) return (int) (20 * this.dip);
        int textWidth = (int) this.mTextView.getPaint().measureText(this.mTextView.getText().toString());
        return Math.max(textWidth + (int) (8 * this.dip), (int) (20 * this.dip));
    }

    public int getW() {
        int baseW = getBaseW();
        return this.mType.equals("m") ? baseW + this.dropdownArea : baseW;
    }

    private int getLabelWidth() {
        return getW();
    }

    private void init(Context var1) {
        byte var3;
        label48: {
            String var2 = this.mType;
            switch (var2.hashCode()) {
                case 98:
                    if (var2.equals("b")) {
                        var3 = 0;
                        break label48;
                    }
                    break;
                case 100:
                    if (var2.equals("d")) {
                        var3 = 1;
                        break label48;
                    }
                    break;
                case 109:
                    if (var2.equals("m")) {
                        var3 = 4;
                        break label48;
                    }
                    break;
                case 110:
                    if (var2.equals("n")) {
                        var3 = 2;
                        break label48;
                    }
                    break;
                case 115:
                    if (var2.equals("s")) {
                        var3 = 3;
                        break label48;
                    }
            }

            var3 = -1;
        }

        switch (var3) {
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

        this.defaultArgWidth = (int) ((float) this.defaultArgWidth * this.dip);
        this.paddingText = (int) ((float) this.paddingText * this.dip);
        if (this.mType.equals("m") || this.mType.equals("d") || this.mType.equals("n") || this.mType.equals("s")) {
            String initialText = "";
            if (this.mType.equals("m") && this.mMenuName != null && !this.mMenuName.isEmpty()) {
                initialText = this.mMenuName;
            }
            this.mTextView = this.makeEditText(initialText);
            this.addView(this.mTextView);
        }

        if (this.mTextView != null) {
            int baseW = getBaseW();
            this.mTextView.getLayoutParams().width = baseW;
            this.setWidthAndTopHeight((float) baseW, (float) this.labelAndArgHeight, false);
        } else {
            this.setWidthAndTopHeight((float) this.defaultArgWidth, (float) this.labelAndArgHeight, false);
        }
    }

    private TextView makeEditText(String var1) {
        TextView var2 = new TextView(this.mContext);
        var2.setText(var1);
        var2.setTextSize(9.0F);
        android.widget.RelativeLayout.LayoutParams var3 = new android.widget.RelativeLayout.LayoutParams(this.defaultArgWidth, this.labelAndArgHeight);
        var3.setMargins(0, 0, 0, 0);
        var2.setPadding((int) (3.0F * this.dip), 0, (int) (3.0F * this.dip), 0);
        var2.setLayoutParams(var3);
        var2.setBackgroundColor(0);
        var2.setSingleLine();
        var2.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.LEFT);
        if (!this.mType.equals("m")) {
            var2.setTextColor(-268435456);
        } else {
            var2.setTextColor(android.graphics.Color.WHITE);
        }
        return var2;
    }

    public Object getArgValue() {
        return !this.mType.equals("d") && !this.mType.equals("m") && !this.mType.equals("s") ? this.argValue : (this.mTextView != null ? this.mTextView.getText() : "");
    }

    public void setArgValue(Object var1) {
        this.argValue = var1;
        if (this.mType.equals("d") || this.mType.equals("m") || this.mType.equals("s")) {
            String textToDisplay = (var1 != null && !var1.toString().isEmpty()) ? var1.toString() : "";
            if (textToDisplay.isEmpty() && this.mType.equals("m") && !this.mMenuName.isEmpty()) {
                textToDisplay = this.mMenuName;
            }
            if (this.mTextView != null) {
                this.mTextView.setText(textToDisplay);
                int baseW = getBaseW();
                this.mTextView.getLayoutParams().width = baseW;
                this.setWidthAndTopHeight((float) baseW, (float) this.labelAndArgHeight, true);
            }
        }
    }

    public void setEditable(boolean var1) {
        this.isEditable = var1;
    }

    public void showPopup() {
        if (this.isEditable) {
            String menuName = this.mMenuName != null ? this.mMenuName.trim() : "";
            if (menuName.startsWith("%m.")) menuName = menuName.substring(3);
            else if (menuName.startsWith("%s.")) menuName = menuName.substring(3);
            else if (menuName.startsWith("m.")) menuName = menuName.substring(2);
            else if (menuName.startsWith("s.")) menuName = menuName.substring(2);

            if ("selector".equalsIgnoreCase(menuName)) {
                UniversalM3Dialog dialog = new UniversalM3Dialog(this.getContext());
                dialog.setTitle("Pick selector")
                      .setInitialValue(this.argValue != null ? this.argValue.toString() : "");
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
                        if (parentBlock != null) {
                            parentBlock.recalcWidthToParent();
                            if (parentBlock.topBlock() != null) parentBlock.topBlock().fixLayout();
                            if (parentBlock.pane != null) parentBlock.pane.calculateWidthHeight();
                        }
                    }
                });
                return;
            }

            if ("color".equalsIgnoreCase(menuName)) {
                UniversalM3Dialog dialog = new UniversalM3Dialog(this.getContext());
                dialog.setTitle("Select Color")
                      .setInitialValue(this.argValue != null ? this.argValue.toString() : "");
                dialog.showColorInput(new UniversalM3Dialog.OnText() {
                    @Override
                    public void onText(String value) {
                        setArgValue(value);
                        if (parentBlock != null) {
                            parentBlock.recalcWidthToParent();
                            if (parentBlock.topBlock() != null) parentBlock.topBlock().fixLayout();
                            if (parentBlock.pane != null) parentBlock.pane.calculateWidthHeight();
                        }
                    }
                });
                return;
            }

            if ("unit".equalsIgnoreCase(menuName)) {
                UniversalM3Dialog dialog = new UniversalM3Dialog(this.getContext());
                dialog.setTitle("Select Unit")
                      .setInitialValue(this.argValue != null ? this.argValue.toString() : "");
                dialog.showUnitInput(new UniversalM3Dialog.OnText() {
                    @Override
                    public void onText(String value) {
                        setArgValue(value);
                        if (parentBlock != null) {
                            parentBlock.recalcWidthToParent();
                            if (parentBlock.topBlock() != null) parentBlock.topBlock().fixLayout();
                            if (parentBlock.pane != null) parentBlock.pane.calculateWidthHeight();
                        }
                    }
                });
                return;
            }

            BlockParamTypeManager pManager = new BlockParamTypeManager(this.getContext());
            final ArrayList<String> options = new ArrayList<>();
            if (pManager.hasType(menuName)) {
                options.addAll(pManager.getOptions(menuName));
            } else if (menuName.equals("varInt")) {
                options.addAll(DesignDataManager.getVariablesByType(LogicBlockActivity.filename, 1));
            } else if (menuName.equals("varBool")) {
                options.addAll(DesignDataManager.getVariablesByType(LogicBlockActivity.filename, 0));
            } else if (menuName.equals("varStr")) {
                options.addAll(DesignDataManager.getVariablesByType(LogicBlockActivity.filename, 2));
            } else if (menuName.equals("listInt")) {
                options.addAll(DesignDataManager.getListsByType(LogicBlockActivity.filename, 1));
            } else if (menuName.equals("listStr")) {
                options.addAll(DesignDataManager.getListsByType(LogicBlockActivity.filename, 2));
            } else if (menuName.equals("list")) {
                options.addAll(DesignDataManager.getAllLists(LogicBlockActivity.filename));
            }

            if (!options.isEmpty() || this.mType.equals("m")) {
                UniversalM3Dialog dialog = new UniversalM3Dialog(this.getContext());
                dialog.setTitle("Select " + (menuName.isEmpty() ? "Value" : menuName))
                      .setOptions(options.toArray(new String[0]))
                      .setInitialValue(this.argValue != null ? this.argValue.toString() : "");
                dialog.showChoiceInput(new UniversalM3Dialog.OnText() {
                    @Override
                    public void onText(String value) {
                        setArgValue(value);
                        if (parentBlock != null) {
                            parentBlock.recalcWidthToParent();
                            if (parentBlock.topBlock() != null) parentBlock.topBlock().fixLayout();
                            if (parentBlock.pane != null) parentBlock.pane.calculateWidthHeight();
                        }
                    }
                });
                return;
            }

            if (this.mType.equals("b") || this.mType.equals("d") || this.mType.equals("s")) {
                boolean isNumber = this.mType.equals("d");
                UniversalM3Dialog dialog = new UniversalM3Dialog(this.getContext());
                dialog.setTitle(isNumber ? "Input Number Value" : "Input String Value")
                      .setInitialValue(this.mTextView != null ? this.mTextView.getText().toString() : "");
                dialog.showTextInput(new UniversalM3Dialog.OnText() {
                    @Override
                    public void onText(String value) {
                        setArgValue(value);
                        if (parentBlock != null) {
                            parentBlock.recalcWidthToParent();
                            if (parentBlock.topBlock() != null) parentBlock.topBlock().fixLayout();
                            if (parentBlock.pane != null) parentBlock.pane.calculateWidthHeight();
                        }
                    }
                });
                return;
            }
        }
    }
}
