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

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewMode == VIEW_MODE_GRID) {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(8), dp(8), dp(8), dp(8));
            return new ViewHolder(root);
        }
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(dp(16), dp(12), dp(16), dp(12));
        layout.setLayoutParams(new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.setBackgroundResource(android.R.drawable.list_selector_background);
        return new ViewHolder(layout);
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
        LinearLayout layout = (LinearLayout) holder.itemView;
        layout.removeAllViews();

        File file = files.get(position);

        FrameLayout iconContainer = new FrameLayout(context);
        int iconSize = dp(40);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMargins(0, 0, dp(16), 0);
        iconContainer.setLayoutParams(iconParams);

        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(dp(10));
        iconBg.setColor(Color.parseColor("#1A73E8"));
        iconContainer.setBackground(iconBg);

        TextView iconText = new TextView(context);
        iconText.setTextSize(18);
        iconText.setTextColor(Color.WHITE);
        iconText.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams iconTextLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        iconTextLp.gravity = Gravity.CENTER;
        iconContainer.addView(iconText, iconTextLp);

        LinearLayout infoCol = new LinearLayout(context);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoCol.setLayoutParams(infoParams);

        TextView nameView = new TextView(context);
        nameView.setTextSize(15);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        nameView.setTextColor(Color.parseColor("#E8EAED"));

        TextView detailView = new TextView(context);
        detailView.setTextSize(12);
        detailView.setTextColor(Color.parseColor("#9AA0A6"));

        if (file == null) {
            iconBg.setColor(Color.parseColor("#5F6368"));
            iconText.setText("↑");
            nameView.setText(".. (Go up)");
            nameView.setTypeface(null, Typeface.ITALIC);
            infoCol.addView(nameView);
            layout.setOnClickListener(v -> goUp());
        } else if (file.isDirectory()) {
            iconBg.setColor(Color.parseColor("#1A73E8"));
            iconText.setText("📁");
            nameView.setText(file.getName());
            nameView.setTypeface(null, Typeface.BOLD);

            File[] children = file.listFiles();
            int count = children != null ? children.length : 0;
            detailView.setText(count + " items");

            infoCol.addView(nameView);
            infoCol.addView(detailView);

            layout.setOnClickListener(v -> {
                if (multiSelectEnabled) toggleSelection(file);
                else {
                    navigateTo(file);
                    if (clickListener != null) clickListener.onFileClick(file);
                }
            });
        } else {
            iconBg.setColor(getFileColor(file.getName()));
            iconText.setText(getFileIcon(file.getName()));
            attachThumbnailIfImage(iconContainer, iconText, file);
            nameView.setText(file.getName());

            String size = formatFileSize(file.length());
            String date = dateFormat.format(new Date(file.lastModified()));
            detailView.setText(size + " • " + date);

            infoCol.addView(nameView);
            infoCol.addView(detailView);

            layout.setOnClickListener(v -> {
                if (multiSelectEnabled) toggleSelection(file);
                else if (clickListener != null) clickListener.onFileClick(file);
            });
        }

        if (file != null) {
            layout.setOnLongClickListener(v -> {
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
        }

        // Selection highlight overlay
        if (file != null && selectedPaths.contains(file.getAbsolutePath())) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x331A73E8);
            bg.setCornerRadius(dp(10));
            layout.setBackground(bg);
        } else {
            layout.setBackgroundResource(android.R.drawable.list_selector_background);
        }

        layout.addView(iconContainer);
        layout.addView(infoCol);
    }

    private void bindGrid(ViewHolder holder, int position) {
        LinearLayout root = (LinearLayout) holder.itemView;
        root.removeAllViews();

        File file = files.get(position);

        // Card
        FrameLayout card = new FrameLayout(context);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(14));
        cardBg.setColor(Color.parseColor("#1F2329"));
        cardBg.setStroke(dp(1), 0x22FFFFFF);
        card.setBackground(cardBg);

        ImageView thumb = new ImageView(context);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(96));
        card.addView(thumb, thumbLp);

        TextView placeholder = new TextView(context);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setTextSize(28);
        placeholder.setTextColor(Color.WHITE);
        FrameLayout.LayoutParams plLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(96));
        plLp.gravity = Gravity.CENTER;
        card.addView(placeholder, plLp);

        TextView name = new TextView(context);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        name.setTextColor(Color.parseColor("#E8EAED"));
        name.setTextSize(13);
        name.setPadding(dp(8), dp(6), dp(8), dp(8));

        if (file == null) {
            placeholder.setText("↑");
            name.setText(".. up");
            root.setOnClickListener(v -> goUp());
        } else if (file.isDirectory()) {
            placeholder.setText("📁");
            cardBg.setColor(Color.parseColor("#202833"));
            name.setText(file.getName());
            root.setOnClickListener(v -> {
                if (multiSelectEnabled) toggleSelection(file);
                else {
                    navigateTo(file);
                    if (clickListener != null) clickListener.onFileClick(file);
                }
            });
        } else {
            placeholder.setText(getFileIcon(file.getName()));
            attachThumbnailIfImage(thumb, placeholder, file);
            name.setText(file.getName());
            root.setOnClickListener(v -> {
                if (multiSelectEnabled) toggleSelection(file);
                else if (clickListener != null) clickListener.onFileClick(file);
            });
        }

        if (file != null) {
            root.setOnLongClickListener(v -> {
                if (multiSelectEnabled) { toggleSelection(file); return true; }
                if (longClickListener != null) { longClickListener.onFileLongClick(file); return true; }
                return false;
            });
        }

        if (file != null && selectedPaths.contains(file.getAbsolutePath())) {
            cardBg.setStroke(dp(2), Color.parseColor("#1A73E8"));
        }

        root.addView(card, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(name);
    }

    private void toggleSelection(File f) {
        if (f == null) return;
        String key = f.getAbsolutePath();
        if (!selectedPaths.add(key)) selectedPaths.remove(key);
        notifySelectionChanged();
        notifyDataSetChanged();
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
            placeholder.setVisibility(View.GONE);
        } else if (target instanceof FrameLayout) {
            FrameLayout frame = (FrameLayout) target;
            ImageView iv = new ImageView(context);
            iv.setImageBitmap(bm);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.gravity = Gravity.CENTER;
            frame.addView(iv, lp);
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

    private String getFileIcon(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (isImageFile(lower)) return "🖼";
        if (lower.endsWith(".svg")) return "🎨";
        if (lower.endsWith(".json")) return "📄";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "🌐";
        if (lower.endsWith(".css")) return "🎨";
        if (lower.endsWith(".js")) return "⚡";
        if (lower.endsWith(".mp4") || lower.endsWith(".webm")) return "🎬";
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg")) return "🎵";
        if (lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz")) return "📦";
        if (lower.endsWith(".ttf") || lower.endsWith(".otf")) return "🅰";
        if (lower.endsWith(".txt")) return "📄";
        return "📄";
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.US, "%.1f KB", size / 1024.0);
        return String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(View v) { super(v); }
    }
}
