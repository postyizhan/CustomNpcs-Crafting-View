package com.customnpcs.craftingview.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import com.customnpcs.craftingview.CraftingViewMod;
import com.customnpcs.craftingview.compat.RecipeAccess;
import com.customnpcs.craftingview.compat.RecipeView;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketFillCraftingGrid implements IMessage {

    private int recipeId;

    public PacketFillCraftingGrid() {}

    public PacketFillCraftingGrid(int recipeId) {
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

    public static class Handler implements IMessageHandler<PacketFillCraftingGrid, IMessage> {

        @Override
        public IMessage onMessage(final PacketFillCraftingGrid msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            final int requestedRecipeId = msg.recipeId;
            NetworkTaskQueue.enqueueServer(new Runnable() {
                @Override
                public void run() {
                    process(player, requestedRecipeId);
                }
            });
            return null;
        }

        private void process(EntityPlayerMP player, int recipeId) {
            Container openContainer = player.openContainer;
            if (!RecipeAccess.isCarpentryContainer(openContainer)) return;

            RecipeView recipe = RecipeAccess.getRecipeById(recipeId);
            if (recipe == null) {
                CraftingViewMod.LOG.warn("Recipe not found: id={}", recipeId);
                return;
            }

            net.minecraft.inventory.IInventory matrix = RecipeAccess.getCraftMatrix(openContainer);
            if (matrix == null) return;

            // Map recipeWidth x recipeHeight items into the 4x4 crafting grid
            int rw = recipe.recipeWidth;
            int rh = recipe.recipeHeight;
            for (int row = 0; row < rh && row < 4; row++) {
                for (int col = 0; col < rw && col < 4; col++) {
                    ItemStack required = recipe.getCraftingItem(row * rw + col);
                    if (required == null) continue;

                    int gridSlot = row * 4 + col;

                    // Skip if slot already has a matching item
                    if (gridSlot >= matrix.getSizeInventory()) continue;
                    ItemStack existing = matrix.getStackInSlot(gridSlot);
                    if (existing != null && matches(existing, required, recipe.ignoreDamage, recipe.ignoreNBT))
                        continue;

                    // Return existing item in slot back to player inventory first
                    if (existing != null) {
                        returnToInventory(player, existing);
                        matrix.setInventorySlotContents(gridSlot, null);
                    }

                    ItemStack found = findAndTake(player, required, recipe.ignoreDamage, recipe.ignoreNBT);
                    if (found != null) {
                        matrix.setInventorySlotContents(gridSlot, found);
                    }
                }
            }

            RecipeAccess.notifyContainer(openContainer, matrix);
        }

        private void returnToInventory(EntityPlayerMP player, ItemStack stack) {
            if (!player.inventory.addItemStackToInventory(stack.copy())) {
                player.dropPlayerItemWithRandomChoice(stack, false);
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
