package com.thebestlogin;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.thebestlogin.network.TheBestLoginNetwork;

@Mod(TheBestLoginMod.MOD_ID)
public final class TheBestLoginMod {
    public static final String MOD_ID = "thebestlogin";

    public TheBestLoginMod(FMLJavaModLoadingContext context) {
        context.getModEventBus().addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        TheBestLoginNetwork.register();
    }
}
