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

/** Server-to-client snapshot of the host's global 3x3 recipes. */
public class PacketSyncGlobalRecipes implements IMessage {

    private static final String TAG_RECIPES = "Recipes";

    private NBTTagCompound payload;

    public PacketSyncGlobalRecipes() {}

    private PacketSyncGlobalRecipes(NBTTagCompound payload) {
        this.payload = payload;
    }

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

    public static NBTTagList extractRecipeList(NBTTagCompound payload) {
        if (payload == null) return new NBTTagList();
        return payload.getTagList(TAG_RECIPES, 10);
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
            final NBTTagCompound payload = msg.payload;
            NetworkTaskQueue.enqueueClient(new Runnable() {
                @Override
                public void run() {
                    CraftingViewMod.proxy.handleGlobalRecipeSync(payload);
                }
            });
            return null;
        }
    }
}
