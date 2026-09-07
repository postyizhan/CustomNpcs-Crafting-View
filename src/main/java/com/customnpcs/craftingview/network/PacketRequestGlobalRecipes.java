package com.customnpcs.craftingview.network;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 客户端 → 服务端：请求下发全局（3x3 工作台）配方快照。
 *
 * <p>
 * 客户端在打开暮色拆解台时主动请求，服务端回一个 {@link PacketSyncGlobalRecipes}。采用"客户端拉取"
 * 而非"服务端推送"，是因为服务端没有廉价可靠的"某玩家刚打开暮色界面"事件 —— Forge 的
 * {@code PlayerOpenContainerEvent} 每 tick 触发，用它做推送等于轮询。客户端 GUI 打开是明确的
 * 一次性时机，由它发起最省事也最准。
 *
 * <p>
 * 空载荷：请求本身即全部信息，玩家身份由 {@link MessageContext} 提供。
 */
public class PacketRequestGlobalRecipes implements IMessage {

    public PacketRequestGlobalRecipes() {}

    @Override
    public void toBytes(ByteBuf buf) {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketRequestGlobalRecipes, IMessage> {

        @Override
        public IMessage onMessage(PacketRequestGlobalRecipes msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;

            PacketSyncGlobalRecipes response = PacketSyncGlobalRecipes.ofCurrentRecipes();
            if (response == null) return null; // NBT 同步不可用，客户端自行降级

            PacketHandler.CHANNEL.sendTo(response, player);
            return null;
        }
    }
}
