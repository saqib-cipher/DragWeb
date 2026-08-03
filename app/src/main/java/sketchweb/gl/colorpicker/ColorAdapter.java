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
    
    View innerFrame = holder.materialCardView.getChildAt(0);
    boolean isTransparent = "transparent".equalsIgnoreCase(hex) || (bgColor & 0xFF000000) == 0;
    if (isTransparent) {
        holder.materialCardView.setCardBackgroundColor(Color.WHITE);
        if (innerFrame != null) {
            innerFrame.setBackground(new android.graphics.drawable.Drawable() {
                @Override
                public void draw(@NonNull android.graphics.Canvas canvas) {
                    int w = canvas.getWidth();
                    int h = canvas.getHeight();
                    android.graphics.Paint paint = new android.graphics.Paint();
                    paint.setColor(Color.WHITE);
                    canvas.drawRect(0, 0, w, h, paint);
                    paint.setColor(Color.RED);
                    paint.setStrokeWidth(4.0f);
                    canvas.drawLine(0, h, w, 0, paint);
                }
                @Override
                public void setAlpha(int alpha) {}
                @Override
                public void setColorFilter(@NonNull android.graphics.ColorFilter colorFilter) {}
                @Override
                public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
            });
        }
        holder.hexText.setText("transparent");
        int textColor = Color.BLACK;
        holder.hexText.setTextColor(textColor);
        holder.checkMark.setColorFilter(textColor);
    } else {
        if (innerFrame != null) {
            innerFrame.setBackground(null);
        }
        holder.materialCardView.setCardBackgroundColor(bgColor);
        holder.hexText.setText(hex);
        int textColor = ColorUtils.getContrastingTextColor(bgColor);
        holder.hexText.setTextColor(textColor);
        holder.checkMark.setColorFilter(textColor);
    }
    
    boolean isSelected = false;
    if (selectedColor != null) {
        if (hex.equalsIgnoreCase(selectedColor)) {
            isSelected = true;
        } else if (!"transparent".equalsIgnoreCase(hex) && !"transparent".equalsIgnoreCase(selectedColor)) {
            int c1 = ColorUtils.parseHexColorSafe(hex);
            int c2 = ColorUtils.parseHexColorSafe(selectedColor);
            isSelected = (Color.red(c1) == Color.red(c2)) 
                      && (Color.green(c1) == Color.green(c2)) 
                      && (Color.blue(c1) == Color.blue(c2));
        }
    }
    holder.checkMark.setVisibility(isSelected ? View.VISIBLE : View.GONE);

    if (isSelected && selectedColor != null && !isTransparent && !"transparent".equalsIgnoreCase(selectedColor)) {
        int selColorInt = ColorUtils.parseHexColorSafe(selectedColor);
        holder.materialCardView.setCardBackgroundColor(selColorInt);
        holder.hexText.setText(selectedColor);
        int textColor = ColorUtils.getContrastingTextColor(selColorInt);
        holder.hexText.setTextColor(textColor);
        holder.checkMark.setColorFilter(textColor);
    }
    
    holder.itemView.setOnClickListener(v -> {
        try {
            if ("transparent".equalsIgnoreCase(hex) || ColorUtils.isValidHexColor(hex)) {
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
