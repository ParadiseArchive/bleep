package io.hookdev.bleep.network;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.entity.BleepEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A new name, typed on a bleep's info screen. */
public record BleepRename(int entityId, String name) implements CustomPacketPayload {
    public static final Type<BleepRename> TYPE = new Type<>(Bleep.id("rename"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BleepRename> CODEC = CustomPacketPayload.codec(BleepRename::write, BleepRename::read);
    public static final int MAX = 20;

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(name, 64);
    }

    private static BleepRename read(RegistryFriendlyByteBuf buf) {
        return new BleepRename(buf.readVarInt(), buf.readUtf(64));
    }

    public static void handle(BleepRename message, IPayloadContext context) {
        rename(context.player(), context.player().level().getEntity(message.entityId()), message.name());
    }

    /** Server side: only its owner, close by, renames it. */
    public static boolean rename(Player player, net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity target, String name) {
        if (!(target instanceof BleepEntity bleep) || !bleep.isOwnedBy(player) || bleep.distanceToSqr(player) > 16 * 16) return false;
        if (bleep.getCustomName() != null && bleep.getCustomName().getString().equals(name.trim())) return false;
        bleep.rename(name);
        return true;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
