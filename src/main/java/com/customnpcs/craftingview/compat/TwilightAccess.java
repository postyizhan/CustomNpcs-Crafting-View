package com.customnpcs.craftingview.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;

import com.customnpcs.craftingview.CraftingViewMod;

/**
 * 暮色森林（Twilight Forest）拆解台的反射门面。
 *
 * <p>
 * 暮色是<b>可选</b>依赖：多数整合包没有它，因此本 mod 不能在任何类的签名、字段或 import 中直接
 * 出现暮色类型，否则未装暮色时会触发 {@code NoClassDefFoundError}。本类把全部暮色接触面收敛到
 * 运行时反射，探测失败即整体降级（{@link #isAvailable()} 返回 false），暮色相关功能静默隐藏。
 *
 * <p>
 * 涉及的暮色符号（取自 1.7.10 源码）：
 * <ul>
 * <li>{@code twilightforest.inventory.ContainerTFUncrafting} —— 拆解台容器</li>
 * <li>{@code ContainerTFUncrafting.assemblyMatrix} —— public 的 3x3 组装格（重组用）</li>
 * <li>{@code twilightforest.client.GuiTFGoblinCrafting} —— 拆解台界面</li>
 * </ul>
 */
public final class TwilightAccess {

    private TwilightAccess() {}

    // Twilight Forest 2.2.x moved the container from inventory to uncrafting.
    private static final String[] CONTAINER_FQNS = { "twilightforest.uncrafting.ContainerTFUncrafting",
        "twilightforest.inventory.ContainerTFUncrafting" };
    private static final String GUI_FQN = "twilightforest.client.GuiTFGoblinCrafting";

    private static Class<?> containerClass;
    private static Class<?> guiClass;
    private static Field fAssemblyMatrix;
    private static Method mChanged;
    private static Method mDetect;

    private static boolean containerAvailable = false;
    private static boolean guiAvailable = false;

    /** 在 preInit 调用：探测暮色容器类与组装格字段。未装暮色时静默降级，不抛异常。 */
    public static void init() {
        try {
            Throwable lastFailure = null;
            for (String fqn : CONTAINER_FQNS) {
                try {
                    containerClass = Class.forName(fqn);
                    break;
                } catch (Throwable t) {
                    lastFailure = t;
                }
            }
            if (containerClass == null) {
                if (lastFailure instanceof ClassNotFoundException) throw (ClassNotFoundException) lastFailure;
                throw new ClassNotFoundException("Twilight uncrafting container not found", lastFailure);
            }

            // The field is public in supported builds, but keep the container
            // detector usable if an obfuscation/remap makes it inaccessible.
            try {
                fAssemblyMatrix = containerClass.getDeclaredField("assemblyMatrix");
                fAssemblyMatrix.setAccessible(true);
            } catch (Throwable ignored) {
                fAssemblyMatrix = findInventoryMatrixField(containerClass);
            }
            for (Method method : containerClass.getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && IInventory.class.isAssignableFrom(params[0])
                    && (method.getName()
                        .equals("onCraftMatrixChanged")
                        || method.getName()
                            .equals("func_75130_a"))) {
                    method.setAccessible(true);
                    mChanged = method;
                }
                if (params.length == 0 && (method.getName()
                    .equals("detectAndSendChanges")
                    || method.getName()
                        .equals("func_75132_a"))) {
                    method.setAccessible(true);
                    mDetect = method;
                }
            }
            containerAvailable = true;
            CraftingViewMod.LOG.info("TwilightAccess bound: " + containerClass.getName());
        } catch (ClassNotFoundException e) {
            // 未装暮色：正常情况，不打 warn 以免污染日志
            containerAvailable = false;
        } catch (Throwable t) {
            containerAvailable = false;
            CraftingViewMod.LOG.warn("TwilightAccess probe failed; uncrafting table panel disabled", t);
        }
    }

    public static boolean isAvailable() {
        return containerAvailable;
    }

    /**
     * 客户端 GUI 类惰性探测。与 {@link #init()} 分开：{@code GuiTFGoblinCrafting} 是客户端类，
     * 在服务端加载会失败，故只在客户端首次判定时解析。
     */
    private static Class<?> guiClass() {
        if (guiClass == null && containerAvailable && !guiAvailable) {
            try {
                guiClass = Class.forName(GUI_FQN);
                guiAvailable = true;
            } catch (Throwable t) {
                // GUI probing must not disable server/container support.
                guiAvailable = false;
            }
        }
        return guiClass;
    }

    /** 给定 GUI 是否为暮色拆解台界面。 */
    public static boolean isUncraftingGui(Object gui) {
        if (gui == null || !containerAvailable) return false;
        Class<?> c = guiClass();
        return c != null && c.isInstance(gui);
    }

    /** 给定容器是否为暮色拆解台容器。 */
    public static boolean isUncraftingContainer(Container container) {
        if (container == null || !containerAvailable) return false;
        return containerClass.isInstance(container);
    }

    /** 取拆解台的 3x3 组装格。非拆解台容器或反射失败时返回 null。 */
    public static IInventory getAssemblyMatrix(Container container) {
        if (!isUncraftingContainer(container) || fAssemblyMatrix == null) return null;
        try {
            return (IInventory) fAssemblyMatrix.get(container);
        } catch (Throwable t) {
            CraftingViewMod.LOG.warn("getAssemblyMatrix failed", t);
            return null;
        }
    }

    private static Field findInventoryMatrixField(Class<?> type) {
        Field craftingCandidate = null;
        for (Field field : type.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                if (IInventory.class.isAssignableFrom(field.getType())) {
                    if (field.getName()
                        .toLowerCase()
                        .contains("assembly")) return field;
                    if (field.getType()
                        .getName()
                        .endsWith("InventoryCrafting")) craftingCandidate = field;
                }
            } catch (Throwable ignored) {
                // Continue probing other fields; this is only a compatibility fallback.
            }
        }
        return craftingCandidate;
    }

    public static void notifyContainer(Container container, IInventory matrix) {
        try {
            if (mChanged != null) mChanged.invoke(container, matrix);
        } catch (Throwable ignored) {}
        try {
            if (mDetect != null) mDetect.invoke(container);
        } catch (Throwable ignored) {}
    }
}
