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
    private final List<RecipeView> allRecipes = new ArrayList<>();
    private final List<RecipeView> filtered = new ArrayList<>();
    private final List<CategoryDefinition> categories = new ArrayList<>();

    private boolean collapsed = false;
    private int scrollOffset = 0;
    private RecipeView selectedRecipe = null;
    private int activeCategoryIndex = 0;

    public GuiTextField searchField;

    public RecipePanel(boolean isAnvil) {
        this.isAnvil = isAnvil;

        allRecipes.addAll(RecipeAccess.getAllCarpentryRecipes());

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
    }

    private boolean matchesCategory(RecipeView recipe, CategoryDefinition cat) {
        if (cat == BROWSE_ALL || (cat.recipeIds.isEmpty() && cat.recipeNames.isEmpty())) return true;
        if (cat.recipeIds.contains(recipe.id)) return true;
        if (recipe.name != null) {
            String rname = recipe.name.toLowerCase();
            for (String n : cat.recipeNames) {
                if (rname.contains(n)) return true;
            }
        }
        return false;
    }

    private boolean matchesSearch(RecipeView recipe, String query) {
        if (recipe.name != null && recipe.name.toLowerCase()
            .contains(query)) return true;
        if (recipe.getRecipeOutput() != null) {
            String itemName = recipe.getRecipeOutput()
                .getDisplayName()
                .toLowerCase();
            if (itemName.contains(query)) return true;
        }
        return false;
    }

    public List<RecipeView> getVisible() {
        int end = Math.min(scrollOffset + RECIPES_PER_PAGE, filtered.size());
        return filtered.subList(scrollOffset, end);
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

    public int getPanelX(int guiLeft) {
        return guiLeft - PANEL_WIDTH - 4;
    }
}
