package glab.dragweb.util;

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
import android.widget.Toast;

public class SketchwareUtil {

    public static void showMessage(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
