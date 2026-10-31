package io.hookdev.bleep.network;

import io.hookdev.bleep.Bleep;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** To its owner: a bleep says Click!, and their game takes a tiny picture of what they see (client/PhotoCamera). */
public record PhotoRequest(int entityId) implements CustomPacketPayload {
    public static final Type<PhotoRequest> TYPE = new Type<>(Bleep.id("photo_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhotoRequest> CODEC = CustomPacketPayload.codec(PhotoRequest::write, PhotoRequest::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    private static PhotoRequest read(RegistryFriendlyByteBuf buf) {
        return new PhotoRequest(buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
