package com.customnpcs.craftingview.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.customnpcs.craftingview.CraftingViewMod;

/**
 * 反射门面，屏蔽原版 CustomNPCs 与 CustomNPC+ 之间的 API 差异，使单一源码编译出的单个 jar
 * 能同时运行于两种宿主。
 *
 * <p>
 * 两宿主仅有 3 处符号差异，由本类在 {@link #init()} 时逐候选探测并锁定：
 * <ul>
 * <li>配方类：{@code noppes.npcs.controllers.RecipeCarpentry} 或 {@code ...controllers.data.RecipeCarpentry}</li>
 * <li>单例字段：{@code RecipeController.instance} 或 {@code RecipeController.Instance}</li>
 * <li>配方集合字段：原版用 {@code anvilRecipes}（装 RecipeCarpentry，即木工台 4x4 配方）；CNPC+ 用
 * {@code carpentryRecipes}，且另有同名 {@code anvilRecipes} 却装异类 RecipeAnvil，故探测优先
 * {@code carpentryRecipes}，避免在 CNPC+ 下误锁到 RecipeAnvil 集合（见 {@link #init()}）。</li>
 * </ul>
 * {@code RecipeController} 类本身在两宿主同包同名，无需探测。
 *
 * <p>
 * 句柄全部缓存为 static。单例为 static 字段且开界面前可能尚未填充，故只缓存其
 * {@link Field}（{@link #fControllerInstance}），每次查询即时取值，避免缓存到 null。
 */
public final class RecipeAccess {

    private RecipeAccess() {}

    private static final String RECIPE_CONTROLLER = "noppes.npcs.controllers.RecipeController";
    private static final String[] RECIPE_CANDIDATES = { "noppes.npcs.controllers.RecipeCarpentry",
        "noppes.npcs.controllers.data.RecipeCarpentry" };

    private static Class<?> recipeClass;
    private static Field fControllerInstance; // static 单例字段：只缓存 Field，每次即时取值
    private static Field fRecipeMap; // 实例字段 anvilRecipes / carpentryRecipes
    private static Method mGetRecipe; // RecipeController.getRecipe(int)

    // RecipeCarpentry 成员句柄（包级可见，供 RecipeView 读取）
    static Field fId, fName, fWidth, fHeight, fIgnoreDamage, fIgnoreNBT;
    private static Method mGetRecipeOutput, mGetCraftingItem;

    private static boolean available = false;

    /** 在 preInit 调用：探测并缓存全部反射句柄。失败则降级为不可用，不抛异常。 */
    public static void init() {
        try {
            recipeClass = tryClass(RECIPE_CANDIDATES);
            Class<?> ctrl = Class.forName(RECIPE_CONTROLLER);

            fControllerInstance = tryField(ctrl, "instance", "Instance");
            // 配方集合字段名两宿主不同，且 CNPC+ 同时存在装 RecipeAnvil 的 anvilRecipes，
            // 故优先探测 carpentryRecipes：原版无此字段会回退到 anvilRecipes（原版即木工台配方），
            // CNPC+ 命中 carpentryRecipes（装 RecipeCarpentry），两宿主都锁定到正确的 4x4 配方集合。
            fRecipeMap = tryField(ctrl, "carpentryRecipes", "anvilRecipes");
            mGetRecipe = ctrl.getMethod("getRecipe", int.class);

            fId = field(recipeClass, "id");
            fName = field(recipeClass, "name");
            fWidth = field(recipeClass, "recipeWidth");
            fHeight = field(recipeClass, "recipeHeight");
            fIgnoreDamage = field(recipeClass, "ignoreDamage");
            fIgnoreNBT = field(recipeClass, "ignoreNBT");
            mGetRecipeOutput = recipeClass.getMethod("getRecipeOutput");
            mGetCraftingItem = recipeClass.getMethod("getCraftingItem", int.class);

            available = true;
            CraftingViewMod.LOG.info(
                "RecipeAccess bound: recipe={}, singleton={}, map={}",
                recipeClass.getName(),
                fControllerInstance.getName(),
                fRecipeMap.getName());
        } catch (Throwable t) {
            available = false;
            CraftingViewMod.LOG.error("RecipeAccess probe failed; recipe panel disabled", t);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    /** 读取宿主全部木工台/铁砧配方并包装。不可用或单例未就绪时返回空列表。 */
    public static List<RecipeView> getAllCarpentryRecipes() {
        List<RecipeView> out = new ArrayList<>();
        if (!available) return out;
        try {
            Object ctrl = fControllerInstance.get(null);
            if (ctrl == null) return out;
            Map<?, ?> map = (Map<?, ?>) fRecipeMap.get(ctrl);
            if (map == null) return out;
            for (Object recipe : map.values()) {
                if (recipe != null) out.add(new RecipeView(recipe));
            }
        } catch (Throwable t) {
            CraftingViewMod.LOG.warn("getAllCarpentryRecipes failed", t);
        }
        return out;
    }

    /** 按 id 取单个配方并包装。不可用、单例未就绪或未找到时返回 null。 */
    public static RecipeView getRecipeById(int id) {
        if (!available) return null;
        try {
            Object ctrl = fControllerInstance.get(null);
            if (ctrl == null) return null;
            Object recipe = mGetRecipe.invoke(ctrl, id);
            return recipe == null ? null : new RecipeView(recipe);
        } catch (Throwable t) {
            CraftingViewMod.LOG.warn("getRecipeById failed: id={}", id, t);
            return null;
        }
    }

    // --- 供 RecipeView 读取每帧变动数据 ---

    static ItemStack readOutput(Object delegate) {
        if (delegate == null || mGetRecipeOutput == null) return null;
        try {
            return (ItemStack) mGetRecipeOutput.invoke(delegate);
        } catch (Throwable t) {
            return null;
        }
    }

    static ItemStack readCraftItem(Object delegate, int index) {
        if (delegate == null || mGetCraftingItem == null) return null;
        try {
            return (ItemStack) mGetCraftingItem.invoke(delegate, index);
        } catch (Throwable t) {
            return null;
        }
    }

    // --- 供 RecipeView 构造时读取不变字段 ---

    static int readInt(Object delegate, Field f, int fallback) {
        if (delegate == null || f == null) return fallback;
        try {
            return f.getInt(delegate);
        } catch (Throwable t) {
            return fallback;
        }
    }

    static boolean readBool(Object delegate, Field f) {
        if (delegate == null || f == null) return false;
        try {
            return f.getBoolean(delegate);
        } catch (Throwable t) {
            return false;
        }
    }

    static Object readObj(Object delegate, Field f) {
        if (delegate == null || f == null) return null;
        try {
            return f.get(delegate);
        } catch (Throwable t) {
            return null;
        }
    }

    // --- 探测工具 ---

    private static Class<?> tryClass(String... names) throws ClassNotFoundException {
        for (String n : names) {
            try {
                return Class.forName(n);
            } catch (ClassNotFoundException ignored) {
                // 尝试下一个候选
            }
        }
        throw new ClassNotFoundException(String.join(" / ", names));
    }

    /** 逐候选名探测字段：先 public（getField），失败回退 declared + setAccessible。 */
    private static Field tryField(Class<?> c, String... names) throws NoSuchFieldException {
        for (String n : names) {
            try {
                Field f = c.getField(n);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                try {
                    Field f = c.getDeclaredField(n);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {
                    // 尝试下一个候选
                }
            }
        }
        throw new NoSuchFieldException(c.getName() + ": " + String.join(" / ", names));
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        return tryField(c, name);
    }
}
