package com.customnpcs.craftingview.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.customnpcs.craftingview.Config.CategoryDefinition;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
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
    private static final int COLOR_OVERLAY_BG = 0xF0202020;

    private static final RenderItem itemRenderer = new RenderItem();

    // guiTop + PADDING(4) + title(10) + search(12+3) + cats(14) + gap(2) = guiTop + 45
    private static final int HEADER_TO_CATS_OFFSET = PADDING + 10 + SEARCH_FIELD_H + 3;
    // + divider(1+3) + scrollUp(7) = guiTop + 56
    private static final int LIST_BASE_OFFSET = HEADER_TO_CATS_OFFSET + CATEGORY_TAB_H + 2 + 1 + 3 + 7;

    public static void render(GuiCarpentryBenchWrapper gui, RecipePanel panel, int mouseX, int mouseY) {
        render(gui, panel, gui.getGuiLeft(), gui.getGuiTop(), mouseX, mouseY);
    }

    public static void render(GuiScreen gui, RecipePanel panel, int guiLeft, int guiTop, int mouseX, int mouseY) {

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;

        int px = panel.getPanelX(guiLeft);
        int py = guiTop;

        if (panel.isCollapsed()) {
            drawCollapsedTab(guiLeft - COLLAPSE_BTN_W - 8, py, mouseX, mouseY, fr);
            return;
        }

        int pw = RecipePanel.PANEL_WIDTH;
        List visible = panel.getVisible();
        RecipeCarpentry sel = panel.getSelectedRecipe();

        int ph = calcPanelHeight(panel);
        drawRect(px, py, px + pw, py + ph, COLOR_BG);
        drawBorder(px, py, pw, ph);

        int cx = px + PADDING;
        int cy = py + PADDING;

        drawCollapseButton(px + pw - COLLAPSE_BTN_W - 2, py + 2, mouseX, mouseY, fr);
        cy = drawHeader(cx, px, cy, pw, panel, mouseX, mouseY, fr);

        // Recipe list — fixed-height rows, no inline push. The selected recipe's grid is drawn
        // afterwards as a floating overlay so list layout never shifts and the grid never overflows.
        ItemStack tooltipStack = null;
        int listTop = cy;
        for (int i = 0; i < visible.size(); i++) {
            RecipeCarpentry recipe = (RecipeCarpentry) visible.get(i);
            boolean selected = recipe == sel;
            ItemStack rowTooltip = drawRecipeRow(cx, px, cy, pw, recipe, selected, mouseX, mouseY, fr, mc);
            if (rowTooltip != null) tooltipStack = rowTooltip;
            cy += RECIPE_ROW_H;
        }

        if (panel.getFilteredSize() > panel.getScrollOffset() + panel.getVisiblePerPage()) {
            fr.drawString("▼", px + pw / 2 - 3, cy, COLOR_TEXT_DIM);
        }

        // Floating ingredient grid for the selected recipe, anchored to its row; direction adaptive.
        int selIdx = panel.getSelectedVisibleIndex();
        if (selIdx >= 0 && sel != null) {
            int rowY = listTop + selIdx * RECIPE_ROW_H;
            int oy = overlayY(rowY, gui.height, panel);
            ItemStack overlayTip = drawIngredientOverlay(px, pw, oy, sel, mouseX, mouseY, fr, panel, mc);
            // Overlay sits on top of the list: when the cursor is within it, its hover result (which
            // may be null over empty space) wins, so row tooltips beneath are not shown through it.
            if (mouseX >= px && mouseX < px + pw && mouseY >= oy && mouseY < oy + overlayHeight(panel)) {
                tooltipStack = overlayTip;
            }
        }

        if (tooltipStack != null) {
            drawItemTooltip(gui, tooltipStack, mouseX, mouseY, mc);
        }
    }

    private static int drawHeader(int cx, int px, int cy, int pw, RecipePanel panel,
        int mouseX, int mouseY, FontRenderer fr) {

        fr.drawString(panel.isWorkbenchSource() ? "Workbench" : "Carpentry", cx, cy, COLOR_TEXT);
        cy += 10;

        // Persistent search field — created/repositioned only when needed, not allocated per frame.
        panel.ensureSearchField(cx, cy);
        panel.searchField.drawTextBox();
        cy += SEARCH_FIELD_H + 3;

        cy = drawCategoryTabs(cx, cy, pw - PADDING * 2, panel, mouseX, mouseY, fr);
        cy += 2;

        drawRect(px, cy, px + pw, cy + 1, COLOR_BORDER);
        cy += 1 + 3;

        if (panel.getScrollOffset() > 0) {
            fr.drawString("▲", px + pw / 2 - 3, cy, COLOR_TEXT_DIM);
        }
        cy += 7;

        return cy;
    }

    private static ItemStack drawRecipeRow(int cx, int px, int ry, int pw, RecipeCarpentry recipe,
        boolean selected, int mouseX, int mouseY, FontRenderer fr, Minecraft mc) {

        boolean hovered = mouseX >= cx && mouseX < px + pw - PADDING && mouseY >= ry && mouseY < ry + RECIPE_ROW_H;
        if (selected) drawRect(cx, ry, px + pw - PADDING, ry + RECIPE_ROW_H, COLOR_ROW_SEL);
        else if (hovered) drawRect(cx, ry, px + pw - PADDING, ry + RECIPE_ROW_H, COLOR_ROW_HOV);

        ItemStack tooltipStack = null;
        ItemStack result = recipe.recipeOutput;
        if (result != null) {
            renderItem(result, cx, ry, mc);
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

    /**
     * Floating 3x3/4x4 ingredient grid for the selected recipe, drawn on top of the list with its
     * own background + border. Anchored at oy (computed by {@link #overlayY}). Returns the ItemStack
     * the cursor hovers (for tooltip), or null.
     */
    private static ItemStack drawIngredientOverlay(int px, int pw, int oy, RecipeCarpentry recipe,
        int mouseX, int mouseY, FontRenderer fr, RecipePanel panel, Minecraft mc) {

        int ox = px;
        int oh = overlayHeight(panel);
        drawRect(ox, oy, ox + pw, oy + oh, COLOR_OVERLAY_BG);
        drawBorder(ox, oy, pw, oh);

        int cx = ox + PADDING;
        int cy = oy + PADDING;
        fr.drawString("Recipe:", cx, cy, COLOR_TEXT_DIM);
        cy += 10;

        ItemStack tooltipStack = null;
        int gridSize = panel.isWorkbenchSource() ? 3 : 4;
        int rw = recipe.recipeWidth;
        int rh = recipe.recipeHeight;
        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                int gx = cx + col * (GRID_CELL + 1);
                int gy = cy + row * (GRID_CELL + 1);
                drawRect(gx, gy, gx + GRID_CELL, gy + GRID_CELL, 0xFF333333);
                ItemStack ing = (row < rh && col < rw) ? recipe.getCraftingItem(row * rw + col) : null;
                if (ing != null) {
                    renderItem(ing, gx, gy, mc);
                    if (mouseX >= gx && mouseX < gx + GRID_CELL && mouseY >= gy && mouseY < gy + GRID_CELL) {
                        tooltipStack = ing;
                    }
                }
            }
        }
        return tooltipStack;
    }

    static int calcPanelHeight(RecipePanel panel) {
        int h = LIST_BASE_OFFSET;
        h += panel.getVisible().size() * RECIPE_ROW_H;
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

    private static void drawCollapsedTab(int x, int y, int mouseX, int mouseY, FontRenderer fr) {
        boolean hov = mouseX >= x && mouseX < x + COLLAPSE_BTN_W + 4 && mouseY >= y && mouseY < y + 20;
        drawRect(x, y, x + COLLAPSE_BTN_W + 4, y + 20, hov ? 0xCC444444 : COLOR_BG);
        drawBorder(x, y, COLLAPSE_BTN_W + 4, 20);
        fr.drawString(">", x + 3, y + 6, COLOR_TEXT);
    }

    private static void drawCollapseButton(int x, int y, int mouseX, int mouseY, FontRenderer fr) {
        boolean hov = mouseX >= x && mouseX < x + COLLAPSE_BTN_W && mouseY >= y && mouseY < y + 12;
        drawRect(x, y, x + COLLAPSE_BTN_W, y + 12, hov ? 0xCC555555 : 0xCC333333);
        fr.drawString("<", x + 2, y + 2, COLOR_TEXT);
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

    private static void renderItem(ItemStack stack, int x, int y, Minecraft mc) {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        itemRenderer.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    private static void drawItemTooltip(GuiScreen gui, ItemStack stack, int mouseX, int mouseY, Minecraft mc) {
        List tooltip = stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        TooltipHelper.drawHoveringText(gui, tooltip, mouseX, mouseY, mc.fontRenderer);
    }

    // --- Hit testing ---

    public static boolean isCollapseButtonHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        if (panel.isCollapsed()) {
            int x = guiLeft - COLLAPSE_BTN_W - 8;
            return mx >= x && mx < x + COLLAPSE_BTN_W + 4 && my >= guiTop && my < guiTop + 20;
        } else {
            int px = panel.getPanelX(guiLeft);
            int x = px + RecipePanel.PANEL_WIDTH - COLLAPSE_BTN_W - 2;
            int y = guiTop + 2;
            return mx >= x && mx < x + COLLAPSE_BTN_W && my >= y && my < y + 12;
        }
    }

    public static int getCategoryTabHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        int px = panel.getPanelX(guiLeft);
        int cx = px + PADDING;
        int cy = guiTop + HEADER_TO_CATS_OFFSET;
        int width = RecipePanel.PANEL_WIDTH - PADDING * 2;
        List cats = panel.getCategories();
        int tabW = Math.min(width / Math.max(cats.size(), 1), 36);
        for (int i = 0; i < cats.size(); i++) {
            int tx = cx + i * (tabW + 1);
            if (mx >= tx && mx < tx + tabW && my >= cy && my < cy + CATEGORY_TAB_H) return i;
        }
        return -1;
    }

    public static int getRecipeRowHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        int px = panel.getPanelX(guiLeft);
        int cx = px + PADDING;
        int visibleCount = panel.getVisible().size();
        for (int i = 0; i < visibleCount; i++) {
            int ry = guiTop + LIST_BASE_OFFSET + i * RECIPE_ROW_H;
            if (mx >= cx && mx < px + RecipePanel.PANEL_WIDTH - PADDING
                && my >= ry && my < ry + RECIPE_ROW_H) return i;
        }
        return -1;
    }

    public static boolean isPlusButtonHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my, int rowIndex) {
        int px = panel.getPanelX(guiLeft);
        int ry = guiTop + LIST_BASE_OFFSET + rowIndex * RECIPE_ROW_H;
        int btnX = px + RecipePanel.PANEL_WIDTH - PADDING - 12;
        return mx >= btnX && mx < btnX + 10 && my >= ry + 3 && my < ry + 13;
    }

    private static int getGridBlockHeight(RecipePanel panel) {
        int gridSize = panel.isWorkbenchSource() ? 3 : 4;
        return 10 + gridSize * (GRID_CELL + 1) + PADDING;
    }

    /** Total height of the floating overlay (top pad + label + grid + bottom pad). */
    private static int overlayHeight(RecipePanel panel) {
        return PADDING + getGridBlockHeight(panel);
    }

    /**
     * Overlay Y: default just below the selected row; if that overflows the screen bottom, flip to
     * above the row. Shared by render and {@link #isOverlayHit} so both agree.
     */
    private static int overlayY(int rowY, int screenHeight, RecipePanel panel) {
        int oh = overlayHeight(panel);
        int oy = rowY + RECIPE_ROW_H;
        if (oy + oh > screenHeight) oy = rowY - oh;
        if (oy < 0) oy = 0;
        return oy;
    }

    /**
     * Whether the selected-recipe overlay currently covers (mx, my). Lets click handling consume
     * clicks inside the overlay so they don't fall through to the list rows beneath it.
     */
    public static boolean isOverlayHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        if (panel.isCollapsed()) return false;
        int selIdx = panel.getSelectedVisibleIndex();
        if (selIdx < 0) return false;
        int px = panel.getPanelX(guiLeft);
        int rowY = guiTop + LIST_BASE_OFFSET + selIdx * RECIPE_ROW_H;
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        int screenHeight = screen != null ? screen.height : 240;
        int oy = overlayY(rowY, screenHeight, panel);
        return mx >= px && mx < px + RecipePanel.PANEL_WIDTH && my >= oy && my < oy + overlayHeight(panel);
    }

    public static boolean isPanelHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        if (panel.isCollapsed()) {
            int x = guiLeft - 4 - COLLAPSE_BTN_W;
            return mx >= x && mx < x + COLLAPSE_BTN_W + 4 && my >= guiTop && my < guiTop + 20;
        }
        int px = panel.getPanelX(guiLeft);
        int py = guiTop;
        int ph = calcPanelHeight(panel);
        return mx >= px && mx < px + RecipePanel.PANEL_WIDTH && my >= py && my < py + ph;
    }
}
