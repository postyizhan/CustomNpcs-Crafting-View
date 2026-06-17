package com.customnpcs.craftingview.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.customnpcs.craftingview.Config.CategoryDefinition;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import noppes.npcs.controllers.RecipeCarpentry;

@SideOnly(Side.CLIENT)
public class RecipePanelRenderer {

    private static final int PADDING = 4;
    private static final int COLLAPSE_BTN_W = 12;
    private static final int SEARCH_FIELD_H = 12;
    private static final int CATEGORY_TAB_H = 14;
    private static final int RECIPE_ROW_H = 16;
    private static final int GRID_CELL = 16;

    private static final int COLOR_BG = 0xCC2D2D2D;
    private static final int COLOR_BORDER = 0xFF555555;
    private static final int COLOR_ROW_SEL = 0x88AAAAFF;
    private static final int COLOR_ROW_HOV = 0x44FFFFFF;
    private static final int COLOR_CAT_ACT = 0xFF5588CC;
    private static final int COLOR_CAT_INACT = 0xFF444444;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_PLUS_BTN = 0xFF336633;
    private static final int COLOR_PLUS_HOV = 0xFF44AA44;

    private static final RenderItem itemRenderer = new RenderItem();

    // guiTop + PADDING(4) + title(10) + search(12+3) + cats(14) + gap(2) = guiTop + 45
    private static final int HEADER_TO_CATS_OFFSET = PADDING + 10 + SEARCH_FIELD_H + 3;
    // + divider(1+3) + scrollUp(7) = guiTop + 56
    private static final int LIST_BASE_OFFSET = HEADER_TO_CATS_OFFSET + CATEGORY_TAB_H + 2 + 1 + 3 + 7;

    /**
     * 获取为了适配屏幕分辨率防止越界而修正后的 py 坐标
     */
    private static int getCorrectedPy(RecipePanel panel, int guiTop) {
        GuiScreen gui = Minecraft.getMinecraft().currentScreen;
        if (gui != null) {
            int ph = calcPanelHeight(panel.getVisible(), panel.getSelectedRecipe(), panel);
            if (guiTop + ph > gui.height) {
                return Math.max(0, gui.height - ph);
            }
        }
        return guiTop;
    }

    public static void render(GuiCarpentryBenchWrapper gui, RecipePanel panel, int mouseX, int mouseY) {
        render(gui, panel, gui.getGuiLeft(), gui.getGuiTop(), mouseX, mouseY);
    }

    public static void render(GuiScreen gui, RecipePanel panel, int guiLeft, int guiTop, int mouseX, int mouseY) {

        int px = panel.getPanelX(guiLeft);
        int py = getCorrectedPy(panel, guiTop);

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

        if (panel.isCollapsed()) {
            drawCollapsedTab(guiLeft - COLLAPSE_BTN_W - 8, py, mouseX, mouseY);
            return;
        }

        int pw = RecipePanel.PANEL_WIDTH;
        List visible = panel.getVisible();
        RecipeCarpentry sel = panel.getSelectedRecipe();

        int ph = calcPanelHeight(visible, sel, panel);
        drawRect(px, py, px + pw, py + ph, COLOR_BG);
        drawBorder(px, py, pw, ph);

        int cx = px + PADDING;
        int cy = py + PADDING;

        drawCollapseButton(px + pw - COLLAPSE_BTN_W - 2, py + 2, mouseX, mouseY, false);
        cy = drawHeader(cx, px, cy, pw, panel, mouseX, mouseY, fr);

        // Recipe list — grid inserted inline after selected row
        ItemStack tooltipStack = null;
        for (int i = 0; i < visible.size(); i++) {
            RecipeCarpentry recipe = (RecipeCarpentry) visible.get(i);
            boolean selected = recipe == sel;
            ItemStack rowTooltip = drawRecipeRow(cx, px, cy, pw, recipe, selected, mouseX, mouseY, fr);
            if (rowTooltip != null) tooltipStack = rowTooltip;

            cy += RECIPE_ROW_H;
            if (selected) {
                int gridSize = panel.isWorkbenchSource() ? 3 : 4;
                ItemStack gridTooltip = drawIngredientGrid(cx, cy, recipe, gridSize, mouseX, mouseY, fr);
                if (gridTooltip != null) tooltipStack = gridTooltip;
                cy += getGridBlockHeight(panel);
            }
        }

        if (panel.getFilteredSize() > panel.getScrollOffset() + panel.getVisiblePerPage()) {
            fr.drawString("\u25BC", px + pw / 2 - 3, cy, COLOR_TEXT_DIM);
        }

        if (tooltipStack != null) {
            drawItemTooltip(gui, tooltipStack, mouseX, mouseY);
        }
    }

