package com.customnpcs.craftingview.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.customnpcs.craftingview.CraftingViewMod;

/** Runtime adapter for original CustomNPCs and catship package layouts. */
public final class RecipeAccess {

    private RecipeAccess() {}

    private static final String[] CONTROLLERS = { "noppes.npcs.controllers.RecipeController",
        "noppes.common.controllers.RecipeController" };
    private static final String[] RECIPES = { "noppes.npcs.controllers.RecipeCarpentry",
        "noppes.npcs.controllers.data.RecipeCarpentry", "noppes.common.controllers.RecipeCarpentry" };
    private static final String[] CONTAINERS = { "noppes.npcs.containers.ContainerCarpentryBench",
        "noppes.common.containers.ContainerCarpentryBench" };
    private static final String[] GUIS = { "noppes.npcs.client.gui.player.GuiNpcCarpentryBench",
        "noppes.client.gui.player.GuiNpcCarpentryBench" };

    private static Class<?> controllerClass, recipeClass, containerClass, guiClass;
    private static Field fController, fRecipes, fGlobal, fCraftMatrix;
    static Field fId, fName, fWidth, fHeight, fOutput, fIgnoreDamage, fIgnoreNBT;
    private static Method mGetRecipe, mOutput, mCraftingItem, mWidth, mHeight, mMetadata, mChanged, mDetect;
    private static Object controller;
    private static Map<?, ?> recipes, globalRecipes;
    private static boolean available;

