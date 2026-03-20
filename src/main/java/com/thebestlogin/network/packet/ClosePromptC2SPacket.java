package com.thebestlogin.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import com.thebestlogin.server.TheBestLoginServer;

import java.util.function.Supplier;

public record ClosePromptC2SPacket() {
    public static void encode(ClosePromptC2SPacket message, FriendlyByteBuf buffer) {
    }

    public static ClosePromptC2SPacket decode(FriendlyByteBuf buffer) {
        return new ClosePromptC2SPacket();
    }

    public static void handle(ClosePromptC2SPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                TheBestLoginServer.get().cancelActivePrompt(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
