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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.HashMap;

public class JsonUtil {
    public static HashMap<String, Object> readJSON(String str) {
        return new Gson().fromJson(str, new TypeToken<HashMap<String, Object>>(){}.getType());
    }

    public static String writeJSON(HashMap<String, Object> hashMap) {
        return new Gson().toJson(hashMap);
    }
}
