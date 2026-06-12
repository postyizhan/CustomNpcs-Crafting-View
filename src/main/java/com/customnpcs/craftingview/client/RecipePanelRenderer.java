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
import com.customnpcs.craftingview.compat.RecipeView;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;

@SideOnly(Side.CLIENT)
public class RecipePanelRenderer {

    // Layout constants
    private static final int PADDING = 4;
    private static final int COLLAPSE_BTN_W = 12;
    private static final int SEARCH_FIELD_H = 12;
    private static final int CATEGORY_TAB_H = 14;
    private static final int RECIPE_ROW_H = 16;
    private static final int GRID_CELL = 16;
    // Height of the inline ingredient grid block: "Recipe:" label + 4 rows of cells
    private static final int GRID_BLOCK_H = 10 + 4 * (GRID_CELL + 1) + PADDING;

    // Colors
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

    // listBaseY offset breakdown:
    // PADDING + title(10) + search(SEARCH_FIELD_H+3) + cats(CATEGORY_TAB_H) + gap(2) + divider(1+3) + scrollUp(7)
    private static final int HEADER_TO_CATS_OFFSET = PADDING + 10 + SEARCH_FIELD_H + 3;
    private static final int LIST_BASE_OFFSET = HEADER_TO_CATS_OFFSET + CATEGORY_TAB_H + 2 + 1 + 3 + 7;

    public static void render(GuiScreen gui, RecipePanel panel, int mouseX, int mouseY) {
        if (!(gui instanceof GuiContainerNPCInterface)) return;
        GuiContainerNPCInterface container = (GuiContainerNPCInterface) gui;

        int guiLeft = container.guiLeft;
        int guiTop = container.guiTop;

        int px = panel.getPanelX(guiLeft);
        int py = guiTop;

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

        if (panel.isCollapsed()) {
            drawCollapsedTab(guiLeft - COLLAPSE_BTN_W - 8, py, mouseX, mouseY, panel);
            return;
        }

        int pw = RecipePanel.PANEL_WIDTH;
        List<RecipeView> visible = panel.getVisible();
        RecipeView sel = panel.getSelectedRecipe();

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
            RecipeView recipe = visible.get(i);
            boolean selected = recipe.equals(sel);
            drawRecipeRow(cx, px, cy, pw, recipe, selected, mouseX, mouseY, fr);
            cy += RECIPE_ROW_H;
            if (selected) {
                tooltipStack = drawIngredientGrid(cx, cy, recipe, mouseX, mouseY, fr);
                cy += GRID_BLOCK_H;
            }
        }

        if (panel.getFilteredSize() > panel.getScrollOffset() + panel.getVisiblePerPage()) {
            fr.drawString("\u25BC", px + pw / 2 - 3, cy, COLOR_TEXT_DIM);
        }

