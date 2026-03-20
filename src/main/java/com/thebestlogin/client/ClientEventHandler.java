package com.thebestlogin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.thebestlogin.TheBestLoginMod;
import com.thebestlogin.network.TheBestLoginNetwork;

@Mod.EventBusSubscriber(modid = TheBestLoginMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEventHandler {
    private static boolean serverPresenceChecked;

    private ClientEventHandler() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!ClientAuthorizationState.isAuthenticated() && event.getNewScreen() instanceof AbstractContainerScreen) {
            event.setNewScreen(null);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !serverPresenceChecked && !minecraft.hasSingleplayerServer()) {
            serverPresenceChecked = true;
            if (minecraft.player.connection != null && !TheBestLoginNetwork.isRemotePresent(minecraft.player.connection.getConnection())) {
                minecraft.player.connection.getConnection().disconnect(Component.literal("На клиенте установлен The Best Login, но на сервере этот мод отсутствует."));
                return;
            }
        }

        if (minecraft.player == null || ClientAuthorizationState.isAuthenticated() || !ClientAuthorizationState.shouldOpenPromptScreen()) {
            return;
        }

        Screen currentScreen = minecraft.screen;
        if (!(currentScreen instanceof TheBestLoginAuthScreen) && !(currentScreen instanceof TheBestLoginExitScreen)) {
            minecraft.setScreen(new TheBestLoginAuthScreen());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        serverPresenceChecked = false;
        ClientAuthorizationState.reset();
    }
}
