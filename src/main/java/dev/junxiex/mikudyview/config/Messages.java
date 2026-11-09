package dev.junxiex.mikudyview.config;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** messages.yml 的读取与占位符渲染，使用 MiniMessage。 */
public final class Messages {

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("status.header", "<aqua>===== MikuDyView =====");
        DEFAULTS.put("status.summary", "<gray>模式 <white><mode></white> | MSPT <white><mspt></white> | 空闲堆 <white><mem>%</white> | 开关 <white><enabled>");
        DEFAULTS.put("status.world", "<yellow><world></yellow> <gray>sim <white><sim></white> view <white><view></white> | 玩家 <white><players></white> | 下次 <white><next>s<flags>");
        DEFAULTS.put("status.flags.excluded", " (excluded)");
        DEFAULTS.put("status.flags.contested", " (contested)");
        DEFAULTS.put("status.flags.override", " (override)");
        DEFAULTS.put("status.none", "<gray>没有正在管理的世界。");
        DEFAULTS.put("history.header", "<aqua>===== 最近 <count> 条变更 =====");
        DEFAULTS.put("history.entry", "<gray><time> <yellow><world></yellow> <gray>sim <white><oldsim></white>-><white><newsim></white> view <white><oldview></white>-><white><newview>");
        DEFAULTS.put("history.reason", "  <dark_gray>原因：<reason>");
        DEFAULTS.put("history.empty", "<gray>暂无变更记录。");
        DEFAULTS.put("simulate.header", "<aqua>===== 预演：<world> =====");
        DEFAULTS.put("simulate.entry", "<yellow><world></yellow> <gray>当前 <white><sim>/<view></white> -> 目标 <white><targetsim>/<targetview></white> <gray>[<verdict><gray>]");
        DEFAULTS.put("simulate.reason", "  <dark_gray>原因：<reason>");
        DEFAULTS.put("simulate.empty", "<gray>没有可预演的世界。");
        DEFAULTS.put("set.ok", "<green>已设置 <white><world></white> 的 <white><type></white> 为 <white><value></white>（保持到下次 reload）。");
        DEFAULTS.put("set.invalid", "<red>距离必须在 2-32 之间。");
        DEFAULTS.put("world.not-found", "<red>找不到世界：<world>");
        DEFAULTS.put("reload.ok", "<green>配置已重新加载。");
        DEFAULTS.put("toggle.on", "<green>自动调节已开启（已写入配置文件）。");
        DEFAULTS.put("toggle.off", "<yellow>自动调节已关闭（已写入配置文件）。");
        DEFAULTS.put("no-permission", "<red>你没有权限。");
        DEFAULTS.put("player-only", "<red>该命令只能由玩家执行。");
        DEFAULTS.put("player.released", "<gray>已恢复 <white><player></white> 的世界视距。");
    }

    private final Map<String, String> values;
    private final MiniMessage mini = MiniMessage.miniMessage();

    private Messages(Map<String, String> values) {
        this.values = values;
    }

    public static Messages load(JavaPlugin plugin) {
        Map<String, String> values = new LinkedHashMap<>(DEFAULTS);
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.isFile()) {
            plugin.saveResource("messages.yml", false);
        }
        if (file.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(true)) {
                if (yaml.isConfigurationSection(key)) {
                    continue;
                }
                String value = yaml.getString(key);
                if (value != null) {
                    values.put(key, value);
                }
            }
        }
        return new Messages(Map.copyOf(values));
    }

    public Component get(String key, String... placeholders) {
        String raw = values.getOrDefault(key, key);
        if (placeholders.length == 0) {
            return mini.deserialize(raw);
        }
        TagResolver.Builder builder = TagResolver.builder();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String name = placeholders[i].toLowerCase(java.util.Locale.ROOT);
            builder.resolver(Placeholder.unparsed(name, placeholders[i + 1]));
        }
        try {
            return mini.deserialize(raw, builder.build());
        } catch (RuntimeException exception) {
            // 模板写错（例如大写占位符）不应该让整条命令失败。
            return Component.text(raw);
        }
    }

    public String raw(String key) {
        return values.getOrDefault(key, key);
    }
}
