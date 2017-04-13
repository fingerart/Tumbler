package io.chengguo.tumbler;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static io.chengguo.tumbler.TumblerUtils.flushStackLocalLeaks;
import static io.chengguo.tumbler.TumblerUtils.getService;
import static io.chengguo.tumbler.TumblerUtils.isAirplaneModeOn;
import static io.chengguo.tumbler.TumblerUtils.log;

/**
 * @author FingerArt http://fingerart.me
 * @date 2017年04月11日 12:17
 */
//@SuppressWarnings("UnusedDeclaration")
class Dispatcher {

    private static final String DISPATCHER_THREAD_NAME = "Dispatcher";
    private static final String OWNER_DISPATCHER = "Dispatcher";
    private static final String VERB_SUBMIT = "enqueue";
    private static final String VERB_COMPLETE = "dequeue";
    private static final String VERB_RETRY = "retry";
    private static final String VERB_REPLAYING = "replaying";
    private static final String VERB_ERROR = "error";

    static final int THREAD_LEAK_CLEANING_MS = 1000;
    private static final long DELAY_RETRY = 500;
    private static final int AIRPLANE_MODE_ON = 1;

    private static final int AIRPLANE_MODE_OFF = 0;
    private static final long DELAY_FAILED = 1000 * 60 / 2;
    private static final int REQUEST_SUBMIT = 1;
    private static final int REQUEST_RETRY = 2;
    private static final int REQUEST_REPLAY = 3;
    private static final int REQUEST_ERROR = 4;
    private static final int REQUEST_FAILED = 5;
    private static final int REQUEST_COMPLETE = 6;
    private static final int REQUEST_NETWORK_STATE_CHANGE = 7;
    private static final int REQUEST_AIRPLANE_MODE_CHANGE = 8;

    final Context context;
    final NetworkBroadcastReceiver receiver;
    final boolean canAccessNetworkState;
    final DispatcherThread dispatcher;
    final DispatcherHandler handler;
    final ExecutorService service;
    final Handler mainThreadHandler;

    private final Map<String, Pitcher> runningPitchers;
    private final Map<String, Pitcher> failPitchers;

    boolean airplaneMode;

    public Dispatcher(Context context, ExecutorService service, Handler mainThreadHandler) {
        this.dispatcher = new DispatcherThread();
        this.dispatcher.start();
        flushStackLocalLeaks(dispatcher.getLooper());
        this.context = context;
        this.service = service;
        this.runningPitchers = new LinkedHashMap<>();
        this.failPitchers = new LinkedHashMap<>();
        this.mainThreadHandler = mainThreadHandler;
        this.handler = new DispatcherHandler(dispatcher.getLooper(), this);
        this.canAccessNetworkState = TumblerUtils.hasPermission(context, Manifest.permission.ACCESS_NETWORK_STATE);
        this.airplaneMode = isAirplaneModeOn(context);
        receiver = new NetworkBroadcastReceiver(this);
        receiver.register();
    }

    void shutdown() {
        Tumbler.HANDLER.post(new Runnable() {
            @Override
            public void run() {
                receiver.unregister();
            }
        });
    }

    /**
     * 调度提交
     *
     * @param pitcher
     */
    void dispatchSubmit(Pitcher pitcher) {
        handler.sendMessage(handler.obtainMessage(REQUEST_SUBMIT, pitcher));
    }

    /**
     * 调度重试
     *
     * @param pitcher
     */
    void dispatchRetry(Pitcher pitcher) {
        handler.sendMessageDelayed(handler.obtainMessage(REQUEST_RETRY, pitcher), DELAY_RETRY);
    }

    void dispatchFailed(Pitcher pitcher) {
        handler.sendMessage(handler.obtainMessage(REQUEST_FAILED, pitcher));
    }

    void dispatchError(Pitcher pitcher) {
        handler.sendMessage(handler.obtainMessage(REQUEST_ERROR, pitcher));
    }

