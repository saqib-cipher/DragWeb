package sketchweb.gl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileExplorerAdapter extends RecyclerView.Adapter<FileExplorerAdapter.ViewHolder> {

    public interface OnFileClickListener {
        void onFileClick(File file);
    }

    public interface OnFileLongClickListener {
        void onFileLongClick(File file);
    }

    private Context context;
    private List<File> files = new ArrayList<>();
    private File currentDir;
    private File rootDir;
    private OnFileClickListener clickListener;
    private OnFileLongClickListener longClickListener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

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

    public void navigateTo(File dir) {
        if (dir == null || !dir.exists()) {
            if (!rootDir.exists()) rootDir.mkdirs();
            dir = rootDir;
        }
        this.currentDir = dir;
        loadFiles();
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public boolean canGoUp() {
        return currentDir != null && !currentDir.equals(rootDir) && currentDir.getParentFile() != null;
    }

    public void goUp() {
        if (canGoUp()) {
            navigateTo(currentDir.getParentFile());
        }
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

    private void loadFiles() {
        files.clear();

        // Add "go up" entry if not at root
        if (canGoUp()) {
            files.add(null); // null represents ".." go up entry
        }

        if (currentDir != null && currentDir.exists() && currentDir.isDirectory()) {
            File[] dirFiles = currentDir.listFiles();
            if (dirFiles != null) {
                // Sort: folders first, then files, alphabetically
                Arrays.sort(dirFiles, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                files.addAll(Arrays.asList(dirFiles));
            }
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(16, 12, 16, 12);
        layout.setLayoutParams(new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new ViewHolder(layout);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LinearLayout layout = (LinearLayout) holder.itemView;
        layout.removeAllViews();

        File file = files.get(position);

        // Icon
        TextView icon = new TextView(context);
        icon.setTextSize(20);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            40, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconParams.setMargins(0, 0, 12, 0);
        icon.setLayoutParams(iconParams);
        icon.setGravity(Gravity.CENTER);

        // Info column
        LinearLayout infoCol = new LinearLayout(context);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoCol.setLayoutParams(infoParams);

        TextView nameView = new TextView(context);
        nameView.setTextSize(14);
        nameView.setSingleLine(true);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);

        if (file == null) {
            // Go up entry
            icon.setText("\uD83D\uDCC1");
            nameView.setText(".. (Go up)");
            nameView.setTextColor(Color.parseColor("#90A4AE"));
            nameView.setTypeface(null, Typeface.ITALIC);
            infoCol.addView(nameView);

            layout.setOnClickListener(v -> goUp());
        } else if (file.isDirectory()) {
            icon.setText("\uD83D\uDCC1");
            nameView.setText(file.getName());
            nameView.setTextColor(Color.parseColor("#42A5F5"));
            nameView.setTypeface(null, Typeface.BOLD);
            infoCol.addView(nameView);

            // File count
            File[] children = file.listFiles();
            int count = children != null ? children.length : 0;
            TextView countView = new TextView(context);
            countView.setText(count + " items");
            countView.setTextSize(11);
            countView.setTextColor(Color.parseColor("#78909C"));
            infoCol.addView(countView);

            layout.setOnClickListener(v -> {
                navigateTo(file);
                if (clickListener != null) clickListener.onFileClick(file);
            });
        } else {
            // File
            icon.setText(getFileIcon(file.getName()));
            nameView.setText(file.getName());
            nameView.setTextColor(Color.parseColor("#CFD8DC"));
            infoCol.addView(nameView);

            // File size and date
            TextView detailView = new TextView(context);
            String size = formatFileSize(file.length());
            String date = dateFormat.format(new Date(file.lastModified()));
            detailView.setText(size + " \u2022 " + date);
            detailView.setTextSize(11);
            detailView.setTextColor(Color.parseColor("#78909C"));
            infoCol.addView(detailView);

            layout.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onFileClick(file);
            });
        }

        if (file != null) {
            layout.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onFileLongClick(file);
                    return true;
                }
                return false;
            });
        }

        layout.addView(icon);
        layout.addView(infoCol);
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    private String getFileIcon(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp")) {
            return "\uD83D\uDDBC"; // framed picture
        } else if (lower.endsWith(".svg")) {
            return "\uD83C\uDFA8"; // art palette
        } else if (lower.endsWith(".json")) {
            return "\uD83D\uDCC4"; // page facing up
        } else if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "\uD83C\uDF10"; // globe
        } else if (lower.endsWith(".css")) {
            return "\uD83C\uDFA8";
        } else if (lower.endsWith(".js")) {
            return "\u26A1"; // lightning
        } else if (lower.endsWith(".mp4") || lower.endsWith(".webm")) {
            return "\uD83C\uDFAC"; // clapper
        } else if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg")) {
            return "\uD83C\uDFB5"; // music note
        } else if (lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz")) {
            return "\uD83D\uDCE6"; // package
        } else if (lower.endsWith(".txt")) {
            return "\uD83D\uDCC4";
        }
        return "\uD83D\uDCC4"; // default: page
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
