package dev.junxiex.mikudyview.engine;

import java.util.Map;

/** 一次全局采样：所有世界共用的那一份数据。 */
public record Snapshot(
        double mspt,
        double freeMemoryPercent,
        double weightSum,
        int activeWorlds,
        int totalPlayers,
        Map<String, Integer> playersByWorld,
        Map<String, Integer> clientMaxView,
        long tick
) {

    public int players(String world) {
        return playersByWorld.getOrDefault(world, 0);
    }

    public int clientMaxView(String world) {
        return clientMaxView.getOrDefault(world, 0);
    }
}
