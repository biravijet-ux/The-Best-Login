package com.thebestlogin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.thebestlogin.auth.AuthMessageType;
import com.thebestlogin.auth.AuthScreenMode;
import com.thebestlogin.network.TheBestLoginNetwork;
import com.thebestlogin.network.packet.AuthChallengeS2CPacket;
import com.thebestlogin.network.packet.AuthPromptS2CPacket;
import com.thebestlogin.network.packet.AuthStateS2CPacket;
import com.thebestlogin.network.packet.LocalPasswordSyncS2CPacket;
import com.thebestlogin.network.packet.LoginRequestC2SPacket;
import com.thebestlogin.util.HashingUtil;

public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    public static void handlePrompt(AuthPromptS2CPacket message) {
        AuthPromptData promptData = new AuthPromptData(
                message.mode(),
                message.nickname(),
                message.serverId(),
                message.challenge(),
                message.currentHashVersion(),
                message.currentSalt(),
                message.targetHashVersion(),
                message.targetSalt(),
                message.publicKey(),
                message.statusMessage(),
                message.statusType(),
                System.currentTimeMillis() + (long) Math.max(0, message.secondsRemaining()) * 1000L
        );

        boolean waitingForAutoLogin = tryAutomaticLogin(message.mode(), message.nickname(), message.serverId(), message.challenge());
        ClientAuthorizationState.setPromptData(promptData, waitingForAutoLogin);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && ClientAuthorizationState.shouldOpenPromptScreen()) {
            minecraft.setScreen(new TheBestLoginAuthScreen());
        }
    }

    public static void handleChallenge(AuthChallengeS2CPacket message) {
        tryAutomaticLogin(AuthScreenMode.LOGIN, message.nickname(), message.serverId(), message.challenge());
    }

    public static void handleAuthState(AuthStateS2CPacket message) {
        ClientAuthorizationState.setAuthenticated(message.authenticated());
        Minecraft minecraft = Minecraft.getInstance();
        if (message.authenticated()) {
            if (minecraft.screen instanceof TheBestLoginAuthScreen) {
                minecraft.setScreen(null);
            }
            return;
        }
        if (minecraft.screen instanceof AbstractContainerScreen) {
            minecraft.setScreen(null);
        }
    }

    public static void handleLocalPasswordSync(LocalPasswordSyncS2CPacket message) {
        if (message.storedHash() == null || message.storedHash().isBlank()) {
            ClientPasswordStore.get().removeStoredHash(message.nickname(), message.serverId());
            return;
        }
        ClientPasswordStore.get().putStoredHash(message.nickname(), message.serverId(), message.storedHash());
    }

    private static boolean tryAutomaticLogin(AuthScreenMode mode, String nickname, String serverId, String challenge) {
        if (mode != AuthScreenMode.LOGIN) {
            return false;
        }
        String storedHash = ClientPasswordStore.get().getStoredHash(nickname, serverId);
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        String proof = HashingUtil.deriveProof(serverId, nickname, storedHash, challenge);
        TheBestLoginNetwork.sendToServer(new LoginRequestC2SPacket(challenge, proof, true));
        return true;
    }
}
