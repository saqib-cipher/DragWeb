package sketchweb.gl.colorpicker;

import android.graphics.Color;
import android.view.*;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import sketchweb.gl.R;

import java.util.*;

public class CategoryColorAdapter extends RecyclerView.Adapter<CategoryColorAdapter.ViewHolder> {
	
	private final List<String> categories;
	private final ColorViewModel viewModel;
	private String selectedCategory;
	
	public CategoryColorAdapter(ColorViewModel vm) {
		this.viewModel = vm;
		this.categories = new ArrayList<>(Arrays.asList(
		"custom",
		"theme",
		"red",
		"pink",
		"purple",
		"deep purple",
		"indigo",
		"blue",
		"light blue",
		"cyan",
		"teal",
		"green",
		"light green",
		"lime",
		"yellow",
		"amber",
		"orange",
		"deep orange",
		"brown",
		"grey",
		"blue gray",
		"black",
		"white"
		));
		
		this.viewModel.getSelectedCategory().observeForever(category -> {
			selectedCategory = category;
			notifyDataSetChanged();
		});
	}

	public void setHexOnlyMode(boolean hexOnly) {
		if (hexOnly) {
			categories.remove("theme");
		}
		notifyDataSetChanged();
	}
	
	@NonNull
	@Override
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_color_category, parent, false);
		return new ViewHolder(v);
	}
	
	@Override
	public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
		String category = categories.get(position);
		holder.text.setText(category.toUpperCase());
		
		int bgColor = ColorUtils.getColorFromCategory(category);
		int textColor = ColorUtils.getContrastingTextColor(bgColor);
		
		holder.colorBox.setBackgroundColor(bgColor);
		holder.text.setTextColor(textColor);
		
		holder.imageCheck.setColorFilter(textColor); 
		
		boolean isSelected = category.equalsIgnoreCase(selectedCategory);
		holder.imageCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);
		
		holder.itemView.setOnClickListener(v -> viewModel.selectCategory(category));
	}
	
	@Override
	public int getItemCount() {
		return categories.size();
	}
	
	static class ViewHolder extends RecyclerView.ViewHolder {
		View colorBox;
		TextView text;
		ImageView imageCheck;
		
		ViewHolder(View itemView) {
			super(itemView);
			colorBox = itemView.findViewById(R.id.color_box);
			text = itemView.findViewById(R.id.text_category);
			imageCheck = itemView.findViewById(R.id.image_check);
		}
	}
	
}
