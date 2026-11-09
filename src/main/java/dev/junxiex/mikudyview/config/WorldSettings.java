package dev.junxiex.mikudyview.config;

/** 单个世界的限制与权重。 */
public record WorldSettings(
        boolean exclude,
        int minSim,
        int maxSim,
        int minView,
        int maxView,
        double weight,
        int ticksPerCheck
) {

    public static WorldSettings defaults() {
        return new WorldSettings(false, 4, 12, 4, 12, 1.0D, 0);
    }

    /** 该世界自己的检查间隔；未配置时跟随全局。 */
    public int interval(Settings settings) {
        return ticksPerCheck > 0 ? ticksPerCheck : settings.checkInterval();
    }
}
