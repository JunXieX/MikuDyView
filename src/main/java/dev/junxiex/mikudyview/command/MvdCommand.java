package dev.junxiex.mikudyview.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.junxiex.mikudyview.MikuDyViewPlugin;
import dev.junxiex.mikudyview.config.Settings;
import dev.junxiex.mikudyview.engine.DistanceEngine;
import dev.junxiex.mikudyview.engine.HistoryRing;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

/** /mvd 命令树。所有输出走 MiniMessage，权限在注册时逐节点声明。 */
public final class MvdCommand {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final MikuDyViewPlugin plugin;
    private final DistanceEngine engine;

    public MvdCommand(MikuDyViewPlugin plugin, DistanceEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("mvd")
                .requires(source -> source.getSender().hasPermission("mikudyview.command"))
                .then(Commands.literal("status")
                        .requires(source -> source.getSender().hasPermission("mikudyview.command.status"))
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("history")
                        .requires(source -> source.getSender().hasPermission("mikudyview.command.history"))
                        .executes(context -> history(context.getSource(), 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                                .executes(context -> history(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("simulate")
                        .requires(source -> source.getSender().hasPermission("mikudyview.command.simulate"))
                        .executes(context -> simulate(context.getSource(), null))
                        .then(Commands.argument("world", ArgumentTypes.world())
                                .executes(context -> simulate(context.getSource(),
                                        context.getArgument("world", World.class)))))
                .then(Commands.literal("set")
                        .requires(source -> source.getSender().hasPermission("mikudyview.command.set"))
                        .then(Commands.literal("sim")
                                .then(Commands.argument("value", IntegerArgumentType.integer(2, 32))
                                        .executes(context -> set(context, "sim", null))
                                        .then(Commands.argument("world", ArgumentTypes.world())
                                                .executes(context -> set(context, "sim",
                                                        context.getArgument("world", World.class))))))
                        .then(Commands.literal("view")
                                .then(Commands.argument("value", IntegerArgumentType.integer(2, 32))
                                        .executes(context -> set(context, "view", null))
                                        .then(Commands.argument("world", ArgumentTypes.world())
                                                .executes(context -> set(context, "view",
                                                        context.getArgument("world", World.class)))))))
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission("mikudyview.command.reload"))
                        .executes(context -> reload(context.getSource())))
                .then(Commands.literal("enable")
                        .requires(source -> source.getSender().hasPermission("mikudyview.command.toggle"))
                        .executes(context -> toggle(context.getSource(), true)))
                .then(Commands.literal("disable")
                        .requires(source -> source.getSender().hasPermission("mikudyview.command.toggle"))
                        .executes(context -> toggle(context.getSource(), false)))
                .build();
    }

    private int status(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        Settings settings = engine.settings();
        sender.sendMessage(plugin.messages().get("status.header"));
        sender.sendMessage(plugin.messages().get("status.summary",
                "mode", settings.mode(),
                "mspt", String.format(Locale.ROOT, "%.2f", engine.currentMspt()),
                "mem", String.format(Locale.ROOT, "%.1f", engine.freeMemoryPercent()),
                "enabled", String.valueOf(settings.enabled())));

        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            StringBuilder flags = new StringBuilder();
            if (settings.world(worldName).exclude()) {
                flags.append(plugin.messages().raw("status.flags.excluded"));
            }
            if (engine.isContested(worldName)) {
                flags.append(plugin.messages().raw("status.flags.contested"));
            }
            if (engine.hasOverride(worldName)) {
                flags.append(plugin.messages().raw("status.flags.override"));
            }
            sender.sendMessage(plugin.messages().get("status.world",
                    "world", worldName,
                    "sim", String.valueOf(world.getSimulationDistance()),
                    "view", String.valueOf(world.getViewDistance()),
                    "players", String.valueOf(engine.players().counts().getOrDefault(worldName, 0)),
                    "next", String.valueOf(engine.nextCheckIn(worldName) / 20),
                    "flags", flags.toString()));
        }
        return 1;
    }

    private int history(CommandSourceStack source, int count) {
        CommandSender sender = source.getSender();
        List<HistoryRing.Entry> entries = engine.history().recent(count);
        if (entries.isEmpty()) {
            sender.sendMessage(plugin.messages().get("history.empty"));
            return 1;
        }
        sender.sendMessage(plugin.messages().get("history.header", "count", String.valueOf(entries.size())));
        for (HistoryRing.Entry entry : entries) {
            sender.sendMessage(plugin.messages().get("history.entry",
                    "time", TIME.format(Instant.ofEpochMilli(entry.time())),
                    "world", entry.world(),
                    "oldsim", String.valueOf(entry.oldSim()),
                    "newsim", String.valueOf(entry.newSim()),
                    "oldview", String.valueOf(entry.oldView()),
                    "newview", String.valueOf(entry.newView())));
            sender.sendMessage(plugin.messages().get("history.reason", "reason", entry.reason()));
        }
        return 1;
    }

    private int simulate(CommandSourceStack source, World world) {
        CommandSender sender = source.getSender();
        List<DistanceEngine.PlanRow> rows = engine.simulate(world == null ? null : world.getName());
        if (rows.isEmpty()) {
            sender.sendMessage(plugin.messages().get("simulate.empty"));
            return 1;
        }
        sender.sendMessage(plugin.messages().get("simulate.header",
                "world", world == null ? "全部世界" : world.getName()));
        for (DistanceEngine.PlanRow row : rows) {
            sender.sendMessage(plugin.messages().get("simulate.entry",
                    "world", row.world(),
                    "sim", String.valueOf(row.currentSim()),
                    "view", String.valueOf(row.currentView()),
                    "targetsim", String.valueOf(row.targetSim()),
                    "targetview", String.valueOf(row.targetView()),
                    "verdict", row.wouldApply() ? "本轮可执行" : "等待确认"));
            sender.sendMessage(plugin.messages().get("simulate.reason", "reason", row.reason()));
        }
        return 1;
    }

    private int set(CommandContext<CommandSourceStack> context, String type, World world) {
        CommandSender sender = context.getSource().getSender();
        int value = IntegerArgumentType.getInteger(context, "value");
        List<World> targets = world != null
                ? List.of(world)
                : Bukkit.getWorlds().stream()
                        .filter(candidate -> !engine.settings().world(candidate.getName()).exclude())
                        .toList();
        for (World target : targets) {
            engine.setManual(target, type, value);
            sender.sendMessage(plugin.messages().get("set.ok",
                    "world", target.getName(), "type", type, "value", String.valueOf(value)));
        }
        return 1;
    }

    private int reload(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        plugin.reloadAll();
        sender.sendMessage(plugin.messages().get("reload.ok"));
        return 1;
    }

    private int toggle(CommandSourceStack source, boolean enabled) {
        CommandSender sender = source.getSender();
        plugin.setPluginEnabled(enabled);
        sender.sendMessage(plugin.messages().get(enabled ? "toggle.on" : "toggle.off"));
        return 1;
    }
}
