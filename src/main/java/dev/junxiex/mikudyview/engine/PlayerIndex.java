package dev.junxiex.mikudyview.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 玩家 -> 世界 的映射，避免在 Folia 上跨区域读取 World#getPlayers。 */
public final class PlayerIndex {

    private final Map<UUID, String> worlds = new ConcurrentHashMap<>();

    public void put(UUID id, String world) {
        worlds.put(id, world);
    }

    public void remove(UUID id) {
        worlds.remove(id);
    }

    public Map<String, Integer> counts() {
        Map<String, Integer> counts = new HashMap<>();
        for (String world : worlds.values()) {
            counts.merge(world, 1, Integer::sum);
        }
        return counts;
    }

    public List<UUID> inWorld(String world) {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : worlds.entrySet()) {
            if (entry.getValue().equals(world)) {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    public String worldOf(UUID id) {
        return worlds.get(id);
    }

    public void clear() {
        worlds.clear();
    }
}
