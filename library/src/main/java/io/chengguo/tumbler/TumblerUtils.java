package io.chengguo.tumbler;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import java.util.concurrent.ThreadFactory;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;
import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static io.chengguo.tumbler.Dispatcher.THREAD_LEAK_CLEANING_MS;
import static io.chengguo.tumbler.Tumbler.TAG;
import static java.lang.String.format;

/**
 * @author FingerArt http://fingerart.me
 * @date 2017年04月11日 12:22
 */
class TumblerUtils {
    static final String THREAD_PREFIX = "Tumbler-";
    static final String THREAD_IDLE_NAME = THREAD_PREFIX + "Idle";

    /**
     * 检测权限
     *
     * @param context
     * @param permission
     * @return
     */
    static boolean hasPermission(Context context, String permission) {
        return context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("unchecked")
    static <T> T getService(Context context, String service) {
        return (T) context.getSystemService(service);
    }

    static void log(String owner, String verb, String logId) {
        log(owner, verb, logId, "");
    }

    static void log(String owner, String verb, String logId, String extras) {
        Log.d(TAG, format("%1$-11s %2$-12s %3$s %4$s", owner, verb, logId, extras));
    }

    static class TumblerThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            return new TumblerThread(r);
        }

        private static class TumblerThread extends Thread {
            TumblerThread(Runnable r) {
                super(r);
            }

            @Override
            public void run() {
                Process.setThreadPriority(THREAD_PRIORITY_BACKGROUND);
                super.run();
            }
        }
    }

    static boolean isAirplaneModeOn(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        try {
            if (SDK_INT < JELLY_BEAN_MR1) {
                //noinspection deprecation
                return Settings.System.getInt(contentResolver, Settings.System.AIRPLANE_MODE_ON, 0) != 0;
            }
            return Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        } catch (NullPointerException e) {
            // https://github.com/square/picasso/issues/761, some devices might crash here, assume that
            // airplane mode is off.
            return false;
        } catch (SecurityException e) {
            //https://github.com/square/picasso/issues/1197
            return false;
        }
    }

    /**
     * Prior to Android 5, HandlerThread always keeps a stack local reference to the last message
     * that was sent to it. This method makes sure that stack local reference never stays there
     * for too long by sending new messages to it every second.
     */
    static void flushStackLocalLeaks(Looper looper) {
        Handler handler = new Handler(looper) {
            @Override public void handleMessage(Message msg) {
                sendMessageDelayed(obtainMessage(), THREAD_LEAK_CLEANING_MS);
            }
        };
        handler.sendMessageDelayed(handler.obtainMessage(), THREAD_LEAK_CLEANING_MS);
    }
}
