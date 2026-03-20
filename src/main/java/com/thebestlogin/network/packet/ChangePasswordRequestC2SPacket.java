package com.thebestlogin.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import com.thebestlogin.server.TheBestLoginServer;

import java.util.function.Supplier;

public record ChangePasswordRequestC2SPacket(String challenge, String oldProof, String encryptedPasswordHash) {
    public static void encode(ChangePasswordRequestC2SPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.challenge);
        buffer.writeUtf(message.oldProof);
        buffer.writeUtf(message.encryptedPasswordHash);
    }

    public static ChangePasswordRequestC2SPacket decode(FriendlyByteBuf buffer) {
        return new ChangePasswordRequestC2SPacket(buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(ChangePasswordRequestC2SPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                TheBestLoginServer.get().handleChangePassword(sender, message.challenge, message.oldProof, message.encryptedPasswordHash);
            }
        });
        context.setPacketHandled(true);
    }
}