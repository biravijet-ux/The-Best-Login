package com.thebestlogin.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import com.thebestlogin.server.TheBestLoginServer;

import java.util.function.Supplier;

public record LoginRequestC2SPacket(String challenge, String proof, boolean automatic) {
    public static void encode(LoginRequestC2SPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.challenge);
        buffer.writeUtf(message.proof);
        buffer.writeBoolean(message.automatic);
    }

    public static LoginRequestC2SPacket decode(FriendlyByteBuf buffer) {
        return new LoginRequestC2SPacket(buffer.readUtf(), buffer.readUtf(), buffer.readBoolean());
    }

    public static void handle(LoginRequestC2SPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                TheBestLoginServer.get().handleLoginProof(sender, message.challenge, message.proof, message.automatic);
            }
        });
        context.setPacketHandled(true);
    }
}