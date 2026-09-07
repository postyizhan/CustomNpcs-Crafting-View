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
import com.customnpcs.craftingview.compat.TwilightAccess;
import com.customnpcs.craftingview.network.PacketFillCraftingGrid;
import com.customnpcs.craftingview.network.PacketFillTwilightGrid;
import com.customnpcs.craftingview.network.PacketHandler;
import com.customnpcs.craftingview.network.PacketRequestGlobalRecipes;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiEventHandler {

    private static GuiEventHandler instance;

    private RecipePanel activePanel = null;
    private boolean lastLeftDown = false;
    private int pendingScroll = 0;

    public GuiEventHandler() {
        instance = this;
    }

    /** Rebuild an already open twilight panel when the server snapshot arrives. */
    public static void refreshGlobalRecipes() {
        if (instance == null || instance.activePanel == null || !instance.activePanel.isGlobalWorkbenchOnly()) return;
        instance.activePanel = RecipePanel.forGlobalWorkbench(TwilightRecipeSyncClient.getRecipes());
        instance.lastLeftDown = false;
    }

    // 在 RenderTickEvent.START 最高优先级捕获滚轮值，早于 MouseTweaks
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderTickStart(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (activePanel == null) return;
        if (activePanel.isGlobalWorkbenchOnly() && TwilightRecipeSyncClient.isSynced()
            && activePanel.getFilteredSize() == 0
            && !TwilightRecipeSyncClient.getRecipes()
                .isEmpty()) {
            activePanel = RecipePanel.forGlobalWorkbench(TwilightRecipeSyncClient.getRecipes());
        }
        int dwheel = Mouse.getDWheel();
        if (dwheel != 0) {
            pendingScroll += dwheel;
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (RecipeAccess.isCarpentryGui(event.gui)) {
            GuiContainer guiContainer = (GuiContainer) event.gui;
            if (RecipeAccess.isAvailable() && RecipeAccess.isCarpentryContainer(guiContainer.inventorySlots)) {
                boolean isAnvil = RecipeAccess.getContainerMetadata(guiContainer.inventorySlots) >= 4;
                activePanel = new RecipePanel(isAnvil);
                lastLeftDown = false;
            } else {
                activePanel = null;
            }
        } else if (isTwilightGui(event.gui)) {
            openTwilightPanel();
        } else {
            activePanel = null;
        }
    }

    /**
     * 暮色拆解台面板：展示宿主的全局 3x3 工作台配方。
     *
     * <p>
     * 配方优先取服务端同步的快照（专用服务器上客户端本地读不到 globalRecipes）；未同步过则先按本地
     * 读取建面板（单机可用），同时发请求，同步到达后下次开界面即用服务端数据。
     */
    private void openTwilightPanel() {
        PacketHandler.CHANNEL.sendToServer(new PacketRequestGlobalRecipes());

        List<RecipeView> recipes = TwilightRecipeSyncClient.isSynced() ? TwilightRecipeSyncClient.getRecipes() : null;
        activePanel = RecipePanel.forGlobalWorkbench(recipes);
        lastLeftDown = false;
    }

    @SubscribeEvent
    public void onGuiDrawPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (activePanel == null) return;

        // 面板可挂在两类界面上：CustomNPCs 木工台/铁砧，以及暮色拆解台。后者不继承
        // GuiContainerNPCInterface，故统一从 GuiContainer 基类取 GUI 原点（经 AT 暴露）。
        if (!(event.gui instanceof GuiContainer)) return;
        GuiContainer gui = (GuiContainer) event.gui;

        boolean isTwilight = activePanel.isGlobalWorkbenchOnly();
        if (isTwilight != isTwilightGui(gui)) return;

        int mx = event.mouseX;
        int my = event.mouseY;

        RecipePanelRenderer.render(event.gui, activePanel, gui.guiLeft, gui.guiTop, mx, my);

        handleMouseInput(gui.guiLeft, gui.guiTop, isTwilight, mx, my);
        handleKeyInput();
    }

    /**
     * Prefer the concrete Twilight GUI class, but retain a container-based
     * fallback for builds that rename or hide the client GUI class.
     */
    private boolean isTwilightGui(Object screen) {
        if (TwilightAccess.isUncraftingGui(screen)) return true;
        if (!(screen instanceof GuiContainer)) return false;
        return TwilightAccess.isUncraftingContainer(((GuiContainer) screen).inventorySlots);
    }

    private void handleMouseInput(int guiLeft, int guiTop, boolean isTwilight, int mx, int my) {
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
                    // 两种界面的目标合成格不同（木工台 4x4 vs 拆解台组装格 3x3），走各自的填充包
                    if (isTwilight) {
                        PacketHandler.CHANNEL.sendToServer(new PacketFillTwilightGrid(recipe.id));
                    } else {
                        PacketHandler.CHANNEL.sendToServer(new PacketFillCraftingGrid(recipe.id));
                    }
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
