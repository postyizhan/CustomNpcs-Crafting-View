package com.customnpcs.craftingview.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;

import com.customnpcs.craftingview.Config;
import com.customnpcs.craftingview.Config.CategoryDefinition;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import noppes.npcs.controllers.RecipeCarpentry;
import noppes.npcs.controllers.RecipeController;

@SideOnly(Side.CLIENT)
public class RecipePanel {

    public static final int PANEL_WIDTH = 124;
    public static final int RECIPES_PER_PAGE = 7;

    public static final int SOURCE_CARPENTRY = 0;
    public static final int SOURCE_WORKBENCH = 1;

    public static final CategoryDefinition BROWSE_ALL = new CategoryDefinition(
        "Browse All", new ArrayList(), new ArrayList());

    private final int recipeSource;
    private final List allRecipes = new ArrayList();
    private final List filtered = new ArrayList();
    private final List categories = new ArrayList();

    // Cached visible page — avoids allocating a subList view every getVisible() call (called
    // multiple times per frame by the renderer and hit-testing). Refreshed only on filter/scroll.
    private final List cachedVisible = new ArrayList();

    // Lowercased name / output-display caches, keyed by recipe identity. Avoids re-running
    // toLowerCase()/getDisplayName() for every recipe on each rebuildFiltered() (per keystroke).
    private final IdentityHashMap lowerNameCache = new IdentityHashMap();
    private final IdentityHashMap lowerDisplayCache = new IdentityHashMap();

    private boolean collapsed = false;
    private int scrollOffset = 0;
    private RecipeCarpentry selectedRecipe = null;
    private int activeCategoryIndex = 0;

    // Persistent search field — created once (lazily, when its on-screen position is first known)
    // and reused. searchField is the single source of truth for search text/focus.
    public GuiTextField searchField;
    private int searchFieldX = Integer.MIN_VALUE;
    private int searchFieldY = Integer.MIN_VALUE;

    public RecipePanel() {
        this(SOURCE_CARPENTRY);
    }

    public RecipePanel(int recipeSource) {
        this.recipeSource = recipeSource;

        reloadRecipes();

        categories.add(BROWSE_ALL);
        categories.addAll(recipeSource == SOURCE_WORKBENCH ? Config.workbenchCategories : Config.categories);

        rebuildFiltered();
    }

    public void reloadRecipes() {
        allRecipes.clear();
        // Recipe instances may be replaced (e.g. Twilight global-recipe resync) — drop stale caches.
        lowerNameCache.clear();
        lowerDisplayCache.clear();
        if (recipeSource == SOURCE_WORKBENCH) {
            HashMap syncedRecipes = TwilightRecipeSyncClient.getSyncedGlobalRecipes();
            for (Object obj : syncedRecipes.values()) {
                RecipeCarpentry recipe = (RecipeCarpentry) obj;
                if (recipe.recipeWidth <= 3 && recipe.recipeHeight <= 3) {
                    allRecipes.add(recipe);
                }
            }
            if (RecipeController.instance != null) {
                for (Object obj : RecipeController.instance.globalRecipes.values()) {
                    RecipeCarpentry recipe = (RecipeCarpentry) obj;
                    if (recipe.recipeWidth <= 3 && recipe.recipeHeight <= 3
                            && !syncedRecipes.containsKey(Integer.valueOf(recipe.id))) {
                        allRecipes.add(recipe);
                    }
                }
            }
        } else if (RecipeController.instance != null) {
            allRecipes.addAll(RecipeController.instance.anvilRecipes.values());
        }
        if (!categories.isEmpty()) rebuildFiltered();
    }

    /**
     * Create the search field once at the given position, or re-create it if the position changed
     * (e.g. GUI resize), carrying over text and focus. Steady-state frames hit the early return,
     * so no GuiTextField is allocated per frame. Called by the renderer before drawing the header.
     */
    public void ensureSearchField(int x, int y) {
        if (searchField != null && searchFieldX == x && searchFieldY == y) return;
        String text = searchField != null ? searchField.getText() : "";
        boolean focused = searchField != null && searchField.isFocused();
        searchField = new GuiTextField(Minecraft.getMinecraft().fontRenderer, x, y, PANEL_WIDTH - 8, 12);
        searchField.setMaxStringLength(32);
        searchField.setText(text);
        searchField.setFocused(focused);
        searchFieldX = x;
        searchFieldY = y;
    }

