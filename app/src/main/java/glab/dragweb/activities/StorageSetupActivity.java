package glab.dragweb.activities;

import glab.dragweb.R;
import glab.dragweb.util.SafStorageUtil;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class StorageSetupActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabIndicator;
    private MaterialButton btnSelectFolder;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvCurrentPath;

    private ActivityResultLauncher<Intent> folderPickerLauncher;
    private Timer autoAdvanceTimer;
    private boolean userHasInteracted = false;
    private boolean hasFinished = false;

    private final String[] stepNumbers = {"STEP 1 OF 4", "STEP 2 OF 4", "STEP 3 OF 4", "STEP 4 OF 4"};
    private final String[] stepTitles = {
        "Open File Manager",
        "Create .dragweb Folder",
        "Return to DragWeb",
        "Select the Folder"
    };
    private final String[] stepDescriptions = {
        "Open your device's built-in File Manager app and navigate to the root of your internal storage (/storage/emulated/0/).",
        "Create a new folder named exactly as shown below. Make sure it begins with a dot (.) — this is important!",
        "Come back to DragWeb. The folder picker will open automatically when you tap the button below.",
        "Navigate to the newly created .dragweb folder and confirm the selection to grant storage access."
    };
    private final int[] stepIcons = {
        R.drawable.file_import,
        R.drawable.folder_plus,
        R.drawable.app_logo,
        R.drawable.folder_check
    };
    private final String[] exampleTexts = {
        "",
        ".dragweb",
        "",
        ""
    };
    private final String[] exampleHints = {
        "",
        "This is the exact name to use",
        "",
        ""
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_storage_setup);

        initViews();
        setupViewPager();
        setupFolderPicker();
        animateEntrance();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        viewPager = findViewById(R.id.viewPager);
        tabIndicator = findViewById(R.id.tabIndicator);
        btnSelectFolder = findViewById(R.id.btnSelectFolder);
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvCurrentPath = findViewById(R.id.tvCurrentPath);

        toolbar.setNavigationOnClickListener(v -> finish());

        View rootLayout = findViewById(R.id.root_layout);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        btnSelectFolder.setOnClickListener(v -> launchFolderPicker());

        updateCurrentPath();
    }

    private void setupViewPager() {
        StepAdapter adapter = new StepAdapter();
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabIndicator, viewPager,
            (tab, position) -> tab.setIcon(R.drawable.tab_dot_selector)
        ).attach();

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                userHasInteracted = true;
                resetAutoAdvance();
                animateCurrentStep(position);
            }
        });
    }

    private void setupFolderPicker() {
        folderPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        SafStorageUtil.ValidationResult validation = SafStorageUtil.validateFolderSelection(this, uri);
                        if (validation.isValid) {
                            SafStorageUtil.persistFolderSelection(this, uri);
                            Toast.makeText(this, "Storage configured successfully!", Toast.LENGTH_SHORT).show();
                            finishSetup();
                        } else {
                            showMismatchDialog(validation.folderName);
                        }
                    }
                }
            }
        );
    }

    private void launchFolderPicker() {
        try {
            folderPickerLauncher.launch(SafStorageUtil.createFolderPickerIntent());
        } catch (Exception e) {
            Toast.makeText(this, "Could not open folder picker: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showMismatchDialog(String selectedFolderName) {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Wrong Folder Selected")
            .setMessage("You selected: \"" + (selectedFolderName != null ? selectedFolderName : "Unknown") + "\"\n\nPlease select the .dragweb folder you created in the previous steps.")
            .setPositiveButton("Select Again", (dialog, which) -> launchFolderPicker())
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void finishSetup() {
        if (hasFinished) return;
        hasFinished = true;
        stopAutoAdvance();

        setResult(RESULT_OK);
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void updateCurrentPath() {
        if (SafStorageUtil.isStorageConfigured(this)) {
            String path = SafStorageUtil.getSavedStoragePath(this);
            if (path != null) {
                tvCurrentPath.setVisibility(View.VISIBLE);
                tvCurrentPath.setText("Current: " + path);
            }
        }
    }

    private void animateEntrance() {
        tvTitle.animate()
            .alpha(1f)
            .translationY(0)
            .setDuration(600)
            .setInterpolator(new OvershootInterpolator(1.2f))
            .start();

        tvSubtitle.animate()
            .alpha(1f)
            .translationY(0)
            .setStartDelay(200)
            .setDuration(600)
            .setInterpolator(new OvershootInterpolator(1.2f))
            .start();

        viewPager.postDelayed(() -> animateCurrentStep(0), 600);
        startAutoAdvance();
    }

    private void animateCurrentStep(int position) {
        View itemView = viewPager.findViewWithTag("step_" + position);
        if (itemView == null) return;

        ImageView ivIcon = itemView.findViewById(R.id.ivStepIcon);
        TextView tvNumber = itemView.findViewById(R.id.tvStepNumber);
        TextView tvTitleStep = itemView.findViewById(R.id.tvStepTitle);
        TextView tvDesc = itemView.findViewById(R.id.tvStepDescription);
        com.google.android.material.card.MaterialCardView  cardExample = itemView.findViewById(R.id.cardExample);

        if (ivIcon != null) {
            ivIcon.setAlpha(0f);
            ivIcon.setScaleX(0.3f);
            ivIcon.setScaleY(0.3f);
            ivIcon.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .setInterpolator(new OvershootInterpolator(2f))
                .start();
        }

        if (tvNumber != null) {
            tvNumber.setAlpha(0f);
            tvNumber.setTranslationY(20f);
            tvNumber.animate()
                .alpha(1f)
                .translationY(0)
                .setStartDelay(150)
                .setDuration(400)
                .start();
        }

        if (tvTitleStep != null) {
            tvTitleStep.setAlpha(0f);
            tvTitleStep.setTranslationY(20f);
            tvTitleStep.animate()
                .alpha(1f)
                .translationY(0)
                .setStartDelay(250)
                .setDuration(400)
                .start();
        }

        if (tvDesc != null) {
            tvDesc.setAlpha(0f);
            tvDesc.setTranslationY(20f);
            tvDesc.animate()
                .alpha(1f)
                .translationY(0)
                .setStartDelay(350)
                .setDuration(400)
                .start();
        }

        if (cardExample != null && cardExample.getVisibility() == View.VISIBLE) {
            cardExample.setAlpha(0f);
            cardExample.setTranslationY(20f);
            cardExample.animate()
                .alpha(1f)
                .translationY(0)
                .setStartDelay(450)
                .setDuration(400)
                .start();
        }
    }

    private void startAutoAdvance() {
        stopAutoAdvance();
        autoAdvanceTimer = new Timer();
        autoAdvanceTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!userHasInteracted && !hasFinished && !isFinishing()) {
                    runOnUiThread(() -> {
                        int current = viewPager.getCurrentItem();
                        int next = (current + 1) % stepNumbers.length;
                        viewPager.setCurrentItem(next, true);
                    });
                }
            }
        }, 4000, 4000);
    }

    private void resetAutoAdvance() {
        if (!userHasInteracted) return;
        stopAutoAdvance();
    }

    private void stopAutoAdvance() {
        if (autoAdvanceTimer != null) {
            autoAdvanceTimer.cancel();
            autoAdvanceTimer = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCurrentPath();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoAdvance();
    }

    class StepAdapter extends RecyclerView.Adapter<StepAdapter.StepViewHolder> {

        @NonNull
        @Override
        public StepViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_setup_step, parent, false);
            return new StepViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull StepViewHolder holder, int position) {
            holder.itemView.setTag("step_" + position);

            holder.tvStepNumber.setText(stepNumbers[position]);
            holder.tvStepTitle.setText(stepTitles[position]);
            holder.tvStepDescription.setText(stepDescriptions[position]);
            holder.ivStepIcon.setImageResource(stepIcons[position]);

            if (exampleTexts[position].isEmpty()) {
                holder.cardExample.setVisibility(View.GONE);
            } else {
                holder.cardExample.setVisibility(View.VISIBLE);
                holder.tvExampleText.setText(exampleTexts[position]);
                holder.tvExampleHint.setText(exampleHints[position]);
            }
        }

        @Override
        public int getItemCount() {
            return stepNumbers.length;
        }

        class StepViewHolder extends RecyclerView.ViewHolder {
            ImageView ivStepIcon;
            TextView tvStepNumber;
            TextView tvStepTitle;
            TextView tvStepDescription;
            com.google.android.material.card.MaterialCardView cardExample;
            TextView tvExampleText;
            TextView tvExampleHint;

            StepViewHolder(@NonNull View itemView) {
                super(itemView);
                ivStepIcon = itemView.findViewById(R.id.ivStepIcon);
                tvStepNumber = itemView.findViewById(R.id.tvStepNumber);
                tvStepTitle = itemView.findViewById(R.id.tvStepTitle);
                tvStepDescription = itemView.findViewById(R.id.tvStepDescription);
                cardExample = itemView.findViewById(R.id.cardExample);
                tvExampleText = itemView.findViewById(R.id.tvExampleText);
                tvExampleHint = itemView.findViewById(R.id.tvExampleHint);
            }
        }
    }
}
