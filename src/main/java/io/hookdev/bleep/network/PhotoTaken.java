package io.hookdev.bleep.network;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Memory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** From the owner's game: the picture for the photo their bleep just took (Memory.Photo pixels). */
public record PhotoTaken(int entityId, byte[] pixels) implements CustomPacketPayload {
    public static final Type<PhotoTaken> TYPE = new Type<>(Bleep.id("photo_taken"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhotoTaken> CODEC = CustomPacketPayload.codec(PhotoTaken::write, PhotoTaken::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByteArray(pixels);
    }

    private static PhotoTaken read(RegistryFriendlyByteBuf buf) {
        return new PhotoTaken(buf.readVarInt(), buf.readByteArray(Memory.Photo.BYTES));
    }

    /** Server side: only for a photo the bleep asked its owner for, a moment ago. */
    public static void handle(PhotoTaken message, IPayloadContext context) {
        if (context.player().level().getEntity(message.entityId()) instanceof BleepEntity bleep) bleep.photoTaken(context.player(), message.pixels());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
