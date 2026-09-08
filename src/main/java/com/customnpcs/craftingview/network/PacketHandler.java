package com.customnpcs.craftingview.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class PacketHandler {

    private static final String CHANNEL_NAME = "cnpcs_craftview";
    private static final int MAX_CHANNEL_NAME_LENGTH = 20;

    public static SimpleNetworkWrapper CHANNEL;

    public static void init() {
        if (CHANNEL_NAME.length() > MAX_CHANNEL_NAME_LENGTH) {
            throw new IllegalStateException("Network channel name exceeds the Minecraft 1.7.10 limit: " + CHANNEL_NAME);
        }
        CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);
        CHANNEL.registerMessage(PacketFillCraftingGrid.Handler.class, PacketFillCraftingGrid.class, 0, Side.SERVER);
        // discriminator 追加在末尾，保持既有编号不变以兼容旧版本客户端
        CHANNEL.registerMessage(PacketSyncGlobalRecipes.Handler.class, PacketSyncGlobalRecipes.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(PacketFillTwilightGrid.Handler.class, PacketFillTwilightGrid.class, 2, Side.SERVER);
        CHANNEL.registerMessage(
            PacketRequestGlobalRecipes.Handler.class,
            PacketRequestGlobalRecipes.class,
            3,
            Side.SERVER);
    }
}
