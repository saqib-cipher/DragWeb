package sketchweb.gl;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DebugActivity extends AppCompatActivity {

    private String crashLogPath;
    private String errorDetail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        String error = getIntent() != null ? getIntent().getStringExtra("error") : "";
        if (error == null) error = "";

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File logsDir = new File(getFilesDir(), "logs");
        logsDir.mkdirs();
        File crashFile = new File(logsDir, "crash_" + timestamp + ".txt");
        try {
            FileOutputStream fos = new FileOutputStream(crashFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos);
            writer.write(error);
            writer.close();
            crashLogPath = crashFile.getAbsolutePath();
        } catch (Exception e) {
            crashLogPath = null;
        }

        MaterialTextView errorSummary = findViewById(R.id.error_summary);
        MaterialTextView errorDetailView = findViewById(R.id.error_detail);

        if (!error.isEmpty()) {
            String[] lines = error.split("\n");
            String firstLine = lines.length > 0 ? lines[0] : "Unknown error";
            errorSummary.setText(firstLine);
            errorDetailView.setText(error);
            errorDetail = error;
        } else {
            errorSummary.setText("No error message available");
            errorDetailView.setText("");
            errorDetail = "";
        }

        MaterialButton btnRestart = findViewById(R.id.btn_restart);
        MaterialButton btnSend = findViewById(R.id.btn_send_report);

        btnRestart.setOnClickListener(v -> {
            Intent intent = new Intent(this, SplashActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            Process.killProcess(Process.myPid());
            finish();
        });

        btnSend.setOnClickListener(v -> shareToTelegram());
    }

    private void shareToTelegram() {
        if (crashLogPath == null) {
            Toast.makeText(this, "No crash log file available", Toast.LENGTH_SHORT).show();
            return;
        }

        File crashFile = new File(crashLogPath);
        if (!crashFile.exists()) {
            Toast.makeText(this, "Crash log file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String caption = buildCaption();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this,
            getPackageName() + ".fileprovider", crashFile));
        intent.putExtra(Intent.EXTRA_TEXT, caption);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setPackage("org.telegram.messenger");

        try {
            startActivity(intent);
        } catch (Exception e) {
            intent.setPackage(null);
            try {
                startActivity(Intent.createChooser(intent, "Share crash report"));
            } catch (Exception e2) {
                Toast.makeText(this, "No app available to share", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String buildCaption() {
        return "**Crash Report**"
            + "\n**Device:** " + Build.MANUFACTURER + " " + Build.MODEL
            + "\n**Android:** " + Build.VERSION.RELEASE
            + "\n**Time:** " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }
}
