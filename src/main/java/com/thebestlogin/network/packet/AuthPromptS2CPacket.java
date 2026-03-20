package com.thebestlogin.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import com.thebestlogin.auth.AuthMessageType;
import com.thebestlogin.auth.AuthScreenMode;
import com.thebestlogin.client.ClientPacketHandlers;

import java.util.function.Supplier;

public record AuthPromptS2CPacket(
        AuthScreenMode mode,
        String nickname,
        String serverId,
        String challenge,
        int currentHashVersion,
        String currentSalt,
        int targetHashVersion,
        String targetSalt,
        String publicKey,
        String statusMessage,
        AuthMessageType statusType,
        int secondsRemaining
) {
    public static void encode(AuthPromptS2CPacket message, FriendlyByteBuf buffer) {
        buffer.writeByte(message.mode.id());
        buffer.writeUtf(message.nickname);
        buffer.writeUtf(message.serverId);
        buffer.writeUtf(message.challenge);
        buffer.writeVarInt(message.currentHashVersion);
        buffer.writeUtf(message.currentSalt);
        buffer.writeVarInt(message.targetHashVersion);
        buffer.writeUtf(message.targetSalt);
        buffer.writeUtf(message.publicKey);
        buffer.writeUtf(message.statusMessage);
        buffer.writeByte(message.statusType.id());
        buffer.writeVarInt(message.secondsRemaining);
    }

    public static AuthPromptS2CPacket decode(FriendlyByteBuf buffer) {
        return new AuthPromptS2CPacket(
                AuthScreenMode.fromId(buffer.readByte()),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                AuthMessageType.fromId(buffer.readByte()),
                buffer.readVarInt()
        );
    }

    public static void handle(AuthPromptS2CPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.handlePrompt(message)));
        context.setPacketHandled(true);
    }
}