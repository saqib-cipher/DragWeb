package sketchweb.gl;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Clean-room implementation of Ss.class.
 * Handles interactive input holes (text, numbers, menus).
 */
public class Ss extends LinearLayout {
    
    public Context T; // context
    public Object U = ""; // argValue
    public TextView V; // label
    public TextView W; // typeLabel (optional)
    
    public String b; // menuName
    public String c; // type
    public float density;
    
    public int color = 0xFFFFFFFF;
    private final Paint paint;

    public Ss(Context context, String type, String menuName) {
        super(context);
        this.T = context;
        this.c = type;
        this.b = menuName;
        this.density = LayoutUtil.getDip(context, 1.0F);
        
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.paint.setStyle(Paint.Style.FILL);
        
        initializeUI();
    }

    private void initializeUI() {
        setGravity(Gravity.CENTER);
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);
        
        V = new TextView(T);
        V.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        V.setTextColor(0xFF000000);
        V.setGravity(Gravity.CENTER);
        
        int pad = (int) (4 * density);
        V.setPadding(pad, 0, pad, 0);
        
        addView(V);
        updateDisplay();
    }

    private void updateDisplay() {
        if (U == null || U.toString().isEmpty()) {
            V.setText(b != null && !b.isEmpty() ? b : " ");
        } else {
            V.setText(U.toString());
        }
        invalidate();
    }

    public void setArgValue(Object value) {
        this.U = value;
        updateDisplay();
    }

    public Object getArgValue() {
        return U;
    }

    public String getMenuName() {
        return b;
    }

    public int getW() {
        // Exact measurement logic from Ss.class
        if (V == null || V.getText() == null) return (int)(30 * density);
        int textWidth = (int) V.getPaint().measureText(V.getText().toString());
        return Math.max(textWidth + (int)(16 * density), (int)(30 * density));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setColor(color);
        RectF rect = new RectF(0, 0, getWidth(), getHeight());
        
        float radius = c.equals("d") ? getHeight() / 2f : 4 * density;
        canvas.drawRoundRect(rect, radius, radius, paint);
        
        super.onDraw(canvas);
    }
}
