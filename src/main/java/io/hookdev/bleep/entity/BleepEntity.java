package io.hookdev.bleep.entity;

import io.hookdev.bleep.block.HomebaseBlock;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A bleep: a little robot on tracks with a display for a face. It has no health, only a battery: driving and damage
 * drain it, and an empty bleep switches off with a last sleepy face instead of dying. With a homebase it goes home
 * to charge; without one it charges slowly where it is, tires sooner and gets bored sooner. Its display shows how it
 * feels, and its info screen what it is doing and thinking.
 */
public class BleepEntity extends TamableAnimal {
    private static final EntityDataAccessor<Float> BATTERY = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> EMOTION = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTIVITY = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BOND = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> THOUGHT = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<GlobalPos>> HOME = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.OPTIONAL_GLOBAL_POS);

    /** The battery, in percent. Below TIRED it looks tired and drives slower. */
    public static final float FULL = 100, TIRED = 20;
    /** Battery per tick while it drives or swims (a full battery lasts about 55 minutes of driving). */
    public static final float DRIVE_COST = 0.0015f;
    /** Without a homebase it uses this much more. */
    public static final float HOMELESS_COST = 1.5f;
    /** Battery per point of damage (a zombie hit of 3 costs 6%). */
    public static final float DAMAGE_COST = 2;
    /** Battery per tick on its homebase (empty to full in 50 seconds), and asleep without one (100 seconds). */
    public static final float HOME_CHARGE = 0.1f, SLEEP_CHARGE = 0.05f;
    /** Ticks without anything to do before it gets bored (half that without a homebase). */
    public static final int BORED_AFTER = 2400;
    /** Ticks it lies switched off where it ran empty before it goes home. */
    public static final int GOES_HOME_AFTER = 40;
    /** Further from home than this (blocks), it does not drive but beams home. */
    public static final double BEAM_HOME_FROM = 32;
    /** Ticks between new thoughts. */
    public static final int THINK_EVERY = 600;
    /**
     * How many thoughts there are of each kind: thought.bleep.<kind>.<n> in the language file. The kind is its emotion,
     * or what it is doing when that says more.
     */
    public static final Map<String, Integer> THOUGHTS = Map.ofEntries(
            Map.entry("happy", 5), Map.entry("excited", 3), Map.entry("sad", 3), Map.entry("angry", 3), Map.entry("bored", 4),
            Map.entry("surprised", 2), Map.entry("tired", 3), Map.entry("asleep", 3), Map.entry("charging", 3), Map.entry("llama", 2));

    /** A short feeling (petted, hit, booting) that shows for a while over the usual face. */
    private Emotion mood = Emotion.HAPPY;
    private int moodTicks, idleTicks, sleptTicks, llamaTicks, thinkTicks;
    private String thoughtKind = "";
    /** Switched off: it sleeps until it is full again, and wakes up with a sad face. */
    private boolean slept;
    /** On its way home (the Go Home button); and whether it was following when it ran empty, to follow again after. */
    private boolean goingHome, followAfterSleep;
    /** Whether its homebase's light is on. */
    private boolean charging;

    public BleepEntity(EntityType<? extends BleepEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new GoHomeGoal());
        this.goalSelector.addGoal(3, new SitWhenOrderedToGoal(this));
        // bleeps are afraid of llamas, for reasons nobody knows
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Llama.class, 10, 1.0, 1.3));
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
        builder.define(ACTIVITY, Activity.FOLLOWING.ordinal());
        builder.define(BOND, 0);
        builder.define(THOUGHT, "");
        builder.define(HOME, Optional.empty());
    }

    // ---------- what the info screen shows ----------

    public float battery() {
        return this.entityData.get(BATTERY);
    }

    public void setBattery(float percent) {
        this.entityData.set(BATTERY, Mth.clamp(percent, 0, FULL));
        if (percent <= 0 && !slept) {
            slept = true;
            sleptTicks = 0;
            followAfterSleep = !isOrderedToSit();
        }
    }

    public Emotion emotion() {
        return Emotion.byId(this.entityData.get(EMOTION));
    }

    public Activity activity() {
        return Activity.byId(this.entityData.get(ACTIVITY));
    }

    /** How much it likes its owner, 0 to 100. */
    public int bond() {
        return this.entityData.get(BOND);
    }

    public void setBond(int bond) {
        this.entityData.set(BOND, Mth.clamp(bond, 0, 100));
    }

    /** What it is thinking: a key in the language file, or empty. */
    public String thought() {
        return this.entityData.get(THOUGHT);
    }

    public @Nullable GlobalPos home() {
        return this.entityData.get(HOME).orElse(null);
    }

    public void setHome(@Nullable GlobalPos home) {
        if (home == null && goingHome) goingHome = false;
        setCharging(false);
        this.entityData.set(HOME, Optional.ofNullable(home));
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

    // ---------- the buttons on the info screen ----------

    public void follow() {
        goingHome = false;
        setOrderedToSit(false);
        getNavigation().stop();
    }

    public void stay() {
        goingHome = false;
        setOrderedToSit(true);
        getNavigation().stop();
    }

    /** Drives home and stays there; a homeless bleep does not know where to go. */
    public boolean goHome() {
        if (!homeHere()) return false;
        setOrderedToSit(false);
        goingHome = true;
        return true;
    }

    // ---------- home ----------

    /** Its homebase is in this dimension. */
    public boolean homeHere() {
        GlobalPos home = home();
        return home != null && home.dimension().equals(level().dimension());
    }

    /** Standing on its homebase. */
    public boolean onHome() {
        return homeHere() && blockPosition().equals(home().pos()) && HomebaseBlock.is(level().getBlockState(home().pos()));
    }

    /** Parks on its homebase, facing out, and stays there. */
    public void dock() {
        BlockPos pos = home().pos();
        BlockState state = level().getBlockState(pos);
        float yaw = HomebaseBlock.is(state) ? state.getValue(HomebaseBlock.FACING).toYRot() : getYRot();
        getNavigation().stop();
        snapTo(pos.getX() + 0.5, pos.getY() + 2 / 16.0, pos.getZ() + 0.5, yaw, 0);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        goingHome = false;
        setOrderedToSit(true);
    }

    /** Too far to drive (or stuck): it beams itself home, with a little flash. */
    public void beamHome() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.6, getZ(), 12, 0.3, 0.4, 0.3, 0.1);
            level.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.4f, 1.8f);
        }
        dock();
    }

    private void setCharging(boolean on) {
        if (charging == on) return;
        charging = on;
        if (homeHere()) HomebaseBlock.setCharging(level(), home().pos(), on);
    }

    // ---------- every tick ----------

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) return;
        boolean moving = getDeltaMovement().horizontalDistanceSqr() > 1.0e-4;
        // a homebase that was broken: homeless, and a little sad
        if (tickCount % 20 == 0 && homeHere() && level().isLoaded(home().pos()) && !HomebaseBlock.is(level().getBlockState(home().pos()))) {
            setHome(null);
            feel(Emotion.SAD, 80);
        }
        boolean home = onHome();
        if (slept) {
            sleptTicks++;
            if (!home && homeHere() && sleptTicks >= GOES_HOME_AFTER) {
                beamHome();
                home = true;
            }
            setBattery(battery() + (home ? HOME_CHARGE : SLEEP_CHARGE));
            if (battery() >= FULL) { // awake again, and a little sad about it
                slept = false;
                feel(Emotion.SAD, 80);
                if (followAfterSleep) follow();
            }
        } else if (home) {
            setBattery(battery() + HOME_CHARGE);
        } else if (moving) {
            setBattery(battery() - DRIVE_COST * (home() == null ? HOMELESS_COST : 1));
        }
        setCharging(home && battery() < FULL);
        if (moodTicks > 0 && --moodTicks == 0 && mood == Emotion.BOOT) feel(Emotion.EXCITED, 40);
        boolean busy = moving || home || (getOwner() != null && distanceToSqr(getOwner()) < 36 && !isOrderedToSit());
        idleTicks = busy ? 0 : idleTicks + 1;
        if (llamaTicks > 0) llamaTicks--;
        if (tickCount % 20 == 0 && !slept && !level().getEntitiesOfClass(Llama.class, getBoundingBox().inflate(8)).isEmpty()) {
            if (llamaTicks == 0 && moodTicks == 0) feel(Emotion.SURPRISED, 30);
            llamaTicks = 40;
        }
        Emotion emotion = currentEmotion();
        Activity activity = currentActivity(home);
        this.entityData.set(EMOTION, emotion.ordinal());
        this.entityData.set(ACTIVITY, activity.ordinal());
        think(emotion, activity);
    }

    private Emotion currentEmotion() {
        if (slept) return Emotion.ASLEEP;
        if (moodTicks > 0) return mood;
        if (battery() < TIRED) return Emotion.TIRED;
        if (idleTicks > (home() == null ? BORED_AFTER / 2 : BORED_AFTER)) return Emotion.BORED;
        return Emotion.HAPPY;
    }

    private Activity currentActivity(boolean home) {
        if (moodTicks > 0 && mood == Emotion.BOOT) return Activity.STARTING;
        if (home) return battery() < FULL ? Activity.CHARGING : slept ? Activity.SLEEPING : Activity.AT_HOME;
        if (slept) return Activity.SLEEPING;
        if (goingHome) return Activity.GOING_HOME;
        if (llamaTicks > 0) return Activity.HIDING;
        return isOrderedToSit() ? Activity.STAYING : Activity.FOLLOWING;
    }

    /** A new thought now and then, and straight away when what it feels or does changes. */
    private void think(Emotion emotion, Activity activity) {
        String kind = emotion == Emotion.ASLEEP ? "asleep" : activity == Activity.CHARGING ? "charging"
                : activity == Activity.HIDING ? "llama" : emotion == Emotion.BOOT ? "excited" : emotion.texture();
        if (kind.equals(thoughtKind) && ++thinkTicks < THINK_EVERY) return;
        thoughtKind = kind;
        thinkTicks = 0;
        this.entityData.set(THOUGHT, "thought.bleep." + kind + "." + getRandom().nextInt(THOUGHTS.getOrDefault(kind, 1)));
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
     * Empty hand: right-click opens its info screen, sneak-right-click pets it. Anything else in your hand (a name
     * tag, a lead) works the usual way.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty() || !isOwnedBy(player)) return super.mobInteract(player, hand);
        if (!player.isSecondaryUseActive()) {
            if (level().isClientSide()) io.hookdev.bleep.client.ClientHooks.openInfo(this);
            return InteractionResult.SUCCESS;
        }
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (asleep()) {
            player.sendOverlayMessage(Component.translatable("message.bleep.asleep", getDisplayName()));
        } else {
            feel(Emotion.EXCITED, 60);
            level().broadcastEntityEvent(this, (byte) 7); // hearts
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
        output.putBoolean("going_home", goingHome);
        output.putBoolean("follow_after_sleep", followAfterSleep);
        output.putInt("bond", bond());
        if (home() != null) output.store("home", GlobalPos.CODEC, home());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setBattery(input.getFloatOr("battery", FULL));
        slept = input.getBooleanOr("slept", false);
        goingHome = input.getBooleanOr("going_home", false);
        followAfterSleep = input.getBooleanOr("follow_after_sleep", false);
        setBond(input.getIntOr("bond", 0));
        this.entityData.set(HOME, input.read("home", GlobalPos.CODEC));
    }

    /** Drives to its homebase after Go Home; beams there when it is far away or does not get there in 15 seconds. */
    private class GoHomeGoal extends Goal {
        private int ticks;

        GoHomeGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return goingHome && homeHere() && !slept;
        }

        @Override
        public void start() {
            ticks = 0;
        }

        @Override
        public void tick() {
            BlockPos pos = home().pos();
            double distance = Math.sqrt(distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos)));
            if (distance > BEAM_HOME_FROM || ++ticks > 300) {
                beamHome();
            } else if (distance < 1.0) {
                dock();
            } else if (ticks % 20 == 1) {
                getNavigation().moveTo(pos.getX() + 0.5, pos.getY() + 0.125, pos.getZ() + 0.5, 1.0);
            }
        }
    }
}
