package glab.dragweb.util;

import glab.dragweb.SketchApplication;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class FileUtil {

    public static File getDragWebDir() {
        return getDragWebDir(SketchApplication.getContext());
    }

    public static File getDragWebDir(Context context) {
        if (context == null) {
            context = SketchApplication.getContext();
        }
        String savedPath = SafStorageUtil.getSavedStoragePath(context);
        File dir;
        if (savedPath != null && !savedPath.trim().isEmpty()) {
            dir = new File(savedPath);
        } else {
            dir = new File(Environment.getExternalStorageDirectory(), ".dragweb");
        }
        if (!dir.exists()) {
            dir.mkdirs();
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

    public static void deleteFile(String path) {
        if (path == null || path.isEmpty()) return;
        File file = new File(path);
        if (!file.exists()) {
            // Might exist in SAF even if not visible in File
            SafStorageUtil.deleteSafFile(path);
            return;
        }

        if (file.isFile()) {
            boolean deleted = file.delete();
            if (!deleted) {
                SafStorageUtil.deleteSafFile(path);
            }
            return;
        }

        File[] fileArr = listFiles(file);
        if (fileArr != null) {
            for (File subFile : fileArr) {
                if (subFile.isDirectory()) {
                    deleteFile(subFile.getAbsolutePath());
                } else if (subFile.isFile()) {
                    boolean d = subFile.delete();
                    if (!d) {
                        SafStorageUtil.deleteSafFile(subFile.getAbsolutePath());
                    }
                }
            }
        }
        boolean dirDeleted = file.delete();
        if (!dirDeleted) {
            SafStorageUtil.deleteSafFile(path);
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
        try {
            File[] files = dir.listFiles();
            if (files != null) {
                return files;
            }
        } catch (Exception ignored) {}

        // Fallback to SAF
        return SafStorageUtil.listSafFiles(dir);
    }

    public static File[] listFiles(String path) {
        if (path == null || path.isEmpty()) return new File[0];
        return listFiles(new File(path));
    }
}
