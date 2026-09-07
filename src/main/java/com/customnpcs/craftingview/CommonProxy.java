package com.customnpcs.craftingview;

import net.minecraft.nbt.NBTTagCompound;

import com.customnpcs.craftingview.network.PacketHandler;

import cpw.mods.fml.common.event.FMLInitializationEvent;

public class CommonProxy {

    public void init(FMLInitializationEvent event) {
        PacketHandler.init();
    }

    /**
     * 处理服务端下发的全局配方同步。服务端侧无操作，客户端由 {@link ClientProxy} 覆写。
     *
     * <p>
     * 经 proxy 分派而非在包处理器里直接调用客户端类，避免服务端类路径触及 {@code client} 包。
     */
    public void handleGlobalRecipeSync(NBTTagCompound payload) {}
}
