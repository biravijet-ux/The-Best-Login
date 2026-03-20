package com.thebestlogin.network.packet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import com.thebestlogin.client.ClientPacketHandlers;

import java.util.function.Supplier;

public record AuthChallengeS2CPacket(String nickname, String serverId, String challenge) {
    public static void encode(AuthChallengeS2CPacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.nickname);
        buffer.writeUtf(message.serverId);
        buffer.writeUtf(message.challenge);
    }

    public static AuthChallengeS2CPacket decode(FriendlyByteBuf buffer) {
        return new AuthChallengeS2CPacket(buffer.readUtf(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(AuthChallengeS2CPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handleChallenge(message)));
        context.setPacketHandled(true);
    }
}
