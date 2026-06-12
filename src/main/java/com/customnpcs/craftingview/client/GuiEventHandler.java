package com.customnpcs.craftingview.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.customnpcs.craftingview.compat.RecipeAccess;
import com.customnpcs.craftingview.compat.RecipeView;
import com.customnpcs.craftingview.network.PacketFillCraftingGrid;
import com.customnpcs.craftingview.network.PacketHandler;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import noppes.npcs.client.gui.player.GuiNpcCarpentryBench;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import noppes.npcs.containers.ContainerCarpentryBench;

@SideOnly(Side.CLIENT)
public class GuiEventHandler {

    private RecipePanel activePanel = null;
    private boolean lastLeftDown = false;
    private int pendingScroll = 0;

    // 在 RenderTickEvent.START 最高优先级捕获滚轮值，早于 MouseTweaks
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderTickStart(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (activePanel == null) return;
        int dwheel = Mouse.getDWheel();
        if (dwheel != 0) {
            pendingScroll += dwheel;
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui instanceof GuiNpcCarpentryBench) {
            GuiContainer guiContainer = (GuiContainer) event.gui;
            if (RecipeAccess.isAvailable() && guiContainer.inventorySlots instanceof ContainerCarpentryBench) {
                ContainerCarpentryBench container = (ContainerCarpentryBench) guiContainer.inventorySlots;
                boolean isAnvil = container.getMetadata() >= 4;
                activePanel = new RecipePanel(isAnvil);
                lastLeftDown = false;
            } else {
                activePanel = null;
            }
        } else {
            activePanel = null;
        }
    }

    @SubscribeEvent
    public void onGuiDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (activePanel == null) return;
        if (!(event.gui instanceof GuiContainerNPCInterface)) return;

        GuiContainerNPCInterface gui = (GuiContainerNPCInterface) event.gui;

        int mx = event.mouseX;
        int my = event.mouseY;

        RecipePanelRenderer.render(event.gui, activePanel, mx, my);

        handleMouseInput(gui, mx, my);
        handleKeyInput();
    }

    private void handleMouseInput(GuiContainerNPCInterface gui, int mx, int my) {
        int guiLeft = gui.guiLeft;
        int guiTop = gui.guiTop;

        boolean leftDown = Mouse.isButtonDown(0);
        boolean clicked = leftDown && !lastLeftDown;
        lastLeftDown = leftDown;

        // Scroll wheel — value captured in onRenderTickStart before MouseTweaks consumes it
        if (pendingScroll != 0) {
            if (RecipePanelRenderer.isPanelHit(activePanel, guiLeft, guiTop, mx, my)) {
                activePanel.scroll(pendingScroll < 0 ? 1 : -1);
            }
            pendingScroll = 0;
            return;
        }

        if (!clicked) return;

        if (!RecipePanelRenderer.isPanelHit(activePanel, guiLeft, guiTop, mx, my)) {
            activePanel.searchField.setFocused(false);
            return;
        }

        // Collapse toggle
        if (RecipePanelRenderer.isCollapseButtonHit(activePanel, guiLeft, guiTop, mx, my)) {
            playClickSound();
            activePanel.toggleCollapsed();
            return;
        }

        if (activePanel.isCollapsed()) return;

        // 选中合成格浮层盖在列表之上：点在浮层内只消费点击，不穿透到被遮住的行。
        if (RecipePanelRenderer.isOverlayHit(activePanel, guiLeft, guiTop, mx, my)) {
            return;
        }

        // Search field click
        activePanel.searchField.mouseClicked(mx, my, 0);

        // Category tab
        int catIdx = RecipePanelRenderer.getCategoryTabHit(activePanel, guiLeft, guiTop, mx, my);
        if (catIdx >= 0) {
            playClickSound();
            activePanel.setCategory(catIdx);
            return;
        }

        // Recipe row / "+" button
        int rowIdx = RecipePanelRenderer.getRecipeRowHit(activePanel, guiLeft, guiTop, mx, my);
        if (rowIdx >= 0) {
            List<RecipeView> visible = activePanel.getVisible();
            if (rowIdx < visible.size()) {
                RecipeView recipe = visible.get(rowIdx);
                if (RecipePanelRenderer.isPlusButtonHit(activePanel, guiLeft, guiTop, mx, my, rowIdx)) {
                    playClickSound();
                    PacketHandler.CHANNEL.sendToServer(new PacketFillCraftingGrid(recipe.id));
                } else if (recipe.equals(activePanel.getSelectedRecipe())) {
                    playClickSound();
                    activePanel.selectRecipe(null);
                } else {
                    playClickSound();
                    activePanel.selectRecipe(recipe);
                }
            }
        }
    }

    private void handleKeyInput() {
        if (activePanel == null || activePanel.isCollapsed()) return;
        if (!activePanel.searchField.isFocused()) return;
        while (Keyboard.next()) {
            if (Keyboard.getEventKeyState()) {
                activePanel.searchField.textboxKeyTyped(Keyboard.getEventCharacter(), Keyboard.getEventKey());
                activePanel.rebuildFiltered();
            }
        }
    }

    private void playClickSound() {
        Minecraft.getMinecraft()
            .getSoundHandler()
            .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }
}
