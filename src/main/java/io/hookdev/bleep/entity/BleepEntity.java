package io.hookdev.bleep.entity;

import com.mojang.serialization.Codec;
import io.hookdev.bleep.block.HomebaseBlock;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * <p>
 * It has a bond with its owner (see {@link Bond}) that grows with care, and a character: it sometimes dawdles over an
 * order, it is crazy about one item ({@link Obsession}), and it drives over to every kind of mob it has not seen yet
 * to have a look and think something dry about it.
 */
public class BleepEntity extends TamableAnimal {
    private static final EntityDataAccessor<Float> BATTERY = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> EMOTION = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTIVITY = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BOND = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> THOUGHT = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<GlobalPos>> HOME = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.OPTIONAL_GLOBAL_POS);
    private static final EntityDataAccessor<Integer> OBSESSION = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> REVEALED = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.BOOLEAN);

    /** The orders from the info screen. */
    public static final int FOLLOW = 0, STAY = 1, GO_HOME = 2;
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
    /** How far it notices a new kind of mob, and how long it looks at it (ticks). */
    public static final double SCAN_RANGE = 10;
    public static final int SCAN_TICKS = 60;
    /** How far it notices its favourite item lying about. */
    public static final double FETCH_RANGE = 8;
    /** Within this range of its owner, things count as done together. */
    public static final double TOGETHER = 16;
    /**
     * How many thoughts there are of each kind: thought.bleep.<kind>.<n> in the language file. The kind is its emotion,
     * or what it is doing when that says more.
     */
    public static final Map<String, Integer> THOUGHTS = Map.ofEntries(
            Map.entry("happy", 5), Map.entry("excited", 3), Map.entry("sad", 3), Map.entry("angry", 3), Map.entry("bored", 4),
            Map.entry("surprised", 2), Map.entry("tired", 3), Map.entry("asleep", 3), Map.entry("charging", 3), Map.entry("llama", 2),
            Map.entry("stubborn", 3), Map.entry("glitch", 2));
    /** What it says about a kind of mob it has no line for (another mod's). */
    public static final String UNKNOWN_MOB = "Never seen one of those. Filing it under weird.";

    /** A short feeling (petted, hit, booting) that shows for a while over the usual face. */
    private Emotion mood = Emotion.HAPPY;
    private int moodTicks, idleTicks, sleptTicks, llamaTicks, thinkTicks, pinTicks;
    private String thoughtKind = "";
    /** Switched off: it sleeps until it is full again, and wakes up with a sad face. */
    private boolean slept;
    /** On its way home (the Go Home button); and whether it was following when it ran empty, to follow again after. */
    private boolean goingHome, followAfterSleep;
    /** Whether its homebase's light is on. */
    private boolean charging;
    /** An order it is dawdling over, and for how long still. */
    private int pendingOrder = -1, dawdleTicks;
    /** The day the counters below are for, and what happened that day. */
    private long bondDay = -1, favouriteDay = -1, lastTogetherDay = -1;
    private int petsToday, giftsToday, fightsToday;
    /** The kinds of mob and the biomes it has seen. */
    private final Set<String> seenMobs = new LinkedHashSet<>(), seenBiomes = new LinkedHashSet<>();
    /** A new kind of mob it is having a look at. */
    private @Nullable Mob scanning;
    private int scanTicks;
    /** Its favourite item, lying about, that it is going to fetch. */
    private @Nullable ItemEntity fetching;

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
        this.goalSelector.addGoal(5, new FetchGoal());
        this.goalSelector.addGoal(5, new CircleOwnerGoal());
        this.goalSelector.addGoal(6, new ScanGoal());
        this.goalSelector.addGoal(7, new FollowOwnerGoal(this, 1.0, 6, 2));
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
        builder.define(OBSESSION, -1);
        builder.define(REVEALED, false);
    }

    // ---------- what the info screen shows ----------

    public float battery() {
        return this.entityData.get(BATTERY);
    }

    public void setBattery(float percent) {
        this.entityData.set(BATTERY, Mth.clamp(percent, 0, FULL));
        if (percent <= 0 && !slept) { // switched off: a small dip in the bond
            slept = true;
            sleptTicks = 0;
            followAfterSleep = !isOrderedToSit();
            changeBond(Bond.SWITCHED_OFF);
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
        this.entityData.set(BOND, Mth.clamp(bond, 0, Bond.MAX));
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

    /** Its favourite item, or null before it has started up. */
    public @Nullable Obsession obsession() {
        return Obsession.byIndex(this.entityData.get(OBSESSION));
    }

    public void setObsession(int index) {
        this.entityData.set(OBSESSION, index);
    }

    /** Whether its owner has found out what its favourite item is (the info screen shows ??? until then). */
    public boolean revealed() {
        return this.entityData.get(REVEALED);
    }

    public Set<String> seenMobs() {
        return seenMobs;
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

    /** The screen comes on for the first time: a start-up screen, then a very happy face. It picks its favourite item. */
    public void boot() {
        feel(Emotion.BOOT, 50);
        if (obsession() == null) setObsession(getRandom().nextInt(Obsession.ALL.size()));
    }

    // ---------- the bond ----------

    /** Raises or lowers the bond; a new level is celebrated. */
    public void changeBond(int delta) {
        if (delta == 0 || level().isClientSide()) return;
        int before = bond();
        setBond(before + delta);
        if (Bond.level(bond()) > Bond.level(before)) {
            feel(Emotion.EXCITED, 60);
            if (getOwner() instanceof Player owner) {
                owner.sendOverlayMessage(Component.translatable("message.bleep.level_up", getDisplayName(), Component.translatable(Bond.key(bond()))));
            }
        }
        if (bond() > before) level().broadcastEntityEvent(this, (byte) 7); // hearts
    }

    /** The counters are per Minecraft day. */
    private long today() {
        long day = level().getGameTime() / Bond.DAY;
        if (day != bondDay) {
            bondDay = day;
            petsToday = giftsToday = fightsToday = 0;
        }
        return day;
    }

    /** Sneak-right-click: hearts, a happy wiggle, and a little more bond (five times a day). */
    public void pet() {
        today();
        feel(Emotion.EXCITED, 60);
        if (petsToday < Bond.PETS_PER_DAY) {
            petsToday++;
            changeBond(Bond.PET);
        } else {
            level().broadcastEntityEvent(this, (byte) 7);
        }
    }

    /**
     * Right-click with something: its favourite item (+10 once a day) or a treat (a cookie or redstone, +3, five a
     * day). Returns whether it took it.
     */
    public boolean give(Player player, ItemStack stack) {
        long day = today();
        Obsession favourite = obsession();
        if (favourite != null && favourite.matches().test(stack)) {
            reveal(player);
            feel(Emotion.EXCITED, 80);
            if (favouriteDay == day) return false; // it already got one today: it loves it, but keeps it for tomorrow
            favouriteDay = day;
            changeBond(Bond.FAVOURITE);
            return true;
        }
        if (stack.is(Items.COOKIE) || stack.is(Items.REDSTONE)) {
            if (giftsToday >= Bond.GIFTS_PER_DAY) {
                player.sendOverlayMessage(Component.translatable("message.bleep.full_of_gifts", getDisplayName()));
                return false;
            }
            giftsToday++;
            feel(Emotion.EXCITED, 60);
            changeBond(Bond.GIFT);
            return true;
        }
        return false;
    }

    /** Its owner won a fight near it. */
    public void foughtTogether() {
        today();
        if (slept || fightsToday >= Bond.FIGHTS_PER_DAY) return;
        fightsToday++;
        changeBond(Bond.FIGHT);
    }

    /** Its owner finds out what its favourite item is. */
    public void reveal(@Nullable Player player) {
        if (revealed() || obsession() == null) return;
        this.entityData.set(REVEALED, true);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.bleep.loves", getDisplayName(), Component.translatable(obsession().nameKey())));
        }
    }

    // ---------- orders ----------

    /**
     * An order from the info screen. Now and then it dawdles first (less as the bond grows): a stubborn face, a turn
     * on the spot, and then it does it anyway. When it is in danger it always listens. False if it cannot do it.
     */
    public boolean order(int order, float roll) {
        if (order == GO_HOME && !homeHere()) return false;
        if (roll < Bond.dawdleChance(bond()) && !inDanger() && !slept) {
            pendingOrder = order;
            dawdleTicks = 40 + getRandom().nextInt(20);
            feel(Emotion.STUBBORN, dawdleTicks);
            getNavigation().stop();
            return true;
        }
        pendingOrder = -1;
        dawdleTicks = 0;
        switch (order) {
            case FOLLOW -> follow();
            case STAY -> stay();
            case GO_HOME -> goHome();
            default -> {
                return false;
            }
        }
        return true;
    }

    public boolean dawdling() {
        return dawdleTicks > 0;
    }

    /** Low battery, a fight, or an owner who is badly hurt: no time for games. */
    public boolean inDanger() {
        if (battery() < TIRED || hurtRecently(this)) return true;
        LivingEntity owner = getOwner();
        return owner != null && (owner.getHealth() < 6 || hurtRecently(owner));
    }

    private static boolean hurtRecently(LivingEntity entity) {
        return entity.getLastHurtByMob() != null && entity.tickCount - entity.getLastHurtByMobTimestamp() < 100;
    }

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
        if (obsession() == null) setObsession(getRandom().nextInt(Obsession.ALL.size())); // a bleep from before favourites
        boolean moving = getDeltaMovement().horizontalDistanceSqr() > 1.0e-4;
        // a homebase that was broken: homeless, and sad about it
        if (tickCount % 20 == 0 && homeHere() && level().isLoaded(home().pos()) && !HomebaseBlock.is(level().getBlockState(home().pos()))) {
            setHome(null);
            feel(Emotion.SAD, 80);
            changeBond(Bond.HOMEBASE_BROKEN);
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
        if (dawdleTicks > 0) dawdle();
        if (moodTicks > 0 && --moodTicks == 0 && mood == Emotion.BOOT) feel(Emotion.EXCITED, 40);
        boolean busy = moving || home || (getOwner() != null && distanceToSqr(getOwner()) < 36 && !isOrderedToSit());
        idleTicks = busy ? 0 : idleTicks + 1;
        if (llamaTicks > 0) llamaTicks--;
        if (tickCount % 20 == 0 && !slept && !level().getEntitiesOfClass(Llama.class, getBoundingBox().inflate(8)).isEmpty()) {
            if (llamaTicks == 0 && moodTicks == 0) feel(Emotion.SURPRISED, 30);
            llamaTicks = 40;
        }
        if (!slept) {
            lookForNewMobs();
            if (tickCount % 40 == 0) lookForFavourite();
            if (tickCount % 40 == 0) noticeBiome();
        }
        if (tickCount % 100 == 0) countDaysTogether();
        Emotion emotion = currentEmotion();
        Activity activity = currentActivity(home);
        this.entityData.set(EMOTION, emotion.ordinal());
        this.entityData.set(ACTIVITY, activity.ordinal());
        think(emotion, activity);
    }

    /** A stubborn turn on the spot, then it does what it was told. */
    private void dawdle() {
        getNavigation().stop();
        float yaw = getYRot() + 9;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        if (--dawdleTicks == 0 && pendingOrder >= 0) order(pendingOrder, 1);
    }

    /** It is standing still while it dawdles. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || asleep() || dawdleTicks > 0;
    }

    /** A new kind of mob in sight: it goes to have a look (ScanGoal), then thinks something about it. */
    private void lookForNewMobs() {
        if (scanning != null) {
            if (!scanning.isAlive() || distanceToSqr(scanning) > 24 * 24) {
                scanning = null;
                return;
            }
            getLookControl().setLookAt(scanning);
            if (++scanTicks >= SCAN_TICKS) finishScan(scanning);
            return;
        }
        if (tickCount % 20 != 0 || dawdleTicks > 0) return;
        scanning = level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(SCAN_RANGE),
                        m -> !(m instanceof BleepEntity) && m.isAlive() && !seenMobs.contains(mobId(m)) && hasLineOfSight(m))
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        scanTicks = 0;
        if (scanning != null) feel(Emotion.SURPRISED, 20);
    }

    /** Its line about a mob: mob.bleep.<name> for Minecraft's own, mob.bleep.<mod>.<name> for another mod's. */
    public static String mobThought(String id) {
        Identifier key = Identifier.parse(id);
        return "mob.bleep." + (key.getNamespace().equals("minecraft") ? key.getPath() : key.getNamespace() + "." + key.getPath());
    }

    private static String mobId(Mob mob) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
    }

    /** Seen it: remembered, a thought about it (told to its owner if near), and a little bond if they saw it together. */
    private void finishScan(Mob mob) {
        scanning = null;
        String id = mobId(mob);
        if (!seenMobs.add(id)) return;
        String key = mobThought(id);
        pinThought(key);
        if (getOwner() instanceof Player owner && distanceToSqr(owner) < TOGETHER * TOGETHER) {
            owner.sendOverlayMessage(Component.translatable("message.bleep.thinks", getDisplayName(), Component.translatableWithFallback(key, UNKNOWN_MOB)));
            changeBond(Bond.DISCOVERY);
        }
    }

    /** A new biome, found together with its owner. */
    private void noticeBiome() {
        String biome = level().getBiome(blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("");
        if (biome.isEmpty() || !seenBiomes.add(biome) || seenBiomes.size() == 1) return;
        if (getOwner() != null && distanceToSqr(getOwner()) < TOGETHER * TOGETHER) changeBond(Bond.DISCOVERY);
    }

    /** A day together: a little more bond; days it did not see its owner: a little less. */
    private void countDaysTogether() {
        long day = level().getGameTime() / Bond.DAY;
        LivingEntity owner = getOwner();
        if (owner == null || distanceToSqr(owner) > 32 * 32) return;
        if (lastTogetherDay < 0) {
            lastTogetherDay = day;
            return;
        }
        if (day <= lastTogetherDay) return;
        long apart = Math.min(day - lastTogetherDay - 1, Bond.DAYS_APART_COUNTED);
        changeBond((int) apart * Bond.DAY_APART + Bond.DAY_TOGETHER);
        lastTogetherDay = day;
    }

    /** Its favourite item lying about: it is going to get it (FetchGoal). */
    private void lookForFavourite() {
        Obsession favourite = obsession();
        if (favourite == null || fetching != null || !getMainHandItem().isEmpty()) return;
        fetching = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(FETCH_RANGE), item -> favourite.matches().test(item.getItem()))
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    /** Picks up its favourite item: so excited it glitches. */
    private void pickUp(ItemEntity item) {
        setItemSlot(EquipmentSlot.MAINHAND, item.getItem().split(1));
        if (item.getItem().isEmpty()) item.discard();
        fetching = null;
        feel(Emotion.GLITCH, 40);
        reveal(getOwner() instanceof Player owner ? owner : null);
    }

    /** ...and gives it to its owner anyway. */
    private void handOver(Player owner) {
        ItemStack stack = getMainHandItem().copy();
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!owner.getInventory().add(stack)) {
            level().addFreshEntity(new ItemEntity(level(), owner.getX(), owner.getY(), owner.getZ(), stack));
        }
        feel(Emotion.EXCITED, 40);
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
        if (dawdleTicks > 0) return Activity.DAWDLING;
        if (goingHome) return Activity.GOING_HOME;
        if (llamaTicks > 0) return Activity.HIDING;
        if (!getMainHandItem().isEmpty() || fetching != null) return Activity.FETCHING;
        if (scanning != null) return Activity.LOOKING;
        return isOrderedToSit() ? Activity.STAYING : Activity.FOLLOWING;
    }

    /** Keeps this thought on its screen for a while, whatever else happens. */
    private void pinThought(String key) {
        this.entityData.set(THOUGHT, key);
        pinTicks = THINK_EVERY;
    }

    /**
     * A new thought now and then, and straight away when what it feels or does changes. Until its owner knows its
     * favourite item, it sometimes hints at it.
     */
    private void think(Emotion emotion, Activity activity) {
        if (pinTicks > 0) {
            pinTicks--;
            return;
        }
        String kind = emotion == Emotion.ASLEEP ? "asleep" : activity == Activity.CHARGING ? "charging"
                : activity == Activity.HIDING ? "llama" : emotion == Emotion.BOOT ? "excited" : emotion.texture();
        if (kind.equals(thoughtKind) && ++thinkTicks < THINK_EVERY) return;
        thoughtKind = kind;
        thinkTicks = 0;
        Obsession favourite = obsession();
        if (favourite != null && !revealed() && (kind.equals("happy") || kind.equals("bored")) && getRandom().nextFloat() < 0.3f) {
            this.entityData.set(THOUGHT, favourite.hintKey());
        } else {
            this.entityData.set(THOUGHT, "thought.bleep." + kind + "." + getRandom().nextInt(THOUGHTS.getOrDefault(kind, 1)));
        }
    }

    /** A tired bleep drives slower. */
    @Override
    public float getSpeed() {
        return battery() < TIRED ? super.getSpeed() * 0.6f : super.getSpeed();
    }

    /**
     * Damage drains the battery instead of health: a bleep never dies, it only runs empty. Only /kill and the void
     * still remove it. Hit by its owner it is angry, and the bond suffers.
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
        if (source.getEntity() instanceof Player player && isOwnedBy(player)) {
            feel(Emotion.ANGRY, 60);
            changeBond(Bond.HIT_BY_OWNER);
        } else {
            feel(Emotion.SURPRISED, 60);
        }
        return true;
    }

    /**
     * Right-click with an empty hand opens its info screen, sneak-right-click pets it, right-click with its favourite
     * item or a treat gives it. Anything else in your hand (a name tag, a lead) works the usual way.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isOwnedBy(player)) return super.mobInteract(player, hand);
        if (!stack.isEmpty()) {
            if (level().isClientSide()) return super.mobInteract(player, hand);
            if (asleep()) return super.mobInteract(player, hand);
            if (give(player, stack)) {
                stack.consume(1, player);
                return InteractionResult.SUCCESS;
            }
            return super.mobInteract(player, hand);
        }
        if (!player.isSecondaryUseActive()) {
            if (level().isClientSide()) io.hookdev.bleep.client.ClientHooks.openInfo(this);
            return InteractionResult.SUCCESS;
        }
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (asleep()) {
            player.sendOverlayMessage(Component.translatable("message.bleep.asleep", getDisplayName()));
        } else {
            pet();
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
        output.putInt("obsession", this.entityData.get(OBSESSION));
        output.putBoolean("revealed", revealed());
        output.putLong("bond_day", bondDay);
        output.putInt("pets_today", petsToday);
        output.putInt("gifts_today", giftsToday);
        output.putInt("fights_today", fightsToday);
        output.putLong("favourite_day", favouriteDay);
        output.putLong("last_together_day", lastTogetherDay);
        output.store("seen_mobs", Codec.STRING.listOf(), List.copyOf(seenMobs));
        output.store("seen_biomes", Codec.STRING.listOf(), List.copyOf(seenBiomes));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(BATTERY, Mth.clamp(input.getFloatOr("battery", FULL), 0, FULL));
        slept = input.getBooleanOr("slept", false);
        goingHome = input.getBooleanOr("going_home", false);
        followAfterSleep = input.getBooleanOr("follow_after_sleep", false);
        setBond(input.getIntOr("bond", 0));
        this.entityData.set(HOME, input.read("home", GlobalPos.CODEC));
        setObsession(input.getIntOr("obsession", -1));
        this.entityData.set(REVEALED, input.getBooleanOr("revealed", false));
        bondDay = input.getLongOr("bond_day", -1);
        petsToday = input.getIntOr("pets_today", 0);
        giftsToday = input.getIntOr("gifts_today", 0);
        fightsToday = input.getIntOr("fights_today", 0);
        favouriteDay = input.getLongOr("favourite_day", -1);
        lastTogetherDay = input.getLongOr("last_together_day", -1);
        seenMobs.clear();
        input.read("seen_mobs", Codec.STRING.listOf()).ifPresent(seenMobs::addAll);
        seenBiomes.clear();
        input.read("seen_biomes", Codec.STRING.listOf()).ifPresent(seenBiomes::addAll);
    }

    /** Not when it is told to stay, is going home, or is switched off. */
    private boolean free() {
        return !slept && !isOrderedToSit() && !goingHome && dawdleTicks == 0;
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

    /** Its owner holds its favourite item: round and round it goes. */
    private class CircleOwnerGoal extends Goal {
        private double angle;

        CircleOwnerGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        private @Nullable Player owner() {
            Obsession favourite = obsession();
            if (favourite == null || !(getOwner() instanceof Player owner) || distanceToSqr(owner) > 10 * 10) return null;
            return favourite.matches().test(owner.getMainHandItem()) || favourite.matches().test(owner.getOffhandItem()) ? owner : null;
        }

        @Override
        public boolean canUse() {
            return free() && getMainHandItem().isEmpty() && owner() != null;
        }

        @Override
        public void start() {
            Player owner = owner();
            angle = Math.atan2(getZ() - owner.getZ(), getX() - owner.getX());
            reveal(owner);
        }

        @Override
        public void tick() {
            Player owner = owner();
            if (owner == null) return;
            angle += 0.15;
            getNavigation().moveTo(owner.getX() + Math.cos(angle) * 2.5, owner.getY(), owner.getZ() + Math.sin(angle) * 2.5, 1.2);
            getLookControl().setLookAt(owner);
            if (moodTicks < 5) feel(Emotion.EXCITED, 10);
        }
    }

    /** Its favourite item lying about: it drives over, picks it up, and brings it to its owner. */
    private class FetchGoal extends Goal {
        /**
         * Its own count: Minecraft runs a mob's goals every other tick, on odd or even ticks depending on its id, so
         * tickCount % 10 might never come round.
         */
        private int ticks;

        FetchGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!free()) return false;
            if (fetching != null) return fetching.isAlive();
            return !getMainHandItem().isEmpty() && getOwner() instanceof Player owner && distanceToSqr(owner) < 24 * 24;
        }

        @Override
        public void tick() {
            if (fetching != null) {
                if (!fetching.isAlive()) {
                    fetching = null;
                } else if (distanceToSqr(fetching) < 1.5 * 1.5) {
                    pickUp(fetching);
                } else if (ticks++ % 5 == 0) {
                    getNavigation().moveTo(fetching, 1.1);
                }
            } else if (getOwner() instanceof Player owner) {
                if (distanceToSqr(owner) < 2.0 * 2.0) {
                    handOver(owner);
                } else if (ticks++ % 5 == 0) {
                    getNavigation().moveTo(owner, 1.1);
                }
            }
        }
    }

    /** A new kind of mob: it drives closer to have a look, unless it was told to stay. */
    private class ScanGoal extends Goal {
        private int ticks;

        ScanGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return free() && scanning != null && distanceToSqr(scanning) > 4 * 4;
        }

        @Override
        public void tick() {
            if (scanning != null && ticks++ % 5 == 0) getNavigation().moveTo(scanning, 1.0);
        }
    }
}
