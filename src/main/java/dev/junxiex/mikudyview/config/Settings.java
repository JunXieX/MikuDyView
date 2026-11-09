package dev.junxiex.mikudyview.config;

import java.util.Map;

/** 一次 reload 产生的不可变配置快照。 */
public record Settings(
        boolean enabled,
        String mode,
        int checkInterval,
        int startDelay,
        double increaseMspt,
        double decreaseMspt,
        int maxIncreaseStep,
        int maxDecreaseStep,
        int checksToIncrease,
        int checksToDecrease,
        int minIncreaseInterval,
        boolean revertOnOverload,
        boolean predictionEnabled,
        int predictionMinSamples,
        boolean memorySafeguard,
        double memoryMinFreePercent,
        boolean clientViewCap,
        int clientViewCapHeadroom,
        boolean fixedViewEnabled,
        String fixedViewPrefix,
        boolean afkEnabled,
        int afkMinutes,
        int afkViewDistance,
        int tickingBudget,
        int nonTickingBudget,
        boolean logChanges,
        Map<String, WorldSettings> worlds
) {

    public boolean autoMode() {
        return enabled && !"off".equals(mode);
    }

    public WorldSettings world(String name) {
        WorldSettings ws = worlds.get(name);
        return ws != null ? ws : worlds.getOrDefault("default", WorldSettings.defaults());
    }
}
