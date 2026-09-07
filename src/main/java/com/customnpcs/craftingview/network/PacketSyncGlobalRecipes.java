package com.customnpcs.craftingview.network;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.customnpcs.craftingview.CraftingViewMod;
import com.customnpcs.craftingview.compat.RecipeAccess;
import com.customnpcs.craftingview.compat.RecipeView;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 服务端 → 客户端：同步宿主的全局（3x3 工作台）配方集合。
 *
 * <p>
 * 暮色拆解台面板展示的是宿主 {@code RecipeController.globalRecipes}。该集合在专用服务器上仅存在于
 * 服务端，客户端本地读不到，故需由服务端序列化后下发。单机/局域网主机两端同进程，走同一通路以保持
 * 行为一致。
 *
 * <p>
 * 配方以宿主自身的 NBT 格式透传（{@link RecipeAccess#writeRecipeNBT}），本 mod 不解析其内部字段，
 * 从而无需关心两宿主 RecipeCarpentry 的字段差异。
 */
public class PacketSyncGlobalRecipes implements IMessage {

    private static final String TAG_RECIPES = "Recipes";

    private NBTTagCompound payload;

    public PacketSyncGlobalRecipes() {}

    private PacketSyncGlobalRecipes(NBTTagCompound payload) {
        this.payload = payload;
    }

    /** 从宿主当前全局配方构建同步包。NBT 序列化不可用时返回 null（调用方应跳过发送）。 */
    public static PacketSyncGlobalRecipes ofCurrentRecipes() {
        if (!RecipeAccess.isNbtSyncAvailable()) return null;

        List<RecipeView> recipes = RecipeAccess.getAllGlobalRecipes();
        NBTTagList list = new NBTTagList();
        for (RecipeView recipe : recipes) {
            NBTTagCompound tag = RecipeAccess.writeRecipeNBT(recipe);
            if (tag != null) list.appendTag(tag);
        }

        NBTTagCompound root = new NBTTagCompound();
        root.setTag(TAG_RECIPES, list);
        return new PacketSyncGlobalRecipes(root);
    }

    /** 从同步载荷中取出配方 NBT 列表。 */
    public static NBTTagList extractRecipeList(NBTTagCompound payload) {
        if (payload == null) return new NBTTagList();
        return payload.getTagList(TAG_RECIPES, 10); // 10 = TAG_Compound
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, payload);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        payload = ByteBufUtils.readTag(buf);
    }

    public NBTTagCompound getPayload() {
        return payload;
    }

    public static class Handler implements IMessageHandler<PacketSyncGlobalRecipes, IMessage> {

        @Override
        public IMessage onMessage(PacketSyncGlobalRecipes msg, MessageContext ctx) {
            if (msg.payload == null) return null;
            // 客户端解析交由 proxy 分派，避免服务端类路径触及 client 包
            CraftingViewMod.proxy.handleGlobalRecipeSync(msg.payload);
            return null;
        }
    }
}
