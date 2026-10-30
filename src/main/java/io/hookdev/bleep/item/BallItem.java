package io.hookdev.bleep.item;

import io.hookdev.bleep.entity.BallEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A ball to play with: right-click throws it, it bounces, and your bleep brings it back. */
public class BallItem extends Item {
    public BallItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5f, 1.3f);
        if (level instanceof ServerLevel server) Projectile.spawnProjectileFromRotation(BallEntity::new, server, stack, player, 0, 1.0f, 1.0f);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
