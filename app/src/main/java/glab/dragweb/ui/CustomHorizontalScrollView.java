package glab.dragweb.ui;

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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

public class CustomHorizontalScrollView extends HorizontalScrollView {
    protected boolean enabled = true;

    public CustomHorizontalScrollView(Context context) {
        super(context);
    }

    public CustomHorizontalScrollView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
        return this.enabled ? super.onInterceptTouchEvent(motionEvent) : false;
    }

    public boolean onTouchEvent(MotionEvent motionEvent) {
        return this.enabled ? super.onTouchEvent(motionEvent) : false;
    }

    public void setScrollDisabled() {
        this.enabled = false;
    }

    public void setScrollEnabled() {
        this.enabled = true;
    }
}
