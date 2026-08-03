package sketchweb.gl;



import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

public class PaletteBlock extends LinearLayout {
    LinearLayout blockBuilder;
    private float dip = 0.0f;
    CustomHorizontalScrollView hscv;
    private Context mContext;
    CustomScrollView scv;

    public PaletteBlock(Context context) {
        super(context);
        init(context);
    }

    public PaletteBlock(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init(context);
    }

    LinearLayout actionsContainer;

    private void init(Context context) {
        this.mContext = context;
        LayoutUtil.inflate(context, this, R.layout.palette_block);
        this.scv = (CustomScrollView) findViewById(R.id.scv);
        this.hscv = (CustomHorizontalScrollView) findViewById(R.id.hscv);
        this.blockBuilder = (LinearLayout) findViewById(R.id.block_builder);
        this.actionsContainer = (LinearLayout) findViewById(R.id.actions_container);
        this.dip = LayoutUtil.getDip(this.mContext, 1.0f); 
    }

    public BlockBase addBlock(String str, String str2, String str3, int i, Object... objArr) {
        View view = new View(this.mContext);
        view.setLayoutParams(new LayoutParams(-1, (int) (8.0f * this.dip)));
        this.blockBuilder.addView(view);
        Block block = new Block(this.mContext, -1, str, str2, str3, new Object[]{Integer.valueOf(i), objArr});
        block.setBlockType(1);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        block.setLayoutParams(lp);
        this.blockBuilder.addView(block);
        return block;
    }

    public TextView addButton(String str) {
        return (TextView) addButton(str, "");
    }

    public View addButton(String str, String tag) {
        if (tag != null && tag.startsWith("label_")) {
            TextView textView = new TextView(this.mContext);
            LayoutParams lp = new LayoutParams(-1, (int) (32.0f * this.dip));
            lp.setMargins(0, (int) (12.0f * this.dip), (int) (8.0f * this.dip), (int) (4.0f * this.dip));
            textView.setLayoutParams(lp);
            
            android.util.TypedValue typedValue = new android.util.TypedValue();
            int bgColor = 0xFFE0E0E0;
            if (this.mContext.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHigh, typedValue, true)) {
                bgColor = typedValue.data;
            } else if (this.mContext.getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true)) {
                bgColor = typedValue.data;
            }
            
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(bgColor);
            gd.setCornerRadius(8.0f * this.dip);
            textView.setBackground(gd);
            
            textView.setText(str);
            textView.setTextSize(11.0f);
            textView.setTypeface(null, android.graphics.Typeface.BOLD);
            textView.setGravity(17);
            
            int textColor = 0xFF616161;
            if (this.mContext.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)) {
                textColor = typedValue.data;
            }
            textView.setTextColor(textColor);
            
            this.blockBuilder.addView(textView);
            return textView;
        } else {
            com.google.android.material.button.MaterialButton button = new com.google.android.material.button.MaterialButton(this.mContext, null, com.google.android.material.R.attr.materialButtonTonalStyle);
            LayoutParams lp = new LayoutParams(-1, LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, (int) (8.0f * this.dip), (int) (6.0f * this.dip));
            button.setLayoutParams(lp);
            button.setText(str);
            button.setCornerRadius((int) (12.0f * this.dip));
            button.setTextSize(13.0f);
            button.setAllCaps(false);
            
            this.blockBuilder.addView(button);
            return button;
        }
    }

    public void removeAllBlocks() {
        this.blockBuilder.removeAllViews();
    }

    public void setDragEnabled(boolean z) {
        if (z) {
            this.scv.setScrollEnabled();
            this.hscv.setScrollEnabled();
            return;
        }
        this.scv.setScrollDisabled();
        this.hscv.setScrollDisabled();
    }

    public void setMinWidth(int i) {
        this.scv.setMinimumWidth(i - ((int) (this.dip * 5.0f)));
        this.hscv.setMinimumWidth(i - ((int) (this.dip * 5.0f)));
        getLayoutParams().width = i;
    }
}
