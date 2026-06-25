package sketchweb.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Modern file explorer adapter.
 *
 * <p>Drop-in replacement for the previous list-only adapter — the public API
 * (constructor, navigateTo, goUp, getCurrentDir, getRelativePath, click and
 * long-click listeners) is preserved so existing MainActivity wiring keeps
 * working. New features layered on top:
 *
 * <ul>
 *   <li>List <i>and</i> grid view modes via {@link #setViewMode(int)}.</li>
 *   <li>Image / SVG thumbnails (cached in an in-memory LRU).</li>
 *   <li>Search filter via {@link #setQuery(String)}.</li>
 *   <li>Multi-select selection model via
 *       {@link #setMultiSelectEnabled(boolean)} and {@link #getSelected()}.</li>
 *   <li>Material 3 rounded cards, adaptive accent colors per file type.</li>
 * </ul>
 */
public class FileExplorerAdapter extends RecyclerView.Adapter<FileExplorerAdapter.ViewHolder> {

    public interface OnFileClickListener {
        void onFileClick(File file);
    }

    public interface OnFileLongClickListener {
        void onFileLongClick(File file);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    public static final int VIEW_MODE_LIST = 0;
    public static final int VIEW_MODE_GRID = 1;

    private final Context context;
    private final List<File> allFiles = new ArrayList<>();
    private final List<File> files = new ArrayList<>();
    private File currentDir;
    private final File rootDir;
    private OnFileClickListener clickListener;
    private OnFileLongClickListener longClickListener;
    private OnSelectionChangedListener selectionListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    private int viewMode = VIEW_MODE_LIST;
    private String query = "";
    private boolean multiSelectEnabled = false;
    private final Set<String> selectedPaths = new HashSet<>();

    // Small thumbnail cache so scrolling through large folders stays smooth
    // without having to decode bitmaps every bind.
    private static final LruCache<String, Bitmap> THUMB_CACHE =
        new LruCache<String, Bitmap>(8 * 1024 * 1024) {
            @Override protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };

    private final java.util.Map<String, String> projectFileTypes = new java.util.HashMap<>();

    private void loadProjectFilesJson() {
        projectFileTypes.clear();
        if (rootDir == null || !rootDir.exists()) return;
        try {
            File manifestFile = new File(rootDir.getParentFile(), "project_files.json");
            if (manifestFile.exists()) {
                String json = FileUtil.readFile(manifestFile.getAbsolutePath());
                if (json != null && !json.trim().isEmpty()) {
                    com.google.gson.JsonObject obj = new com.google.gson.Gson().fromJson(json, com.google.gson.JsonObject.class);
                    if (obj != null && obj.has("files")) {
                        com.google.gson.JsonArray filesArr = obj.getAsJsonArray("files");
                        if (filesArr != null) {
                            for (int i = 0; i < filesArr.size(); i++) {
                                com.google.gson.JsonObject item = filesArr.get(i).getAsJsonObject();
                                if (item.has("path") && item.has("type")) {
                                    String path = item.get("path").getAsString();
                                    String type = item.get("type").getAsString();
                                    projectFileTypes.put(path, type);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.w("FileExplorer", "Failed to load project_files.json: " + e.getMessage());
        }
    }

    private String getProjectRelativePath(File file) {
        if (file == null || rootDir == null) return null;
        try {
            String rootParentPath = rootDir.getParentFile().getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.startsWith(rootParentPath)) {
                String relative = filePath.substring(rootParentPath.length());
                if (relative.startsWith("/")) {
                    relative = relative.substring(1);
                }
                return relative;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void setFileType(File file, String type) {
        String rel = getProjectRelativePath(file);
        if (rel != null) {
            projectFileTypes.put(rel, type);
            updateProjectFilesJson();
        }
    }

    public void renameFileType(File oldFile, File newFile) {
        String oldRel = getProjectRelativePath(oldFile);
        String newRel = getProjectRelativePath(newFile);
        if (oldRel != null && newRel != null) {
            String type = projectFileTypes.remove(oldRel);
            if (type != null) {
                projectFileTypes.put(newRel, type);
            } else {
                projectFileTypes.put(newRel, isSystemFileFallback(newFile) ? "project" : "external");
            }
            updateProjectFilesJson();
        }
    }

    public void removeFileType(File file) {
        String rel = getProjectRelativePath(file);
        if (rel != null) {
            projectFileTypes.remove(rel);
            String prefix = rel + "/";
            java.util.List<String> keysToRemove = new java.util.ArrayList<>();
            for (String key : projectFileTypes.keySet()) {
                if (key.startsWith(prefix)) {
                    keysToRemove.add(key);
                }
            }
            for (String key : keysToRemove) {
                projectFileTypes.remove(key);
            }
            updateProjectFilesJson();
        }
    }

    public FileExplorerAdapter(Context context, File rootDir) {
        this.context = context;
        this.rootDir = rootDir;
        this.currentDir = rootDir;
    }

    public void setOnFileClickListener(OnFileClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnFileLongClickListener(OnFileLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener l) {
        this.selectionListener = l;
    }

    public void setViewMode(int mode) {
        if (mode != VIEW_MODE_LIST && mode != VIEW_MODE_GRID) return;
        if (mode == this.viewMode) return;
        this.viewMode = mode;
        notifyDataSetChanged();
    }

    public int getViewMode() {
        return viewMode;
    }

    public void setQuery(String q) {
        this.query = q == null ? "" : q.toLowerCase(Locale.US).trim();
        applyFilter();
    }

    public void setMultiSelectEnabled(boolean enabled) {
        this.multiSelectEnabled = enabled;
        if (!enabled) selectedPaths.clear();
        notifySelectionChanged();
        notifyDataSetChanged();
    }

    public boolean isMultiSelectEnabled() {
        return multiSelectEnabled;
    }

    public List<File> getSelected() {
        List<File> list = new ArrayList<>();
        for (File f : files) {
            if (f != null && selectedPaths.contains(f.getAbsolutePath())) list.add(f);
        }
        return list;
    }

    public int getSelectionCount() {
        return selectedPaths.size();
    }

    public void clearSelection() {
        selectedPaths.clear();
        notifySelectionChanged();
        notifyDataSetChanged();
    }

    public void navigateTo(File dir) {
        if (dir == null || !dir.exists()) {
            if (!rootDir.exists()) rootDir.mkdirs();
            dir = rootDir;
        }
        this.currentDir = dir;
        selectedPaths.clear();
        notifySelectionChanged();
        loadFiles();
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public boolean canGoUp() {
        return currentDir != null && !currentDir.equals(rootDir) && currentDir.getParentFile() != null;
    }

    public void goUp() {
        if (canGoUp()) navigateTo(currentDir.getParentFile());
    }

    public String getRelativePath() {
        if (currentDir == null || rootDir == null) return "/assets/";
        String rootPath = rootDir.getAbsolutePath();
        String currentPath = currentDir.getAbsolutePath();
        if (currentPath.startsWith(rootPath)) {
            String relative = currentPath.substring(rootPath.length());
            return "/assets" + (relative.isEmpty() ? "/" : relative + "/");
        }
        return "/assets/";
    }

    /** Breadcrumb chips showing the current path segments. */
    public List<String> getBreadcrumbs() {
        List<String> segments = new ArrayList<>();
        segments.add("assets");
        if (currentDir == null || rootDir == null) return segments;
        String rootPath = rootDir.getAbsolutePath();
        String currentPath = currentDir.getAbsolutePath();
        if (currentPath.startsWith(rootPath)) {
            String relative = currentPath.substring(rootPath.length());
            for (String part : relative.split("/")) {
                if (!part.isEmpty()) segments.add(part);
            }
        }
        return Collections.unmodifiableList(segments);
    }

    private void loadFiles() {
        loadProjectFilesJson();
        allFiles.clear();
        if (canGoUp()) {
            allFiles.add(null); // ".."
        }

        if (currentDir != null && currentDir.exists() && currentDir.isDirectory()) {
            File[] dirFiles = currentDir.listFiles();
            if (dirFiles != null) {
                Arrays.sort(dirFiles, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                allFiles.addAll(Arrays.asList(dirFiles));
            }
        }

        applyFilter();
        updateProjectFilesJson();
    }

    private void applyFilter() {
        files.clear();
        if (query.isEmpty()) {
            files.addAll(allFiles);
        } else {
            for (File f : allFiles) {
                if (f == null) {
                    files.add(null); // keep the "go up" entry visible
                    continue;
                }
                if (f.getName().toLowerCase(Locale.US).contains(query)) {
                    files.add(f);
                }
            }
        }
        notifyDataSetChanged();
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) selectionListener.onSelectionChanged(selectedPaths.size());
    }

    @Override
    public int getItemViewType(int position) {
        return viewMode;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_MODE_GRID) {
            View v = android.view.LayoutInflater.from(context).inflate(R.layout.item_asset_grid, parent, false);
            return new ViewHolder(v, VIEW_MODE_GRID);
        } else {
            View v = android.view.LayoutInflater.from(context).inflate(R.layout.item_asset_list, parent, false);
            return new ViewHolder(v, VIEW_MODE_LIST);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (viewMode == VIEW_MODE_GRID) {
            bindGrid(holder, position);
        } else {
            bindList(holder, position);
        }
    }

    private void bindList(ViewHolder holder, int position) {
        File file = files.get(position);

        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(dp(10));
        iconBg.setColor(Color.parseColor("#1A73E8"));
        holder.iconContainer.setBackground(iconBg);

        ImageView listThumb = holder.iconContainer.findViewById(R.id.thumb_list);
        if (listThumb != null) {
            listThumb.setVisibility(View.GONE);
        }
        holder.iconView.setVisibility(View.VISIBLE);

        if (file == null) {
            iconBg.setColor(Color.parseColor("#5F6368"));
            holder.iconView.setImageResource(R.drawable.icon_arrow_back_round);
            holder.nameView.setText(".. (Go up)");
            holder.nameView.setTypeface(null, Typeface.ITALIC);
            holder.detailView.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> goUp());
        } else if (file.isDirectory()) {
            iconBg.setColor(Color.parseColor("#1A73E8"));
            holder.iconView.setImageResource(R.drawable.icon_folder_round);
            holder.nameView.setText(file.getName());
            holder.nameView.setTypeface(null, Typeface.BOLD);
            holder.detailView.setVisibility(View.VISIBLE);

            File[] children = file.listFiles();
            int count = children != null ? children.length : 0;
            holder.detailView.setText(count + " items");

            holder.itemView.setOnClickListener(v -> {
                if (multiSelectEnabled) toggleSelection(file);
                else {
                    navigateTo(file);
                    if (clickListener != null) clickListener.onFileClick(file);
                }
            });
        } else {
            iconBg.setColor(getFileColor(file.getName()));
            holder.iconView.setImageResource(getFileIconResource(file.getName()));
            holder.nameView.setTypeface(null, Typeface.NORMAL);
            holder.detailView.setVisibility(View.VISIBLE);
            attachThumbnailIfImage(holder.iconContainer, holder.iconView, file);
            holder.nameView.setText(file.getName());

            String size = formatFileSize(file.length());
            String date = dateFormat.format(new Date(file.lastModified()));
            holder.detailView.setText(size + " • " + date);

            holder.itemView.setOnClickListener(v -> {
                if (multiSelectEnabled) toggleSelection(file);
                else if (clickListener != null) clickListener.onFileClick(file);
            });
        }

        if (file != null) {
            holder.itemView.setOnLongClickListener(v -> {
                if (multiSelectEnabled) {
                    toggleSelection(file);
                    return true;
                }
                if (longClickListener != null) {
                    longClickListener.onFileLongClick(file);
                    return true;
                }
                return false;
            });
        } else {
            holder.itemView.setOnLongClickListener(null);
        }

        // Selection highlight using CardView stroke and container color
        if (holder.card != null) {
            if (file != null && selectedPaths.contains(file.getAbsolutePath())) {
                int colorSelectedBg = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondaryContainer, 0x331A73E8);
                int colorPrimary = com.google.android.material.color.MaterialColors.getColor(context, android.R.attr.colorPrimary, 0xFF1A73E8);
                holder.card.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(colorSelectedBg));
                holder.card.setStrokeColor(android.content.res.ColorStateList.valueOf(colorPrimary));
                holder.card.setStrokeWidth(dp(2));
            } else {
                int colorBg = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerLow, 0xFF1F2329);
                holder.card.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(colorBg));
                holder.card.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
                holder.card.setStrokeWidth(dp(0));
            }
        }

        // Show lock icon for project-generated files
        if (holder.lockIcon != null) {
            if (isSystemFile(file)) {
                holder.lockIcon.setVisibility(View.VISIBLE);
                holder.lockIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(context, android.R.attr.textColorSecondary, 0x80FFFFFF)
                ));
            } else {
                holder.lockIcon.setVisibility(View.GONE);
            }
        }
    }

    private void bindGrid(ViewHolder holder, int position) {
        File file = files.get(position);

        // Reset views
        holder.thumb.setVisibility(View.GONE);
        holder.placeholder.setVisibility(View.VISIBLE);

        int iconColor = Color.WHITE;
        if (file == null) {
            iconColor = Color.parseColor("#5F6368");
            holder.placeholder.setImageResource(R.drawable.icon_arrow_back_round);
            holder.name.setText(".. up");
            holder.itemView.setOnClickListener(v -> goUp());
        } else if (file.isDirectory()) {
            iconColor = Color.parseColor("#1A73E8");
            holder.placeholder.setImageResource(R.drawable.icon_folder_round);
            holder.name.setText(file.getName());
            holder.itemView.setOnClickListener(v -> {
                if (multiSelectEnabled) toggleSelection(file);
                else {
                    navigateTo(file);
                    if (clickListener != null) clickListener.onFileClick(file);
                }
            });
        } else {
            iconColor = getFileColor(file.getName());
            holder.placeholder.setImageResource(getFileIconResource(file.getName()));
            attachThumbnailIfImage(holder.thumb, holder.placeholder, file);
            holder.name.setText(file.getName());
            holder.itemView.setOnClickListener(v -> {
                if (multiSelectEnabled) toggleSelection(file);
                else if (clickListener != null) clickListener.onFileClick(file);
            });
        }

        holder.placeholder.setColorFilter(iconColor);

        if (file != null) {
            holder.itemView.setOnLongClickListener(v -> {
                if (multiSelectEnabled) { toggleSelection(file); return true; }
                if (longClickListener != null) { longClickListener.onFileLongClick(file); return true; }
                return false;
            });
        } else {
            holder.itemView.setOnLongClickListener(null);
        }

        // Selection styling via MaterialCardView
        if (holder.card != null) {
            if (file != null && selectedPaths.contains(file.getAbsolutePath())) {
                int colorSelectedBg = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondaryContainer, 0x331A73E8);
                int colorPrimary = com.google.android.material.color.MaterialColors.getColor(context, android.R.attr.colorPrimary, 0xFF1A73E8);
                holder.card.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(colorSelectedBg));
                holder.card.setStrokeColor(android.content.res.ColorStateList.valueOf(colorPrimary));
                holder.card.setStrokeWidth(dp(2));
            } else {
                int colorBg = com.google.android.material.color.MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerLow, 0xFF1F2329);
                holder.card.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(colorBg));
                holder.card.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.TRANSPARENT));
                holder.card.setStrokeWidth(dp(0));
            }
        }

        // Show lock icon for project-generated files
        if (holder.lockIcon != null) {
            if (isSystemFile(file)) {
                holder.lockIcon.setVisibility(View.VISIBLE);
                holder.lockIcon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(context, android.R.attr.textColorSecondary, 0x80FFFFFF)
                ));
            } else {
                holder.lockIcon.setVisibility(View.GONE);
            }
        }
    }

    private void toggleSelection(File f) {
        if (f == null) return;
        String key = f.getAbsolutePath();
        if (!selectedPaths.add(key)) selectedPaths.remove(key);
        notifySelectionChanged();
        notifyDataSetChanged();
    }

    public boolean isSystemFileFallback(File file) {
        if (file == null) return false;
        try {
            String rootPath = rootDir.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (!filePath.startsWith(rootPath)) {
                return false;
            }
            String relative = filePath.substring(rootPath.length());
            if (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            if (relative.equals("css") || relative.equals("js")) {
                return true;
            }
            if (relative.equals("css/style.css") || relative.equals("js/script.js")) {
                return true;
            }
            if (!relative.contains("/") && (relative.endsWith(".html") || relative.endsWith(".htm"))) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public boolean isSystemFile(File file) {
        if (file == null) return false;
        String rel = getProjectRelativePath(file);
        if (rel != null) {
            if (projectFileTypes.containsKey(rel)) {
                return "project".equals(projectFileTypes.get(rel));
            }
        }
        return isSystemFileFallback(file);
    }

    public void updateProjectFilesJson() {
        if (rootDir == null || !rootDir.exists()) return;
        try {
            java.util.List<java.util.Map<String, String>> fileList = new java.util.ArrayList<>();
            scanDirForManifest(rootDir, rootDir, fileList);

            java.util.Map<String, Object> manifest = new java.util.HashMap<>();
            manifest.put("files", fileList);

            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(manifest);
            File manifestFile = new File(rootDir.getParentFile(), "project_files.json");
            FileUtil.writeFile(manifestFile.getAbsolutePath(), json);
        } catch (Exception e) {
            android.util.Log.w("FileExplorer", "Failed to update project_files.json: " + e.getMessage());
        }
    }

    private void scanDirForManifest(File dir, File root, java.util.List<java.util.Map<String, String>> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            try {
                String rootPath = root.getCanonicalPath();
                String filePath = f.getCanonicalPath();
                String relative = filePath.substring(rootPath.length());
                if (relative.startsWith("/")) {
                    relative = relative.substring(1);
                }
                if (relative.isEmpty()) continue;
                if (relative.equals("project_files.json")) continue;

                String actualPath = "assets/" + relative;

                java.util.Map<String, String> item = new java.util.HashMap<>();
                item.put("path", actualPath);
                
                String type = projectFileTypes.get(actualPath);
                if (type == null) {
                    type = isSystemFileFallback(f) ? "project" : "external";
                }
                item.put("type", type);
                list.add(item);

                if (f.isDirectory()) {
                    scanDirForManifest(f, root, list);
                }
            } catch (Exception ignored) {}
        }
    }

    private void attachThumbnailIfImage(View target, View placeholder, File file) {
        String name = file.getName().toLowerCase(Locale.US);
        if (!isImageFile(name)) return;
        Bitmap cached = THUMB_CACHE.get(file.getAbsolutePath());
        if (cached != null) {
            applyThumb(target, placeholder, cached);
            return;
        }
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            int sample = 1;
            int target128 = dp(80);
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                while ((opts.outWidth / sample) > target128 * 2 && (opts.outHeight / sample) > target128 * 2) {
                    sample *= 2;
                }
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = Math.max(sample, 1);
            Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (bm != null) {
                THUMB_CACHE.put(file.getAbsolutePath(), bm);
                applyThumb(target, placeholder, bm);
            }
        } catch (Throwable ignore) {
            // Falls back to icon glyph automatically.
        }
    }

    private void applyThumb(View target, View placeholder, Bitmap bm) {
        if (target instanceof ImageView) {
            ((ImageView) target).setImageBitmap(bm);
            ((ImageView) target).setVisibility(View.VISIBLE);
            placeholder.setVisibility(View.GONE);
        } else if (target instanceof FrameLayout) {
            FrameLayout frame = (FrameLayout) target;
            ImageView iv = frame.findViewById(R.id.thumb_list);
            if (iv == null) {
                iv = new ImageView(context);
                iv.setId(R.id.thumb_list);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                lp.gravity = Gravity.CENTER;
                frame.addView(iv, lp);
            }
            iv.setImageBitmap(bm);
            iv.setVisibility(View.VISIBLE);
            placeholder.setVisibility(View.GONE);
        }
    }

    private boolean isImageFile(String lower) {
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
            || lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    private int getFileColor(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return Color.parseColor("#E44D26");
        if (lower.endsWith(".css")) return Color.parseColor("#264DE4");
        if (lower.endsWith(".js")) return Color.parseColor("#F7DF1E");
        if (lower.endsWith(".json")) return Color.parseColor("#8BC34A");
        if (lower.endsWith(".svg")) return Color.parseColor("#FFB300");
        if (lower.endsWith(".ttf") || lower.endsWith(".otf")) return Color.parseColor("#FB8C00");
        if (lower.endsWith(".mp4") || lower.endsWith(".webm")) return Color.parseColor("#E53935");
        if (isImageFile(lower)) return Color.parseColor("#9C27B0");
        return Color.parseColor("#78909C");
    }

    private int dp(int px) {
        return (int) (px * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    private int getFileIconResource(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (isImageFile(lower)) return R.drawable.default_image;
        if (lower.endsWith(".svg")) return R.drawable.icon_theme_round;
        if (lower.endsWith(".json")) return R.drawable.code_xml_24px;
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return R.drawable.icon_web_round;
        if (lower.endsWith(".css")) return R.drawable.icon_theme_round;
        if (lower.endsWith(".js")) return R.drawable.icon_code_round;
        if (lower.endsWith(".mp4") || lower.endsWith(".webm")) return R.drawable.icon_widgets_round;
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg")) return R.drawable.icon_widgets_round;
        if (lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz")) return R.drawable.code_xml_24px;
        if (lower.endsWith(".ttf") || lower.endsWith(".otf")) return R.drawable.code_xml_24px;
        if (lower.endsWith(".txt")) return R.drawable.code_xml_24px;
        return R.drawable.code_xml_24px;
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.US, "%.1f KB", size / 1024.0);
        return String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        com.google.android.material.card.MaterialCardView card;
        ImageView lockIcon;

        // List mode views
        FrameLayout iconContainer;
        ImageView iconView;
        TextView nameView;
        TextView detailView;

        // Grid mode views
        ImageView thumb;
        ImageView placeholder;
        TextView name;

        ViewHolder(View v, int viewType) {
            super(v);
            card = (com.google.android.material.card.MaterialCardView) v.findViewById(R.id.card);
            lockIcon = v.findViewById(R.id.lock_icon);
            if (viewType == VIEW_MODE_GRID) {
                thumb = v.findViewById(R.id.thumb);
                placeholder = v.findViewById(R.id.placeholder);
                name = v.findViewById(R.id.name);
            } else {
                iconContainer = v.findViewById(R.id.icon_container);
                iconView = v.findViewById(R.id.icon_view);
                nameView = v.findViewById(R.id.name_view);
                detailView = v.findViewById(R.id.detail_view);
            }
        }
    }
}
