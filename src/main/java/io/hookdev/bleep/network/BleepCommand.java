package io.hookdev.bleep.network;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.entity.BleepEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/** A button on a bleep's info screen: Follow, Stay or Go Home. */
public record BleepCommand(int entityId, int command) implements CustomPacketPayload {
    public static final int FOLLOW = 0, STAY = 1, GO_HOME = 2;
    public static final Type<BleepCommand> TYPE = new Type<>(Bleep.id("command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BleepCommand> CODEC = CustomPacketPayload.codec(BleepCommand::write, BleepCommand::read);
    /** How far from the player a bleep still hears its owner. */
    private static final double REACH = 16;

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(command);
    }

    private static BleepCommand read(RegistryFriendlyByteBuf buf) {
        return new BleepCommand(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(BleepCommand message, IPayloadContext context) {
        obey(context.player(), context.player().level().getEntity(message.entityId()), message.command());
    }

    /** Server side: only the owner's own bleep, close by, listens. True if it did as it was told. */
    public static boolean obey(Player player, @Nullable Entity target, int command) {
        if (!(target instanceof BleepEntity bleep) || !bleep.isOwnedBy(player) || bleep.distanceToSqr(player) > REACH * REACH) return false;
        switch (command) {
            case FOLLOW -> bleep.follow();
            case STAY -> bleep.stay();
            case GO_HOME -> {
                if (bleep.goHome()) return true;
                player.sendOverlayMessage(Component.translatable("message.bleep.no_home", bleep.getDisplayName()));
                return false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