    private static int drawHeader(int cx, int px, int cy, int pw, RecipePanel panel,
        int mouseX, int mouseY, FontRenderer fr) {

        fr.drawString(panel.isWorkbenchSource() ? "Workbench" : (panel.isAnvil() ? "Anvil" : "Carpentry"), cx, cy, COLOR_TEXT);
        cy += 10;

        GuiTextField tf = panel.buildSearchField(cx, cy);
        tf.drawTextBox();
        cy += SEARCH_FIELD_H + 3;

        cy = drawCategoryTabs(cx, cy, pw - PADDING * 2, panel, mouseX, mouseY, fr);
        cy += 2;

        drawRect(px, cy, px + pw, cy + 1, COLOR_BORDER);
        cy += 1 + 3;

        if (panel.getScrollOffset() > 0) {
            fr.drawString("\u25B2", px + pw / 2 - 3, cy, COLOR_TEXT_DIM);
        }
        cy += 7;

        return cy;
    }

    private static ItemStack drawRecipeRow(int cx, int px, int ry, int pw, RecipeCarpentry recipe,
        boolean selected, int mouseX, int mouseY, FontRenderer fr) {

        boolean hovered = mouseX >= cx && mouseX < px + pw - PADDING && mouseY >= ry && mouseY < ry + RECIPE_ROW_H;
        if (selected) drawRect(cx, ry, px + pw - PADDING, ry + RECIPE_ROW_H, COLOR_ROW_SEL);
        else if (hovered) drawRect(cx, ry, px + pw - PADDING, ry + RECIPE_ROW_H, COLOR_ROW_HOV);

        ItemStack tooltipStack = null;
        ItemStack result = recipe.recipeOutput;
        if (result != null) {
            renderItem(result, cx, ry);
            if (mouseX >= cx && mouseX < cx + 16 && mouseY >= ry && mouseY < ry + 16) {
                tooltipStack = result;
            }
        }

        String name = (recipe.name == null || recipe.name.isEmpty()) && result != null
            ? result.getDisplayName() : (recipe.name != null ? recipe.name : "");
        fr.drawString(fr.trimStringToWidth(name, pw - PADDING * 2 - 18 - 14), cx + 18, ry + 4, COLOR_TEXT);

        int btnX = px + pw - PADDING - 12;
        boolean btnHov = mouseX >= btnX && mouseX < btnX + 10 && mouseY >= ry + 3 && mouseY < ry + 13;
        drawRect(btnX, ry + 3, btnX + 10, ry + 13, btnHov ? COLOR_PLUS_HOV : COLOR_PLUS_BTN);
        fr.drawString("+", btnX + 2, ry + 4, COLOR_TEXT);

        return tooltipStack;
    }

