package dev.junxiex.mikudyview;

import dev.junxiex.mikudyview.command.MvdCommand;
import dev.junxiex.mikudyview.config.Messages;
import dev.junxiex.mikudyview.config.Settings;
import dev.junxiex.mikudyview.config.SettingsLoader;
import dev.junxiex.mikudyview.engine.DistanceEngine;
import dev.junxiex.mikudyview.listener.PlayerListener;
import io.papermc.paper.plugin.PermissionManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

/** 插件入口：只负责装配与生命周期，业务在 engine 与 command 里。 */
public final class MikuDyViewPlugin extends JavaPlugin {

    private static final List<String> PERMISSIONS = List.of(
            "mikudyview.command",
            "mikudyview.command.status",
            "mikudyview.command.history",
            "mikudyview.command.simulate",
            "mikudyview.command.set",
            "mikudyview.command.reload",
            "mikudyview.command.toggle"
    );

    private Settings settings;
    private Messages messages;
    private DistanceEngine engine;

    @Override
    public void onLoad() {
        // Folia 上 COMMANDS 生命周期事件在 onEnable 之前触发，注册必须提前到这里。
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(new MvdCommand(this).build(), "MikuDyView 主命令", List.of("mikudyview"));
            getLogger().info("已注册命令 /mvd。");
        });
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = Messages.load(this);
        this.settings = SettingsLoader.load(getConfig(), getLogger());

        registerPermissions();

        this.engine = new DistanceEngine(this, settings, messages);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, engine), this);

        engine.start();
        getLogger().info("MikuDyView " + getPluginMeta().getVersion()
                + " 已启用，模式 " + settings.mode() + "，检查间隔 " + settings.checkInterval() + " tick。");
    }

    @Override
    public void onDisable() {
        if (engine != null) {
            engine.stop();
        }
        unregisterPermissions();
    }

    /** 重读 config.yml 与 messages.yml，并把新配置交给引擎。 */
    public void reloadAll() {
        reloadConfig();
        this.settings = SettingsLoader.load(getConfig(), getLogger());
        this.messages = Messages.load(this);
        this.engine.reload(settings);
    }

    /** 运行期开关，同时写回 config.yml，重启后仍然有效。 */
    public void setPluginEnabled(boolean enabled) {
        getConfig().set("enabled", enabled);
        saveConfig();
        reloadAll();
    }

    public Messages messages() {
        return messages;
    }

    public DistanceEngine engine() {
        return engine;
    }

    public Settings settings() {
        return settings;
    }

    private void registerPermissions() {
        PermissionManager manager = getServer().getPluginManager();
        for (String node : PERMISSIONS) {
            manager.addPermission(new Permission(node, "MikuDyView", PermissionDefault.OP));
        }
    }

    private void unregisterPermissions() {
        PermissionManager manager = getServer().getPluginManager();
        for (String node : PERMISSIONS) {
            manager.removePermission(node);
        }
    }
}
