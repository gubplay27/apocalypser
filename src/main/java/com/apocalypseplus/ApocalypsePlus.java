package com.apocalypseplus;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(ApocalypsePlus.MODID)
public class ApocalypsePlus {
    public static final String MODID = "apocalypse_plus";

    public ApocalypsePlus() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }
}