    private static ItemStack drawIngredientGrid(int cx, int cy, RecipeCarpentry recipe, int gridSize,
        int mouseX, int mouseY, FontRenderer fr) {

        fr.drawString("Recipe:", cx, cy, COLOR_TEXT_DIM);
        cy += 10;

        ItemStack tooltipStack = null;
        int rw = recipe.recipeWidth;
        int rh = recipe.recipeHeight;
        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                int gx = cx + col * (GRID_CELL + 1);
                int gy = cy + row * (GRID_CELL + 1);
                drawRect(gx, gy, gx + GRID_CELL, gy + GRID_CELL, 0xFF333333);
                ItemStack ing = (row < rh && col < rw) ? recipe.getCraftingItem(row * rw + col) : null;
                if (ing != null) {
                    renderItem(ing, gx, gy);
                    if (mouseX >= gx && mouseX < gx + GRID_CELL && mouseY >= gy && mouseY < gy + GRID_CELL) {
                        tooltipStack = ing;
                    }
                }
            }
        }
        return tooltipStack;
    }

    static int calcPanelHeight(List visible, RecipeCarpentry sel, RecipePanel panel) {
        int h = LIST_BASE_OFFSET;
        for (int i = 0; i < visible.size(); i++) {
            h += RECIPE_ROW_H;
            if (visible.get(i) == sel) h += getGridBlockHeight(panel);
        }
        boolean canScrollDown = panel.getFilteredSize() > panel.getScrollOffset() + panel.getVisiblePerPage();
        if (canScrollDown) h += 10;
        return h + PADDING;
    }

    private static int drawCategoryTabs(int x, int y, int width, RecipePanel panel,
            int mouseX, int mouseY, FontRenderer fr) {
        List cats = panel.getCategories();
        int tabW = Math.min(width / Math.max(cats.size(), 1), 36);
        for (int i = 0; i < cats.size(); i++) {
            int tx = x + i * (tabW + 1);
            boolean active = i == panel.getActiveCategoryIndex();
            boolean hov = mouseX >= tx && mouseX < tx + tabW && mouseY >= y && mouseY < y + CATEGORY_TAB_H;
            drawRect(tx, y, tx + tabW, y + CATEGORY_TAB_H,
                active ? COLOR_CAT_ACT : (hov ? 0xFF555566 : COLOR_CAT_INACT));
            String label = fr.trimStringToWidth(((CategoryDefinition) cats.get(i)).name, tabW - 2);
            fr.drawString(label, tx + 2, y + 3, COLOR_TEXT);
        }
        return y + CATEGORY_TAB_H;
    }

    private static void drawCollapsedTab(int x, int y, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX < x + COLLAPSE_BTN_W + 4 && mouseY >= y && mouseY < y + 20;
        drawRect(x, y, x + COLLAPSE_BTN_W + 4, y + 20, hov ? 0xCC444444 : COLOR_BG);
        drawBorder(x, y, COLLAPSE_BTN_W + 4, 20);
        Minecraft.getMinecraft().fontRenderer.drawString(">", x + 3, y + 6, COLOR_TEXT);
    }

    private static void drawCollapseButton(int x, int y, int mouseX, int mouseY, boolean collapsed) {
        boolean hov = mouseX >= x && mouseX < x + COLLAPSE_BTN_W && mouseY >= y && mouseY < y + 12;
        drawRect(x, y, x + COLLAPSE_BTN_W, y + 12, hov ? 0xCC555555 : 0xCC333333);
        Minecraft.getMinecraft().fontRenderer.drawString(collapsed ? ">" : "<", x + 2, y + 2, COLOR_TEXT);
    }

    private static void drawBorder(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + 1, COLOR_BORDER);
        drawRect(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        drawRect(x, y, x + 1, y + h, COLOR_BORDER);
        drawRect(x + w - 1, y, x + w, y + h, COLOR_BORDER);
    }

    private static void drawRect(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1, y1, x2, y2, color);
    }

    private static void renderItem(ItemStack stack, int x, int y) {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        itemRenderer.renderItemAndEffectIntoGUI(
            Minecraft.getMinecraft().fontRenderer,
            Minecraft.getMinecraft().getTextureManager(),
            stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    private static void drawItemTooltip(GuiScreen gui, ItemStack stack, int mouseX, int mouseY) {
        List tooltip = stack.getTooltip(
            Minecraft.getMinecraft().thePlayer,
            Minecraft.getMinecraft().gameSettings.advancedItemTooltips);
        TooltipHelper.drawHoveringText(gui, tooltip, mouseX, mouseY, Minecraft.getMinecraft().fontRenderer);
    }

    // --- Hit testing ---

    public static boolean isCollapseButtonHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        int py = getCorrectedPy(panel, guiTop);
        if (panel.isCollapsed()) {
            int x = guiLeft - COLLAPSE_BTN_W - 8;
            return mx >= x && mx < x + COLLAPSE_BTN_W + 4 && my >= py && my < py + 20;
        } else {
            int px = panel.getPanelX(guiLeft);
            int x = px + RecipePanel.PANEL_WIDTH - COLLAPSE_BTN_W - 2;
            int y = py + 2;
            return mx >= x && mx < x + COLLAPSE_BTN_W && my >= y && my < y + 12;
        }
    }

    public static int getCategoryTabHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        int px = panel.getPanelX(guiLeft);
        int cx = px + PADDING;
        int py = getCorrectedPy(panel, guiTop);
        int cy = py + HEADER_TO_CATS_OFFSET;
        int width = RecipePanel.PANEL_WIDTH - PADDING * 2;
        List cats = panel.getCategories();
        int tabW = Math.min(width / Math.max(cats.size(), 1), 36);
        for (int i = 0; i < cats.size(); i++) {
            int tx = cx + i * (tabW + 1);
            if (mx >= tx && mx < tx + tabW && my >= cy && my < cy + CATEGORY_TAB_H) return i;
        }
        return -1;
    }

    private static int getRowY(int baseY, List visible, RecipeCarpentry sel, RecipePanel panel, int targetIndex) {
        int cy = baseY;
        for (int i = 0; i < targetIndex; i++) {
            cy += RECIPE_ROW_H;
            if (visible.get(i) == sel) cy += getGridBlockHeight(panel);
        }
        return cy;
    }

    public static int getRecipeRowHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        int px = panel.getPanelX(guiLeft);
        int cx = px + PADDING;
        List visible = panel.getVisible();
        RecipeCarpentry sel = panel.getSelectedRecipe();
        int py = getCorrectedPy(panel, guiTop);
        for (int i = 0; i < visible.size(); i++) {
            int ry = getRowY(py + LIST_BASE_OFFSET, visible, sel, panel, i);
            if (mx >= cx && mx < px + RecipePanel.PANEL_WIDTH - PADDING
                    && my >= ry && my < ry + RECIPE_ROW_H) return i;
        }
        return -1;
    }

    public static boolean isPlusButtonHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my, int rowIndex) {
        int px = panel.getPanelX(guiLeft);
        List visible = panel.getVisible();
        RecipeCarpentry sel = panel.getSelectedRecipe();
        int py = getCorrectedPy(panel, guiTop);
        int ry = getRowY(py + LIST_BASE_OFFSET, visible, sel, panel, rowIndex);
        int btnX = px + RecipePanel.PANEL_WIDTH - PADDING - 12;
        return mx >= btnX && mx < btnX + 10 && my >= ry + 3 && my < ry + 13;
    }

    private static int getGridBlockHeight(RecipePanel panel) {
        int gridSize = panel.isWorkbenchSource() ? 3 : 4;
        return 10 + gridSize * (GRID_CELL + 1) + PADDING;
    }

    public static boolean isPanelHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        if (panel.isCollapsed()) {
            int x = guiLeft - 4 - COLLAPSE_BTN_W;
            return mx >= x && mx < x + COLLAPSE_BTN_W + 4 && my >= guiTop && my < guiTop + 20;
        }
        int px = panel.getPanelX(guiLeft);
        int py = getCorrectedPy(panel, guiTop);
        int ph = calcPanelHeight(panel.getVisible(), panel.getSelectedRecipe(), panel);
        return mx >= px && mx < px + RecipePanel.PANEL_WIDTH && my >= py && my < py + ph;
    }
}
