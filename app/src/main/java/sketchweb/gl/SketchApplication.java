package sketchweb.gl;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;
import com.google.android.material.color.DynamicColors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SketchApplication extends Application {

    private static Context mApplicationContext;

    public static Context getContext() {
        return mApplicationContext;
    }

    @Override
    public void onCreate() {
        mApplicationContext = getApplicationContext();
        DynamicColors.applyToActivitiesIfAvailable(this);

        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread thread, Throwable throwable) {
                        String stackTrace = Log.getStackTraceString(throwable);

                        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                        File logsDir = new File(getApplicationContext().getFilesDir(), "logs");
                        logsDir.mkdirs();
                        File crashFile = new File(logsDir, "crash_" + timestamp + ".txt");
                        try {
                            FileOutputStream fos = new FileOutputStream(crashFile);
                            OutputStreamWriter writer = new OutputStreamWriter(fos);
                            writer.write(stackTrace);
                            writer.close();
                        } catch (Exception ignored) {}

                        Intent intent = new Intent(getApplicationContext(), DebugActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        intent.putExtra("error", stackTrace);
                        startActivity(intent);
                        Process.killProcess(Process.myPid());
                        System.exit(1);
                    }
                });
        super.onCreate();
    }
}