    void dispatchComplete(Pitcher pitcher) {
        handler.sendMessage(handler.obtainMessage(REQUEST_COMPLETE, pitcher));
    }

    void dispatchNetworkStateChange(NetworkInfo networkInfo) {
        handler.sendMessage(handler.obtainMessage(REQUEST_NETWORK_STATE_CHANGE, networkInfo));
    }

    void dispatchAirplaneModeChange(boolean airplaneMode) {
        handler.sendMessage(handler.obtainMessage(REQUEST_AIRPLANE_MODE_CHANGE, airplaneMode ? AIRPLANE_MODE_ON : AIRPLANE_MODE_OFF, 0));
    }

    /**
     * 执行提交
     *
     * @param pitcher
     */
    void performSubmit(Pitcher pitcher) {
        if (pitcher == null || pitcher.isCanceled() || runningPitchers.containsKey(pitcher.javelin.uniqueId())) {
            return;
        }

        runningPitchers.put(pitcher.javelin.uniqueId(), pitcher);

        pitcher.future = service.submit(pitcher);

        if (pitcher.getTumbler().loggingEnabled) {
            log(OWNER_DISPATCHER, VERB_SUBMIT, pitcher.javelin.uniqueId());
        }
    }

    private void performRetry(Pitcher pitcher) {
        if (pitcher == null || pitcher.isCanceled()) {
            return;
        }

        NetworkInfo networkInfo = getNetworkInfo();

        if (pitcher.javelin.shouldRetry(airplaneMode, networkInfo)) {
            if (pitcher.tumbler.loggingEnabled) {
                log(OWNER_DISPATCHER, VERB_RETRY, pitcher.javelin.uniqueId());
            }
            pitcher.future = service.submit(pitcher);
        } else {
            boolean willReplay = pitcher.javelin.supportReplay();
            performError(pitcher, willReplay);
            if (willReplay) {
                performFail(pitcher);
            }
        }
    }

    private void performReplay() {
        flushFailedPitchers();
    }

    private void performError(Pitcher pitcher, boolean willReplay) {
        if (pitcher.tumbler.loggingEnabled) {
            log(OWNER_DISPATCHER, VERB_ERROR, pitcher.javelin.uniqueId(), "for error " + (willReplay ? "（willReplay）" : ""));
        }
        runningPitchers.remove(pitcher);
    }

    private void performFail(Pitcher pitcher) {
        failPitchers.put(pitcher.javelin.uniqueId(), pitcher);
        if (!handler.hasMessages(REQUEST_REPLAY) && networkConnected()) {
            handler.sendEmptyMessageDelayed(REQUEST_REPLAY, DELAY_FAILED);
        }
    }

    private void performComplete(final Pitcher pitcher) {
        runningPitchers.remove(pitcher.javelin.uniqueId());
        if (pitcher.tumbler.loggingEnabled) {
            log(OWNER_DISPATCHER, VERB_COMPLETE, pitcher.javelin.uniqueId());
        }
    }

    private void performNetworkStateChange(NetworkInfo networkInfo) {
        if (service instanceof TumblerExecutorService) {
            ((TumblerExecutorService) service).adjustThreadCount(networkInfo);
        }
        if (networkConnected(networkInfo)) {
            flushFailedPitchers();
        } else {
            handler.removeMessages(REQUEST_REPLAY);
        }
    }

    private void performAirplaneModeChange(boolean airplaneMode) {
        this.airplaneMode = airplaneMode;
    }

    private void flushFailedPitchers() {
        if (failPitchers.isEmpty()) {
            return;
        }
        Iterator<Pitcher> iterator = failPitchers.values().iterator();
        while (iterator.hasNext()) {
            Pitcher pitcher = iterator.next();
            iterator.remove();
            if (pitcher.tumbler.loggingEnabled) {
                log(OWNER_DISPATCHER, VERB_REPLAYING, pitcher.javelin.uniqueId());
            }
            performSubmit(pitcher);
        }
    }

    private boolean networkConnected() {
        NetworkInfo networkInfo = getNetworkInfo();
        return networkConnected(networkInfo);
    }

