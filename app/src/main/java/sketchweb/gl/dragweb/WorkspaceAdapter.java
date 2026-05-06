package sketchweb.gl.dragweb;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import sketchweb.gl.R;

/**
 * Renders the placed-block list. Each row exposes inline inputs (one per
 * token detected in the block's display string) plus reorder/duplicate/
 * delete controls. Edits are pushed back through {@link Listener} so the
 * activity can save the project and re-run the code generator.
 */
public class WorkspaceAdapter extends RecyclerView.Adapter<WorkspaceAdapter.VH> {

    public interface Listener {
        void onChanged();
        void onMoveUp(int pos);
        void onMoveDown(int pos);
        void onDuplicate(int pos);
        void onDelete(int pos);
        SelectorIndex selectorIndex();
    }

    private final DragWebBlockRegistry registry;
    private final List<BlockInstance> instances = new ArrayList<>();
    private final Listener listener;

    public WorkspaceAdapter(DragWebBlockRegistry registry, Listener listener) {
        this.registry = registry;
        this.listener = listener;
    }

    public void setInstances(List<BlockInstance> list) {
        instances.clear();
        if (list != null) instances.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dragweb_workspace_block, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        BlockInstance inst = instances.get(position);
        DragWebBlock def = registry.get(inst.blockId);
        Context ctx = h.itemView.getContext();

        if (def == null) {
            h.label.setText("⚠ Unknown block: " + inst.blockId);
            h.preview.setText("");
            h.inputs.removeAllViews();
            return;
        }

        h.label.setText(def.display);
        h.preview.setText(DragWebTemplateEngine.render(def.template, inst.inputs));

        h.inputs.removeAllViews();
        List<String> tokens = DragWebTemplateEngine.detectTokens(def.display);
        if (tokens.isEmpty()) tokens = DragWebTemplateEngine.detectTokens(def.template);

        // Make sure the input list is the right size before binding the views.
        while (inst.inputs.size() < tokens.size()) inst.inputs.add("");

        for (int i = 0; i < tokens.size(); i++) {
            final int slot = i;
            final String token = tokens.get(i);

            TextInputLayout til = new TextInputLayout(ctx);
            til.setHint(humanize(token));
            til.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            if (token.startsWith("%m.")) {
                AutoCompleteTextView ac = new AutoCompleteTextView(ctx);
                ac.setThreshold(0);
                List<String> suggestions = listener != null
                        ? new ArrayList<>(listener.selectorIndex().forToken(token))
                        : new ArrayList<>();
                ac.setAdapter(new ArrayAdapter<>(ctx,
                        android.R.layout.simple_dropdown_item_1line, suggestions));
                ac.setText(inst.inputs.get(slot));
                ac.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void afterTextChanged(Editable s) {
                        ensureSize(inst.inputs, slot + 1);
                        inst.inputs.set(slot, s.toString());
                        h.preview.setText(DragWebTemplateEngine.render(def.template, inst.inputs));
                        if (listener != null) listener.onChanged();
                    }
                });
                til.addView(ac);
            } else {
                TextInputEditText et = new TextInputEditText(ctx);
                if ("%d".equals(token)) {
                    et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                }
                et.setText(inst.inputs.get(slot));
                et.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                    @Override public void afterTextChanged(Editable s) {
                        ensureSize(inst.inputs, slot + 1);
                        inst.inputs.set(slot, s.toString());
                        h.preview.setText(DragWebTemplateEngine.render(def.template, inst.inputs));
                        if (listener != null) listener.onChanged();
                    }
                });
                til.addView(et);
            }
            h.inputs.addView(til);
        }

        h.up.setOnClickListener(v -> {
            if (listener != null) listener.onMoveUp(h.getBindingAdapterPosition());
        });
        h.down.setOnClickListener(v -> {
            if (listener != null) listener.onMoveDown(h.getBindingAdapterPosition());
        });
        h.duplicate.setOnClickListener(v -> {
            if (listener != null) listener.onDuplicate(h.getBindingAdapterPosition());
        });
        h.delete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(h.getBindingAdapterPosition());
        });
    }

    private static void ensureSize(List<String> list, int size) {
        while (list.size() < size) list.add("");
    }

    private static String humanize(String token) {
        switch (token) {
            case "%s": return "text";
            case "%d": return "number";
            case "%m.id": return "id";
            case "%m.class": return "class";
            case "%m.tag": return "tag";
            case "%m.file": return "file";
            case "%m.section": return "section id";
            default:
                if (token.matches("%\\d+\\$s")) return "input " + token.replaceAll("[^0-9]", "");
                return token;
        }
    }

    @Override
    public int getItemCount() {
        return instances.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView label;
        final TextView preview;
        final LinearLayout inputs;
        final ImageButton up, down, duplicate, delete;

        VH(View v) {
            super(v);
            label = v.findViewById(R.id.workspace_block_label);
            preview = v.findViewById(R.id.workspace_block_preview);
            inputs = v.findViewById(R.id.workspace_block_inputs);
            up = v.findViewById(R.id.workspace_block_up);
            down = v.findViewById(R.id.workspace_block_down);
            duplicate = v.findViewById(R.id.workspace_block_duplicate);
            delete = v.findViewById(R.id.workspace_block_delete);
        }
    }
}
