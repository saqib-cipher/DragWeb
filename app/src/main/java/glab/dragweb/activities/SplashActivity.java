package glab.dragweb.activities;

import glab.dragweb.R;
import glab.dragweb.util.*;

import android.content.Intent;
import android.os.Bundle;
import java.io.File;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Timer;
import java.util.TimerTask;

public class SplashActivity extends AppCompatActivity {

	private Timer _timer = new Timer();
	private LinearLayout linear1;
	private TextView textview1;
	private TimerTask t;
	private Intent n = new Intent();
	private boolean hasStarted = false;

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

		initializeLogic();
	}

	private void initializeLogic() {
		textview1.animate()
			.alpha(1f)
			.scaleX(1f)
			.scaleY(1f)
			.setDuration(800)
			.setInterpolator(new OvershootInterpolator(4f));

		checkStorageAndProceed();
	}

	private void checkStorageAndProceed() {
		if (SafStorageUtil.isStorageConfigured(this)) {
			File dragWebDir = FileUtil.getDragWebDir(this);
			if (!dragWebDir.exists()) {
				dragWebDir.mkdirs();
			}
			go();
		} else {
			showFolderPickerDialog();
		}
	}

	private void showFolderPickerDialog() {
		goToStorageSetup();
	}

	private void launchFolderPicker() {
		goToStorageSetup();
	}

	private void goToStorageSetup() {
		Intent intent = new Intent(this, StorageSetupActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
		finish();
	}

	private void showMismatchDialog(String selectedFolderName) {
		goToStorageSetup();
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

		scheduleHomeNavigation();
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