    private boolean networkConnected(NetworkInfo networkInfo) {
        return networkInfo!= null && networkInfo.isConnected();
    }

    @Nullable
    private NetworkInfo getNetworkInfo() {
        NetworkInfo networkInfo = null;
        if (canAccessNetworkState) {
            ConnectivityManager connectivityManager = getService(context, CONNECTIVITY_SERVICE);
            networkInfo = connectivityManager.getActiveNetworkInfo();
        }
        return networkInfo;
    }

    /**
     * 调度线程
     */
    static class DispatcherThread extends HandlerThread {
        DispatcherThread() {
            super(TumblerUtils.THREAD_PREFIX + DISPATCHER_THREAD_NAME, THREAD_PRIORITY_BACKGROUND);
        }
    }

    /**
     * 调度Handler
     */
    static class DispatcherHandler extends Handler {
        private final Dispatcher dispatcher;

        public DispatcherHandler(Looper looper, Dispatcher dispatcher) {
            super(looper);
            this.dispatcher = dispatcher;
        }

        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case REQUEST_SUBMIT: {
                    Pitcher pitcher = (Pitcher) msg.obj;
                    dispatcher.performSubmit(pitcher);
                    break;
                }
                case REQUEST_RETRY: {
                    Pitcher pitcher = (Pitcher) msg.obj;
                    dispatcher.performRetry(pitcher);
                    break;
                }
                case REQUEST_REPLAY: {
                    dispatcher.performReplay();
                    break;
                }
                case REQUEST_ERROR: {
                    Pitcher pitcher = (Pitcher) msg.obj;
                    dispatcher.performError(pitcher, false);
                    break;
                }
                case REQUEST_FAILED: {
                    Pitcher pitcher = (Pitcher) msg.obj;
                    dispatcher.performFail(pitcher);
                    break;
                }
                case REQUEST_COMPLETE: {
                    Pitcher pitcher = (Pitcher) msg.obj;
                    dispatcher.performComplete(pitcher);
                    break;
                }
                case REQUEST_NETWORK_STATE_CHANGE: {
                    NetworkInfo info = (NetworkInfo) msg.obj;
                    dispatcher.performNetworkStateChange(info);
                    break;
                }
                case REQUEST_AIRPLANE_MODE_CHANGE: {
                    dispatcher.performAirplaneModeChange(msg.arg1 == AIRPLANE_MODE_ON);
                    break;
                }
                default: {
                    Tumbler.HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            throw new AssertionError("Unknown handler message received: " + msg.what);
                        }
                    });
                }
            }
        }
    }

    /**
     * 网络变化广播接受者
     */
    static class NetworkBroadcastReceiver extends BroadcastReceiver {
        static final String EXTRA_AIRPLANE_STATE = "state";

        private final Dispatcher dispatcher;

        NetworkBroadcastReceiver(Dispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        void register() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_AIRPLANE_MODE_CHANGED);
            if (dispatcher.canAccessNetworkState) {
                filter.addAction(CONNECTIVITY_ACTION);
            }
            dispatcher.context.registerReceiver(this, filter);
        }

        void unregister() {
            dispatcher.context.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // On some versions of Android this may be called with a null Intent,
            // also without extras (getExtras() == null), in such case we use defaults.
            if (intent == null) {
                return;
            }
            final String action = intent.getAction();
            if (ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                if (!intent.hasExtra(EXTRA_AIRPLANE_STATE)) {
                    return; // No airplane state, ignore it. Should we query TumblerUtils.isAirplaneModeOn?
                }
                dispatcher.dispatchAirplaneModeChange(intent.getBooleanExtra(EXTRA_AIRPLANE_STATE, false));
            } else if (CONNECTIVITY_ACTION.equals(action)) {
                ConnectivityManager connectivityManager = getService(context, CONNECTIVITY_SERVICE);
                dispatcher.dispatchNetworkStateChange(connectivityManager.getActiveNetworkInfo());
            }
        }
    }
}
