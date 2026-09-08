package glab.dragweb.activities;

import glab.dragweb.R;
import glab.dragweb.util.*;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Timer;
import java.util.TimerTask;

public class SplashActivity extends AppCompatActivity {

	private Timer _timer = new Timer();
	private LinearLayout linear1;
	private TextView textview1;
	private TimerTask t;
	private Intent n = new Intent();
	private boolean hasStarted = false;

	private ActivityResultLauncher<Intent> folderPickerLauncher;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EdgeToEdge.enable(this);
		setContentView(R.layout.splash);
		linear1 = findViewById(R.id.linear1);
		textview1 = findViewById(R.id.textview1);

		final View mainRoot = findViewById(R.id.main);
		final int initialTop = mainRoot.getPaddingTop();
		final int initialBottom = mainRoot.getPaddingBottom();
		final int initialLeft = mainRoot.getPaddingLeft();
		final int initialRight = mainRoot.getPaddingRight();

		ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(initialLeft + systemBars.left, initialTop + systemBars.top, initialRight + systemBars.right, initialBottom + systemBars.bottom);
			return insets;
		});

		folderPickerLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == RESULT_OK && result.getData() != null) {
					Uri uri = result.getData().getData();
					if (uri != null) {
						SafStorageUtil.ValidationResult validation = SafStorageUtil.validateFolderSelection(this, uri);
						if (validation.isValid) {
							SafStorageUtil.persistFolderSelection(this, uri);
							go();
						} else {
							showMismatchDialog(validation.folderName);
						}
						return;
					}
				}
				showFolderPickerDialog();
			}
		);

		initializeLogic();
	}

	private void initializeLogic() {
		textview1.animate()
			.alpha(1f)
			.scaleX(1f)
			.scaleY(1f)
			.setDuration(800)
			.setInterpolator(new OvershootInterpolator(4f));

		if (SafStorageUtil.isStorageConfigured(this)) {
			go();
		} else {
			showFolderPickerDialog();
		}
	}

	private void showFolderPickerDialog() {
		new MaterialAlertDialogBuilder(this)
			.setTitle("Select Workspace Folder")
			.setMessage("DragWeb saves all your website design projects, assets, custom blocks, and themes in a '.dragweb' folder.\n\nPlease select or create the '.dragweb' folder on your device storage to continue.")
			.setCancelable(false)
			.setPositiveButton("Select Folder", (dialog, which) -> {
				launchFolderPicker();
			})
			.setNegativeButton("Exit", (dialog, which) -> {
				finish();
			})
			.show();
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
			.setTitle("Folder Location Mismatch")
			.setMessage("You selected: \"" + (selectedFolderName != null ? selectedFolderName : "Unknown folder") + "\"\n\nDragWeb requires the folder to be '.dragweb' so your website projects and configurations are properly organized.\n\nPlease select or create the '.dragweb' folder.")
			.setCancelable(false)
			.setPositiveButton("Select Again", (dialog, which) -> {
				launchFolderPicker();
			})
			.setNegativeButton("Exit", (dialog, which) -> {
				finish();
			})
			.show();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!hasStarted && SafStorageUtil.isStorageConfigured(this)) {
			go();
		}
	}

	private void go() {
		if (hasStarted) return;
		hasStarted = true;

		// Ensure .dragweb directory exists
		File dragWebDir = FileUtil.getDragWebDir(this);
		if (!dragWebDir.exists()) {
			dragWebDir.mkdirs();
		}
		new File(dragWebDir, "projects").mkdirs();
		new File(dragWebDir, "custom").mkdirs();

		if (CustomStorageUtil.needsSync(this)) {
			showSyncBottomSheetAndGo();
		} else {
			scheduleHomeNavigation();
		}
	}

	private void showSyncBottomSheetAndGo() {
		com.google.android.material.bottomsheet.BottomSheetDialog sheet = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
		sheet.setCancelable(false);
		View view = getLayoutInflater().inflate(R.layout.bottom_sheet_json_sync, null);
		sheet.setContentView(view);

		TextView tvStatus = view.findViewById(R.id.tv_sync_status);
		com.google.android.material.progressindicator.LinearProgressIndicator progress = view.findViewById(R.id.progress_sync);

		sheet.show();

		CustomStorageUtil.syncAssetsToStorage(this, (status, percent) -> {
			runOnUiThread(() -> {
				if (tvStatus != null) tvStatus.setText(status);
				if (progress != null) progress.setProgress(percent);
				if (percent >= 100) {
					sheet.dismiss();
					scheduleHomeNavigation();
				}
			});
		});
	}

	private void scheduleHomeNavigation() {
		t = new TimerTask() {
			@Override
			public void run() {
				runOnUiThread(() -> {
					n.setClass(getApplicationContext(), HomeActivity.class);
					startActivity(n);
					finish();
				});
			}
		};
		_timer.schedule(t, 1200);
	}
}
