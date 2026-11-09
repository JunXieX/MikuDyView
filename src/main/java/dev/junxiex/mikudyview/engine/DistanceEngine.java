package dev.junxiex.mikudyview.engine;

import dev.junxiex.mikudyview.api.ViewDistanceChangedEvent;
import dev.junxiex.mikudyview.config.Messages;
import dev.junxiex.mikudyview.config.Settings;
import dev.junxiex.mikudyview.config.WorldSettings;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 距离引擎：每 tick 推进时钟，按各世界的节奏做检查，满足条件时通过唯一入口写回世界。
 * 所有世界级状态只在全局区域线程上访问；逐玩家数据用并发容器。
 */
public final class DistanceEngine {

    private static final int MIN_DISTANCE = 2;
    private static final int MAX_DISTANCE = 32;
    private static final int PROBE_INTERVAL = 100;
    private static final int CONTESTED_THRESHOLD = 3;

    private final Plugin plugin;
    private final Messages messages;
    private final TargetCalculator calculator = new TargetCalculator();
    private final MsptSampler mspt = new MsptSampler(5);
    private final MemoryMonitor memory = new MemoryMonitor();
    private final PlayerIndex players = new PlayerIndex();
    private final HistoryRing history = new HistoryRing(20);

    private final Map<String, WorldState> states = new HashMap<>();
    private final Map<String, Integer> worldCounters = new HashMap<>();
    private final Deque<double[]> predictionSamples = new ArrayDeque<>();
    private final Map<UUID, Integer> appliedView = new ConcurrentHashMap<>();
    private final Map<String, Integer> clientMaxView = new ConcurrentHashMap<>();

    private volatile Settings settings;
    private Snapshot snapshot;
    private ScheduledTask task;
    private long tickCounter;
    private long startTick;
    private long globalCounter;
    private int probeCounter;

    public DistanceEngine(Plugin plugin, Settings settings, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.startTick = 0L;
        this.globalCounter = Math.max(settings.checkInterval() - 1, 0);
    }

