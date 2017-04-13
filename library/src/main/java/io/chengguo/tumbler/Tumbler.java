package io.chengguo.tumbler;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import java.util.concurrent.ExecutorService;

import static io.chengguo.tumbler.PitcherCreator.forPitcher;

/**
 * @author FingerArt http://fingerart.me
 * @date 2017年04月10日 17:34
 */
@SuppressWarnings("UnusedDeclaration")
public class Tumbler {
    final static String TAG = "Tumbler";
    private static volatile Tumbler singleton;

    static final Handler HANDLER = new Handler(Looper.getMainLooper());

    final Context context;
    final Dispatcher dispatcher;
    volatile boolean loggingEnabled;

    public enum Priority {
        HIGH,
        NORMAL,
        LOW
    }

    public Tumbler(Context context, Dispatcher dispatcher, boolean loggingEnabled) {
        this.context = context;
        this.dispatcher = dispatcher;
        this.loggingEnabled = loggingEnabled;
    }

    /**
     * 使用默认或通过{@link #setSingletonInstance(Tumbler)}的全局实例
     *
     * @param context
     * @return
     */
    public static Tumbler with(@NonNull Context context) {
        if (singleton == null) {
            synchronized (Tumbler.class) {
                if (singleton == null) {
                    singleton = new Builder(context).build();
                }
            }
        }
        return singleton;
    }

    public Tumbler enableLogging(boolean enable) {
        loggingEnabled = enable;
        return this;
    }

    public void enqueueAndSubmit(Javelin javelin) {
        Pitcher pitcher = forPitcher(this, javelin);
        dispatcher.dispatchSubmit(pitcher);
    }

    /**
     * 设置默认的实例
     * 如果默认实例已经存在将会抛出异常
     *
     * @param tumbler
     */
    public static void setSingletonInstance(@NonNull Tumbler tumbler) {
        if (tumbler == null) {
            throw new IllegalArgumentException("Tumbler must not be null.");
        }
        if (singleton != null) {
            throw new IllegalStateException("Singleton instance already exists.");
        }
        singleton = tumbler;
    }

    public static class Builder {
        private Context context;
        private ExecutorService service;
        boolean loggingEnabled;

        public Builder(@NonNull Context context) {
            if (context == null) {
                throw new IllegalArgumentException("Context must not be null.");
            }
            this.context = context.getApplicationContext();
        }

        public Builder executor(@NonNull ExecutorService executorService) {
            if (executorService == null) {
                throw new IllegalArgumentException("Executor service must not be null.");
            }
            if (service != null) {
                throw new IllegalStateException("Executor service already set.");
            }
            this.service = executorService;
            return this;
        }

        public Builder enableLogging(boolean enable) {
            loggingEnabled = enable;
            return this;
        }

        public Tumbler build() {
            if (service == null) {
                service = new TumblerExecutorService();
            }
            Dispatcher dispatcher = new Dispatcher(context, service, HANDLER);
            return new Tumbler(context, dispatcher, loggingEnabled);
        }
    }
}