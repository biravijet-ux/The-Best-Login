package com.thebestlogin.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.server.level.ServerPlayer;
import com.thebestlogin.server.TheBestLoginServer;

import java.util.function.Supplier;

public record AuthProofC2SPacket(String challenge, String proof) {
    public static void encode(AuthProofC2SPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.challenge);
        buffer.writeUtf(message.proof);
    }

    public static AuthProofC2SPacket decode(FriendlyByteBuf buffer) {
        return new AuthProofC2SPacket(buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(AuthProofC2SPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                TheBestLoginServer.get().handleProof(sender, message.challenge, message.proof);
            }
        });
        context.setPacketHandled(true);
    }
}
