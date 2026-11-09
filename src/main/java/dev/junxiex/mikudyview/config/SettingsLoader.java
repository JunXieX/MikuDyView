package dev.junxiex.mikudyview.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** 把 config.yml 读成 Settings，并对每个数值做钳制与校验。 */
public final class SettingsLoader {

    private SettingsLoader() {
    }

    public static Settings load(FileConfiguration cfg, Logger logger) {
        boolean enabled = cfg.getBoolean("enabled", true);
        String mode = cfg.getString("mode", "mixed").trim().toLowerCase(Locale.ROOT);
        if (!mode.equals("mixed") && !mode.equals("off")) {
            logger.warning("未知的 mode '" + mode + "'，回落为 mixed。");
            mode = "mixed";
        }

        int checkInterval = clampInt(cfg.getInt("check-interval", 200), 40, 2400);
        int startDelay = Math.max(cfg.getInt("start-delay", 2400), 0);
        double increase = cfg.getDouble("increase-mspt", 40.0D);
        double decrease = cfg.getDouble("decrease-mspt", 47.0D);
        if (increase >= decrease) {
            logger.warning("increase-mspt (" + increase + ") 不应大于等于 decrease-mspt (" + decrease + ")，已回落为 40/47。");
            increase = 40.0D;
            decrease = 47.0D;
        }

        int maxIncrease = clampInt(cfg.getInt("max-increase-step", 2), 1, 8);
        int maxDecrease = clampInt(cfg.getInt("max-decrease-step", 4), 1, 8);
        int checksIncrease = clampInt(cfg.getInt("checks-to-increase", 4), 1, 100);
        int checksDecrease = clampInt(cfg.getInt("checks-to-decrease", 1), 1, 100);
        int minIncreaseInterval = Math.max(cfg.getInt("min-increase-interval", 200), 0);

        boolean revert = cfg.getBoolean("revert-on-overload", true);
        boolean prediction = cfg.getBoolean("prediction.enabled", true);
        int predictionMinSamples = clampInt(cfg.getInt("prediction.min-samples", 5), 2, 100);

        boolean memory = cfg.getBoolean("memory-safeguard.enabled", true);
        double memoryFree = clampDouble(cfg.getDouble("memory-safeguard.min-free-percent", 15.0D), 0.0D, 95.0D);

        boolean clientCap = cfg.getBoolean("client-view-cap.enabled", false);
        int clientHeadroom = clampInt(cfg.getInt("client-view-cap.headroom", 1), 0, 8);

        boolean fixedView = cfg.getBoolean("per-player-fixed-view.enabled", false);
        String fixedPrefix = cfg.getString("per-player-fixed-view.permission-prefix", "mikudyview.fixed.")
                .trim().toLowerCase(Locale.ROOT);
        if (fixedPrefix.isEmpty()) {
            fixedPrefix = "mikudyview.fixed.";
        }
        if (!fixedPrefix.endsWith(".")) {
            fixedPrefix = fixedPrefix + ".";
        }

        boolean afk = cfg.getBoolean("afk-throttle.enabled", false);
        int afkMinutes = clampInt(cfg.getInt("afk-throttle.minutes", 5), 1, 720);
        int afkView = clampInt(cfg.getInt("afk-throttle.view-distance", 6), 2, 32);

        int tickingBudget = Math.max(cfg.getInt("budget.ticking", 5780), 1);
        int nonTickingBudget = Math.max(cfg.getInt("budget.non-ticking", 6720), 1);
        boolean logChanges = cfg.getBoolean("log-changes", true);

        Map<String, WorldSettings> worlds = new LinkedHashMap<>();
        ConfigurationSection section = cfg.getConfigurationSection("world-settings");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection ws = section.getConfigurationSection(key);
                if (ws == null) {
                    continue;
                }
                int minSim = clampInt(ws.getInt("simulation.min", 4), 2, 32);
                int maxSim = clampInt(ws.getInt("simulation.max", 8), minSim, 32);
                int minView = clampInt(ws.getInt("view.min", 4), 2, 32);
                int maxView = clampInt(ws.getInt("view.max", 12), Math.max(minView, minSim), 32);
                double weight = clampDouble(ws.getDouble("weight", 1.0D), 0.0D, 10.0D);
                int ticksPerCheck = Math.max(ws.getInt("ticks-per-check", 0), 0);
                worlds.put(key, new WorldSettings(
                        ws.getBoolean("exclude", false),
                        minSim, maxSim, minView, maxView, weight, ticksPerCheck));
            }
        }
        if (!worlds.containsKey("default")) {
            worlds.put("default", WorldSettings.defaults());
        }

        return new Settings(enabled, mode, checkInterval, startDelay, increase, decrease,
                maxIncrease, maxDecrease, checksIncrease, checksDecrease, minIncreaseInterval, revert,
                prediction, predictionMinSamples, memory, memoryFree, clientCap, clientHeadroom,
                fixedView, fixedPrefix, afk, afkMinutes, afkView,
                tickingBudget, nonTickingBudget, logChanges, Map.copyOf(worlds));
    }

    static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static double clampDouble(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
