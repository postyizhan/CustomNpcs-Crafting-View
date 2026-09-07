package com.customnpcs.craftingview;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;

import com.customnpcs.craftingview.client.GuiEventHandler;
import com.customnpcs.craftingview.client.TwilightRecipeSyncClient;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        GuiEventHandler handler = new GuiEventHandler();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler); // for RenderTickEvent
    }

    @Override
    public void handleGlobalRecipeSync(NBTTagCompound payload) {
        TwilightRecipeSyncClient.handleGlobalRecipes(payload);
    }
}
