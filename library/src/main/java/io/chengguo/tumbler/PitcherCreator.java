package io.chengguo.tumbler;

/**
 * @author FingerArt http://fingerart.me
 * @date 2017年04月13日 9:51
 */
public class PitcherCreator {

    public static Pitcher forPitcher(Tumbler tumbler, Javelin javelin) {
        if (javelin instanceof TaskJavelin) {
            return new SampleTaskPitcher(tumbler, javelin);
        }
        throw new IllegalArgumentException("Javelin is not handle.");
    }
}
