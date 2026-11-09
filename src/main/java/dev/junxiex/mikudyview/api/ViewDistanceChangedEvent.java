package dev.junxiex.mikudyview.api;

import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 某个世界的模拟距离或视距被本插件写入后触发。
 * 在全局区域线程上同步触发，监听器不应在此做重活。
 */
public final class ViewDistanceChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final int oldSimulationDistance;
    private final int newSimulationDistance;
    private final int oldViewDistance;
    private final int newViewDistance;
    private final String reason;

    public ViewDistanceChangedEvent(World world,
                                    int oldSimulationDistance, int newSimulationDistance,
                                    int oldViewDistance, int newViewDistance,
                                    String reason) {
        this.world = world;
        this.oldSimulationDistance = oldSimulationDistance;
        this.newSimulationDistance = newSimulationDistance;
        this.oldViewDistance = oldViewDistance;
        this.newViewDistance = newViewDistance;
        this.reason = reason;
    }

    public World getWorld() {
        return world;
    }

    public int getOldSimulationDistance() {
        return oldSimulationDistance;
    }

    public int getNewSimulationDistance() {
        return newSimulationDistance;
    }

    public int getOldViewDistance() {
        return oldViewDistance;
    }

    public int getNewViewDistance() {
        return newViewDistance;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
