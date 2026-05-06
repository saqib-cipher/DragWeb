package sketchweb.gl;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
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

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			if (!Environment.isExternalStorageManager()) {
				Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
				intent.setData(Uri.parse("package:" + getPackageName()));
				startActivityForResult(intent, 1000);
			} else {
				go();
			}
		} else {
			if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
				android.content.pm.PackageManager.PERMISSION_DENIED) {
				requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
			} else {
				go();
			}
		}
	}

	private void go() {
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
		_timer.schedule(t, 2000);
	}
}
