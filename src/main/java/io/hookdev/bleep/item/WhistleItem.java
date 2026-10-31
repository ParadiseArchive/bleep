package io.hookdev.bleep.item;

import io.hookdev.bleep.entity.BleepEntity;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A whistle (owner, 31 Oct 2026): right-click and your bleeps within 64 blocks come to you and follow; sneak and
 * right-click and they stay where they are. As with an order from the info screen, a new bleep may dawdle first.
 */
public class WhistleItem extends Item {
    public static final double RANGE = 64;

    public WhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean stay = player.isShiftKeyDown();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 0.8f, stay ? 1.2f : 1.8f);
        if (level instanceof ServerLevel server) {
            List<BleepEntity> bleeps = server.getEntitiesOfClass(BleepEntity.class, player.getBoundingBox().inflate(RANGE),
                    b -> b.isOwnedBy(player) && !b.asleep() && !b.abandoned() && b.assembly() == 0);
            for (BleepEntity bleep : bleeps) bleep.whistled(stay);
            player.sendOverlayMessage(bleeps.isEmpty() ? Component.translatable("message.bleep.whistle_nobody")
                    : Component.translatable(stay ? "message.bleep.whistle_stay" : "message.bleep.whistle_come", bleeps.size()));
            player.getCooldowns().addCooldown(stack, 20);
        }
        return InteractionResult.SUCCESS;
    }
}
