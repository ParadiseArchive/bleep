package io.hookdev.bleep.item;

import io.hookdev.bleep.BleepContent;
import io.hookdev.bleep.entity.BleepEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The Bleep Core, the heart of a bleep. Made of bedrock, so a bleep can never break. For now, using it on a block boots
 * a bleep there that belongs to you; later the core goes together with the parts found around the world.
 */
public class BleepCoreItem extends Item {
    public BleepCoreItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || context.getPlayer() == null) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        BleepEntity bleep = BleepContent.BLEEP.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (bleep == null) return InteractionResult.FAIL;
        // facing the player who built it
        bleep.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, context.getPlayer().getYRot() + 180, 0);
        bleep.setYHeadRot(bleep.getYRot());
        bleep.setYBodyRot(bleep.getYRot());
        bleep.tame(context.getPlayer());
        bleep.boot();
        level.addFreshEntity(bleep);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.6f, 1.8f);
        context.getItemInHand().consume(1, context.getPlayer());
        return InteractionResult.SUCCESS;
    }
}
