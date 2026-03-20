package com.thebestlogin.network;

import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import com.thebestlogin.TheBestLoginMod;
import com.thebestlogin.network.packet.AuthPromptS2CPacket;
import com.thebestlogin.network.packet.AuthStateS2CPacket;
import com.thebestlogin.network.packet.ChangePasswordRequestC2SPacket;
import com.thebestlogin.network.packet.ClosePromptC2SPacket;
import com.thebestlogin.network.packet.LocalPasswordSyncS2CPacket;
import com.thebestlogin.network.packet.LoginRequestC2SPacket;
import com.thebestlogin.network.packet.RegisterRequestC2SPacket;

public final class TheBestLoginNetwork {
    private static final String PROTOCOL_VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(TheBestLoginMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            TheBestLoginNetwork::isVersionAccepted,
            TheBestLoginNetwork::isVersionAccepted
    );

    private static boolean registered;

    private TheBestLoginNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        int id = 0;
        CHANNEL.registerMessage(id++, AuthPromptS2CPacket.class, AuthPromptS2CPacket::encode, AuthPromptS2CPacket::decode, AuthPromptS2CPacket::handle);
        CHANNEL.registerMessage(id++, AuthStateS2CPacket.class, AuthStateS2CPacket::encode, AuthStateS2CPacket::decode, AuthStateS2CPacket::handle);
        CHANNEL.registerMessage(id++, LocalPasswordSyncS2CPacket.class, LocalPasswordSyncS2CPacket::encode, LocalPasswordSyncS2CPacket::decode, LocalPasswordSyncS2CPacket::handle);
        CHANNEL.registerMessage(id++, LoginRequestC2SPacket.class, LoginRequestC2SPacket::encode, LoginRequestC2SPacket::decode, LoginRequestC2SPacket::handle);
        CHANNEL.registerMessage(id++, RegisterRequestC2SPacket.class, RegisterRequestC2SPacket::encode, RegisterRequestC2SPacket::decode, RegisterRequestC2SPacket::handle);
        CHANNEL.registerMessage(id++, ChangePasswordRequestC2SPacket.class, ChangePasswordRequestC2SPacket::encode, ChangePasswordRequestC2SPacket::decode, ChangePasswordRequestC2SPacket::handle);
        CHANNEL.registerMessage(id, ClosePromptC2SPacket.class, ClosePromptC2SPacket::encode, ClosePromptC2SPacket::decode, ClosePromptC2SPacket::handle);
    }

    public static boolean canSendTo(ServerPlayer player) {
        return CHANNEL.isRemotePresent(player.connection.connection);
    }

    public static boolean isRemotePresent(Connection connection) {
        return connection != null && CHANNEL.isRemotePresent(connection);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    private static boolean isVersionAccepted(String version) {
        return PROTOCOL_VERSION.equals(version);
    }
}
