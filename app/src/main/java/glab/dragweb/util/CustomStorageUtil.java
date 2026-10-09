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
import android.os.Environment;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.Executors;

public class CustomStorageUtil {

    public interface OnSyncProgressListener {
        void onProgress(String status, int percent);
    }

    public static File getCustomDir(Context context) {
        File dragWebDir = FileUtil.getDragWebDir(context);
        File customDir = new File(dragWebDir, "custom");
        if (!customDir.exists()) customDir.mkdirs();
        return customDir;
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
        if (storageFile.exists() && storageFile.length() > 0) {
            return false;
        }
        return SafStorageUtil.isSafFileMissingOrEmpty(context, storageFile.getAbsolutePath());
    }

    public static final int ASSETS_VERSION = 3;

    public static boolean needsSync(Context context) {
        return false;
    }

    public static void syncAssetsToStorage(Context context, OnSyncProgressListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
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

            boolean posixSuccess = false;
            try (InputStream is = context.getAssets().open(filename);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, n);
                }
                fos.flush();
                posixSuccess = true;
            } catch (Exception ignored) {}

            if (!posixSuccess) {
                try (InputStream is = context.getAssets().open(filename);
                     java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, n);
                    }
                    SafStorageUtil.writeSafBytes(context, destFile.getAbsolutePath(), baos.toByteArray());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
