package com.customnpcs.craftingview;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.customnpcs.craftingview.compat.RecipeAccess;
import com.customnpcs.craftingview.compat.TwilightAccess;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = CraftingViewMod.MODID,
    version = Tags.VERSION,
    name = "CustomNPCs Crafting View",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:customnpcs")
public class CraftingViewMod {

    public static final String MODID = "customnpcs_crafting_view";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.customnpcs.craftingview.ClientProxy",
        serverSide = "com.customnpcs.craftingview.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.load(event.getSuggestedConfigurationFile());
        RecipeAccess.init();
        TwilightAccess.init();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
