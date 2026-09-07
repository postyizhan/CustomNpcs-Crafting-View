package com.customnpcs.craftingview.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Client request for the server's global 3x3 recipe snapshot. */
public class PacketRequestGlobalRecipes implements IMessage {

    public PacketRequestGlobalRecipes() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketRequestGlobalRecipes, IMessage> {

        @Override
        public IMessage onMessage(PacketRequestGlobalRecipes msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            NetworkTaskQueue.enqueueServer(new Runnable() {
                @Override
                public void run() {
                    PacketSyncGlobalRecipes response = PacketSyncGlobalRecipes.ofCurrentRecipes();
                    if (response != null && player.playerNetServerHandler != null) {
                        PacketHandler.CHANNEL.sendTo(response, player);
                    }
                }
            });
            return null;
        }
    }
}
