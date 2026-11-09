package dev.junxiex.mikudyview.listener;

import dev.junxiex.mikudyview.engine.DistanceEngine;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

/** 玩家与世界生命周期，只做登记与清理，不做距离计算。 */
public final class PlayerListener implements Listener {

    private final Plugin plugin;
    private final DistanceEngine engine;

    public PlayerListener(Plugin plugin, DistanceEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        engine.onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        engine.onQuit(event.getPlayer());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        engine.onWorldChange(event.getPlayer());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        // Folia: 事件在区域线程触发，而引擎状态归全局线程所有，交回全局线程再改。
        String worldName = event.getWorld().getName();
        Bukkit.getServer().getGlobalRegionScheduler().execute(plugin, () -> engine.forgetWorld(worldName));
    }
}
