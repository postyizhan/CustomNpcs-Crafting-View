package com.customnpcs.craftingview.client;

import java.util.List;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Mouse;

import com.customnpcs.craftingview.network.PacketHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import noppes.npcs.containers.ContainerCarpentryBench;
import noppes.npcs.controllers.RecipeCarpentry;

@SideOnly(Side.CLIENT)
public class GuiCarpentryBenchWrapper extends noppes.npcs.client.gui.player.GuiNpcCarpentryBench {

    private final RecipePanel panel;
    private boolean panelMouseButton;

    public GuiCarpentryBenchWrapper(ContainerCarpentryBench container) {
        super(container);
        panel = new RecipePanel();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        super.drawScreen(mouseX, mouseY, partialTick);
        RecipePanelRenderer.render(this, panel, mouseX, mouseY);
    }

    public int getGuiLeft() { return guiLeft; }
    public int getGuiTop() { return guiTop; }

    @Override
    public void handleMouseInput() {
        int dwheel = Mouse.getEventDWheel();
        Minecraft mc = Minecraft.getMinecraft();
        int mouseX = Mouse.getEventX() * this.width / mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / mc.displayHeight - 1;

        super.handleMouseInput();

        if (dwheel != 0 && !panel.isCollapsed()
                && RecipePanelRenderer.isPanelHit(panel, guiLeft, guiTop, mouseX, mouseY)) {
            panel.scroll(dwheel < 0 ? 1 : -1);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (!RecipePanelRenderer.isPanelHit(panel, guiLeft, guiTop, mouseX, mouseY)) {
            panel.setSearchFocused(false);
            super.mouseClicked(mouseX, mouseY, button);
            return;
        }

        // The panel is outside the vanilla container. Consume every button there so GuiContainer
        // does not interpret it as an outside click and drop the stack carried by the cursor.
        panelMouseButton = true;
        if (button != 0) return;

        if (RecipePanelRenderer.isCollapseButtonHit(panel, guiLeft, guiTop, mouseX, mouseY)) {
            playClickSound();
            panel.toggleCollapsed();
            return;
        }

        if (panel.isCollapsed()) return;
        if (RecipePanelRenderer.isOverlayHit(panel, guiLeft, guiTop, mouseX, mouseY)) return;

        if (panel.searchField != null) {
            panel.searchField.mouseClicked(mouseX, mouseY, button);
        }

        int catIdx = RecipePanelRenderer.getCategoryTabHit(panel, guiLeft, guiTop, mouseX, mouseY);
        if (catIdx >= 0) {
            playClickSound();
            panel.setCategory(catIdx);
            return;
        }

        int rowIdx = RecipePanelRenderer.getRecipeRowHit(panel, guiLeft, guiTop, mouseX, mouseY);
        if (rowIdx < 0) return;

        List visible = panel.getVisible();
        if (rowIdx >= visible.size()) return;

        RecipeCarpentry recipe = (RecipeCarpentry) visible.get(rowIdx);
        playClickSound();
        if (RecipePanelRenderer.isPlusButtonHit(panel, guiLeft, guiTop, mouseX, mouseY, rowIdx)) {
            Minecraft.getMinecraft().getNetHandler().addToSendQueue(
                PacketHandler.buildFillGridPacket(recipe.id));
        } else if (recipe == panel.getSelectedRecipe()) {
            panel.selectRecipe(null);
        } else {
            panel.selectRecipe(recipe);
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        if (panelMouseButton) {
            panelMouseButton = false;
            return;
        }
        super.mouseMovedOrUp(mouseX, mouseY, button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        if (!panelMouseButton) {
            super.mouseClickMove(mouseX, mouseY, button, elapsed);
        }
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (!panel.isCollapsed() && panel.searchField != null
                && panel.searchField.textboxKeyTyped(character, keyCode)) {
            panel.rebuildFiltered();
            return;
        }
        super.keyTyped(character, keyCode);
    }

    private void playClickSound() {
        Minecraft.getMinecraft().sndManager.playSoundFX("random.click", 1.0F, 1.0F);
    }
}
