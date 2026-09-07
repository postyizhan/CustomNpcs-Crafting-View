package com.customnpcs.craftingview.network;

import com.customnpcs.craftingview.CraftingViewMod;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class PacketHandler {

    public static SimpleNetworkWrapper CHANNEL;

    public static void init() {
        CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(CraftingViewMod.MODID);
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
