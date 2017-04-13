package io.chengguo.tumbler;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static io.chengguo.tumbler.TumblerUtils.THREAD_IDLE_NAME;
import static io.chengguo.tumbler.TumblerUtils.THREAD_PREFIX;

/**
 * @author FingerArt http://fingerart.me
 * @date 2017年04月11日 15:37
 */
public abstract class Pitcher implements Runnable {
    public int sequence;

    protected Tumbler tumbler;
    protected Javelin javelin;
    protected Future future;
    private static final AtomicInteger SEQUENCE_GENERATOR = new AtomicInteger();
    private static final ThreadLocal<StringBuilder> NAME_BUILDER = new ThreadLocal<StringBuilder>() {
        @Override
        protected StringBuilder initialValue() {
            return new StringBuilder(THREAD_PREFIX);
        }
    };

    public Pitcher(Tumbler tumbler, Javelin javelin) {
        sequence = SEQUENCE_GENERATOR.incrementAndGet();
        this.tumbler = tumbler;
        this.javelin = javelin;
    }

    /**
     * 处理
     */
    abstract void handle();

    @Override
    public void run() {
        try {
            updateThreadName(javelin.uniqueId());
            handle();
        } finally {
            Thread.currentThread().setName(THREAD_IDLE_NAME);
        }
    }

    public static void updateThreadName(String name) {
        StringBuilder sb = NAME_BUILDER.get();
        sb.ensureCapacity(THREAD_PREFIX.length() + name.length());
        sb.replace(THREAD_PREFIX.length(), sb.length(), name);
        Thread.currentThread().setName(sb.toString());
    }

    protected void complete() {
        tumbler.dispatcher.dispatchComplete(this);
    }

    protected void retry() {
        tumbler.dispatcher.dispatchRetry(this);
    }

    protected void fail() {
        tumbler.dispatcher.dispatchFailed(this);
    }

    protected void error() {
        tumbler.dispatcher.dispatchError(this);
    }

    boolean cancel() {
        return future != null && future.cancel(false);
    }

    boolean isCanceled() {
        return future != null && future.isCancelled();
    }

    public Tumbler getTumbler() {
        return tumbler;
    }
}
