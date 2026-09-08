package glab.dragweb.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;
import java.util.HashMap;

public class SharedPreferenceUtil {
    private Context context;
    private Editor editor;
    private SharedPreferences sharedPref;

    public SharedPreferenceUtil(Context context, String str) {
        Context ctx = context != null ? context : glab.dragweb.SketchApplication.getContext();
        this.context = ctx;
        if (ctx != null && str != null) {
            this.sharedPref = ctx.getSharedPreferences(str, Context.MODE_PRIVATE);
            this.editor = this.sharedPref.edit();
        }
    }

    public boolean clear(String str) {
        if (this.editor != null) {
            this.editor.remove(str);
            this.editor.apply();
        }
        return true;
    }

    public boolean clearAll() {
        if (this.editor != null) {
            this.editor.clear();
            this.editor.apply();
        }
        return true;
    }

    public boolean commit() {
        if (this.editor != null) {
            this.editor.apply();
        }
        return true;
    }

    public boolean containKey(String str) {
        return this.sharedPref != null && this.sharedPref.contains(str);
    }

    public boolean getBoolean(String str) {
        return getBoolean(str, false);
    }

    public boolean getBoolean(String str, boolean z) {
        return this.sharedPref != null ? this.sharedPref.getBoolean(str, z) : z;
    }

    public int getInt(String str) {
        return getInt(str, 0);
    }

    public int getInt(String str, int i) {
        return this.sharedPref != null ? this.sharedPref.getInt(str, i) : i;
    }

    public long getLong(String str) {
        return getLong(str, 0L);
    }

    public long getLong(String str, long l) {
        return this.sharedPref != null ? this.sharedPref.getLong(str, l) : l;
    }

    public String getString(String str) {
        return getString(str, "");
    }

    public String getString(String str, String str2) {
        return this.sharedPref != null ? this.sharedPref.getString(str, str2) : str2;
    }

    public void putObject(String str, Object obj) {
        putObject(str, obj, true);
    }

    public void putObject(String str, Object obj, boolean z) {
        if (this.editor == null) return;
        if (obj instanceof String) {
            this.editor.putString(str, (String) obj);
        } else if (obj instanceof Integer) {
            this.editor.putInt(str, ((Integer) obj).intValue());
        } else if (obj instanceof Long) {
            this.editor.putLong(str, ((Long) obj).longValue());
        } else if (obj instanceof Boolean) {
            this.editor.putBoolean(str, ((Boolean) obj).booleanValue());
        } else if (obj != null) {
            this.editor.putString(str, obj.toString());
        }
        if (z) {
            this.editor.apply();
        }
    }

    public HashMap<String, Object> readJSON(String str) {
        String string = getString(str);
        return string.isEmpty() ? new HashMap<>() : JsonUtil.readJSON(string);
    }

    public HashMap<String, Object> readState() {
        try {
            return this.sharedPref != null ? (HashMap) this.sharedPref.getAll() : new HashMap<>();
        } catch (Throwable e) {
            Log.e("SharedPreferenceUtil", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    public void setListener(OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {
        if (this.sharedPref != null) {
            this.sharedPref.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
        }
    }

    public void writeJSON(String str, HashMap<String, Object> hashMap) {
        putObject(str, JsonUtil.writeJSON(hashMap));
    }

    public boolean writeState(HashMap<String, Object> hashMap) {
        try {
            if (hashMap != null && this.editor != null) {
                for (String str : hashMap.keySet()) {
                    putObject(str, hashMap.get(str), false);
                }
                this.editor.apply();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            if (this.editor != null) this.editor.clear().apply();
            return false;
        }
    }
}
