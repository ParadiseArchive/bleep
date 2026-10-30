package io.hookdev.bleep.entity;

import io.hookdev.bleep.BleepContent;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown ball: it bounces off what it hits, a bit less every time, and when it has rolled out it lies there as an
 * item for a bleep to fetch. A bleep it hits catches it.
 */
public class BallEntity extends ThrowableItemProjectile {
    /** Below this speed (blocks a tick) it stops bouncing; it never bounces more often than this. */
    private static final double REST = 0.12;
    private static final int MAX_BOUNCES = 8;
    private int bounces;

    public BallEntity(EntityType<? extends BallEntity> type, Level level) {
        super(type, level);
    }

    public BallEntity(Level level, LivingEntity owner, ItemStack stack) {
        super(BleepContent.BALL_ENTITY.get(), owner, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return BleepContent.BALL.get();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        Vec3 v = getDeltaMovement();
        Direction.Axis axis = hit.getDirection().getAxis();
        // back off the face it hit, then bounce: the speed into that face turns round and halves, the rest slows a little
        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i());
        setPos(hit.getLocation().add(normal.scale(0.05)));
        v = new Vec3(axis == Direction.Axis.X ? -v.x * 0.5 : v.x * 0.8, axis == Direction.Axis.Y ? -v.y * 0.5 : v.y * 0.8, axis == Direction.Axis.Z ? -v.z * 0.5 : v.z * 0.8);
        setDeltaMovement(v);
        needsSync = true;
        if (level() instanceof ServerLevel server) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_JUMP_SMALL, SoundSource.NEUTRAL, 0.4f, 1.4f + random.nextFloat() * 0.3f);
            if (v.length() < REST || ++bounces > MAX_BOUNCES) rest(server);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(level() instanceof ServerLevel server)) return;
        if (hit.getEntity() instanceof BleepEntity bleep && bleep.catchBall(getItem().copy())) { // caught!
            discard();
            return;
        }
        setDeltaMovement(getDeltaMovement().scale(-0.3));
        needsSync = true;
        if (++bounces > MAX_BOUNCES) rest(server);
    }

    /** It has stopped: a ball lying on the ground. */
    private void rest(ServerLevel server) {
        ItemEntity item = new ItemEntity(server, getX(), getY(), getZ(), getItem().copy());
        item.setDeltaMovement(getDeltaMovement().scale(0.5));
        if (getOwner() != null) item.setThrower(getOwner());
        server.addFreshEntity(item);
        discard();
    }
}