    public void setSearchFocused(boolean focused) {
        if (searchField != null) searchField.setFocused(focused);
    }

    public boolean isSearchFocused() {
        return searchField != null && searchField.isFocused();
    }

    public void rebuildFiltered() {
        String query = searchField != null ? searchField.getText().toLowerCase().trim() : "";
        CategoryDefinition cat = (CategoryDefinition) categories.get(activeCategoryIndex);

        filtered.clear();
        for (int i = 0; i < allRecipes.size(); i++) {
            RecipeCarpentry recipe = (RecipeCarpentry) allRecipes.get(i);
            if (!matchesCategory(recipe, cat)) continue;
            if (!query.isEmpty() && !matchesSearch(recipe, query)) continue;
            filtered.add(recipe);
        }

        int maxScroll = Math.max(0, filtered.size() - RECIPES_PER_PAGE);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        updateVisibleCache();
    }

    private boolean matchesCategory(RecipeCarpentry recipe, CategoryDefinition cat) {
        if (cat == BROWSE_ALL || (cat.recipeIds.isEmpty() && cat.recipeNames.isEmpty())) return true;
        if (cat.recipeIds.contains(Integer.valueOf(recipe.id))) return true;
        String rname = getLowerName(recipe);
        if (rname != null) {
            for (int i = 0; i < cat.recipeNames.size(); i++) {
                if (rname.contains((String) cat.recipeNames.get(i))) return true;
            }
        }
        return false;
    }

    private boolean matchesSearch(RecipeCarpentry recipe, String query) {
        String name = getLowerName(recipe);
        if (name != null && name.contains(query)) return true;
        String display = getLowerDisplay(recipe);
        if (display != null && display.contains(query)) return true;
        return false;
    }

    /** Lowercased recipe name, cached by recipe identity; null if the recipe has no name. */
    private String getLowerName(RecipeCarpentry recipe) {
        if (recipe.name == null) return null;
        String cached = (String) lowerNameCache.get(recipe);
        if (cached == null) {
            cached = recipe.name.toLowerCase();
            lowerNameCache.put(recipe, cached);
        }
        return cached;
    }

    /** Lowercased output display name, cached by recipe identity; null if there is no output. */
    private String getLowerDisplay(RecipeCarpentry recipe) {
        String cached = (String) lowerDisplayCache.get(recipe);
        if (cached != null) return cached;
        if (recipe.recipeOutput == null) return null;
        cached = recipe.recipeOutput.getDisplayName().toLowerCase();
        lowerDisplayCache.put(recipe, cached);
        return cached;
    }

    /** Rebuild the cached visible page from filtered + scrollOffset. */
    private void updateVisibleCache() {
        cachedVisible.clear();
        int end = Math.min(scrollOffset + RECIPES_PER_PAGE, filtered.size());
        for (int i = scrollOffset; i < end; i++) {
            cachedVisible.add(filtered.get(i));
        }
    }

    public List getVisible() {
        return cachedVisible;
    }

    public int getScrollOffset() { return scrollOffset; }
    public int getVisiblePerPage() { return RECIPES_PER_PAGE; }
    public int getFilteredSize() { return filtered.size(); }

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

    /**
     * Row index of the selected recipe within the current visible page (0-based), or -1 if nothing
     * is selected or the selection is scrolled off the current page. Lets the renderer anchor the
     * floating ingredient overlay to the selected row.
     */
    public int getSelectedVisibleIndex() {
        if (selectedRecipe == null) return -1;
        int idx = filtered.indexOf(selectedRecipe);
        if (idx < scrollOffset || idx >= scrollOffset + RECIPES_PER_PAGE) return -1;
        return idx - scrollOffset;
    }

    public void selectRecipe(RecipeCarpentry recipe) { selectedRecipe = recipe; }
    public void toggleCollapsed() { collapsed = !collapsed; }
    public boolean isCollapsed() { return collapsed; }
    public RecipeCarpentry getSelectedRecipe() { return selectedRecipe; }
    public int getActiveCategoryIndex() { return activeCategoryIndex; }
    public List getCategories() { return categories; }
    public boolean isWorkbenchSource() { return recipeSource == SOURCE_WORKBENCH; }
    public int getPanelX(int guiLeft) { return guiLeft - PANEL_WIDTH - 4; }
}
