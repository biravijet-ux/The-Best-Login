package com.thebestlogin.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import com.thebestlogin.client.ClientPacketHandlers;

import java.util.function.Supplier;

public record LocalPasswordSyncS2CPacket(String nickname, String serverId, String storedHash) {
    public static void encode(LocalPasswordSyncS2CPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.nickname);
        buffer.writeUtf(message.serverId);
        buffer.writeBoolean(message.storedHash != null && !message.storedHash.isBlank());
        if (message.storedHash != null && !message.storedHash.isBlank()) {
            buffer.writeUtf(message.storedHash);
        }
    }

    public static LocalPasswordSyncS2CPacket decode(FriendlyByteBuf buffer) {
        String nickname = buffer.readUtf();
        String serverId = buffer.readUtf();
        String storedHash = buffer.readBoolean() ? buffer.readUtf() : "";
        return new LocalPasswordSyncS2CPacket(nickname, serverId, storedHash);
    }

    public static void handle(LocalPasswordSyncS2CPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleLocalPasswordSync(message)));
        context.setPacketHandled(true);
    }
}