package dev.junxiex.mikudyview.listener;

import dev.junxiex.mikudyview.engine.DistanceEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/** 玩家与世界生命周期，只做登记与清理，不做距离计算。 */
public final class PlayerListener implements Listener {

    private final DistanceEngine engine;

    public PlayerListener(DistanceEngine engine) {
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
        engine.forgetWorld(event.getWorld().getName());
    }
}
