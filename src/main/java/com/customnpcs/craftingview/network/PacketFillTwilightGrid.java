package com.customnpcs.craftingview.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.customnpcs.craftingview.CraftingViewMod;
import com.customnpcs.craftingview.compat.RecipeAccess;
import com.customnpcs.craftingview.compat.RecipeView;
import com.customnpcs.craftingview.compat.TwilightAccess;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * 客户端 → 服务端：把选中的全局（3x3）配方填入暮色森林拆解台的组装格。
 *
 * <p>
 * 与 {@link PacketFillCraftingGrid}（木工台 4x4）分开：暮色拆解台的目标容器是
 * {@code twilightforest.inventory.ContainerTFUncrafting}，组装格为 3x3 的 {@code assemblyMatrix}，
 * 槽位映射与格子尺寸都不同。暮色为可选依赖，故容器访问全部经 {@link TwilightAccess} 反射，
 * 未装暮色时本包不会被触发，也不会导致类加载失败。
 */
public class PacketFillTwilightGrid implements IMessage {

    private static final int GRID_WIDTH = 3;

    private int recipeId;

    public PacketFillTwilightGrid() {}

    public PacketFillTwilightGrid(int recipeId) {
        this.recipeId = recipeId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(recipeId);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        recipeId = buf.readInt();
    }

    public static class Handler implements IMessageHandler<PacketFillTwilightGrid, IMessage> {

        @Override
        public IMessage onMessage(PacketFillTwilightGrid msg, MessageContext ctx) {
            // SimpleNetworkWrapper handlers on Side.SERVER run on the main server thread
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            Container openContainer = player.openContainer;
            if (!TwilightAccess.isUncraftingContainer(openContainer)) return null;

            IInventory assembly = TwilightAccess.getAssemblyMatrix(openContainer);
            if (assembly == null) return null;

            RecipeView recipe = RecipeAccess.getGlobalRecipeById(msg.recipeId);
            if (recipe == null) {
                CraftingViewMod.LOG.warn("Global recipe not found: id={}", msg.recipeId);
                return null;
            }

            fill(player, assembly, recipe);

            TwilightAccess.notifyContainer(openContainer, assembly);
            return null;
        }

        /**
         * 清空组装格后按配方重填。
         *
         * <p>
         * 与木工台的就地增量填充不同，这里先整格清空再填：拆解台的组装格参与"重组"判定，残留
         * 物品会让配方无法匹配，且 3x3 格子少、清空代价低。
         */
        private void fill(EntityPlayerMP player, IInventory assembly, RecipeView recipe) {
            int slots = Math.min(assembly.getSizeInventory(), GRID_WIDTH * GRID_WIDTH);

            for (int i = 0; i < slots; i++) {
                ItemStack existing = assembly.getStackInSlot(i);
                if (existing != null) {
                    returnToInventory(player, existing);
                    assembly.setInventorySlotContents(i, null);
                }
            }

            int rw = recipe.recipeWidth;
            int rh = recipe.recipeHeight;
            for (int row = 0; row < rh && row < GRID_WIDTH; row++) {
                for (int col = 0; col < rw && col < GRID_WIDTH; col++) {
                    ItemStack required = recipe.getCraftingItem(row * rw + col);
                    if (required == null) continue;

                    ItemStack found = findAndTake(player, required, recipe.ignoreDamage, recipe.ignoreNBT);
                    if (found != null) {
                        assembly.setInventorySlotContents(row * GRID_WIDTH + col, found);
                    }
                }
            }
        }

        private void returnToInventory(EntityPlayerMP player, ItemStack stack) {
            ItemStack remaining = stack.copy();
            if (!player.inventory.addItemStackToInventory(remaining) && remaining.stackSize > 0) {
                player.dropPlayerItemWithRandomChoice(remaining, false);
            }
        }

        private ItemStack findAndTake(EntityPlayerMP player, ItemStack required, boolean ignoreDamage,
            boolean ignoreNBT) {
            for (int invSlot = 0; invSlot < player.inventory.mainInventory.length; invSlot++) {
                ItemStack stack = player.inventory.mainInventory[invSlot];
                if (stack == null) continue;
                if (!matches(stack, required, ignoreDamage, ignoreNBT)) continue;

                ItemStack taken = stack.copy();
                taken.stackSize = 1;
                if (stack.stackSize <= 1) {
                    player.inventory.mainInventory[invSlot] = null;
                } else {
                    stack.stackSize--;
                }
                return taken;
            }
            return null;
        }

        private boolean matches(ItemStack stack, ItemStack required, boolean ignoreDamage, boolean ignoreNBT) {
            if (stack.getItem() != required.getItem()) return false;
            if (!ignoreDamage && stack.getItemDamage() != required.getItemDamage()) return false;
            if (!ignoreNBT && required.hasTagCompound()) {
                if (!stack.hasTagCompound()) return false;
                if (!stack.getTagCompound()
                    .equals(required.getTagCompound())) return false;
            }
            return true;
        }
    }
}
