package glab.dragweb.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Centralized executor pool for DragWeb.
 *
 * Usage:
 *   AppExecutors.io().execute(() -> { /* background work *\/ });
 *   AppExecutors.mainThread().post(() -> { /* UI work *\/ });
 */
public final class AppExecutors {

    private static volatile AppExecutors sInstance;

    // Single-thread executor for sequential file I/O (ordering is important for save/load)
    private final ExecutorService ioExecutor;

    // Separate pool for CPU-bound work (JSON parsing, codegen)
    private final ExecutorService cpuExecutor;

    // Main-thread handler
    private final Handler mainHandler;

    private AppExecutors() {
        ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "DragWeb-IO");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        cpuExecutor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() - 1),
                r -> {
                    Thread t = new Thread(r, "DragWeb-CPU");
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                });
        mainHandler = new Handler(Looper.getMainLooper());
    }

    private static AppExecutors getInstance() {
        if (sInstance == null) {
            synchronized (AppExecutors.class) {
                if (sInstance == null) {
                    sInstance = new AppExecutors();
                }
            }
        }
        return sInstance;
    }

    /** Single-thread executor for sequential file I/O operations. */
    public static ExecutorService io() {
        return getInstance().ioExecutor;
    }

    /** Multi-thread executor for CPU-bound tasks (parsing, codegen). */
    public static ExecutorService cpu() {
        return getInstance().cpuExecutor;
    }

    /** Post a runnable to the main (UI) thread. */
    public static void mainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            getInstance().mainHandler.post(runnable);
        }
    }

    /** Post a runnable to the main thread with a delay in ms. */
    public static void mainThreadDelayed(Runnable runnable, long delayMs) {
        getInstance().mainHandler.postDelayed(runnable, delayMs);
    }
}
