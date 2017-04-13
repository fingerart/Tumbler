package io.chengguo.tumbler;

/**
 * @author FingerArt http://fingerart.me
 * @date 2017年04月11日 16:29
 */
public class SampleTaskPitcher extends Pitcher {

    public SampleTaskPitcher(Tumbler tumbler, Javelin javelin) {
        super(tumbler, javelin);
        checkJavelin(javelin);
    }

    @Override
    void handle() {
        try {
            javelin.pitch(this);
            complete();
        } catch (Exception e) {
            retry();
        }
    }

    private void checkJavelin(Javelin javelin) {
        if (!(javelin instanceof TaskJavelin)) {
            throw new IllegalArgumentException("Javelin must extends TaskJavelin");
        }
    }
}
