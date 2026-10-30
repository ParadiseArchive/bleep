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
    public static final int FOLLOW = BleepEntity.FOLLOW, STAY = BleepEntity.STAY, GO_HOME = BleepEntity.GO_HOME, TAKE_TORCH = 3;
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

    /** Server side: only the owner's own bleep, close by, listens (and it may dawdle first). */
    public static boolean obey(Player player, @Nullable Entity target, int command) {
        return obey(player, target, command, target == null ? 1 : target.getRandom().nextFloat());
    }

    /** With the dice already thrown: below the bleep's dawdle chance it dawdles. True if it will do as it was told. */
    public static boolean obey(Player player, @Nullable Entity target, int command, float roll) {
        if (!(target instanceof BleepEntity bleep) || !bleep.isOwnedBy(player) || bleep.distanceToSqr(player) > REACH * REACH) return false;
        if (command == TAKE_TORCH) return bleep.takeTorch(player);
        if (bleep.order(command, roll)) return true;
        if (command == GO_HOME) player.sendOverlayMessage(Component.translatable("message.bleep.no_home", bleep.getDisplayName()));
        return false;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
