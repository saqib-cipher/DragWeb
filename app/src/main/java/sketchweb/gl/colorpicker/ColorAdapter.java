package sketchweb.gl.colorpicker;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import sketchweb.gl.R;

public class ColorAdapter extends RecyclerView.Adapter<ColorAdapter.ViewHolder> {
	
	private List<String> colorList = new ArrayList<>();
	private String selectedColor;
	private final ColorViewModel viewModel;
	
	public ColorAdapter(ColorViewModel vm) {
		this.viewModel = vm;
		this.viewModel.getSelectedColor().observeForever(hex -> {
			selectedColor = hex;
			notifyDataSetChanged();
		});
	}
	
	public void setColors(List<String> colors) {
		this.colorList = new ArrayList<>(colors);
		notifyDataSetChanged();
	}
	
	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_color, parent, false);
		return new ViewHolder(view);
	}
	
	@Override
public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    String hex = colorList.get(position);
    int bgColor = ColorUtils.parseHexColorSafe(hex);
    
    holder.materialCardView.setCardBackgroundColor(bgColor);
    holder.hexText.setText(hex);
    
    int textColor = ColorUtils.getContrastingTextColor(bgColor);
    holder.hexText.setTextColor(textColor);
    holder.checkMark.setColorFilter(textColor);
    
    boolean isSelected = hex.equalsIgnoreCase(selectedColor);
    holder.checkMark.setVisibility(isSelected ? View.VISIBLE : View.GONE);
    
    holder.itemView.setOnClickListener(v -> {
        try {
            if (ColorUtils.isValidHexColor(hex)) {
                viewModel.selectColor(hex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    });
    
    holder.itemView.setOnLongClickListener(v -> {
        if ("custom".equalsIgnoreCase(viewModel.getSelectedCategory().getValue())) {
            showDeleteDialog(v, hex);
            return true;
        }
        return false;
    });
}
	
	@Override
	public int getItemCount() {
		return colorList.size();
	}
	
	static class ViewHolder extends RecyclerView.ViewHolder {
		TextView hexText;
		ImageView checkMark;
		MaterialCardView materialCardView;
		
		ViewHolder(@NonNull View itemView) {
			super(itemView);
			
			hexText = itemView.findViewById(R.id.text_hex);
			checkMark = itemView.findViewById(R.id.image_check);
			materialCardView = itemView.findViewById(R.id.card_view);
		}
	}
	
	
	private void showDeleteDialog(View view, String hex) {
	if (!"custom".equalsIgnoreCase(viewModel.getSelectedCategory().getValue())) return;

	Context context = view.getContext();
	new MaterialAlertDialogBuilder(context)
		.setTitle(context.getString(R.string.dialog_delete_title))
		.setMessage(context.getString(R.string.dialog_delete_message))
		.setPositiveButton(context.getString(R.string.dialog_button_yes), (dialog, which) -> {
			viewModel.removeCustomColor(hex);
		})
		.setNegativeButton(context.getString(R.string.dialog_button_no), null)
		.show();
}
}