    public void start() {
        task = Bukkit.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, scheduled -> tick(), 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reload(Settings newSettings) {
        this.settings = newSettings;
        this.worldCounters.clear();
        this.predictionSamples.clear();
        this.states.values().forEach(WorldState::clearOverride);
        this.states.values().forEach(state -> {
            state.contested = false;
            state.contestedStreak = 0;
            state.resetStreaks();
            state.pendingRevert = null;
        });
        this.startTick = tickCounter;
        this.globalCounter = Math.max(newSettings.checkInterval() - 1, 0);
        this.snapshot = null;
    }

    public Settings settings() {
        return settings;
    }

    public HistoryRing history() {
        return history;
    }

    public PlayerIndex players() {
        return players;
    }

    public double currentMspt() {
        return mspt.mspt();
    }

    public double freeMemoryPercent() {
        Snapshot current = snapshot;
        return current == null ? 100.0D : current.freeMemoryPercent();
    }

    // ------------------------------------------------------------------
    // 主循环
    // ------------------------------------------------------------------

    private void tick() {
        tickCounter++;
        memory.sample();

        if (++probeCounter >= PROBE_INTERVAL) {
            probeCounter = 0;
            probePlayers();
        }

        Settings current = settings;
        if (!current.enabled()) {
            return;
        }
        if (tickCounter - startTick < current.startDelay()) {
            return;
        }

        globalCounter++;
        if (snapshot == null || globalCounter >= current.checkInterval()) {
            globalCounter = 0;
            mspt.sample();
            snapshot = takeSnapshot(current);
        }

        for (World world : Bukkit.getWorlds()) {
            WorldSettings worldSettings = current.world(world.getName());
            int interval = worldSettings.interval(current);
            int elapsed = worldCounters.merge(world.getName(), 1, Integer::sum);
            if (elapsed < interval) {
                continue;
            }
            worldCounters.put(world.getName(), 0);
            try {
                checkWorld(world, worldSettings, current);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("检查世界 " + world.getName() + " 时出错：" + throwable);
            }
        }
    }

    private Snapshot takeSnapshot(Settings current) {
        Map<String, Integer> counts = players.counts();
        double weightSum = 0.0D;
        int activeWorlds = 0;
        int totalPlayers = 0;
        int totalChunks = 0;

        for (World world : Bukkit.getWorlds()) {
            int inWorld = counts.getOrDefault(world.getName(), 0);
            totalPlayers += inWorld;
            if (inWorld > 0) {
                weightSum += Math.max(current.world(world.getName()).weight(), 0.0D);
                activeWorlds++;
            }
            int sim = world.getSimulationDistance();
            int view = world.getViewDistance();
            totalChunks += ChunkMath.tickingChunks(sim, inWorld)
                    + ChunkMath.nonTickingChunks(view, sim, inWorld);
        }

        double msptNow = mspt.mspt();
        if (current.predictionEnabled()) {
            predictionSamples.addLast(new double[]{totalChunks, msptNow});
            int maxSamples = Math.max(
                    (int) Math.ceil(1800.0D / Math.max(current.checkInterval() / 20.0D, 1.0D)), 1);
            while (predictionSamples.size() > maxSamples) {
                predictionSamples.removeFirst();
            }
        }

        return new Snapshot(msptNow, memory.freePercent(), Math.max(weightSum, 1.0e-4D),
                Math.max(activeWorlds, 1), totalPlayers, Map.copyOf(counts),
                Map.copyOf(clientMaxView), tickCounter);
    }

    private void checkWorld(World world, WorldSettings worldSettings, Settings current) {
        String name = world.getName();
        WorldState state = state(name);
        int currentSim = world.getSimulationDistance();
        int currentView = world.getViewDistance();

        // 争用检测：别人也在写这个世界时，先放弃管理，而不是每轮互相覆盖。
        if (state.lastApplied != null
                && (state.lastApplied[0] != currentSim || state.lastApplied[1] != currentView)) {
            if (++state.contestedStreak >= CONTESTED_THRESHOLD) {
                state.contested = true;
                plugin.getLogger().warning("世界 '" + name + "' 的距离连续 " + CONTESTED_THRESHOLD
                        + " 次被其它插件或配置改写，已停止自动管理；执行 /mvd reload 可重新接管。");
                return;
            }
        } else {
            state.contestedStreak = 0;
        }
        if (state.contested) {
            return;
        }

        if (state.overrideActive(tickCounter)) {
            apply(world, worldSettings, state.overrideSim, state.overrideView, "手动覆盖", false);
            return;
        }

        if (!current.autoMode() || worldSettings.exclude()) {
            return;
        }

        Snapshot snapshotNow = snapshot;
        if (snapshotNow == null) {
            return;
        }

        Target target = calculator.compute(current, worldSettings, currentSim, currentView,
                snapshotNow.players(name), snapshotNow.clientMaxView(name), snapshotNow);

        if (state.pendingRevert != null) {
            int[] previous = state.pendingRevert;
            state.pendingRevert = null;
            if (snapshotNow.mspt() >= current.decreaseMspt()) {
                apply(world, worldSettings, previous[0], previous[1], "刚升即过载，回滚", true);
                state.resetStreaks();
                return;
            }
        }

        boolean increase = target.sim() > currentSim || target.view() > currentView;
        boolean decrease = target.sim() < currentSim || target.view() < currentView;

        if (increase && !decrease) {
            if (memoryBlocked(current) || predictionBlocks(target.sim(), current)) {
                state.increaseStreak = 0;
                return;
            }
            if (tickCounter - state.lastIncreaseTick < current.minIncreaseInterval()) {
                state.increaseStreak = 0;
                return;
            }
            if (++state.increaseStreak < current.checksToIncrease()) {
                return;
            }
            state.increaseStreak = 0;
            state.decreaseStreak = 0;
            state.lastIncreaseTick = tickCounter;
            if (current.revertOnOverload()) {
                state.pendingRevert = new int[]{currentSim, currentView};
            }
            apply(world, worldSettings, target.sim(), target.view(), target.reasonText(), true);
        } else if (decrease) {
            state.increaseStreak = 0;
            if (++state.decreaseStreak >= current.checksToDecrease()) {
                state.decreaseStreak = 0;
                apply(world, worldSettings, target.sim(), target.view(), target.reasonText(), true);
            }
        } else {
            state.resetStreaks();
        }
    }

    private boolean memoryBlocked(Settings current) {
        return current.memorySafeguard()
                && snapshot != null
                && snapshot.freeMemoryPercent() < current.memoryMinFreePercent();
    }

    /** 用历史 (区块数, MSPT) 做一元线性回归，预测升到目标后是否会越过下调阈值。 */
    private boolean predictionBlocks(int targetSim, Settings current) {
        if (!current.predictionEnabled() || predictionSamples.size() < current.predictionMinSamples()) {
            return false;
        }
        int players = Math.max(snapshot == null ? 1 : snapshot.totalPlayers(), 1);
        double newChunks = Math.pow(2.0D * targetSim + 1.0D, 2.0D) * players;

        double sumX = 0.0D;
        double sumY = 0.0D;
        double sumXY = 0.0D;
        double sumX2 = 0.0D;
        int n = predictionSamples.size();
        for (double[] sample : predictionSamples) {
            sumX += sample[0];
            sumY += sample[1];
            sumXY += sample[0] * sample[1];
            sumX2 += sample[0] * sample[0];
        }
        double denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 1.0e-9D) {
            return false;
        }
        double slope = (n * sumXY - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;
        double predicted = intercept + slope * newChunks;
        return Double.isFinite(predicted) && predicted >= current.decreaseMspt();
    }

    // ------------------------------------------------------------------
    // 唯一写入口
    // ------------------------------------------------------------------

    public boolean apply(World world, WorldSettings worldSettings, int sim, int view, String reason, boolean record) {
        int safeSim = clamp(sim);
        int safeView = clamp(Math.max(view, safeSim));
        int oldSim = world.getSimulationDistance();
        int oldView = world.getViewDistance();
        boolean changed = false;
        try {
            if (safeSim != oldSim) {
                world.setSimulationDistance(safeSim);
                changed = true;
            }
            if (safeView != oldView) {
                world.setViewDistance(safeView);
                changed = true;
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("写入世界 " + world.getName() + " 的距离失败：" + throwable);
            return false;
        }

        int newSim = world.getSimulationDistance();
        int newView = world.getViewDistance();
        WorldState state = state(world.getName());
        state.lastApplied = new int[]{newSim, newView};

        if (!changed) {
            return false;
        }

        history.add(new HistoryRing.Entry(System.currentTimeMillis(), world.getName(),
                oldSim, newSim, oldView, newView, reason));

        if (record) {
            Bukkit.getPluginManager().callEvent(
                    new ViewDistanceChangedEvent(world, oldSim, newSim, oldView, newView, reason));
            if (settings.logChanges()) {
                plugin.getLogger().info("[" + world.getName() + "] sim " + oldSim + " -> " + newSim
                        + " | view " + oldView + " -> " + newView + " (" + reason + ")");
            }
        }

        if (newView != oldView) {
            reapplyOverrides(world.getName());
        }
        return true;
    }

    public void setManual(World world, String type, int value) {
        int sim = "sim".equals(type) ? value : world.getSimulationDistance();
        int view = "view".equals(type) ? value : world.getViewDistance();
        if (view < sim) {
            view = sim;
        }
        WorldState state = state(world.getName());
        state.contested = false;
        state.contestedStreak = 0;
        state.setOverride(sim, view, Long.MAX_VALUE);
        apply(world, settings.world(world.getName()), sim, view, "手动 /mvd set", true);
    }

    public void clearOverrides() {
        states.values().forEach(WorldState::clearOverride);
    }

    public void forgetWorld(String name) {
        states.remove(name);
        worldCounters.remove(name);
        clientMaxView.remove(name);
    }

    // ------------------------------------------------------------------
    // 逐玩家视距
    // ------------------------------------------------------------------

    private void probePlayers() {
        Settings current = settings;
        boolean needed = current.fixedViewEnabled() || current.afkEnabled() || current.clientViewCap();
        if (!needed) {
            return;
        }
        clientMaxView.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(plugin, scheduled -> probe(player), () -> {
            });
        }
    }

    private void probe(Player player) {
        if (!player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        String worldName = player.getWorld().getName();
        players.put(id, worldName);

        int client = 0;
        try {
            int reported = player.getClientViewDistance();
            if (reported >= MIN_DISTANCE && reported <= MAX_DISTANCE) {
                client = reported;
            }
        } catch (Throwable ignored) {
            // 非 Paper 端不会有这个 API；纯 Paper 插件里只是保险。
        }
        if (client > 0) {
            clientMaxView.merge(worldName, client, Math::max);
        }

        if (!settings.fixedViewEnabled() && !settings.afkEnabled()) {
            return;
        }

        int desired = 0;
        if (settings.fixedViewEnabled()) {
            desired = resolveFixedView(player);
        }
        if (desired < MIN_DISTANCE && settings.afkEnabled()) {
            try {
                if (player.getIdleDuration().toMinutes() >= settings.afkMinutes()) {
                    desired = Math.min(settings.afkViewDistance(), player.getWorld().getViewDistance());
                }
            } catch (Throwable ignored) {
                // 读不到空闲时间就不做 AFK 降距。
            }
        }

        if (desired >= MIN_DISTANCE) {
            applyPlayerView(player, Math.min(desired, MAX_DISTANCE), false);
        } else {
            releasePlayerView(player);
        }
    }

    private int resolveFixedView(Player player) {
        String prefix = settings.fixedViewPrefix();
        int best = 0;
        for (var info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String permission = info.getPermission().toLowerCase(java.util.Locale.ROOT);
            if (!permission.startsWith(prefix)) {
                continue;
            }
            try {
                int value = Integer.parseInt(permission.substring(prefix.length()));
                if (value >= MIN_DISTANCE && value <= MAX_DISTANCE) {
                    best = Math.max(best, value);
                }
            } catch (NumberFormatException ignored) {
                // 权限名不以数字结尾就跳过。
            }
        }
        return best;
    }

    private void applyPlayerView(Player player, int value, boolean force) {
        UUID id = player.getUniqueId();
        Integer applied = appliedView.get(id);
        if (!force && applied != null && applied == value) {
            return;
        }
        player.setViewDistance(value);
        appliedView.put(id, value);
    }

    private void releasePlayerView(Player player) {
        UUID id = player.getUniqueId();
        if (appliedView.remove(id) == null) {
            return;
        }
        player.setViewDistance(player.getWorld().getViewDistance());
    }

    private void reapplyOverrides(String worldName) {
        if (!settings.fixedViewEnabled() && !settings.afkEnabled()) {
            return;
        }
        for (UUID id : players.inWorld(worldName)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            appliedView.remove(id);
            player.getScheduler().run(plugin, scheduled -> probe(player), () -> {
            });
        }
    }

    public void onJoin(Player player) {
        players.put(player.getUniqueId(), player.getWorld().getName());
        player.getScheduler().run(plugin, scheduled -> probe(player), () -> {
        });
    }

    public void onQuit(Player player) {
        players.remove(player.getUniqueId());
        appliedView.remove(player.getUniqueId());
    }

    public void onWorldChange(Player player) {
        players.put(player.getUniqueId(), player.getWorld().getName());
        player.getScheduler().run(plugin, scheduled -> {
            appliedView.remove(player.getUniqueId());
            probe(player);
        }, () -> {
        });
    }

    public int trackedPlayers() {
        return appliedView.size();
    }

    // ------------------------------------------------------------------
    // 预览与查询
    // ------------------------------------------------------------------

    public record PlanRow(String world, int currentSim, int currentView,
                          int targetSim, int targetView, boolean wouldApply, String reason) {
    }

    public List<PlanRow> simulate(String worldFilter) {
        Settings current = settings;
        Snapshot snapshotNow = snapshot != null ? snapshot : takeSnapshot(current);
        List<PlanRow> rows = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (worldFilter != null && !world.getName().equalsIgnoreCase(worldFilter)) {
                continue;
            }
            WorldSettings worldSettings = current.world(world.getName());
            int sim = world.getSimulationDistance();
            int view = world.getViewDistance();
            Target target = calculator.compute(current, worldSettings, sim, view,
                    snapshotNow.players(world.getName()), snapshotNow.clientMaxView(world.getName()), snapshotNow);
            WorldState state = state(world.getName());
            boolean wouldApply;
            if (state.contested || worldSettings.exclude() || !current.autoMode()) {
                wouldApply = false;
            } else if (target.sim() > sim || target.view() > view) {
                wouldApply = state.increaseStreak + 1 >= current.checksToIncrease();
            } else if (target.sim() < sim || target.view() < view) {
                wouldApply = state.decreaseStreak + 1 >= current.checksToDecrease();
            } else {
                wouldApply = false;
            }
            rows.add(new PlanRow(world.getName(), sim, view, target.sim(), target.view(),
                    wouldApply, target.reasonText()));
        }
        return rows;
    }

    public int nextCheckIn(String worldName) {
        Settings current = settings;
        WorldSettings worldSettings = current.world(worldName);
        int interval = worldSettings.interval(current);
        int elapsed = worldCounters.getOrDefault(worldName, 0);
        return Math.max(interval - elapsed, 0);
    }

    public boolean isContested(String worldName) {
        WorldState state = states.get(worldName);
        return state != null && state.contested;
    }

    public boolean hasOverride(String worldName) {
        WorldState state = states.get(worldName);
        return state != null && state.overrideActive(tickCounter);
    }

    private WorldState state(String worldName) {
        return states.computeIfAbsent(worldName, key -> new WorldState());
    }

    private static int clamp(int value) {
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, value));
    }
}