    public static void init() {
        try {
            controllerClass = findClass(CONTROLLERS);
            recipeClass = findClass(RECIPES);
            fController = findField(controllerClass, "instance", "Instance");
            fRecipes = findMapField(controllerClass, "carpentryRecipes", "anvilRecipes");
            fGlobal = findFieldOptional(controllerClass, "globalRecipes");
            fId = findFieldOptional(recipeClass, "id", "field_6");
            fName = findFieldOptional(recipeClass, "name");
            fWidth = findFieldOptional(recipeClass, "recipeWidth", "field_77576_b");
            fHeight = findFieldOptional(recipeClass, "recipeHeight", "field_77577_c");
            fOutput = findFieldOptional(recipeClass, "recipeOutput", "field_77579_d");
            fIgnoreDamage = findFieldOptional(recipeClass, "ignoreDamage");
            fIgnoreNBT = findFieldOptional(recipeClass, "ignoreNBT");
            mOutput = findMethodByName(recipeClass, "getRecipeOutput", "func_77571_b", "getResult");
            mWidth = findMethodByName(recipeClass, "getWidth");
            mHeight = findMethodByName(recipeClass, "getHeight");
            mCraftingItem = findMethodByName(recipeClass, "getCraftingItem");
            available = true;
            CraftingViewMod.LOG.info("RecipeAccess bound to {} / {}", controllerClass.getName(), recipeClass.getName());
        } catch (Throwable t) {
            available = false;
            CraftingViewMod.LOG.error("RecipeAccess probe failed; recipe panel disabled", t);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isGlobalAvailable() {
        return available && fGlobal != null;
    }

    private static Object controller() {
        if (!available) return null;
        try {
            if (controller == null) {
                controller = fController.get(null);
                if (controller != null) {
                    recipes = asMap(fRecipes.get(controller));
                    globalRecipes = fGlobal == null ? null : asMap(fGlobal.get(controller));
                }
            }
            return controller;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Map<?, ?> map(boolean global) {
        if (controller() == null) return null;
        try {
            if (global && globalRecipes == null && fGlobal != null) globalRecipes = asMap(fGlobal.get(controller));
            if (!global && recipes == null) recipes = asMap(fRecipes.get(controller));
            return global ? globalRecipes : recipes;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<RecipeView> wrap(Map<?, ?> map) {
        List<RecipeView> result = new ArrayList<RecipeView>();
        if (map != null) for (Object value : map.values()) if (value != null) result.add(new RecipeView(value));
        return result;
    }

    public static List<RecipeView> getAllCarpentryRecipes() {
        return wrap(map(false));
    }

    public static List<RecipeView> getAllGlobalRecipes() {
        return wrap(map(true));
    }

    public static RecipeView getRecipeById(int id) {
        Object c = controller();
        if (c == null) return null;
        try {
            if (mGetRecipe == null) mGetRecipe = findMethod(controllerClass, "getRecipe", int.class);
            Object value = mGetRecipe.invoke(c, Integer.valueOf(id));
            return value == null ? null : new RecipeView(value);
        } catch (Throwable ignored) {
            Map<?, ?> m = map(false);
            Object value = m == null ? null : m.get(Integer.valueOf(id));
            return value == null ? null : new RecipeView(value);
        }
    }

    public static RecipeView getGlobalRecipeById(int id) {
        Map<?, ?> m = map(true);
        Object value = m == null ? null : m.get(Integer.valueOf(id));
        return value == null ? null : new RecipeView(value);
    }

    public static boolean isCarpentryGui(Object gui) {
        if (gui == null) return false;
        try {
            if (guiClass == null) guiClass = findClass(GUIS);
            return guiClass.isInstance(gui);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isCarpentryContainer(Object container) {
        if (container == null) return false;
        try {
            if (containerClass == null) {
                containerClass = findClass(CONTAINERS);
                fCraftMatrix = findFieldOptional(containerClass, "craftMatrix");
                mMetadata = findMethodByName(containerClass, "getMetadata");
                mChanged = findInventoryCallback(containerClass);
                mDetect = findMethodByName(containerClass, "detectAndSendChanges", "func_75132_a");
            }
            return containerClass.isInstance(container);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static IInventory getCraftMatrix(Object container) {
        if (!isCarpentryContainer(container) || fCraftMatrix == null) return null;
        try {
            return (IInventory) fCraftMatrix.get(container);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static int getContainerMetadata(Object container) {
        if (!isCarpentryContainer(container) || mMetadata == null) return 0;
        try {
            return ((Number) mMetadata.invoke(container)).intValue();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static void notifyContainer(Object container, IInventory matrix) {
        try {
            if (mChanged != null) mChanged.invoke(container, matrix);
        } catch (Throwable ignored) {}
        try {
            if (mDetect != null) mDetect.invoke(container);
        } catch (Throwable ignored) {}
    }

    public static boolean isNbtSyncAvailable() {
        return available;
    }

    public static NBTTagCompound writeRecipeNBT(RecipeView recipe) {
        if (recipe == null) return null;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("id", recipe.id);
        tag.setString("name", recipe.name == null ? "" : recipe.name);
        tag.setInteger("width", recipe.recipeWidth);
        tag.setInteger("height", recipe.recipeHeight);
        tag.setBoolean("ignoreDamage", recipe.ignoreDamage);
        tag.setBoolean("ignoreNBT", recipe.ignoreNBT);
        ItemStack output = recipe.getRecipeOutput();
        if (output != null) tag.setTag("output", output.writeToNBT(new NBTTagCompound()));
        NBTTagList items = new NBTTagList();
        int count = Math.max(0, recipe.recipeWidth * recipe.recipeHeight);
        for (int i = 0; i < count; i++) {
            ItemStack stack = recipe.getCraftingItem(i);
            if (stack != null) {
                NBTTagCompound item = new NBTTagCompound();
                item.setByte("slot", (byte) i);
                item.setTag("stack", stack.writeToNBT(new NBTTagCompound()));
                items.appendTag(item);
            }
        }
        tag.setTag("items", items);
        return tag;
    }

    public static RecipeView readRecipeNBT(NBTTagCompound tag) {
        if (tag == null || recipeClass == null) return null;
        try {
            int id = tag.getInteger("id");
            String name = tag.getString("name");
            int width = tag.getInteger("width");
            int height = tag.getInteger("height");
            ItemStack output = tag.hasKey("output", 10) ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("output"))
                : null;
            Object recipe = constructRecipe(id, width, height, output, name);
            if (recipe == null) return null;
            set(fId, recipe, Integer.valueOf(id));
            set(fName, recipe, name);
            set(fWidth, recipe, Integer.valueOf(width));
            set(fHeight, recipe, Integer.valueOf(height));
            set(fOutput, recipe, output);
            set(fIgnoreDamage, recipe, Boolean.valueOf(tag.getBoolean("ignoreDamage")));
            set(fIgnoreNBT, recipe, Boolean.valueOf(tag.getBoolean("ignoreNBT")));
            Method setter = findMethodWithParams(recipeClass, "setCraftingItem", int.class, ItemStack.class);
            NBTTagList items = tag.getTagList("items", 10);
            for (int i = 0; setter != null && i < items.tagCount(); i++) {
                NBTTagCompound item = items.getCompoundTagAt(i);
                ItemStack stack = ItemStack.loadItemStackFromNBT(item.getCompoundTag("stack"));
                if (stack != null) setter.invoke(recipe, Integer.valueOf(item.getByte("slot")), stack);
            }
            return new RecipeView(recipe);
        } catch (Throwable t) {
            CraftingViewMod.LOG.warn("readRecipeNBT failed", t);
            return null;
        }
    }

    private static Object constructRecipe(int id, int width, int height, ItemStack output, String name)
        throws Exception {
        try {
            Constructor<?> c = recipeClass.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (NoSuchMethodException ignored) {}
        try {
            Constructor<?> c = recipeClass.getConstructor(int.class, int.class, ItemStack[].class, ItemStack.class);
            return c.newInstance(width, height, new ItemStack[Math.max(0, width * height)], output);
        } catch (NoSuchMethodException ignored) {}
        Constructor<?> c = recipeClass.getConstructor(String.class);
        return c.newInstance(name);
    }

    static ItemStack readOutput(Object recipe) {
        try {
            if (mOutput != null) return (ItemStack) mOutput.invoke(recipe);
            return fOutput == null ? null : (ItemStack) fOutput.get(recipe);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static ItemStack readCraftItem(Object recipe, int index) {
        try {
            return mCraftingItem == null ? null : (ItemStack) mCraftingItem.invoke(recipe, Integer.valueOf(index));
        } catch (Throwable ignored) {
            return null;
        }
    }

    static int readInt(Object recipe, Field field, int fallback) {
        try {
            if (field != null) return field.getInt(recipe);
        } catch (Throwable ignored) {}
        try {
            if (field != null && field == fWidth && mWidth != null) return ((Number) mWidth.invoke(recipe)).intValue();
            if (field != null && field == fHeight && mHeight != null)
                return ((Number) mHeight.invoke(recipe)).intValue();
        } catch (Throwable ignored) {}
        return fallback;
    }

    static boolean readBool(Object recipe, Field field) {
        try {
            return field != null && field.getBoolean(recipe);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static Object readObj(Object recipe, Field field) {
        try {
            return field == null ? null : field.get(recipe);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void set(Field field, Object target, Object value) {
        try {
            if (field != null) field.set(target, value);
        } catch (Throwable ignored) {}
    }

    private static Method findInventoryCallback(Class<?> c) {
        for (Method m : c.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && IInventory.class.isAssignableFrom(p[0])
                && (m.getName()
                    .equals("onCraftMatrixChanged")
                    || m.getName()
                        .equals("func_75130_a")
                    || m.getName()
                        .equals("a"))) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static Class<?> findClass(String... names) throws ClassNotFoundException {
        for (String name : names) try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {}
        throw new ClassNotFoundException(names[0]);
    }

    private static Field findField(Class<?> c, String... names) throws NoSuchFieldException {
        Field f = findFieldOptional(c, names);
        if (f == null) throw new NoSuchFieldException(c.getName());
        return f;
    }

    private static Field findFieldOptional(Class<?> c, String... names) {
        for (String name : names) {
            try {
                Field f = c.getField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {}
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Field findMapField(Class<?> c, String... names) throws NoSuchFieldException {
        Field f = findField(c, names);
        if (!Map.class.isAssignableFrom(f.getType())) throw new NoSuchFieldException(c.getName());
        return f;
    }

    private static Method findMethod(Class<?> c, String name, Class<?>... params) throws NoSuchMethodException {
        Method m = c.getMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    private static Method findMethodByName(Class<?> c, String... names) {
        for (String name : names) for (Method m : c.getMethods()) if (m.getName()
            .equals(name)) {
                m.setAccessible(true);
                return m;
            }
        return null;
    }

    private static Method findMethodWithParams(Class<?> c, String name, Class<?>... params) {
        try {
            return findMethod(c, name, params);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : null;
    }
}
