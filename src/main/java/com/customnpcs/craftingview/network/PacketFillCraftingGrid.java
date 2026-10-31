package com.customnpcs.craftingview.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

import com.customnpcs.craftingview.CraftingViewMod;
import com.customnpcs.craftingview.compat.RecipeAccess;
import com.customnpcs.craftingview.compat.RecipeView;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import noppes.npcs.containers.ContainerCarpentryBench;

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
            // SimpleNetworkWrapper handlers on Side.SERVER run on the main server thread
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            Container openContainer = player.openContainer;
            if (openContainer == null) return null;

            // The client can only fill the container it currently has open.
            if (openContainer instanceof ContainerWorkbench && !openContainer.canInteractWith(player)) {
                return null;
            }

            RecipeView recipe = RecipeAccess.getRecipeById(msg.recipeId);
            if (recipe == null) {
                CraftingViewMod.LOG.warn("Recipe not found: id={}", msg.recipeId);
                return null;
            }

            if (openContainer instanceof ContainerCarpentryBench) {
                ContainerCarpentryBench container = (ContainerCarpentryBench) openContainer;
                fill(player, container, container.craftMatrix, 4, recipe);
                return null;
            }
            if (openContainer instanceof ContainerWorkbench) {
                ContainerWorkbench container = (ContainerWorkbench) openContainer;
                fill(player, container, container.craftMatrix, 3, recipe);
                return null;
            }
            return null;
        }

        private void fill(EntityPlayerMP player, Container container, InventoryCrafting matrix, int columns,
            RecipeView recipe) {
            int rw = recipe.recipeWidth;
            int rh = recipe.recipeHeight;
            if (rw <= 0 || rh <= 0 || rw > columns || rh > columns) return;

            // Clear the whole grid; leftovers go back to the player before refilling.
            for (int slot = 0; slot < matrix.getSizeInventory(); slot++) {
                ItemStack existing = matrix.getStackInSlot(slot);
                if (existing != null) {
                    returnToInventory(player, existing);
                    matrix.setInventorySlotContents(slot, null);
                }
            }

            int crafts = maxCrafts(player, recipe, rw, rh);
            if (crafts <= 0) {
                container.onCraftMatrixChanged(matrix);
                container.detectAndSendChanges();
                return;
            }

            for (int row = 0; row < rh; row++) {
                for (int col = 0; col < rw; col++) {
                    ItemStack required = recipe.getCraftingItem(row * rw + col);
                    if (required == null) continue;

                    int amount = Math.max(1, required.stackSize) * crafts;
                    ItemStack found = findAndTake(player, required, amount, recipe.ignoreDamage, recipe.ignoreNBT);
                    if (found != null) {
                        matrix.setInventorySlotContents(row * columns + col, found);
                    }
                }
            }

            container.onCraftMatrixChanged(matrix);
            container.detectAndSendChanges();
        }

        /**
         * How many complete recipe sets fit both the inventory and the grid stack limits. Stacks are only
         * placed; the player still takes the result manually.
         */
        private int maxCrafts(EntityPlayerMP player, RecipeView recipe, int rw, int rh) {
            int max = 64;
            for (int row = 0; row < rh; row++) {
                for (int col = 0; col < rw; col++) {
                    ItemStack required = recipe.getCraftingItem(row * rw + col);
                    if (required != null) {
                        int perCraft = Math.max(1, required.stackSize);
                        int stackLimit = Math.max(1, required.getMaxStackSize());
                        max = Math.min(max, stackLimit / perCraft);
                    }
                }
            }
            for (int crafts = max; crafts > 0; crafts--) {
                if (canSupply(player, recipe, rw, rh, crafts)) {
                    return crafts;
                }
            }
            return 0;
        }

        private boolean canSupply(EntityPlayerMP player, RecipeView recipe, int rw, int rh, int crafts) {
            ItemStack[] inventory = player.inventory.mainInventory;
            int[] available = new int[inventory.length];
            for (int i = 0; i < inventory.length; i++) {
                available[i] = inventory[i] == null ? 0 : inventory[i].stackSize;
            }

            for (int row = 0; row < rh; row++) {
                for (int col = 0; col < rw; col++) {
                    ItemStack required = recipe.getCraftingItem(row * rw + col);
                    if (required == null) continue;

                    int need = Math.max(1, required.stackSize) * crafts;
                    for (int i = 0; i < inventory.length && need > 0; i++) {
                        if (available[i] > 0 && matches(inventory[i], required, recipe.ignoreDamage,
                            recipe.ignoreNBT)) {
                            int take = Math.min(need, available[i]);
                            need -= take;
                            available[i] -= take;
                        }
                    }
                    if (need > 0) return false;
                }
            }
            return true;
        }

        private void returnToInventory(EntityPlayerMP player, ItemStack stack) {
            if (!player.inventory.addItemStackToInventory(stack.copy())) {
                player.dropPlayerItemWithRandomChoice(stack, false);
            }
        }

        private ItemStack findAndTake(EntityPlayerMP player, ItemStack required, int amount, boolean ignoreDamage,
            boolean ignoreNBT) {
            ItemStack result = required.copy();
            result.stackSize = 0;

            for (int invSlot = 0; invSlot < player.inventory.mainInventory.length && amount > 0; invSlot++) {
                ItemStack stack = player.inventory.mainInventory[invSlot];
                if (stack == null || !matches(stack, required, ignoreDamage, ignoreNBT)) continue;

                int take = Math.min(amount, stack.stackSize);
                result.stackSize += take;
                amount -= take;
                stack.stackSize -= take;
                if (stack.stackSize <= 0) {
                    player.inventory.mainInventory[invSlot] = null;
                }
            }

            return amount > 0 ? null : result;
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
