package com.thebestlogin.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import com.thebestlogin.server.TheBestLoginServer;

import java.util.function.Supplier;

public record RegisterRequestC2SPacket(String challenge, String encryptedPasswordHash) {
    public static void encode(RegisterRequestC2SPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.challenge);
        buffer.writeUtf(message.encryptedPasswordHash);
    }

    public static RegisterRequestC2SPacket decode(FriendlyByteBuf buffer) {
        return new RegisterRequestC2SPacket(buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(RegisterRequestC2SPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                TheBestLoginServer.get().handleRegistration(sender, message.challenge, message.encryptedPasswordHash);
            }
        });
        context.setPacketHandled(true);
    }
}