package dev.junxiex.mikudyview.engine;

/** 单个世界的运行期状态，全部只在全局线程上访问。 */
final class WorldState {

    int increaseStreak;
    int decreaseStreak;
    int contestedStreak;
    boolean contested;

    /** 上一次实际写入世界的 {sim, view}，用于发现别的插件在覆盖。 */
    int[] lastApplied;

    /** 刚升过时的 {sim, view}，只对下一次检查有效。 */
    int[] pendingRevert;

    long lastIncreaseTick;

    boolean hasOverride;
    int overrideSim;
    int overrideView;
    long overrideUntil;

    void resetStreaks() {
        increaseStreak = 0;
        decreaseStreak = 0;
    }

    boolean overrideActive(long now) {
        return hasOverride && now < overrideUntil;
    }

    void setOverride(int sim, int view, long until) {
        this.hasOverride = true;
        this.overrideSim = sim;
        this.overrideView = view;
        this.overrideUntil = until;
    }

    void clearOverride() {
        this.hasOverride = false;
    }
}
