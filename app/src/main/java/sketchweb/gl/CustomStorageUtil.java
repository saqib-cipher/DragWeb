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
        if (storageFile == null) storageFile = new File(getCustomDir(context), filename);
        return !storageFile.exists() || storageFile.length() == 0;
    }

    public static boolean needsSync(Context context) {
        String[] files = new String[]{"blocks.json", "categories.json", "param.json", "widgets.json"};
        for (String file : files) {
            if (isStorageOutdatedOrMissing(context, file, null)) {
                return true;
            }
        }
        return false;
    }

    public static void syncAssetsToStorage(Context context, OnSyncProgressListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            String[] files = new String[]{"blocks.json", "categories.json", "param.json", "widgets.json"};
            int total = files.length;
            for (int i = 0; i < total; i++) {
                String filename = files[i];
                if (listener != null) {
                    listener.onProgress("Processing " + filename + "...", (int) (((i + 0.2f) / total) * 100));
                }
                File destFile = new File(getCustomDir(context), filename);
                // Always force-copy: ensures file is never 0 bytes from a previous failed write
                copyAssetToStorage(context, filename, destFile);
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
            // Ensure parent directory exists
            File parent = destFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            // Read from assets and write bytes directly using FileOutputStream
            try (InputStream is = context.getAssets().open(filename);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, n);
                }
                fos.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
