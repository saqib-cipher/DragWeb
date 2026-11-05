package glab.dragweb.util;

import glab.dragweb.SketchApplication;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class FileUtil {

    public static boolean hasStoragePermission(Context context) {
        if (context == null) {
            context = SketchApplication.getContext();
        }
        if (context == null) return false;

        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public static boolean hasAnyStorageAccess(Context context) {
        return hasStoragePermission(context) || SafStorageUtil.isStorageConfigured(context);
    }

    public static boolean isAllFilesAccessGranted() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    public static void requestAllFilesAccessPermission(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                if (!(context instanceof Activity)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    if (!(context instanceof Activity)) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    context.startActivity(intent);
                } catch (Exception ex) {
                    Log.e("FileUtil", "Cannot open all files access settings: " + ex.getMessage());
                }
            }
        }
    }

    public static void requestStoragePermission(Activity activity, int requestCode) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT >= 30) {
            requestAllFilesAccessPermission(activity);
        } else {
            ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, requestCode);
        }
    }

    public static File getDragWebDir() {
        return getDragWebDir(SketchApplication.getContext());
    }

    public static File getDragWebDir(Context context) {
        if (context == null) {
            context = SketchApplication.getContext();
        }
        File dir;
        if (hasStoragePermission(context)) {
            dir = new File(Environment.getExternalStorageDirectory(), ".dragweb");
        } else {
            String savedPath = SafStorageUtil.getSavedStoragePath(context);
            if (savedPath != null && !savedPath.trim().isEmpty()) {
                dir = new File(savedPath);
            } else {
                dir = new File(Environment.getExternalStorageDirectory(), ".dragweb");
            }
        }
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created && !dir.exists()) {
                SafStorageUtil.makeSafDir(dir.getAbsolutePath());
            }
        }
        return dir;
    }

    public static File getExternalStorageDirectory() {
        return Environment.getExternalStorageDirectory();
    }

    public static String getExternalStorageDir() {
        return getExternalStorageDirectory().getAbsolutePath();
    }

    public static void createNewFile(String path) {
        if (path == null || path.isEmpty()) return;
        int lastSep = path.lastIndexOf(File.separator);
        if (lastSep > 0) {
            String dirPath = path.substring(0, lastSep);
            makeDir(dirPath);
        }

        File file = new File(path);
        try {
            if (!file.exists()) {
                boolean created = file.createNewFile();
                if (!created && !file.exists()) {
                    SafStorageUtil.createSafFile(path);
                }
            }
        } catch (IOException e) {
            SafStorageUtil.createSafFile(path);
        }
    }

    public static String readFile(String path) {
        if (path == null || path.isEmpty()) return "";
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            StringBuilder sb = new StringBuilder();
            try (FileReader fr = new FileReader(file)) {
                char[] buff = new char[4096];
                int length;
                while ((length = fr.read(buff)) > 0) {
                    sb.append(buff, 0, length);
                }
                return sb.toString();
            } catch (IOException ignored) {}
        }

        // Fallback to SAF
        return SafStorageUtil.readSafFile(path);
    }

    public static void writeFile(String path, String str) {
        if (path == null || path.isEmpty()) return;
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileWriter fileWriter = new FileWriter(file, false)) {
            fileWriter.write(str != null ? str : "");
            fileWriter.flush();
            return;
        } catch (IOException e) {
            // Direct file write failed, fallback to SAF
            SafStorageUtil.writeSafFile(path, str);
        }
    }

    public static boolean deleteFile(String path) {
        if (path == null || path.trim().isEmpty()) return true;
        return deleteFile(new File(path));
    }

    public static boolean deleteFile(File file) {
        if (file == null) return true;
        String path = file.getAbsolutePath();

        // 1. Recursive Java File delete
        deleteJavaFileRecursive(file);

        // 2. Recursive SAF delete (for Scoped Storage, tree URI, or different UID ownership)
        SafStorageUtil.deleteSafRecursive(path);

        // 3. Invalidate caches
        SafStorageUtil.removeCacheForPath(path);

        // 4. Return true if file/dir is completely gone
        return !file.exists() && !SafStorageUtil.safFileExists(file);
    }

    private static boolean deleteJavaFileRecursive(File file) {
        if (file == null || !file.exists()) return true;
        try {
            if (file.isDirectory() || isDirectory(file)) {
                File[] children = listFiles(file);
                if (children != null) {
                    for (File child : children) {
                        if (child != null && !child.getAbsolutePath().equals(file.getAbsolutePath())) {
                            deleteJavaFileRecursive(child);
                        }
                    }
                }
            }
            boolean deleted = file.delete();
            if (!deleted) {
                file.setWritable(true);
                deleted = file.delete();
            }
            return deleted;
        } catch (Exception e) {
            Log.w("FileUtil", "Java delete exception for " + file.getAbsolutePath() + ": " + e.getMessage());
            return false;
        }
    }

    public static void makeDir(String path) {
        if (path == null || path.isEmpty()) return;
        File file = new File(path);
        if (!file.exists()) {
            boolean created = file.mkdirs();
            if (!created && !file.exists()) {
                SafStorageUtil.makeSafDir(path);
            }
        }
    }

    public static File[] listFiles(File dir) {
        if (dir == null) return new File[0];
        java.util.LinkedHashMap<String, File> resultMap = new java.util.LinkedHashMap<>();

        try {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f != null && f.getName() != null && !f.getName().isEmpty()) {
                        resultMap.put(f.getName(), f);
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            File[] safFiles = SafStorageUtil.listSafFiles(dir);
            if (safFiles != null) {
                for (File f : safFiles) {
                    if (f != null && f.getName() != null && !f.getName().isEmpty()) {
                        if (!resultMap.containsKey(f.getName())) {
                            resultMap.put(f.getName(), f);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return resultMap.values().toArray(new File[0]);
    }

    public static File[] listFiles(String path) {
        if (path == null || path.isEmpty()) return new File[0];
        return listFiles(new File(path));
    }

    public static boolean isDirectory(File file) {
        if (file == null) return false;
        try {
            if (file.exists() && file.isDirectory()) {
                return true;
            }
        } catch (Exception ignored) {}
        return SafStorageUtil.isSafDirectory(file);
    }

    public static boolean isFile(File file) {
        if (file == null) return false;
        try {
            if (file.exists() && file.isFile()) {
                return true;
            }
        } catch (Exception ignored) {}
        return SafStorageUtil.isSafFile(file);
    }

    public static boolean exists(File file) {
        if (file == null) return false;
        try {
            if (file.exists()) {
                return true;
            }
        } catch (Exception ignored) {}
        return SafStorageUtil.safFileExists(file);
    }

    public static long getFileLength(File file) {
        if (file == null) return 0L;
        try {
            if (file.exists()) {
                long len = file.length();
                if (len > 0) return len;
            }
        } catch (Exception ignored) {}
        return SafStorageUtil.getSafFileLength(file);
    }

    public static long getLastModified(File file) {
        if (file == null) return 0L;
        try {
            if (file.exists()) {
                long mod = file.lastModified();
                if (mod > 0) return mod;
            }
        } catch (Exception ignored) {}
        return SafStorageUtil.getSafLastModified(file);
    }
}
