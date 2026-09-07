package com.customnpcs.craftingview.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.customnpcs.craftingview.CraftingViewMod;
import com.customnpcs.craftingview.compat.RecipeAccess;
import com.customnpcs.craftingview.compat.RecipeView;
import com.customnpcs.craftingview.network.PacketSyncGlobalRecipes;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 客户端侧的全局（3x3 工作台）配方缓存。
 *
 * <p>
 * 宿主的 {@code globalRecipes} 在专用服务器上只存在于服务端，客户端本地读不到，故由服务端在玩家
 * 打开暮色拆解台时下发（{@link PacketSyncGlobalRecipes}），本类接收并缓存，供
 * {@link RecipePanel#forGlobalWorkbench(List)} 构建面板。
 *
 * <p>
 * 缓存随每次同步整体替换，不做增量合并 —— 服务端下发的始终是完整快照，替换比合并更不易残留陈旧数据。
 */
@SideOnly(Side.CLIENT)
public final class TwilightRecipeSyncClient {

    private TwilightRecipeSyncClient() {}

    private static volatile List<RecipeView> cachedRecipes = Collections.emptyList();
    private static volatile boolean synced = false;

    /** 处理服务端下发的配方快照。在网络线程调用，仅做纯数据转换，不触碰 GUI 状态。 */
    public static void handleGlobalRecipes(NBTTagCompound payload) {
        NBTTagList list = PacketSyncGlobalRecipes.extractRecipeList(payload);
        List<RecipeView> parsed = new ArrayList<>(list.tagCount());

        for (int i = 0; i < list.tagCount(); i++) {
            RecipeView recipe = RecipeAccess.readRecipeNBT(list.getCompoundTagAt(i));
            if (recipe != null) parsed.add(recipe);
        }

        cachedRecipes = parsed;
        synced = true;
        CraftingViewMod.LOG.debug("Synced {} global recipes from server", parsed.size());
    }

    /** 是否已收到过服务端同步。未同步时暮色面板回退本地读取（单机场景）。 */
    public static boolean isSynced() {
        return synced;
    }

    /** 已缓存的全局配方快照。返回不可变视图，调用方不得修改。 */
    public static List<RecipeView> getRecipes() {
        return Collections.unmodifiableList(cachedRecipes);
    }

    /** 断开连接时清空，避免把上一个服务器的配方带到下一个。 */
    public static void reset() {
        cachedRecipes = Collections.emptyList();
        synced = false;
    }
}
