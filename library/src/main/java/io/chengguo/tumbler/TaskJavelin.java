package io.chengguo.tumbler;

/**
 * @author FingerArt http://fingerart.me
 * @date 2017年04月12日 14:48
 */
abstract class TaskJavelin implements Javelin {
    protected Object evidence;

    public TaskJavelin(Object evidence) {
        this.evidence = evidence;
    }

}
