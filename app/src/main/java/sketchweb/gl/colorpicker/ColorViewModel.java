package sketchweb.gl.colorpicker;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class ColorViewModel extends AndroidViewModel {

    private static final String PREFS_NAME = "color_picker_prefs";
    private static final String KEY_CUSTOM_COLORS = "custom_colors";

    private final Map<String, List<String>> colorMap = new LinkedHashMap<>();
    private final MutableLiveData<String> selectedCategory = new MutableLiveData<>("custom");
    private final MutableLiveData<String> selectedColor = new MutableLiveData<>(null);

    // Новое: храним выбранный цвет на каждую категорию
    private final Map<String, String> categoryColorMap = new HashMap<>();

    public ColorViewModel(@NonNull Application application) {
        super(application);
        loadCustomColors();
        loadPredefinedColorsFromAssets();

        // Установим стартовую категорию, например "red"
        selectCategory("red");
    }

    private void loadCustomColors() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, 0);
        String json = prefs.getString(KEY_CUSTOM_COLORS, "[]");
        List<String> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                list.add(array.getString(i));
            }
        } catch (JSONException ignored) {}
        colorMap.put("custom", list);
    }

    private void saveCustomColors() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = prefs.edit();
        JSONArray array = new JSONArray(colorMap.get("custom"));
        editor.putString(KEY_CUSTOM_COLORS, array.toString());
        editor.apply();
    }

    private void loadPredefinedColorsFromAssets() {
        AssetManager assetManager = getApplication().getAssets();
        try (InputStream is = assetManager.open("colors.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            JSONObject jsonObject = new JSONObject(jsonBuilder.toString());
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String category = keys.next();
                JSONArray array = jsonObject.getJSONArray(category);
                List<String> colors = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    colors.add(array.getString(i));
                }
                colorMap.put(category, colors);
            }

        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    public LiveData<String> getSelectedCategory() {
        return selectedCategory;
    }

    public LiveData<String> getSelectedColor() {
        return selectedColor;
    }

    public List<String> getColorsForCategory(String category) {
        return colorMap.getOrDefault(category, Collections.emptyList());
    }

    public void selectCategory(String category) {
        selectedCategory.setValue(category);

        List<String> colors = getColorsForCategory(category);
        if (!colors.isEmpty()) {
            String previouslySelectedHex = categoryColorMap.get(category);
            if (previouslySelectedHex != null && colors.contains(previouslySelectedHex)) {
                selectColor(previouslySelectedHex);
            } else {
                selectColor(colors.get(0));
            }
        }
    }

    public void selectColor(String hex) {
        selectedColor.setValue(hex);

        String category = selectedCategory.getValue();
        if (category != null && hex != null) {
            categoryColorMap.put(category, hex);
        }
    }

    public boolean addCustomColor(String hex) {
        if (!ColorUtils.isValidHexColor(hex)) return false;

        String normalizedHex = ColorUtils.normalizeHexColor(hex);
        List<String> list = colorMap.get("custom");

        if (list == null) {
            list = new ArrayList<>();
            colorMap.put("custom", list);
        }

        if (!list.contains(normalizedHex)) {
            list.add(0, normalizedHex);
            saveCustomColors();

            if ("custom".equals(selectedCategory.getValue())) {
                selectedCategory.setValue("custom"); // триггер обновления
            }

            return true;
        }

        return false;
    }

    public boolean removeCustomColor(String hex) {
        List<String> list = colorMap.get("custom");
        if (list != null && list.remove(hex)) {
            saveCustomColors();
            if ("custom".equals(selectedCategory.getValue())) {
                selectedCategory.setValue("custom"); // триггер обновления
            }
            return true;
        }
        return false;
    }

}
