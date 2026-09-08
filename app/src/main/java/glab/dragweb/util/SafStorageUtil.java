package glab.dragweb.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import glab.dragweb.SketchApplication;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class SafStorageUtil {

    private static final String TAG = "SafStorageUtil";
    private static final String PREFS_NAME = "dragweb_storage_prefs";
    private static final String KEY_STORAGE_URI = "storage_tree_uri";
    private static final String KEY_STORAGE_PATH = "storage_folder_path";
    private static final String KEY_IS_CONFIGURED = "storage_configured";

    private static final Map<String, DocumentFile> dirCache = new ConcurrentHashMap<>();
    private static final Map<String, DocumentFile> fileCache = new ConcurrentHashMap<>();

    public static class ValidationResult {
        public final boolean isValid;
        public final String folderName;
        public final String message;

        public ValidationResult(boolean isValid, String folderName, String message) {
            this.isValid = isValid;
            this.folderName = folderName;
            this.message = message;
        }
    }

    public static void clearCache() {
        dirCache.clear();
        fileCache.clear();
    }

    public static ValidationResult validateFolderSelection(Context context, Uri treeUri) {
        if (context == null || treeUri == null) {
            return new ValidationResult(false, null, "No folder selected.");
        }

        try {
            DocumentFile docFile = DocumentFile.fromTreeUri(context, treeUri);
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            String name = (docFile != null && docFile.getName() != null) ? docFile.getName() : null;

            if (name == null && docId != null) {
                if (docId.contains(":")) {
                    String[] parts = docId.split(":", 2);
                    name = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : parts[0];
                } else {
                    name = docId;
                }
            }

            boolean isDragWeb = false;
            if (name != null && name.equalsIgnoreCase(".dragweb")) {
                isDragWeb = true;
            } else if (docId != null && (docId.endsWith("/.dragweb") || docId.equals("primary:.dragweb") || docId.endsWith(":.dragweb"))) {
                isDragWeb = true;
            }

            if (isDragWeb) {
                return new ValidationResult(true, ".dragweb", "Valid .dragweb folder");
            } else {
                return new ValidationResult(false, name != null ? name : "Unknown folder", "Selected folder does not match '.dragweb'.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error validating folder selection: " + e.getMessage(), e);
            return new ValidationResult(false, null, "Error inspecting selected folder: " + e.getMessage());
        }
    }

    public static boolean isStorageConfigured(Context context) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null) return false;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean configured = sp.getBoolean(KEY_IS_CONFIGURED, false);
        if (!configured) return false;
        String uriStr = sp.getString(KEY_STORAGE_URI, null);
        if (uriStr == null || uriStr.trim().isEmpty()) return false;

        try {
            Uri uri = Uri.parse(uriStr);
            boolean hasPermission = false;
            for (android.content.UriPermission perm : context.getContentResolver().getPersistedUriPermissions()) {
                if (perm.getUri().equals(uri) && perm.isWritePermission() && perm.isReadPermission()) {
                    hasPermission = true;
                    break;
                }
            }
            if (!hasPermission) {
                sp.edit().putBoolean(KEY_IS_CONFIGURED, false).apply();
                return false;
            }

            String path = sp.getString(KEY_STORAGE_PATH, null);
            if (path == null) {
                sp.edit().putBoolean(KEY_IS_CONFIGURED, false).apply();
                return false;
            }
            File f = new File(path);
            if (!f.getName().equalsIgnoreCase(".dragweb")) {
                sp.edit().putBoolean(KEY_IS_CONFIGURED, false).apply();
                return false;
            }

            // Verify .dragweb directory physically exists on storage or via SAF
            if (!f.exists()) {
                DocumentFile df = DocumentFile.fromTreeUri(context, uri);
                if (df == null || !df.exists() || !df.isDirectory()) {
                    sp.edit().putBoolean(KEY_IS_CONFIGURED, false).apply();
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error verifying storage configuration: " + e.getMessage());
            return false;
        }
    }

    public static String getSavedStoragePath(Context context) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null) return null;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_STORAGE_PATH, null);
    }

    public static Uri getSavedStorageUri(Context context) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null) return null;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uriStr = sp.getString(KEY_STORAGE_URI, null);
        if (uriStr != null && !uriStr.trim().isEmpty()) {
            try {
                return Uri.parse(uriStr);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse saved storage URI: " + uriStr, e);
            }
        }
        return null;
    }

    public static Intent createFolderPickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        return intent;
    }

    public static boolean persistFolderSelection(Context context, Uri treeUri) {
        if (context == null || treeUri == null) return false;

        try {
            // Take persistable permission so URI access survives app restarts & reboots
            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            context.getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
        } catch (Exception e) {
            Log.w(TAG, "Failed to take persistable URI permission: " + e.getMessage());
        }

        String resolvedPath = getPathFromTreeUri(context, treeUri);
        File targetDragWebDir;

        if (resolvedPath != null && !resolvedPath.isEmpty()) {
            File pickedFolder = new File(resolvedPath);
            String pickedName = pickedFolder.getName();
            if (pickedName != null && pickedName.equalsIgnoreCase(".dragweb")) {
                targetDragWebDir = pickedFolder;
            } else {
                targetDragWebDir = new File(pickedFolder, ".dragweb");
            }
        } else {
            // Fallback to /storage/emulated/0/.dragweb
            targetDragWebDir = new File(Environment.getExternalStorageDirectory(), ".dragweb");
        }

        clearCache();

        // Save preferences immediately
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_STORAGE_URI, treeUri.toString())
                .putString(KEY_STORAGE_PATH, targetDragWebDir.getAbsolutePath())
                .putBoolean(KEY_IS_CONFIGURED, true)
                .apply();

        // Run SAF and directory structure initialization on background thread to prevent UI lag/freezing
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ensureFolderStructureWithSaf(context, treeUri, targetDragWebDir);
                initFolderStructure(targetDragWebDir);
            } catch (Exception e) {
                Log.w(TAG, "Background directory initialization notice: " + e.getMessage());
            }
        });

        Log.i(TAG, "Configured .dragweb storage path: " + targetDragWebDir.getAbsolutePath());
        return true;
    }

    public static String getRelativeSubPath(Context context, String path) {
        if (path == null || path.isEmpty()) return "";
        String normalized = path.replace('\\', '/');
        String storagePath = getSavedStoragePath(context);
        if (storagePath != null) {
            String normStorage = storagePath.replace('\\', '/');
            if (normalized.startsWith(normStorage)) {
                String sub = normalized.substring(normStorage.length());
                while (sub.startsWith("/")) sub = sub.substring(1);
                while (sub.endsWith("/")) sub = sub.substring(0, sub.length() - 1);
                return sub;
            }
        }
        int idx = normalized.lastIndexOf("/.dragweb");
        if (idx >= 0) {
            String sub = normalized.substring(idx + "/.dragweb".length());
            while (sub.startsWith("/")) sub = sub.substring(1);
            while (sub.endsWith("/")) sub = sub.substring(0, sub.length() - 1);
            return sub;
        }
        if (normalized.startsWith(".dragweb")) {
            String sub = normalized.substring(".dragweb".length());
            while (sub.startsWith("/")) sub = sub.substring(1);
            while (sub.endsWith("/")) sub = sub.substring(0, sub.length() - 1);
            return sub;
        }
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    public static DocumentFile getBaseDocumentFile(Context context) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null) return null;

        DocumentFile cached = dirCache.get("");
        if (cached != null && cached.exists()) {
            return cached;
        }

        Uri treeUri = getSavedStorageUri(context);
        if (treeUri == null) return null;

        try {
            DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
            if (root == null || !root.exists()) return null;

            DocumentFile base;
            String name = root.getName();
            if (name != null && name.equalsIgnoreCase(".dragweb")) {
                base = root;
            } else {
                DocumentFile sub = root.findFile(".dragweb");
                if (sub == null) {
                    sub = root.createDirectory(".dragweb");
                }
                base = sub != null ? sub : root;
            }

            if (base != null && base.exists()) {
                dirCache.put("", base);
            }
            return base;
        } catch (Exception e) {
            Log.e(TAG, "Error getting base DocumentFile: " + e.getMessage(), e);
            return null;
        }
    }

    public static DocumentFile getDirectoryDocumentFile(Context context, String relativeDir, boolean createIfMissing) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null) return null;

        if (relativeDir == null || relativeDir.isEmpty() || relativeDir.equals(".")) {
            return getBaseDocumentFile(context);
        }

        String normDir = relativeDir.replace('\\', '/');
        while (normDir.startsWith("/")) normDir = normDir.substring(1);
        while (normDir.endsWith("/")) normDir = normDir.substring(0, normDir.length() - 1);
        if (normDir.isEmpty()) {
            return getBaseDocumentFile(context);
        }

        DocumentFile cached = dirCache.get(normDir);
        if (cached != null && cached.exists() && cached.isDirectory()) {
            return cached;
        }

        DocumentFile base = getBaseDocumentFile(context);
        if (base == null) return null;

        String[] parts = normDir.split("/");
        DocumentFile current = base;
        StringBuilder accum = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (accum.length() > 0) accum.append("/");
            accum.append(part);
            String pathSoFar = accum.toString();

            DocumentFile stepCached = dirCache.get(pathSoFar);
            if (stepCached != null && stepCached.exists() && stepCached.isDirectory()) {
                current = stepCached;
            } else {
                DocumentFile next = current.findFile(part);
                if (next == null && createIfMissing) {
                    next = current.createDirectory(part);
                }
                if (next != null && next.exists() && next.isDirectory()) {
                    dirCache.put(pathSoFar, next);
                    current = next;
                } else {
                    return null;
                }
            }
        }

        return current;
    }

    public static DocumentFile getFileDocumentFile(Context context, String fullPath, boolean createIfMissing) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null || fullPath == null) return null;

        String subPath = getRelativeSubPath(context, fullPath);
        if (subPath.isEmpty()) return null;

        // Check file cache first for instant O(1) memory lookup
        DocumentFile cached = fileCache.get(subPath);
        if (cached != null && cached.exists()) {
            return cached;
        }

        int lastSlash = subPath.lastIndexOf('/');
        String dirSub = lastSlash > 0 ? subPath.substring(0, lastSlash) : "";
        String fileName = lastSlash >= 0 ? subPath.substring(lastSlash + 1) : subPath;

        DocumentFile parentDir = getDirectoryDocumentFile(context, dirSub, createIfMissing);
        if (parentDir == null || !parentDir.exists()) return null;

        DocumentFile fileDoc = parentDir.findFile(fileName);
        if (fileDoc == null) {
            // Check if it was previously created with .txt extension
            fileDoc = parentDir.findFile(fileName + ".txt");
        }

        if (fileDoc != null && fileDoc.exists()) {
            fileCache.put(subPath, fileDoc);
            return fileDoc;
        }

        if (createIfMissing) {
            String mime = getMimeType(fileName);
            DocumentFile created = parentDir.createFile(mime, fileName);
            if (created != null && created.exists()) {
                fileCache.put(subPath, created);
                return created;
            }
        }

        return null;
    }

    public static String getMimeType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".txt")) return "text/plain";
        // For non-standard extensions like .meta, .theme, .logic, use octet-stream to prevent Android appending .txt
        return "application/octet-stream";
    }

    public static boolean writeSafFile(Context context, String path, String content) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null || path == null) return false;
        try {
            DocumentFile fileDoc = getFileDocumentFile(context, path, true);
            if (fileDoc == null) return false;

            try (OutputStream os = context.getContentResolver().openOutputStream(fileDoc.getUri(), "wt")) {
                if (os != null) {
                    byte[] bytes = (content != null ? content : "").getBytes(StandardCharsets.UTF_8);
                    os.write(bytes);
                    os.flush();
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error writing SAF file " + path + ": " + e.getMessage(), e);
        }
        return false;
    }

    public static boolean writeSafFile(String path, String content) {
        return writeSafFile(SketchApplication.getContext(), path, content);
    }

    public static String readSafFile(Context context, String path) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null || path == null) return "";
        try {
            DocumentFile fileDoc = getFileDocumentFile(context, path, false);
            if (fileDoc == null || !fileDoc.exists()) return "";

            try (InputStream is = context.getContentResolver().openInputStream(fileDoc.getUri());
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                if (is != null) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    return new String(baos.toByteArray(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading SAF file " + path + ": " + e.getMessage(), e);
        }
        return "";
    }

    public static String readSafFile(String path) {
        return readSafFile(SketchApplication.getContext(), path);
    }

    public static boolean writeSafBytes(Context context, String path, byte[] bytes) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null || path == null) return false;
        try {
            DocumentFile fileDoc = getFileDocumentFile(context, path, true);
            if (fileDoc == null) return false;

            try (OutputStream os = context.getContentResolver().openOutputStream(fileDoc.getUri(), "wt")) {
                if (os != null) {
                    if (bytes != null && bytes.length > 0) {
                        os.write(bytes);
                    }
                    os.flush();
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error writing SAF bytes to " + path + ": " + e.getMessage(), e);
        }
        return false;
    }

    public static boolean writeSafBytes(String path, byte[] bytes) {
        return writeSafBytes(SketchApplication.getContext(), path, bytes);
    }

    public static byte[] readSafBytes(Context context, String path) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null || path == null) return null;
        try {
            DocumentFile fileDoc = getFileDocumentFile(context, path, false);
            if (fileDoc == null || !fileDoc.exists()) return null;

            try (InputStream is = context.getContentResolver().openInputStream(fileDoc.getUri());
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                if (is != null) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    return baos.toByteArray();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading SAF bytes from " + path + ": " + e.getMessage(), e);
        }
        return null;
    }

    public static byte[] readSafBytes(String path) {
        return readSafBytes(SketchApplication.getContext(), path);
    }

    public static boolean createSafFile(Context context, String path) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null || path == null) return false;
        DocumentFile doc = getFileDocumentFile(context, path, true);
        return doc != null && doc.exists();
    }

    public static boolean createSafFile(String path) {
        return createSafFile(SketchApplication.getContext(), path);
    }

    public static boolean makeSafDir(Context context, String path) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null || path == null) return false;
        String subPath = getRelativeSubPath(context, path);
        DocumentFile doc = getDirectoryDocumentFile(context, subPath, true);
        return doc != null && doc.exists();
    }

    public static boolean makeSafDir(String path) {
        return makeSafDir(SketchApplication.getContext(), path);
    }

    public static boolean deleteSafFile(Context context, String path) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null || path == null) return false;
        try {
            String subPath = getRelativeSubPath(context, path);
            if (!subPath.isEmpty()) {
                dirCache.remove(subPath);
                dirCache.keySet().removeIf(k -> k.startsWith(subPath + "/"));
                fileCache.remove(subPath);
                fileCache.keySet().removeIf(k -> k.startsWith(subPath + "/"));
            }

            int lastSlash = subPath.lastIndexOf('/');
            String dirSub = lastSlash > 0 ? subPath.substring(0, lastSlash) : "";
            String fileName = lastSlash >= 0 ? subPath.substring(lastSlash + 1) : subPath;

            DocumentFile parent = getDirectoryDocumentFile(context, dirSub, false);
            if (parent != null && parent.exists()) {
                DocumentFile target = parent.findFile(fileName);
                if (target == null) {
                    target = parent.findFile(fileName + ".txt");
                }
                if (target != null && target.exists()) {
                    return target.delete();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting SAF file " + path + ": " + e.getMessage(), e);
        }
        return false;
    }

    public static boolean deleteSafFile(String path) {
        return deleteSafFile(SketchApplication.getContext(), path);
    }

    public static File[] listSafFiles(Context context, File dir) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null || dir == null) return new File[0];
        try {
            String subPath = getRelativeSubPath(context, dir.getAbsolutePath());
            DocumentFile dirDoc = getDirectoryDocumentFile(context, subPath, false);
            if (dirDoc != null && dirDoc.exists() && dirDoc.isDirectory()) {
                DocumentFile[] children = dirDoc.listFiles();
                if (children != null) {
                    File[] result = new File[children.length];
                    for (int i = 0; i < children.length; i++) {
                        String name = children[i].getName();
                        // Cache child in fileCache if it's a file
                        if (children[i].isFile()) {
                            String childSubPath = subPath.isEmpty() ? name : subPath + "/" + name;
                            fileCache.put(childSubPath, children[i]);
                        }
                        result[i] = new File(dir, name != null ? name : "");
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error listing SAF files in " + dir.getAbsolutePath() + ": " + e.getMessage(), e);
        }
        return new File[0];
    }

    public static File[] listSafFiles(File dir) {
        return listSafFiles(SketchApplication.getContext(), dir);
    }

    public static boolean isSafFileMissingOrEmpty(Context context, String path) {
        if (context == null) context = SketchApplication.getContext();
        if (context == null || path == null) return true;
        try {
            DocumentFile doc = getFileDocumentFile(context, path, false);
            return doc == null || !doc.exists() || doc.length() == 0;
        } catch (Exception e) {
            return true;
        }
    }

    public static void ensureProjectFolderWithSaf(Context context, String projectId) {
        if (context == null || projectId == null || projectId.isEmpty()) return;
        Uri treeUri = getSavedStorageUri(context);
        if (treeUri == null) return;
        try {
            DocumentFile rootDoc = DocumentFile.fromTreeUri(context, treeUri);
            if (rootDoc != null && rootDoc.isDirectory()) {
                DocumentFile dragWebDoc = rootDoc;
                String rootName = rootDoc.getName();
                if (rootName == null || !rootName.equalsIgnoreCase(".dragweb")) {
                    DocumentFile existing = rootDoc.findFile(".dragweb");
                    if (existing != null && existing.isDirectory()) {
                        dragWebDoc = existing;
                    }
                }
                if (dragWebDoc != null) {
                    DocumentFile projectsDoc = dragWebDoc.findFile("projects");
                    if (projectsDoc == null || !projectsDoc.isDirectory()) {
                        projectsDoc = dragWebDoc.createDirectory("projects");
                    }
                    if (projectsDoc != null) {
                        DocumentFile projDoc = projectsDoc.findFile(projectId);
                        if (projDoc == null || !projDoc.isDirectory()) {
                            projDoc = projectsDoc.createDirectory(projectId);
                        }
                        if (projDoc != null) {
                            createSubDirIfMissing(projDoc, "pages");
                            createSubDirIfMissing(projDoc, "assets");
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Notice ensuring project folder with SAF: " + e.getMessage());
        }
    }

    private static void ensureFolderStructureWithSaf(Context context, Uri treeUri, File targetDragWebDir) {
        try {
            DocumentFile rootDoc = DocumentFile.fromTreeUri(context, treeUri);
            if (rootDoc != null && rootDoc.isDirectory()) {
                DocumentFile dragWebDoc = rootDoc;
                String rootName = rootDoc.getName();
                if (rootName == null || !rootName.equalsIgnoreCase(".dragweb")) {
                    DocumentFile existing = rootDoc.findFile(".dragweb");
                    if (existing == null || !existing.isDirectory()) {
                        dragWebDoc = rootDoc.createDirectory(".dragweb");
                    } else {
                        dragWebDoc = existing;
                    }
                }
                if (dragWebDoc != null) {
                    createSubDirIfMissing(dragWebDoc, "projects");
                    createSubDirIfMissing(dragWebDoc, "custom");
                    createSubDirIfMissing(dragWebDoc, "templates");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "SAF directory creation notice: " + e.getMessage());
        }
    }

    private static void createSubDirIfMissing(DocumentFile parent, String dirName) {
        try {
            DocumentFile existing = parent.findFile(dirName);
            if (existing == null || !existing.isDirectory()) {
                parent.createDirectory(dirName);
            }
        } catch (Exception ignored) {}
    }

    public static void initFolderStructure(File dragWebDir) {
        if (dragWebDir == null) return;
        try {
            if (!dragWebDir.exists()) {
                dragWebDir.mkdirs();
            }
            File projectsDir = new File(dragWebDir, "projects");
            if (!projectsDir.exists()) {
                projectsDir.mkdirs();
            }
            File customDir = new File(dragWebDir, "custom");
            if (!customDir.exists()) {
                customDir.mkdirs();
            }
            File templatesDir = new File(dragWebDir, "templates");
            if (!templatesDir.exists()) {
                templatesDir.mkdirs();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing .dragweb directory structure", e);
        }
    }

    public static String getPathFromTreeUri(Context context, Uri treeUri) {
        if (treeUri == null) return null;

        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            if (docId != null) {
                if (docId.startsWith("primary:")) {
                    String subPath = docId.substring("primary:".length());
                    File extRoot = Environment.getExternalStorageDirectory();
                    if (subPath.isEmpty()) {
                        return extRoot.getAbsolutePath();
                    }
                    return new File(extRoot, subPath).getAbsolutePath();
                } else if (docId.contains(":")) {
                    String[] parts = docId.split(":", 2);
                    String storageId = parts[0];
                    String subPath = parts.length > 1 ? parts[1] : "";
                    File storageDir = new File("/storage/" + storageId);
                    if (storageDir.exists()) {
                        return subPath.isEmpty() ? storageDir.getAbsolutePath() : new File(storageDir, subPath).getAbsolutePath();
                    }
                } else if (docId.startsWith("raw:")) {
                    return docId.substring("raw:".length());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error decoding tree URI: " + treeUri, e);
        }

        // Fallback: check DocumentFile name or standard external paths
        try {
            DocumentFile docFile = DocumentFile.fromTreeUri(context, treeUri);
            if (docFile != null && docFile.getName() != null) {
                File candidate = new File(Environment.getExternalStorageDirectory(), docFile.getName());
                if (candidate.exists()) {
                    return candidate.getAbsolutePath();
                }
            }
        } catch (Exception ignored) {}

        return null;
    }
}
