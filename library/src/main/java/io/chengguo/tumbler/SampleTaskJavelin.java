package io.chengguo.tumbler;

import android.net.NetworkInfo;
import android.util.Log;

/**
 * 同步证据创建本地创建成功
 *
 * @author FingerArt http://fingerart.me
 * @date 2017年04月11日 16:28
 */
public class SampleTaskJavelin extends TaskJavelin {
    private static final String TAG = "SampleTaskJavelin";
    private int retryCount = 3;

    public SampleTaskJavelin(Object evidence) {
        super(evidence);
    }

    @Override
    public String uniqueId() {
        return "id";
    }

    @Override
    public void pitch(Pitcher pitcher) {
        Log.d("TAG", "pitch() called with: pitcher = [" + pitcher + "]");
        throw new RuntimeException("mock exception");
    }

    @Override
    public boolean shouldRetry(boolean airplaneMode, NetworkInfo networkInfo) {
        return retryCount-- > 0;
    }

    @Override
    public boolean supportReplay() {
        return true;
    }
}
