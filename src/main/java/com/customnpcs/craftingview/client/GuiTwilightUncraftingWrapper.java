package com.customnpcs.craftingview.client;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.customnpcs.craftingview.CraftingViewMod;
import com.customnpcs.craftingview.network.PacketHandler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import noppes.npcs.controllers.RecipeCarpentry;

@SideOnly(Side.CLIENT)
public class GuiTwilightUncraftingWrapper extends GuiContainer {

    public static final String GUI_CLASS = "twilightforest.client.GuiTFGoblinCrafting";
    public static final String CONTAINER_CLASS = "twilightforest.ContainerTFUncrafting";

    private static final ResourceLocation TEXTURE =
        new ResourceLocation("twilightforest:textures/gui/guigoblintinkering.png");

    private final RecipePanel panel;
    private boolean lastLeftDown = false;

    // Twilight cost accessor methods (foreign API) resolved once via reflection, then cached —
    // avoids a getMethod() lookup every frame in drawGuiContainerBackgroundLayer.
    private Method mUncraftCost;
    private Method mRecraftCost;
    private boolean costMethodsResolved = false;

    public GuiTwilightUncraftingWrapper(Container container) {
        super(container);
        this.panel = new RecipePanel(false, RecipePanel.SOURCE_WORKBENCH);
        Minecraft.getMinecraft().getNetHandler().addToSendQueue(
            PacketHandler.buildRequestGlobalRecipesPacket());
    }

    public static boolean isTwilightGui(Object gui) {
        return gui != null && GUI_CLASS.equals(gui.getClass().getName());
    }

    public static boolean isTwilightContainer(Object container) {
        return container != null && CONTAINER_CLASS.equals(container.getClass().getName());
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        super.drawScreen(mouseX, mouseY, partialTick);
        RecipePanelRenderer.render(this, panel, this.guiLeft, this.guiTop, mouseX, mouseY);
        handleMouseInput(mouseX, mouseY);
        handleKeyInput();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.fontRenderer.drawString("Uncrafting Table", 8, 6, 4210752);
        this.fontRenderer.drawString(StatCollector.translateToLocal("container.inventory"),
            8, this.ySize - 96 + 2, 4210752);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTick, int mouseX, int mouseY) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        int frameX = (this.width - this.xSize) / 2;
        int frameY = (this.height - this.ySize) / 2;
        this.drawTexturedModalRect(frameX, frameY, 0, 0, this.xSize, this.ySize);

        RenderHelper.enableGUIStandardItemLighting();
        GL11.glPushMatrix();
        GL11.glTranslatef((float)this.guiLeft, (float)this.guiTop, 0.0F);
        for (int i = 0; i < 9; i++) {
            Slot uncrafting = this.inventorySlots.getSlot(2 + i);
            Slot assembly = this.inventorySlots.getSlot(11 + i);
            if (uncrafting.getStack() != null) {
                drawSlotAsBackground(uncrafting, assembly);
            }
        }
        GL11.glPopMatrix();

        FontRenderer fr = this.mc.fontRenderer;
        RenderHelper.disableStandardItemLighting();
        resolveCostMethods();
        drawCost(getContainerCost(mUncraftCost), frameX + 48, frameY + 38, fr);
        drawCost(getContainerCost(mRecraftCost), frameX + 130, frameY + 38, fr);
    }

    private void drawSlotAsBackground(Slot backgroundSlot, Slot appearSlot) {
        int screenX = appearSlot.xDisplayPosition;
        int screenY = appearSlot.yDisplayPosition;
        ItemStack itemStackToRender = backgroundSlot.getStack();
        this.zLevel = 50.0F;
        itemRenderer.zLevel = 50.0F;
        itemRenderer.renderItemAndEffectIntoGUI(this.fontRenderer, this.mc.getTextureManager(),
            itemStackToRender, screenX, screenY);
        itemRenderer.renderItemOverlayIntoGUI(this.fontRenderer, this.mc.getTextureManager(),
            itemStackToRender, screenX, screenY);
        boolean itemBroken = itemStackToRender != null && itemStackToRender.stackSize == 0;
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        drawRect(screenX, screenY, screenX + 16, screenY + 16,
            itemBroken ? 0x8100000B : 0x9F8B8B8B);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        itemRenderer.zLevel = 0.0F;
        this.zLevel = 0.0F;
    }

    private void resolveCostMethods() {
        if (costMethodsResolved) return;
        costMethodsResolved = true;
        Class cls = this.inventorySlots.getClass();
        mUncraftCost = resolveCostMethod(cls, "getUncraftingCost");
        mRecraftCost = resolveCostMethod(cls, "getRecraftingCost");
    }

    private Method resolveCostMethod(Class cls, String methodName) {
        try {
            return cls.getMethod(methodName, new Class[0]);
        } catch (Exception e) {
            CraftingViewMod.LOG.fine("Twilight cost method unavailable: " + methodName);
            return null;
        }
    }

    private int getContainerCost(Method method) {
        if (method == null) return 0;
        try {
            Object value = method.invoke(this.inventorySlots, new Object[0]);
            return value instanceof Integer ? ((Integer)value).intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void drawCost(int costVal, int rightX, int y, FontRenderer fr) {
        if (costVal <= 0) return;
        int color = this.mc.thePlayer.experienceLevel < costVal && !this.mc.thePlayer.capabilities.isCreativeMode
            ? 10485760 : 8453920;
        String cost = String.valueOf(costVal);
        fr.drawString(cost, rightX - fr.getStringWidth(cost), y, color);
    }

    public void reloadRecipes() {
        panel.reloadRecipes();
    }

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

    private void handleMouseInput(int mx, int my) {
        boolean leftDown = Mouse.isButtonDown(0);
        boolean clicked = leftDown && !lastLeftDown;
        lastLeftDown = leftDown;

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

        // Clicks inside the floating overlay are consumed here, not passed through to rows beneath.
        if (RecipePanelRenderer.isOverlayHit(panel, guiLeft, guiTop, mx, my)) return;

        if (panel.searchField != null) {
            panel.searchField.mouseClicked(mx, my, 0);
        }

        int catIdx = RecipePanelRenderer.getCategoryTabHit(panel, guiLeft, guiTop, mx, my);
        if (catIdx >= 0) {
            playClickSound();
            panel.setCategory(catIdx);
            return;
        }

        int rowIdx = RecipePanelRenderer.getRecipeRowHit(panel, guiLeft, guiTop, mx, my);
        if (rowIdx >= 0) {
            java.util.List visible = panel.getVisible();
            if (rowIdx < visible.size()) {
                RecipeCarpentry recipe = (RecipeCarpentry) visible.get(rowIdx);
                if (RecipePanelRenderer.isPlusButtonHit(panel, guiLeft, guiTop, mx, my, rowIdx)) {
                    playClickSound();
                    Minecraft.getMinecraft().getNetHandler().addToSendQueue(
                        PacketHandler.buildFillTwilightGridPacket(recipe.id));
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
            if (Keyboard.getEventKeyState() && panel.searchField != null) {
                panel.searchField.textboxKeyTyped(Keyboard.getEventCharacter(), Keyboard.getEventKey());
                panel.rebuildFiltered();
            }
        }
    }

    private void playClickSound() {
        Minecraft.getMinecraft().sndManager.playSoundFX("random.click", 1.0F, 1.0F);
    }
}
