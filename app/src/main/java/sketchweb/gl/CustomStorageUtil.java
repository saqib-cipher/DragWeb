package sketchweb.gl;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.io.InputStream;

public class CustomStorageUtil {

    public static File getCustomDir(Context context) {
        File dir = null;
        try {
            dir = new File(Environment.getExternalStorageDirectory(), ".dragweb/custom");
            if (!dir.exists()) dir.mkdirs();
        } catch (Exception ignored) {}

        if (dir == null || !dir.exists()) {
            if (context != null) {
                dir = new File(context.getExternalFilesDir(null), "custom");
                if (!dir.exists()) dir.mkdirs();
            }
        }
        return dir;
    }

    public static File getCustomFile(Context context, String filename) {
        File dir = getCustomDir(context);
        File file = new File(dir, filename);
        if (!file.exists()) {
            copyAssetToStorage(context, filename, file);
        }
        return file;
    }

    public static void copyAssetToStorage(Context context, String filename, File destFile) {
        if (context == null || destFile == null) return;
        try {
            String assetName = filename;
            if ("params.json".equals(filename)) assetName = "param.json";

            try (InputStream is = context.getAssets().open(assetName)) {
                byte[] buf = new byte[is.available()];
                int read = is.read(buf);
                if (read > 0) {
                    String content = new String(buf, 0, read, "UTF-8");
                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    FileUtil.writeFile(destFile.getAbsolutePath(), content);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
