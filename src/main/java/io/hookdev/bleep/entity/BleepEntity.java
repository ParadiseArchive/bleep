package io.hookdev.bleep.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A bleep: a little robot on tracks with a display for a face. It has no health, only a battery: driving and damage
 * drain it, and an empty bleep switches off with a last sleepy face instead of dying. Its display shows how it feels.
 */
public class BleepEntity extends TamableAnimal {
    private static final EntityDataAccessor<Float> BATTERY = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> EMOTION = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);

    /** The battery, in percent. Below TIRED it looks tired and drives slower. */
    public static final float FULL = 100, TIRED = 20;
    /** Battery per tick while it drives or swims (a full battery lasts about 55 minutes of driving). */
    public static final float DRIVE_COST = 0.0015f;
    /** Battery per point of damage (a zombie hit of 3 costs 6%). */
    public static final float DAMAGE_COST = 2;
    /** Battery per tick while it sleeps where it is: for now it charges itself, until it has a homebase to go to. */
    public static final float SLEEP_CHARGE = 0.05f;
    /** Ticks without anything to do before it gets bored. */
    public static final int BORED_AFTER = 2400;

    /** A short feeling (petted, hit, booting) that shows for a while over the usual face. */
    private Emotion mood = Emotion.HAPPY;
    private int moodTicks, idleTicks;
    /** Switched off: it sleeps until it is full again, and wakes up with a sad face. */
    private boolean slept;

    public BleepEntity(EntityType<? extends BleepEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        // bleeps are afraid of llamas, for reasons nobody knows
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Llama.class, 10, 1.0, 1.3));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 6, 2));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BATTERY, FULL);
        builder.define(EMOTION, Emotion.HAPPY.ordinal());
    }

    public float battery() {
        return this.entityData.get(BATTERY);
    }

    public void setBattery(float percent) {
        this.entityData.set(BATTERY, Mth.clamp(percent, 0, FULL));
        if (percent <= 0) slept = true;
    }

    public Emotion emotion() {
        return Emotion.byId(this.entityData.get(EMOTION));
    }

    /** Switched off after running empty, until it is charged full again. */
    public boolean asleep() {
        return level().isClientSide() ? emotion() == Emotion.ASLEEP : slept;
    }

    /** Shows a feeling for this many ticks. */
    public void feel(Emotion emotion, int ticks) {
        this.mood = emotion;
        this.moodTicks = ticks;
        this.idleTicks = 0;
    }

    /** The screen comes on for the first time: a start-up screen, then a very happy face. */
    public void boot() {
        feel(Emotion.BOOT, 50);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) return;
        boolean moving = getDeltaMovement().horizontalDistanceSqr() > 1.0e-4;
        if (slept) {
            setBattery(battery() + SLEEP_CHARGE);
            if (battery() >= FULL) { // awake again, and a little sad about it
                slept = false;
                feel(Emotion.SAD, 80);
            }
        } else if (moving) {
            setBattery(battery() - DRIVE_COST);
        }
        if (moodTicks > 0 && --moodTicks == 0 && mood == Emotion.BOOT) feel(Emotion.EXCITED, 40);
        boolean busy = moving || (getOwner() != null && distanceToSqr(getOwner()) < 36 && !isOrderedToSit());
        idleTicks = busy ? 0 : idleTicks + 1;
        if (tickCount % 20 == 0 && !asleep() && moodTicks == 0
                && !level().getEntitiesOfClass(Llama.class, getBoundingBox().inflate(8)).isEmpty()) {
            feel(Emotion.SURPRISED, 30);
        }
        this.entityData.set(EMOTION, currentEmotion().ordinal());
    }

    private Emotion currentEmotion() {
        if (asleep()) return Emotion.ASLEEP;
        if (moodTicks > 0) return mood;
        if (battery() < TIRED) return Emotion.TIRED;
        if (idleTicks > BORED_AFTER) return Emotion.BORED;
        return Emotion.HAPPY;
    }

    /** An empty bleep does not move until it is charged. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || asleep();
    }

    /** A tired bleep drives slower. */
    @Override
    public float getSpeed() {
        return battery() < TIRED ? super.getSpeed() * 0.6f : super.getSpeed();
    }

    /**
     * Damage drains the battery instead of health: a bleep never dies, it only runs empty. Only /kill and the void
     * still remove it.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurtServer(level, source, damage);
        if (isInvulnerableTo(level, source) || asleep()) return false;
        // let Minecraft do the red flash, the knockback and the sound, but never take more health than one point
        boolean hurt = super.hurtServer(level, source, Math.min(damage, getHealth() - 1));
        setHealth(getMaxHealth());
        if (!hurt) return false;
        setBattery(battery() - damage * DAMAGE_COST);
        feel(source.getEntity() instanceof Player player && isOwnedBy(player) ? Emotion.ANGRY : Emotion.SURPRISED, 60);
        return true;
    }

    /**
     * Empty hand: sneak-right-click pets it, right-click tells it to stay or to follow. Anything else in your hand
     * (a name tag, a lead) works the usual way.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty() || !isOwnedBy(player)) return super.mobInteract(player, hand);
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (asleep()) {
            player.sendOverlayMessage(Component.translatable("message.bleep.asleep", getDisplayName()));
        } else if (player.isSecondaryUseActive()) {
            feel(Emotion.EXCITED, 60);
            level().broadcastEntityEvent(this, (byte) 7); // hearts
        } else {
            setOrderedToSit(!isOrderedToSit());
            getNavigation().stop();
            setTarget(null);
            player.sendOverlayMessage(Component.translatable(isOrderedToSit() ? "message.bleep.stay" : "message.bleep.follow", getDisplayName()));
        }
        return InteractionResult.SUCCESS;
    }

    /** A name tag gives a bleep a name that always shows, like a player's. */
    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        setCustomNameVisible(name != null);
    }

    /** A robot does not need air. */
    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COPPER_GOLEM_HURT;
    }

    /** Bleeps are built, not bred. */
    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putFloat("battery", battery());
        output.putBoolean("slept", slept);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setBattery(input.getFloatOr("battery", FULL));
        slept = input.getBooleanOr("slept", false);
    }
}
