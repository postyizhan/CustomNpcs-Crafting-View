package com.customnpcs.craftingview.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;

import com.customnpcs.craftingview.Config;
import com.customnpcs.craftingview.Config.CategoryDefinition;
import com.customnpcs.craftingview.compat.RecipeAccess;
import com.customnpcs.craftingview.compat.RecipeView;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RecipePanel {

    public static final int PANEL_WIDTH = 124;
    public static final int RECIPES_PER_PAGE = 7;

    // Special "Browse All" category always at index 0
    public static final CategoryDefinition BROWSE_ALL = new CategoryDefinition(
        "Browse All",
        new ArrayList<>(),
        new ArrayList<>());

    private final boolean isAnvil;
    /** true 表示面板展示宿主的全局 3x3 工作台配方（暮色拆解台场景），而非 4x4 木工台/铁砧配方。 */
    private final boolean globalWorkbenchOnly;
    private final List<RecipeView> allRecipes = new ArrayList<>();
    private final List<RecipeView> filtered = new ArrayList<>();
    private final List<CategoryDefinition> categories = new ArrayList<>();

    // 缓存可见列表，避免每次 getVisible() 创建 subList
    private final List<RecipeView> cachedVisible = new ArrayList<>();

    private boolean collapsed = false;
    private int scrollOffset = 0;
    private RecipeView selectedRecipe = null;
    private int activeCategoryIndex = 0;

    public GuiTextField searchField;

    public RecipePanel(boolean isAnvil) {
        this(isAnvil, false, null);
    }

    /**
     * 全局工作台配方面板（暮色拆解台场景）。
     *
     * @param recipes 配方来源。非 null 时直接使用（客户端由服务端同步而来）；null 时回退本地读取。
     */
    public static RecipePanel forGlobalWorkbench(List<RecipeView> recipes) {
        return new RecipePanel(false, true, recipes);
    }

    private RecipePanel(boolean isAnvil, boolean globalWorkbenchOnly, List<RecipeView> providedRecipes) {
        this.isAnvil = isAnvil;
        this.globalWorkbenchOnly = globalWorkbenchOnly;

        if (providedRecipes != null) {
            for (RecipeView recipe : providedRecipes) {
                if (!globalWorkbenchOnly || (recipe.recipeWidth <= 3 && recipe.recipeHeight <= 3)) {
                    allRecipes.add(recipe);
                }
            }
        } else if (globalWorkbenchOnly) {
            for (RecipeView recipe : RecipeAccess.getAllGlobalRecipes()) {
                if (recipe.recipeWidth <= 3 && recipe.recipeHeight <= 3) allRecipes.add(recipe);
            }
        } else {
            allRecipes.addAll(RecipeAccess.getAllCarpentryRecipes());
        }

        // Build category list: Browse All + config categories
        categories.add(BROWSE_ALL);
        categories.addAll(Config.categories);

        // Init search field (x/y set later by renderer)
        searchField = new GuiTextField(Minecraft.getMinecraft().fontRenderer, 0, 0, PANEL_WIDTH - 8, 12);
        searchField.setMaxStringLength(32);
        searchField.setText("");

        rebuildFiltered();
    }

    public void rebuildFiltered() {
        String query = searchField.getText()
            .toLowerCase()
            .trim();
        CategoryDefinition cat = categories.get(activeCategoryIndex);

        filtered.clear();
        for (RecipeView recipe : allRecipes) {
            if (!matchesCategory(recipe, cat)) continue;
            if (!query.isEmpty() && !matchesSearch(recipe, query)) continue;
            filtered.add(recipe);
        }

        // Clamp scroll
        int maxScroll = Math.max(0, filtered.size() - RECIPES_PER_PAGE);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        updateVisibleCache();
    }

    private boolean matchesCategory(RecipeView recipe, CategoryDefinition cat) {
        if (cat == BROWSE_ALL || (cat.recipeIds.isEmpty() && cat.recipeNames.isEmpty())) return true;
        if (cat.recipeIds.contains(recipe.id)) return true;

        // 优化：使用 HashSet 查找（需要 Config.CategoryDefinition 支持）
        // 当前先用线性查找，但使用缓存的小写名称避免重复 toLowerCase()
        String lowerName = recipe.getLowerCaseName();
        if (lowerName != null) {
            for (String n : cat.recipeNames) {
                if (lowerName.contains(n)) return true;
            }
        }
        return false;
    }

    private boolean matchesSearch(RecipeView recipe, String query) {
        // 使用缓存的小写名称，避免每次搜索都调用 toLowerCase()
        String lowerName = recipe.getLowerCaseName();
        if (lowerName != null && lowerName.contains(query)) return true;

        String lowerDisplayName = recipe.getLowerCaseDisplayName();
        if (lowerDisplayName != null && lowerDisplayName.contains(query)) return true;

        return false;
    }

    /**
     * 更新可见列表缓存，在 rebuildFiltered() 和 scroll() 后调用。
     * 避免每帧 getVisible() 创建新的 subList，减少 GC 压力。
     */
    private void updateVisibleCache() {
        cachedVisible.clear();
        int end = Math.min(scrollOffset + RECIPES_PER_PAGE, filtered.size());
        for (int i = scrollOffset; i < end; i++) {
            cachedVisible.add(filtered.get(i));
        }
    }

    public List<RecipeView> getVisible() {
        return cachedVisible;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public int getVisiblePerPage() {
        return RECIPES_PER_PAGE;
    }

    public int getFilteredSize() {
        return filtered.size();
    }

    public void scroll(int delta) {
        int maxScroll = Math.max(0, filtered.size() - RECIPES_PER_PAGE);
        scrollOffset = Math.max(0, Math.min(scrollOffset + delta, maxScroll));
        updateVisibleCache();
    }

    public void setCategory(int index) {
        if (index >= 0 && index < categories.size()) {
            activeCategoryIndex = index;
            scrollOffset = 0;
            rebuildFiltered();
        }
    }

    public void selectRecipe(RecipeView recipe) {
        selectedRecipe = recipe;
    }

    /**
     * 选中配方在当前可视页中的行索引（0-based）；未选中或选中项不在当前可视页时返回 -1。
     * 供渲染层决定合成格浮层朝上还是朝下展开。
     */
    public int getSelectedVisibleIndex() {
        if (selectedRecipe == null) return -1;
        int idx = filtered.indexOf(selectedRecipe);
        if (idx < scrollOffset || idx >= scrollOffset + RECIPES_PER_PAGE) return -1;
        return idx - scrollOffset;
    }

    public void toggleCollapsed() {
        collapsed = !collapsed;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public RecipeView getSelectedRecipe() {
        return selectedRecipe;
    }

    public int getActiveCategoryIndex() {
        return activeCategoryIndex;
    }

    public List<CategoryDefinition> getCategories() {
        return categories;
    }

    public boolean isAnvil() {
        return isAnvil;
    }

    /** true 表示展示全局 3x3 工作台配方（暮色拆解台场景），渲染与填充均按 3x3 处理。 */
    public boolean isGlobalWorkbenchOnly() {
        return globalWorkbenchOnly;
    }

    public int getPanelX(int guiLeft) {
        return guiLeft - PANEL_WIDTH - 4;
    }
}
