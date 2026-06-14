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
    // Floating ingredient-grid overlay height: top pad + "Recipe:" label + 4 rows of cells + bottom pad
    private static final int OVERLAY_H = PADDING + 10 + 4 * (GRID_CELL + 1) + PADDING;

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
    private static final int COLOR_OVERLAY_BG = 0xF0202020;

    private static final RenderItem itemRenderer = new RenderItem();

    // listBaseY offset breakdown:
    // PADDING + title(10) + search(SEARCH_FIELD_H+3) + cats(CATEGORY_TAB_H) + gap(2) + divider(1+3) + scrollUp(7)
    private static final int HEADER_TO_CATS_OFFSET = PADDING + 10 + SEARCH_FIELD_H + 3;
    private static final int LIST_BASE_OFFSET = HEADER_TO_CATS_OFFSET + CATEGORY_TAB_H + 2 + 1 + 3 + 7;

    public static void render(GuiScreen gui, RecipePanel panel, int mouseX, int mouseY) {
        if (!(gui instanceof GuiContainerNPCInterface)) return;
        GuiContainerNPCInterface container = (GuiContainerNPCInterface) gui;

        // 优化：缓存 Minecraft 实例和 FontRenderer，避免重复调用
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;

        int guiLeft = container.guiLeft;
        int guiTop = container.guiTop;

        int px = panel.getPanelX(guiLeft);
        int py = guiTop;

        if (panel.isCollapsed()) {
            drawCollapsedTab(guiLeft - COLLAPSE_BTN_W - 8, py, mouseX, mouseY, panel, fr);
            return;
        }

        int pw = RecipePanel.PANEL_WIDTH;
        List<RecipeView> visible = panel.getVisible();
        RecipeView sel = panel.getSelectedRecipe();

        int ph = calcPanelHeight(panel);
        drawRect(px, py, px + pw, py + ph, COLOR_BG);
        drawBorder(px, py, pw, ph);

        int cx = px + PADDING;
        int cy = py + PADDING;

        drawCollapseButton(px + pw - COLLAPSE_BTN_W - 2, py + 2, mouseX, mouseY, false, fr);
        cy = drawHeader(cx, px, cy, pw, panel, mouseX, mouseY, fr);

        // Recipe list — fixed-height rows, no inline push. Selected recipe's grid is drawn afterwards
        // as a floating overlay so list layout never shifts and the grid never overflows the screen.
        ItemStack tooltipStack = null;
        int listTop = cy;
        for (int i = 0; i < visible.size(); i++) {
            RecipeView recipe = visible.get(i);
            boolean selected = recipe.equals(sel);
            ItemStack rowTip = drawRecipeRow(cx, px, cy, pw, recipe, selected, mouseX, mouseY, fr, mc);
            if (rowTip != null) tooltipStack = rowTip;
            cy += RECIPE_ROW_H;
        }

        if (panel.getFilteredSize() > panel.getScrollOffset() + panel.getVisiblePerPage()) {
            fr.drawString("▼", px + pw / 2 - 3, cy, COLOR_TEXT_DIM);
        }

        // Floating ingredient grid for the selected recipe, anchored to its row; direction adaptive.
        int selIdx = panel.getSelectedVisibleIndex();
        if (selIdx >= 0 && sel != null) {
            int rowY = listTop + selIdx * RECIPE_ROW_H;
            // 优化：传递 gui.height 避免每次创建 ScaledResolution
            ItemStack overlayTip = drawIngredientOverlay(px, pw, rowY, sel, mouseX, mouseY, fr, gui.height, mc);
            // 浮层盖在列表行之上：鼠标落在浮层矩形内时，tooltip 由浮层决定（命中原料则显示该原料，
            // 否则清空），避免被遮住的行产物 tooltip 穿透显示。
            int oy = overlayY(rowY, gui.height);
            boolean overOverlay = mouseX >= px && mouseX < px + pw && mouseY >= oy && mouseY < oy + OVERLAY_H;
            if (overOverlay) tooltipStack = overlayTip;
        }

        if (tooltipStack != null) {
            drawItemTooltip(gui, tooltipStack, mouseX, mouseY, mc);
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
            fr.drawString("▲", px + pw / 2 - 3, cy, COLOR_TEXT_DIM);
        }
        cy += 7;

        return cy;
    }

    /**
     * 绘制单个配方行。返回鼠标悬停在左侧产物图标（{@code (cx, ry)} 起的 16x16 区域）上时的产物
     * ItemStack（用于 tooltip），未命中或无产物则 null。
     */
    private static ItemStack drawRecipeRow(int cx, int px, int ry, int pw, RecipeView recipe, boolean selected,
        int mouseX, int mouseY, FontRenderer fr, Minecraft mc) {

        boolean hovered = mouseX >= cx && mouseX < px + pw - PADDING && mouseY >= ry && mouseY < ry + RECIPE_ROW_H;
        if (selected) drawRect(cx, ry, px + pw - PADDING, ry + RECIPE_ROW_H, COLOR_ROW_SEL);
        else if (hovered) drawRect(cx, ry, px + pw - PADDING, ry + RECIPE_ROW_H, COLOR_ROW_HOV);

        ItemStack result = recipe.getRecipeOutput();
        ItemStack tooltipStack = null;
        if (result != null) {
            renderItem(result, cx, ry, mc);
            if (mouseX >= cx && mouseX < cx + GRID_CELL && mouseY >= ry && mouseY < ry + GRID_CELL) {
                tooltipStack = result;
            }
        }

        String name = (recipe.name == null || recipe.name.isEmpty()) && result != null ? result.getDisplayName()
            : (recipe.name != null ? recipe.name : "");
        fr.drawString(fr.trimStringToWidth(name, pw - PADDING * 2 - 18 - 14), cx + 18, ry + 4, COLOR_TEXT);

        int btnX = px + pw - PADDING - 12;
        boolean btnHov = mouseX >= btnX && mouseX < btnX + 10 && mouseY >= ry + 3 && mouseY < ry + 13;
        drawRect(btnX, ry + 3, btnX + 10, ry + 13, btnHov ? COLOR_PLUS_HOV : COLOR_PLUS_BTN);
        fr.drawString("+", btnX + 2, ry + 4, COLOR_TEXT);

        return tooltipStack;
    }

    /**
     * 选中配方的 4x4 合成格浮层。锚定在选中行（rowY）旁，方向自适应：行下方空间够则朝下展开，
     * 否则朝上展开，保证浮层始终在屏幕内、不被截断。浮层自带背景与边框，盖在列表之上。
     * 返回鼠标悬停的原料 ItemStack（用于 tooltip），无则 null。
     *
     * @param screenHeight GUI 高度，从外部传入避免每帧创建 ScaledResolution
     */
    private static ItemStack drawIngredientOverlay(int px, int pw, int rowY, RecipeView recipe, int mouseX, int mouseY,
        FontRenderer fr, int screenHeight, Minecraft mc) {

        // 方向自适应：贴选中行下方，下方超屏则改贴上方（overlayY 与命中测试共用，保证一致）。
        int oy = overlayY(rowY, screenHeight);

        int ox = px;
        drawRect(ox, oy, ox + pw, oy + OVERLAY_H, COLOR_OVERLAY_BG);
        drawBorder(ox, oy, pw, OVERLAY_H);

        int cx = ox + PADDING;
        int cy = oy + PADDING;
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
                    renderItem(ing, gx, gy, mc);
                    if (mouseX >= gx && mouseX < gx + GRID_CELL && mouseY >= gy && mouseY < gy + GRID_CELL) {
                        tooltipStack = ing;
                    }
                }
            }
        }
        return tooltipStack;
    }

    private static int calcPanelHeight(RecipePanel panel) {
        int h = LIST_BASE_OFFSET;
        h += panel.getVisible()
            .size() * RECIPE_ROW_H;
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

    private static void drawCollapsedTab(int x, int y, int mouseX, int mouseY, RecipePanel panel, FontRenderer fr) {
        boolean hov = mouseX >= x && mouseX < x + COLLAPSE_BTN_W + 4 && mouseY >= y && mouseY < y + 20;
        drawRect(x, y, x + COLLAPSE_BTN_W + 4, y + 20, hov ? 0xCC444444 : COLOR_BG);
        drawBorder(x, y, COLLAPSE_BTN_W + 4, 20);
        fr.drawString(">", x + 3, y + 6, COLOR_TEXT);
    }

    private static void drawCollapseButton(int x, int y, int mouseX, int mouseY, boolean collapsed, FontRenderer fr) {
        boolean hov = mouseX >= x && mouseX < x + COLLAPSE_BTN_W && mouseY >= y && mouseY < y + 12;
        drawRect(x, y, x + COLLAPSE_BTN_W, y + 12, hov ? 0xCC555555 : 0xCC333333);
        fr.drawString(collapsed ? ">" : "<", x + 2, y + 2, COLOR_TEXT);
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

    @SuppressWarnings("unchecked")
    private static void drawItemTooltip(GuiScreen gui, ItemStack stack, int mouseX, int mouseY, Minecraft mc) {
        List<String> tooltip = stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        TooltipHelper.drawHoveringText(gui, tooltip, mouseX, mouseY, mc.fontRenderer);
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
        int visibleCount = panel.getVisible()
            .size();
        for (int i = 0; i < visibleCount; i++) {
            int ry = guiTop + LIST_BASE_OFFSET + i * RECIPE_ROW_H;
            if (mx >= cx && mx < px + RecipePanel.PANEL_WIDTH - PADDING && my >= ry && my < ry + RECIPE_ROW_H) return i;
        }
        return -1;
    }

    public static boolean isPlusButtonHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my, int rowIndex) {
        int px = panel.getPanelX(guiLeft);
        int ry = guiTop + LIST_BASE_OFFSET + rowIndex * RECIPE_ROW_H;
        int btnX = px + RecipePanel.PANEL_WIDTH - PADDING - 12;
        return mx >= btnX && mx < btnX + 10 && my >= ry + 3 && my < ry + 13;
    }

    /**
     * 选中合成格浮层当前是否命中给定屏幕坐标。与渲染用同一套自适应方向逻辑，供点击处理消费浮层内的点击，
     * 避免点击穿透到被浮层遮住的列表行。未选中或选中项不在可视页时返回 false。
     */
    public static boolean isOverlayHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        if (panel.isCollapsed()) return false;
        int selIdx = panel.getSelectedVisibleIndex();
        if (selIdx < 0) return false;
        int px = panel.getPanelX(guiLeft);
        int rowY = guiTop + LIST_BASE_OFFSET + selIdx * RECIPE_ROW_H;
        // 使用 GuiScreen.height 而非创建 ScaledResolution
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        int screenHeight = screen != null ? screen.height : 240; // 240 为降级默认值
        int oy = overlayY(rowY, screenHeight);
        return mx >= px && mx < px + RecipePanel.PANEL_WIDTH && my >= oy && my < oy + OVERLAY_H;
    }

    /**
     * 浮层 Y 坐标：默认贴选中行下方，下方超屏则改贴上方。渲染与命中测试共用，保证一致。
     *
     * @param rowY         选中行的 Y 坐标
     * @param screenHeight GUI 高度（从 GuiScreen.height 获取，避免创建 ScaledResolution）
     */
    private static int overlayY(int rowY, int screenHeight) {
        int oy = rowY + RECIPE_ROW_H;
        if (oy + OVERLAY_H > screenHeight) oy = rowY - OVERLAY_H;
        if (oy < 0) oy = 0;
        return oy;
    }

    public static boolean isPanelHit(RecipePanel panel, int guiLeft, int guiTop, int mx, int my) {
        if (panel.isCollapsed()) {
            int x = guiLeft - 4 - COLLAPSE_BTN_W;
            return mx >= x && mx < x + COLLAPSE_BTN_W + 4 && my >= guiTop && my < guiTop + 20;
        }
        int px = panel.getPanelX(guiLeft);
        int ph = calcPanelHeight(panel);
        return mx >= px && mx < px + RecipePanel.PANEL_WIDTH && my >= guiTop && my < guiTop + ph;
    }
}
