package sketchweb.gl.dragweb;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import sketchweb.gl.R;

public class PaletteAdapter extends RecyclerView.Adapter<PaletteAdapter.VH> {

    public interface OnPaletteBlockClick {
        void onClick(DragWebBlock block);
    }

    private final List<DragWebBlock> blocks = new ArrayList<>();
    private final OnPaletteBlockClick listener;

    public PaletteAdapter(OnPaletteBlockClick listener) {
        this.listener = listener;
    }

    public void setBlocks(List<DragWebBlock> newBlocks) {
        blocks.clear();
        if (newBlocks != null) blocks.addAll(newBlocks);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dragweb_palette_block, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DragWebBlock b = blocks.get(position);
        h.display.setText(b.display);
        h.template.setText(b.template);
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(b);
        });
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView display;
        final TextView template;
        VH(View v) {
            super(v);
            display = v.findViewById(R.id.palette_display);
            template = v.findViewById(R.id.palette_template);
        }
    }
}
