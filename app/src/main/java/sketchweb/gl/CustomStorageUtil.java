package sketchweb.gl;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.Executors;

public class CustomStorageUtil {

    public interface OnSyncProgressListener {
        void onProgress(String status, int percent);
    }

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
        if (isStorageOutdatedOrMissing(context, filename, file)) {
            copyAssetToStorage(context, filename, file);
        }
        return file;
    }

    public static boolean isStorageOutdatedOrMissing(Context context, String filename, File storageFile) {
        if (context == null) return false;
        if (storageFile == null) storageFile = new File(getCustomDir(context), filename);
        if (!storageFile.exists() || storageFile.length() == 0) return true;

        String assetName = filename;
        if ("params.json".equals(filename)) assetName = "param.json";

        try (InputStream is = context.getAssets().open(assetName)) {
            long assetSize = is.available();
            if (storageFile.length() < assetSize) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean needsSync(Context context) {
        String[] files = new String[]{"blocks.json", "categories.json", "params.json", "widgets.json"};
        for (String file : files) {
            if (isStorageOutdatedOrMissing(context, file, null)) {
                return true;
            }
        }
        return false;
    }

    public static void syncAssetsToStorage(Context context, OnSyncProgressListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            String[] files = new String[]{"blocks.json", "categories.json", "params.json", "widgets.json"};
            int total = files.length;
            for (int i = 0; i < total; i++) {
                String filename = files[i];
                if (listener != null) {
                    listener.onProgress("Processing " + filename + "...", (int) (((i + 0.2f) / total) * 100));
                }
                File destFile = new File(getCustomDir(context), filename);
                if (isStorageOutdatedOrMissing(context, filename, destFile)) {
                    copyAssetToStorage(context, filename, destFile);
                }
                if (listener != null) {
                    listener.onProgress("Synced " + filename, (int) (((i + 1.0f) / total) * 100));
                }
            }
            if (listener != null) {
                listener.onProgress("Assets Initialization Complete", 100);
            }
        });
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
