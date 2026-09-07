package com.customnpcs.craftingview.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Runs packet work on the Minecraft thread instead of a Netty IO thread. */
public final class NetworkTaskQueue {

    public static final NetworkTaskQueue INSTANCE = new NetworkTaskQueue();

    private final Queue<Runnable> serverTasks = new ConcurrentLinkedQueue<Runnable>();
    private final Queue<Runnable> clientTasks = new ConcurrentLinkedQueue<Runnable>();

    private NetworkTaskQueue() {}

    public static void enqueueServer(Runnable task) {
        if (task != null) INSTANCE.serverTasks.add(task);
    }

    public static void enqueueClient(Runnable task) {
        if (task != null) INSTANCE.clientTasks.add(task);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) drain(serverTasks);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) drain(clientTasks);
    }

    private static void drain(Queue<Runnable> tasks) {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable ignored) {
                // Keep packet failures from terminating the tick loop.
            }
        }
    }
}
