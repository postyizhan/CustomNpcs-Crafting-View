package com.customnpcs.craftingview.compat;

import net.minecraft.item.ItemStack;

/**
 * 对宿主（CustomNPCs / CustomNPC+）的 RecipeCarpentry 实例的轻量包装。
 *
 * <p>
 * 两个宿主 mod 的配方类全限定名不同（{@code noppes.npcs.controllers.RecipeCarpentry}
 * vs {@code noppes.npcs.controllers.data.RecipeCarpentry}），但成员字段/方法名一致。本类把
 * 底层实例作为 {@link Object} 持有，通过 {@link RecipeAccess} 反射访问，使业务代码无需直接
 * import 任一宿主的 RecipeCarpentry 类，从而单源码兼容两种宿主。
 *
 * <p>
 * 不变字段在构造时一次性反射读出并缓存为 public final；每帧可能变动的产物/原料
 * （{@link #getRecipeOutput()} / {@link #getCraftingItem(int)}）则按需反射。
 */
public final class RecipeView {

    /** 底层宿主 RecipeCarpentry 实例。 */
    final Object delegate;

    // 构造时一次性反射读出的不变字段，业务代码直接以 recipe.id 等形式访问。
    public final int id;
    public final String name;
    public final int recipeWidth;
    public final int recipeHeight;
    public final boolean ignoreDamage;
    public final boolean ignoreNBT;

    RecipeView(Object delegate) {
        this.delegate = delegate;
        this.id = RecipeAccess.readInt(delegate, RecipeAccess.fId, -1);
        this.name = (String) RecipeAccess.readObj(delegate, RecipeAccess.fName);
        this.recipeWidth = RecipeAccess.readInt(delegate, RecipeAccess.fWidth, 0);
        this.recipeHeight = RecipeAccess.readInt(delegate, RecipeAccess.fHeight, 0);
        this.ignoreDamage = RecipeAccess.readBool(delegate, RecipeAccess.fIgnoreDamage);
        this.ignoreNBT = RecipeAccess.readBool(delegate, RecipeAccess.fIgnoreNBT);
    }

    /** 配方产物。每帧渲染调用，走反射；返回 null 表示不可用。 */
    public ItemStack getRecipeOutput() {
        return RecipeAccess.readOutput(delegate);
    }

    /** 第 index 个合成原料。走反射；返回 null 表示空格或不可用。 */
    public ItemStack getCraftingItem(int index) {
        return RecipeAccess.readCraftItem(delegate, index);
    }

    /**
     * 基于底层 delegate 的身份比较。RecipeView 每次 {@code getAllCarpentryRecipes()} 都会新建，
     * 同一底层配方跨界面会产生多个包装实例，故不能用对象 {@code ==}；以 delegate 身份统一判定。
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof RecipeView && ((RecipeView) o).delegate == this.delegate;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(delegate);
    }
}
