package com.customnpcs.craftingview.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.customnpcs.craftingview.CraftingViewMod;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 1.7.10 has no MinecraftServer.addScheduledTask, and SimpleNetworkWrapper handlers run on the
 * network thread. Queue container changes and execute them at the end of a server tick.
 */
public final class ServerTaskQueue {

    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();

    public ServerTaskQueue() {}

    public static void execute(Runnable task) {
        if (task != null) {
            TASKS.add(task);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Runnable task;
        while ((task = TASKS.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                CraftingViewMod.LOG.error("Queued crafting fill failed", t);
            }
        }
    }
}
