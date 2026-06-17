package com.customnpcs.craftingview.client;

import java.util.List;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.customnpcs.craftingview.network.PacketHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import noppes.npcs.containers.ContainerCarpentryBench;
import noppes.npcs.controllers.RecipeCarpentry;

@SideOnly(Side.CLIENT)
public class GuiCarpentryBenchWrapper extends noppes.npcs.client.gui.player.GuiNpcCarpentryBench {

    private final RecipePanel panel;
    private boolean lastLeftDown = false;

    public GuiCarpentryBenchWrapper(ContainerCarpentryBench container) {
        super(container);
        panel = new RecipePanel(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        super.drawScreen(mouseX, mouseY, partialTick);
        panel.syncSearchField();
        RecipePanelRenderer.render(this, panel, mouseX, mouseY);
        handleMouseInput(mouseX, mouseY);
        handleKeyInput();
    }

    public int getGuiLeft() { return guiLeft; }
    public int getGuiTop() { return guiTop; }

    private void handleMouseInput(int mx, int my) {
        boolean leftDown = Mouse.isButtonDown(0);
        boolean clicked = leftDown && !lastLeftDown;
        lastLeftDown = leftDown;

        int dwheel = Mouse.getDWheel();
        if (dwheel != 0) {
            if (RecipePanelRenderer.isPanelHit(panel, guiLeft, guiTop, mx, my)) {
                panel.scroll(dwheel < 0 ? 1 : -1);
            }
            return;
        }

        if (!clicked) return;

        if (!RecipePanelRenderer.isPanelHit(panel, guiLeft, guiTop, mx, my)) {
            panel.setSearchFocused(false);
            return;
        }

        if (RecipePanelRenderer.isCollapseButtonHit(panel, guiLeft, guiTop, mx, my)) {
            playClickSound();
            panel.toggleCollapsed();
            return;
        }

        if (panel.isCollapsed()) return;

        if (panel.searchField != null) {
            panel.searchField.mouseClicked(mx, my, 0);
            panel.syncSearchField();
        }

        int catIdx = RecipePanelRenderer.getCategoryTabHit(panel, guiLeft, guiTop, mx, my);
        if (catIdx >= 0) {
            playClickSound();
            panel.setCategory(catIdx);
            return;
        }

        int rowIdx = RecipePanelRenderer.getRecipeRowHit(panel, guiLeft, guiTop, mx, my);
        if (rowIdx >= 0) {
            List visible = panel.getVisible();
            if (rowIdx < visible.size()) {
                RecipeCarpentry recipe = (RecipeCarpentry) visible.get(rowIdx);
                if (RecipePanelRenderer.isPlusButtonHit(panel, guiLeft, guiTop, mx, my, rowIdx)) {
                    playClickSound();
                    Minecraft.getMinecraft().getNetHandler().addToSendQueue(
                        PacketHandler.buildFillGridPacket(recipe.id));
                } else if (recipe == panel.getSelectedRecipe()) {
                    playClickSound();
                    panel.selectRecipe(null);
                } else {
                    playClickSound();
                    panel.selectRecipe(recipe);
                }
            }
        }
    }

    private void handleKeyInput() {
        if (panel.isCollapsed()) return;
        if (!panel.isSearchFocused()) return;
        while (Keyboard.next()) {
            if (Keyboard.getEventKeyState()) {
                if (panel.searchField != null) {
                    panel.searchField.textboxKeyTyped(Keyboard.getEventCharacter(), Keyboard.getEventKey());
                    panel.syncSearchField();
                    panel.rebuildFiltered();
                }
            }
        }
    }

    private void playClickSound() {
        Minecraft.getMinecraft().sndManager.playSoundFX("random.click", 1.0F, 1.0F);
    }
}
