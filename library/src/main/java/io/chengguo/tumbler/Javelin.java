package io.chengguo.tumbler;

import android.net.NetworkInfo;

/**
 * @author FingerArt http://fingerart.me
 * @date 2017年04月11日 15:38
 */
interface Javelin {
    /**
     * UniqueId
     *
     * @return
     */
    String uniqueId();

    /**
     * 处理操作
     *
     * @param pitcher
     */
    void pitch(Pitcher pitcher);

    boolean shouldRetry(boolean airplaneMode, NetworkInfo networkInfo);

    boolean supportReplay();
}