        if (tooltipStack != null) {
            drawItemTooltip(gui, tooltipStack, mouseX, mouseY);
        }
    }

    private static int drawHeader(int cx, int px, int cy, int pw, RecipePanel panel, int mouseX, int mouseY,
        FontRenderer fr) {

        fr.drawString(panel.isAnvil() ? "Anvil" : "Carpentry", cx, cy, COLOR_TEXT);
        cy += 10;

        panel.searchField.xPosition = cx;
        panel.searchField.yPosition = cy;
        panel.searchField.width = pw - PADDING * 2;
        panel.searchField.drawTextBox();
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

    private static void drawRecipeRow(int cx, int px, int ry, int pw, RecipeView recipe, boolean selected, int mouseX,
        int mouseY, FontRenderer fr) {

        boolean hovered = mouseX >= cx && mouseX < px + pw - PADDING && mouseY >= ry && mouseY < ry + RECIPE_ROW_H;
        if (selected) drawRect(cx, ry, px + pw - PADDING, ry + RECIPE_ROW_H, COLOR_ROW_SEL);
        else if (hovered) drawRect(cx, ry, px + pw - PADDING, ry + RECIPE_ROW_H, COLOR_ROW_HOV);

        ItemStack result = recipe.getRecipeOutput();
        if (result != null) renderItem(result, cx, ry);

        String name = (recipe.name == null || recipe.name.isEmpty()) && result != null ? result.getDisplayName()
            : (recipe.name != null ? recipe.name : "");
        fr.drawString(fr.trimStringToWidth(name, pw - PADDING * 2 - 18 - 14), cx + 18, ry + 4, COLOR_TEXT);

        int btnX = px + pw - PADDING - 12;
        boolean btnHov = mouseX >= btnX && mouseX < btnX + 10 && mouseY >= ry + 3 && mouseY < ry + 13;
        drawRect(btnX, ry + 3, btnX + 10, ry + 13, btnHov ? COLOR_PLUS_HOV : COLOR_PLUS_BTN);
        fr.drawString("+", btnX + 2, ry + 4, COLOR_TEXT);
    }

    private static ItemStack drawIngredientGrid(int cx, int cy, RecipeView recipe, int mouseX, int mouseY,
        FontRenderer fr) {

        fr.drawString("Recipe:", cx, cy, COLOR_TEXT_DIM);
        cy += 10;

        ItemStack tooltipStack = null;
        int rw = recipe.recipeWidth;
        int rh = recipe.recipeHeight;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
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

    private static int calcPanelHeight(List<RecipeView> visible, RecipeView sel, RecipePanel panel) {
        int h = LIST_BASE_OFFSET;
        for (RecipeView recipe : visible) {
            h += RECIPE_ROW_H;
            if (recipe.equals(sel)) h += GRID_BLOCK_H;
        }
        boolean canScrollDown = panel.getFilteredSize() > panel.getScrollOffset() + panel.getVisiblePerPage();
        if (canScrollDown) h += 10;
        return h + PADDING;
    }

    private static int drawCategoryTabs(int x, int y, int width, RecipePanel panel, int mouseX, int mouseY,
        FontRenderer fr) {
        List<CategoryDefinition> cats = panel.getCategories();
        int tabW = Math.min(width / Math.max(cats.size(), 1), 36);
        for (int i = 0; i < cats.size(); i++) {
            int tx = x + i * (tabW + 1);
            boolean active = i == panel.getActiveCategoryIndex();
            boolean hov = mouseX >= tx && mouseX < tx + tabW && mouseY >= y && mouseY < y + CATEGORY_TAB_H;
            drawRect(
                tx,
                y,
                tx + tabW,
                y + CATEGORY_TAB_H,
                active ? COLOR_CAT_ACT : (hov ? 0xFF555566 : COLOR_CAT_INACT));
            String label = fr.trimStringToWidth(cats.get(i).name, tabW - 2);
            fr.drawString(label, tx + 2, y + 3, COLOR_TEXT);
        }
        return y + CATEGORY_TAB_H;
    }

    private static void drawCollapsedTab(int x, int y, int mouseX, int mouseY, RecipePanel panel) {
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
            Minecraft.getMinecraft()
                .getTextureManager(),
            stack,
            x,
            y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    @SuppressWarnings("unchecked")
    private static void drawItemTooltip(GuiScreen gui, ItemStack stack, int mouseX, int mouseY) {
        List<String> tooltip = stack
            .getTooltip(Minecraft.getMinecraft().thePlayer, Minecraft.getMinecraft().gameSettings.advancedItemTooltips);
        TooltipHelper.drawHoveringText(gui, tooltip, mouseX, mouseY, Minecraft.getMinecraft().fontRenderer);
    }

    // --- Hit testing helpers (used by GuiEventHandler) ---

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
        List<CategoryDefinition> cats = panel.getCategories();
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
        List<RecipeView> visible = panel.getVisible();
        RecipeView sel = panel.getSelectedRecipe();
        for (int i = 0; i < visible.size(); i++) {
            int ry = getRowY(guiTop + LIST_BASE_OFFSET, visible, sel, i);
            if (mx >= cx && mx < px + RecipePanel.PANEL_WIDTH - PADDING && my >= ry && my < ry + RECIPE_ROW_H) return i;
        }
        return -1;
    }

    public static boolean isPlusButtonHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my, int rowIndex) {
        int px = panel.getPanelX(guiLeft);
        List<RecipeView> visible = panel.getVisible();
        RecipeView sel = panel.getSelectedRecipe();
        int ry = getRowY(guiTop + LIST_BASE_OFFSET, visible, sel, rowIndex);
        int btnX = px + RecipePanel.PANEL_WIDTH - PADDING - 12;
        return mx >= btnX && mx < btnX + 10 && my >= ry + 3 && my < ry + 13;
    }

    public static boolean isPanelHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        if (panel.isCollapsed()) {
            int x = guiLeft - 4 - COLLAPSE_BTN_W;
            return mx >= x && mx < x + COLLAPSE_BTN_W + 4 && my >= guiTop && my < guiTop + 20;
        }
        int px = panel.getPanelX(guiLeft);
        int ph = calcPanelHeight(panel.getVisible(), panel.getSelectedRecipe(), panel);
        return mx >= px && mx < px + RecipePanel.PANEL_WIDTH && my >= guiTop && my < guiTop + ph;
    }

    /** 计算第 targetIndex 行的起始 Y 坐标，考虑选中行展开的额外高度 */
    private static int getRowY(int baseY, List<RecipeView> visible, RecipeView sel, int targetIndex) {
        int cy = baseY;
        for (int i = 0; i < targetIndex; i++) {
            cy += RECIPE_ROW_H;
            if (visible.get(i)
                .equals(sel)) cy += GRID_BLOCK_H;
        }
        return cy;
    }
}
