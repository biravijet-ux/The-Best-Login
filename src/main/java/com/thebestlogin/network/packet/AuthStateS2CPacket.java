package com.thebestlogin.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import com.thebestlogin.client.ClientPacketHandlers;

import java.util.function.Supplier;

public record AuthStateS2CPacket(boolean authenticated, boolean registered) {
    public static void encode(AuthStateS2CPacket message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.authenticated);
        buffer.writeBoolean(message.registered);
    }

    public static AuthStateS2CPacket decode(FriendlyByteBuf buffer) {
        return new AuthStateS2CPacket(buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(AuthStateS2CPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleAuthState(message)));
        context.setPacketHandled(true);
    }
}