package io.hookdev.bleep.entity;

import com.mojang.serialization.Codec;
import io.hookdev.bleep.BleepContent;
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
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
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
    private static final EntityDataAccessor<Integer> ASSEMBLY = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HAT = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STICKER = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    /** What it wears over its eyes, round its body, on its back and round its neck: a byte each (the accessory + 1, 0 for nothing). */
    private static final EntityDataAccessor<Integer> WEAR = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DISPLAY = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> NEWS = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.BOOLEAN);
    /** What it dreams of: "mob:" and a name key, "scene:" and a kind of place, "star" or "strange". */
    private static final EntityDataAccessor<String> DREAM = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> ABANDONED = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MAGNET = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> PARTY = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.BOOLEAN);
    /** Its work while it stays: the jobs that are on (JOB_*), and how far it goes from its spot. */
    private static final EntityDataAccessor<Integer> JOBS = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RADIUS = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    /** Its upgrade chips (Chip bits). */
    private static final EntityDataAccessor<Integer> CHIPS = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    /** Treasure hunt: where it buried something (the client draws warmer and colder from it). */
    private static final EntityDataAccessor<Optional<BlockPos>> TREASURE = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    /** The orders from the info screen. */
    public static final int FOLLOW = 0, STAY = 1, GO_HOME = 2;
    /** The battery, in percent. Below TIRED it looks tired and drives slower. */
    public static final float FULL = 100, TIRED = 20;
    /** Battery per tick while it drives or swims (a full battery lasts about 83 minutes of driving). */
    public static final float DRIVE_COST = 0.001f;
    /** Without a homebase it uses this much more. */
    public static final float HOMELESS_COST = 1.5f;
    /** Battery per point of damage (a zombie hit of 3 costs 6%). */
    public static final float DAMAGE_COST = 2;
    /** Battery per tick on its homebase (empty to full in about 2 minutes), and asleep without one (about 4 minutes). */
    public static final float HOME_CHARGE = 0.04f, SLEEP_CHARGE = 0.02f;
    /** Ticks without anything to do before it gets bored (half that without a homebase). */
    public static final int BORED_AFTER = 2400;
    /** Ticks it lies switched off where it ran empty before it goes home. */
    public static final int GOES_HOME_AFTER = 40;
    /** Further from home than this (blocks), it does not drive but beams home. */
    public static final double BEAM_HOME_FROM = 32;
    /** Battery from a redstone dust and a block of redstone. */
    public static final float REDSTONE_CHARGE = 8, REDSTONE_BLOCK_CHARGE = 72;
    /** Ticks it is overcharged after a lightning strike: hyper and glitchy. */
    public static final int OVERCHARGE_TICKS = 600;
    /** Battery per blow it lands in a fight. */
    public static final float FIGHT_COST = 1.5f;
    /** How bright its torch lights the way. */
    public static final int TORCH_LIGHT = 14;
    /** How far it notices loot lying about (Buddy and up). */
    public static final double LOOT_RANGE = 6;
    /** Ticks with nothing to do before it may get up to something. */
    public static final int NAUGHTY_AFTER = 1200;
    /** Ticks it takes to be put together: tracks, body, arms, head, a quarter each. */
    public static final int ASSEMBLE_TICKS = 60, ASSEMBLE_STEP = 15;
    /** How loud it talks, and how often it says something by itself (ticks, on average). */
    public static final float VOLUME = 0.6f;
    public static final int CHATTER_EVERY = 300;
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
            Map.entry("stubborn", 3), Map.entry("glitch", 2), Map.entry("fighting", 3), Map.entry("overcharged", 3), Map.entry("storm", 2), Map.entry("dream", 4), Map.entry("dance", 3), Map.entry("play", 3));
    /** What it says about a kind of mob it has no line for (another mod's). */
    public static final String UNKNOWN_MOB = "Never seen one of those. Filing it under weird.";
    /** Mobs worth a line in its diary. */
    private static final Set<String> SPECIAL_MOBS = Set.of("minecraft:warden", "minecraft:ender_dragon", "minecraft:wither", "minecraft:llama", "minecraft:iron_golem", "minecraft:allay");

    /** A short feeling (petted, hit, booting) that shows for a while over the usual face. */
    private Emotion mood = Emotion.HAPPY;
    /** The face it showed last tick: a new one gets its blip. */
    private Emotion shown = Emotion.HAPPY;
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
    /** What it remembers: the mobs, biomes and special blocks it found, its diary and its stats (the Bleepedia). */
    private final Memory memory = new Memory();
    /** A new kind of mob it is having a look at. */
    private @Nullable Mob scanning;
    private int scanTicks;
    /** Its favourite item (or, from Buddy, anything) lying about, that it is going to fetch. */
    private @Nullable ItemEntity fetching;
    /** The highest bond level it ever reached: what it has learned (Skill) stays learned. */
    private int learned;
    /** Where its torch's light is now. */
    private @Nullable BlockPos lit;
    /** Ticks it has had nothing to do; a bored bleep gets up to something (NaughtyGoal). */
    private int quietTicks;
    /** How far it sees a ball to fetch. */
    public static final int BALL_RANGE = 16;
    /** Playing with another bleep: who, for how long; and how long before it may cheer up, or be jealous, again. */
    private @Nullable BleepEntity partner;
    private int playTicks, playCooldown, cheerTicks, jealousTicks, playsToday;
    /** What its home has around it (a bed, a toy, its name on a sign: COMFORT_*), and the day that gave it a bond point. */
    private int comforts;
    private long comfortDay = -1;
    public static final int COMFORT_BED = 1, COMFORT_TOY = 2, COMFORT_SIGN = 4;
    /** Bleeps it saw lately (id: game time): one it has not seen for a while it greets. */
    private final Map<java.util.UUID, Long> lastSeen = new java.util.HashMap<>();
    public static final int GREET_AFTER = 6000, CHATS = 8, CHAT_LINES = 4, CHAT_STEP = 40;
    /** Who it is saying hi to, talking with or fighting; whose turn; tag: whether it is "it". */
    private @Nullable BleepEntity greeting, talkingTo, fightingWith;
    private int greetTicks, talkTicks, fightTicks, tagCooldown, chat, ballPasses;
    private boolean leads, it, ballDecided;
    /** Told to stay: the spot it potters about near (STAY_RADIUS); null while parked on its homebase, where it keeps still. */
    private @Nullable BlockPos stayAt;
    public static final int STAY_RADIUS = 6;
    /** Away from its owner this long (game ticks, or real milliseconds: logged out) and it is over the moon to see them again. */
    public static final long WELCOME_AFTER = 6000, WELCOME_AFTER_REAL = 5 * 60 * 1000;
    /** A birthday every so many days of its life. */
    public static final int BIRTHDAY_EVERY = 30;
    /** Long.MIN_VALUE: never with its owner yet. */
    private long lastWithOwner = Long.MIN_VALUE, lastWithOwnerReal = Long.MIN_VALUE, lastBirthday = -1;
    private int welcomeTicks, partyTicks;
    /** Looking up at the night sky; and the nights it last did that, and sang at full moon. */
    private int gazeTicks;
    private long gazeNight = -1, chorusNight = -1;
    /** Care: where its owner died (it guards their things there), the things it kept for them, and how long before it fusses again. */
    private @Nullable BlockPos guardAt;
    private final List<ItemStack> kept = new java.util.ArrayList<>();
    private int careCooldown, warnCooldown, guardTicks, reactCooldown;
    /** Adventures: the villager it goes shopping at with its owner's emeralds; caves (lava, drops, ore); riding a minecart behind its owner's. */
    private net.minecraft.world.entity.npc.villager.@Nullable Villager shopAt;
    private ItemStack purse = ItemStack.EMPTY;
    private int shopTicks, caveCooldown, oreCooldown, cartWait;
    public static final int GUARD_RANGE = 8, GUARD_TIME = 24000;
    public static final int JOB_FARMER = 1, JOB_GUARD = 2, JOB_TIDY = 4, JOB_FISHER = 8, JOB_SMELTER = 16, JOB_GARDENER = 32, JOB_HERDER = 64, JOB_LUMBERJACK = 128, MIN_RADIUS = 2, MAX_RADIUS = 16;
    /** What its work at workAt is. */
    private static final int WORK_CROP = 1, WORK_CHEST = 2, WORK_FISH = 3, WORK_FURNACE = 4, WORK_GARDEN = 5, WORK_ANIMAL = 6, WORK_TREE = 7;
    /** The herder breeds no more than this many of a kind round its spot. */
    public static final int HERD_MAX = 12;
    /** The animal it is on its way to (herder): it moves about, so the goal follows it. */
    private net.minecraft.world.entity.animal.@Nullable Animal workAnimal;
    private int workKind;
    /** Places it could not get to lately: skipped for a while. */
    private final Set<BlockPos> unreachable = new java.util.HashSet<>();
    /** Fishing: how long until a bite, the water it looks at, and the wait before it goes again. */
    private int fishTicks, fishCooldown;
    private @Nullable BlockPos fishWater;
    /** A garden: no more flowers than this round its spot. */
    public static final int GARDEN_SIZE = 12;
    /** Where its work takes it next (a ripe crop, a chest), and how long before it sounds the alarm again. */
    private @Nullable BlockPos workAt;
    private int alarmCooldown;
    /** A trick it is doing, and hide and seek: HIDE_* what it is doing, until when, and where. */
    private @Nullable Trick trick;
    private int trickTicks, hideMode;
    /** Hide and seek by the clock (goals only run every other tick): when it started, and when it gives up. */
    private long hideStart, hideUntil;
    private @Nullable BlockPos hideSpot;
    public static final int HIDE_GOING = 1, HIDE_HIDDEN = 2, SEEK_COUNTING = 3, SEEK_LOOKING = 4, COUNT_TICKS = 200;
    /** Treasure hunt: it buries something and you dig for it (5 minutes), or you hide something (20 seconds to do it) and it looks for it. */
    public static final int BURYING = 1, BURIED = 2, UNBURYING = 3, FIND_COUNTING = 4, FINDING = 5, HUNT_TICKS = 6000, HIDE_TICKS = 400;
    private int treasureMode, treasureTicks, decoys, lastWarmth;
    /** Dreams: what it saw today (mob name keys, kinds of place; not saved: after a reload it dreams of older things), when it dreams next, the last night it wrote one down. */
    private final java.util.ArrayDeque<String> dayMobs = new java.util.ArrayDeque<>(), dayScenes = new java.util.ArrayDeque<>();
    private int dreamIn = 400;
    /** Show the way: where it leads its owner (its homebase, or their bed), and whether that is their bed. */
    private @Nullable BlockPos wayTo;
    private boolean wayToBed;
    private long wayUntil;
    private long dreamtOn = -1;
    private long treasureStart, treasureUntil, lidCloses;
    private ItemStack treasureItem = ItemStack.EMPTY;
    private @Nullable Object hidden; // what its owner hid: an ItemEntity they tossed, or the BlockPos of a container they opened
    private @Nullable BlockPos lidAt, searchSpot;
    /** Ticks left of being overcharged by lightning. */
    private int overchargeTicks;
    /** Ticks left of what its display shows instead of its face. */
    private int displayTicks;
    /** Ticks left of dancing (music close by), of digging something up, and before it gets excited about a golem again. */
    private int danceTicks, digTicks, golemTicks;
    /** Asleep next to its owner's bed, while they sleep. */
    private boolean bedside;
    /** Its storage, as big as a small chest. */
    public static final int STORAGE = 27;
    /** How far its magnet reaches, and how long it may take to show its owner something before it gives up. */
    public static final int MAGNET_RANGE = 6, SHOW_TICKS = 400;
    /** A new bleep's name. */
    public static final String DEFAULT_NAME = "Bleep";
    private final net.minecraft.world.SimpleContainer storage = new net.minecraft.world.SimpleContainer(STORAGE) {
        @Override
        public boolean stillValid(Player player) {
            return isAlive() && !isRemoved() && distanceToSqr(player) < 8 * 8;
        }
    };
    /**
     * What it holds came out of its storage (a weapon for a fight, or something it is showing off) and goes back
     * there, instead of being handed to its owner like a find.
     */
    private boolean ownHand, armed;
    private int showTicks, disarmTicks;
    private boolean showed;
    /** The last thing its magnet picked up, which it likes to show off. */
    private net.minecraft.world.item.@Nullable Item lastPicked;
    /** Made in registerGoals, which Minecraft calls from the constructor, before field initialisers run: no initialiser here. */
    private NaughtyGoal naughty;

    public BleepEntity(EntityType<? extends BleepEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.ATTACK_DAMAGE, 3);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new GoHomeGoal());
        this.goalSelector.addGoal(2, new BedsideGoal());
        this.goalSelector.addGoal(2, new GuardGoal());
        this.goalSelector.addGoal(4, new WelcomeGoal());
        this.goalSelector.addGoal(4, new ShopGoal());
        this.goalSelector.addGoal(4, new HideAndSeekGoal());
        this.goalSelector.addGoal(4, new TreasureGoal());
        this.goalSelector.addGoal(4, new WayGoal());
        this.goalSelector.addGoal(6, new WorkGoal());
        this.goalSelector.addGoal(5, new SocialGoal());
        this.goalSelector.addGoal(6, new ShowGoal());
        this.goalSelector.addGoal(7, new PlayGoal());
        this.goalSelector.addGoal(3, new StayGoal());
        // bleeps are afraid of llamas, for reasons nobody knows
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Llama.class, 10, 1.0, 1.3));
        naughty = new NaughtyGoal(this);
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(6, new FetchGoal());
        this.goalSelector.addGoal(6, new CircleOwnerGoal());
        this.goalSelector.addGoal(6, naughty);
        this.goalSelector.addGoal(7, new ScanGoal());
        this.goalSelector.addGoal(8, new FollowOwnerGoal(this, 1.0, 6, 2));
        this.goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        // from Buddy: it fights what goes for its owner, and what its owner goes for (wantsToAttack says which)
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
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
        builder.define(TREASURE, Optional.empty());
        builder.define(DREAM, "");
        builder.define(OBSESSION, -1);
        builder.define(REVEALED, false);
        builder.define(ASSEMBLY, 0);
        builder.define(COLOR, -1);
        builder.define(HAT, -1);
        builder.define(STICKER, -1);
        builder.define(WEAR, 0);
        builder.define(DISPLAY, 0);
        builder.define(NEWS, false);
        builder.define(ABANDONED, false);
        builder.define(MAGNET, false);
        builder.define(PARTY, false);
        builder.define(JOBS, 0);
        builder.define(RADIUS, STAY_RADIUS);
        builder.define(CHIPS, 0);
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
            memory.timesEmpty++;
            rememberFirst("empty", "diary.bleep.first_empty", "");
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
        if (home != null && !home.equals(home()) && !level().isClientSide()) remember("diary.bleep.home", "");
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
        return memory.mobs.keySet();
    }

    public Memory memory() {
        return memory;
    }

    public net.minecraft.world.SimpleContainer storage() {
        return storage;
    }

    /** With its magnet on it pulls in what lies about and keeps it in its storage. */
    public boolean magnet() {
        return this.entityData.get(MAGNET);
    }

    public void setMagnet(boolean on) {
        this.entityData.set(MAGNET, on);
    }

    /** Its owner opens its storage (the chest on its info screen). */
    public void openStorage(net.minecraft.server.level.ServerPlayer player) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inventory, storage), getDisplayName()));
    }

    /** A new name, typed on its info screen: plain text, never empty. */
    public void rename(String name) {
        String clean = net.minecraft.util.StringUtil.filterText(name).trim();
        if (clean.length() > 20) clean = clean.substring(0, 20).trim();
        setCustomName(Component.literal(clean.isEmpty() ? DEFAULT_NAME : clean));
    }

    public boolean abandoned() {
        return this.entityData.get(ABANDONED);
    }

    public void setAbandoned(boolean abandoned) {
        this.entityData.set(ABANDONED, abandoned);
        if (abandoned) {
            setBattery(0);
            slept = true;
        }
    }

    /** A block of redstone wakes an abandoned bleep: it starts up again, and it is the player's, with its past in its diary. */
    public void repair(Player player) {
        this.entityData.set(ABANDONED, false);
        slept = false;
        setBattery(FULL);
        tame(player);
        if (memory.born < 0) memory.born = level().getGameTime();
        boot();
        remember("diary.bleep.abandoned", "");
        remember("diary.bleep.past." + getRandom().nextInt(Abandoned.PASTS), "");
    }

    /** Starts digging now, if it is free to (tests). */
    public boolean dig() {
        if (!mayBeNaughty() || !getMainHandItem().isEmpty()) return false;
        digTicks = 40;
        return true;
    }

    public boolean dancing() {
        return danceTicks > 0;
    }

    /** Something new for its Bleepedia: its owner gets a ! until they have looked. */
    public boolean news() {
        return this.entityData.get(NEWS);
    }

    private void remember(String key, String arg) {
        memory.write(level().getGameTime(), key, arg);
        this.entityData.set(NEWS, true);
    }

    private void rememberFirst(String what, String key, String arg) {
        if (memory.first(level().getGameTime(), what, key, arg)) this.entityData.set(NEWS, true);
    }

    /** Its owner opens its Bleepedia: everything it remembers goes to them, and the news is read. */
    public void openBleepedia(net.minecraft.server.level.ServerPlayer player) {
        // only to a player whose game can show it (not a test player, nor one without Bleep)
        if (player.connection.hasChannel(io.hookdev.bleep.network.BleepediaData.TYPE)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, io.hookdev.bleep.network.BleepediaData.of(this));
        }
        memory.lastOpened = level().getGameTime();
        this.entityData.set(NEWS, false);
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
        shown = Emotion.BOOT;
        talk(Emotion.BOOT);
        if (memory.born < 0) memory.born = level().getGameTime();
        rememberFirst("born", "diary.bleep.born", "");
        if (obsession() == null) setObsession(getRandom().nextInt(Obsession.ALL.size()));
    }

    /** Put together from its parts: tracks first, then its body, arms and head, then the screen comes on. */
    public void assemble() {
        this.entityData.set(ASSEMBLY, ASSEMBLE_TICKS);
        if (obsession() == null) setObsession(getRandom().nextInt(Obsession.ALL.size()));
    }

    /** Its colour (RGB), or -1 for its own white. */
    public int color() {
        return this.entityData.get(COLOR);
    }

    public void setColor(int rgb) {
        this.entityData.set(COLOR, rgb);
    }

    public @Nullable Accessory hat() {
        return Accessory.byId(this.entityData.get(HAT));
    }

    public @Nullable Accessory sticker() {
        return Accessory.byId(this.entityData.get(STICKER));
    }

    /** What it wears in this slot, if anything. */
    public @Nullable Accessory worn(Accessory.Slot slot) {
        return switch (slot) {
            case HAT -> hat();
            case STICKER -> sticker();
            default -> Accessory.byId(((this.entityData.get(WEAR) >> shift(slot)) & 0xFF) - 1);
        };
    }

    private static int shift(Accessory.Slot slot) {
        return 8 * (slot.ordinal() - Accessory.Slot.EYES.ordinal());
    }

    private void setWorn(Accessory.Slot slot, @Nullable Accessory accessory) {
        switch (slot) {
            case HAT -> this.entityData.set(HAT, accessory == null ? -1 : accessory.ordinal());
            case STICKER -> this.entityData.set(STICKER, accessory == null ? -1 : accessory.ordinal());
            default -> this.entityData.set(WEAR, (this.entityData.get(WEAR) & ~(0xFF << shift(slot))) | ((accessory == null ? 0 : accessory.ordinal() + 1) << shift(slot)));
        }
    }

    /** Puts this on; what it wore in that place goes back to the player. */
    public void wear(Player player, Accessory accessory) {
        Accessory old = worn(accessory.slot);
        if (old != null) giveBack(player, new ItemStack(BleepContent.ACCESSORIES.get(old).get()));
        setWorn(accessory.slot, accessory);
        feel(Emotion.EXCITED, 40);
        if (accessory == Accessory.SUNGLASSES) pinThought("thought.bleep.dress.sunglasses");
        else if (accessory == Accessory.LEATHER_JACKET) pinThought("thought.bleep.dress.jacket");
        else if (accessory == Accessory.CAPE) pinThought("thought.bleep.dress.cape");
        else if (accessory == Accessory.CROWN) pinThought("thought.bleep.dress.crown");
    }

    /** Shears take off everything it wears, back to the player. False if it wears nothing. */
    public boolean takeOff(Player player) {
        boolean any = false;
        for (Accessory.Slot slot : Accessory.Slot.values()) {
            Accessory worn = worn(slot);
            if (worn == null) continue;
            giveBack(player, new ItemStack(BleepContent.ACCESSORIES.get(worn).get()));
            setWorn(slot, null);
            any = true;
        }
        return any;
    }

    private void giveBack(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) level().addFreshEntity(new ItemEntity(level(), player.getX(), player.getY(), player.getZ(), stack));
    }

    /** What its display shows instead of its face, if anything. */
    public Display display() {
        return Display.byId(this.entityData.get(DISPLAY));
    }

    /** Shows this on its display for a while (0: until something else comes up). */
    public void showDisplay(Display display, int ticks) {
        this.entityData.set(DISPLAY, display.ordinal());
        displayTicks = ticks;
    }

    /** Ticks left until it is put together, or 0. */
    public int assembly() {
        return this.entityData.get(ASSEMBLY);
    }

    /** A blip in its own voice. */
    public void talk(Emotion emotion) {
        var sound = BleepContent.VOICE.get(emotion);
        if (sound != null) playSound(sound.get(), VOLUME, getVoicePitch());
    }

    /** Every bleep has a voice of its own: a little higher or lower. */
    @Override
    public float getVoicePitch() {
        return 0.9f + Math.floorMod(getUUID().hashCode(), 25) / 100f;
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
            learn(Bond.level(bond()));
            remember("diary.bleep.level", Bond.key(bond()));
        }
        if (bond() > before) level().broadcastEntityEvent(this, (byte) 7); // hearts
    }

    /** A new bond level: what goes with it is learned for good, and its owner hears about it. */
    private void learn(int level) {
        if (level <= learned) return;
        for (Skill skill : Skill.values()) {
            if (skill.level > learned && skill.level <= level) {
                if (getOwner() instanceof Player owner) owner.sendSystemMessage(Component.translatable(skill.key(), getDisplayName()));
                remember("diary.bleep.learned." + skill.name().toLowerCase(java.util.Locale.ROOT), "");
            }
        }
        learned = level;
    }

    /** Whether it has learned this. */
    public boolean can(Skill skill) {
        return Math.max(learned, Bond.level(bond())) >= skill.level;
    }

    /** The counters are per Minecraft day. */
    private long today() {
        long day = level().getGameTime() / Bond.DAY;
        if (day != bondDay) {
            bondDay = day;
            petsToday = giftsToday = fightsToday = playsToday = 0;
        }
        return day;
    }

    /** Sneak-right-click: hearts, a happy wiggle, and a little more bond (five times a day). */
    public void pet() {
        today();
        feel(Emotion.EXCITED, 60);
        if (fightingWith != null) { // break it up
            endFight("thought.bleep.fight.stopped");
            return;
        }
        if (getOwner() instanceof Player owner) { // the others saw that
            for (BleepEntity other : level().getEntitiesOfClass(BleepEntity.class, getBoundingBox().inflate(8), b -> b != this && b.isOwnedBy(owner) && !b.asleep())) other.jealous();
        }
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
        Chip chip = BleepContent.chip(stack);
        if (chip != null) {
            if (has(chip)) {
                player.sendOverlayMessage(Component.translatable("message.bleep.has_chip", getDisplayName(), stack.getHoverName()));
                return false;
            }
            install(chip);
            return true;
        }
        if (stack.is(Items.CAKE) && party()) { // a birthday cake!
            feel(Emotion.GLITCH, 40);
            say("thought.bleep.birthday.cake");
            remember("diary.bleep.cake", "");
            changeBond(Bond.FAVOURITE / 2);
            return true;
        }
        var dye = stack.get(net.minecraft.core.component.DataComponents.DYE);
        if (dye != null) { // white dye washes it back to its own white
            int rgb = dye == net.minecraft.world.item.DyeColor.WHITE ? -1 : dye.getTextureDiffuseColor() & 0xFFFFFF;
            if (rgb == color()) return false;
            setColor(rgb);
            feel(Emotion.EXCITED, 40);
            return true;
        }
        Accessory accessory = BleepContent.accessory(stack);
        if (accessory != null) {
            wear(player, accessory);
            return true;
        }
        if (stack.is(Items.REDSTONE_BLOCK)) {
            if (battery() >= FULL) {
                player.sendOverlayMessage(Component.translatable("message.bleep.full", getDisplayName()));
                return false;
            }
            charge(REDSTONE_BLOCK_CHARGE);
            return true;
        }
        if (stack.is(Items.REDSTONE) && battery() < FULL) {
            charge(REDSTONE_CHARGE);
            today();
            if (giftsToday < Bond.GIFTS_PER_DAY && !slept) { // still a treat too, as long as there are treats left today
                giftsToday++;
                changeBond(Bond.GIFT);
            }
            return true;
        }
        if (stack.is(Items.TORCH)) {
            if (!can(Skill.TORCH)) {
                player.sendOverlayMessage(Component.translatable("message.bleep.not_yet", getDisplayName(), Component.translatable(Bond.key(Skill.TORCH.level * 25))));
                return false;
            }
            if (!getOffhandItem().isEmpty()) return false;
            setItemSlot(EquipmentSlot.OFFHAND, stack.copyWithCount(1));
            feel(Emotion.EXCITED, 40);
            pinThought("thought.bleep.torch");
            return true;
        }
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
        if (stack.is(Items.EMERALD)) return goShopping(player, stack);
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
        if (slept) return;
        memory.fightsWon++;
        rememberFirst("fight", "diary.bleep.first_fight", "");
        if (fightsToday >= Bond.FIGHTS_PER_DAY) return;
        fightsToday++;
        changeBond(Bond.FIGHT);
    }

    /** Its owner finds out what its favourite item is. */
    public void reveal(@Nullable Player player) {
        if (revealed() || obsession() == null) return;
        this.entityData.set(REVEALED, true);
        remember("diary.bleep.favourite", obsession().nameKey());
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.bleep.loves", getDisplayName(), Component.translatable(obsession().nameKey())));
        }
    }

    /** Charges it this much, with a few sparks. */
    public void charge(float percent) {
        setBattery(battery() + percent);
        if (level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.7, getZ(), 6, 0.3, 0.3, 0.3, 0.05);
        }
        playSound(SoundEvents.BEACON_POWER_SELECT, 0.4f, 1.8f);
    }

    /** Tops it up from a power bank, as far as the bank and the battery go. The bank is kept (it shows what is left). */
    public boolean usePowerBank(Player player, ItemStack bank) {
        int left = bank.getMaxDamage() - bank.getDamageValue();
        int needed = (int) Math.ceil(FULL - battery());
        if (left <= 0) {
            player.sendOverlayMessage(Component.translatable("message.bleep.power_bank_empty"));
            return false;
        }
        if (needed <= 0) {
            player.sendOverlayMessage(Component.translatable("message.bleep.full", getDisplayName()));
            return false;
        }
        int given = Math.min(left, needed);
        bank.setDamageValue(bank.getDamageValue() + given);
        charge(given);
        return false; // nothing used up: the bank stays in the hand
    }

    /** Whether giving it this works while it is switched off: only what charges it. */
    private static boolean charges(ItemStack stack) {
        return stack.is(Items.REDSTONE) || stack.is(Items.REDSTONE_BLOCK) || stack.is(BleepContent.POWER_BANK.get());
    }

    /** Lightning overcharges it: full at once, and hyper for a while. It does not hurt it. */
    @Override
    public void thunderHit(ServerLevel level, net.minecraft.world.entity.LightningBolt lightning) {
        setBattery(FULL);
        if (slept) {
            slept = false;
            if (followAfterSleep) follow();
        }
        overchargeTicks = OVERCHARGE_TICKS;
        setRemainingFireTicks(0);
        remember("diary.bleep.lightning", "");
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.7, getZ(), 30, 0.4, 0.6, 0.4, 0.2);
    }

    public boolean overcharged() {
        return overchargeTicks > 0;
    }

    /** Gives its torch back. */
    public boolean takeTorch(Player player) {
        ItemStack torch = getOffhandItem();
        if (torch.isEmpty()) return false;
        setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        if (!player.getInventory().add(torch)) level().addFreshEntity(new ItemEntity(level(), player.getX(), player.getY(), player.getZ(), torch));
        removeLight();
        return true;
    }

    /** The light of its torch moves along with it: an invisible light block over it, taken away where it was. */
    private void lightTheWay() {
        boolean torch = !slept && getOffhandItem().is(Items.TORCH);
        BlockPos here = blockPosition().above();
        if (!torch) {
            removeLight();
            return;
        }
        if (here.equals(lit)) return;
        removeLight();
        if (level().getBlockState(here).isAir()) {
            level().setBlock(here, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, TORCH_LIGHT), 3);
            lit = here;
        }
    }

    private void removeLight() {
        if (lit != null && level().getBlockState(lit).is(Blocks.LIGHT)) level().setBlock(lit, Blocks.AIR.defaultBlockState(), 3);
        lit = null;
    }

    public @Nullable BlockPos light() {
        return lit;
    }

    /** Its live display texture goes too. */
    @Override
    public void onClientRemoval() {
        super.onClientRemoval();
        io.hookdev.bleep.client.ClientHooks.forgetDisplay(getId());
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) removeLight();
        if (level() instanceof ServerLevel server && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
            io.hookdev.bleep.BleepOwners.get(server).remove(getUUID());
        }
        if (!level().isClientSide() && reason == RemovalReason.KILLED) {
            stow();
            net.minecraft.world.Containers.dropContents(level(), this, storage);
            if (level() instanceof ServerLevel server) kept.forEach(stack -> spawnAtLocation(server, stack));
            if (level() instanceof ServerLevel server && !purse.isEmpty()) spawnAtLocation(server, purse);
            if (level() instanceof ServerLevel server && !treasureItem.isEmpty()) spawnAtLocation(server, treasureItem);
        }
        super.remove(reason);
    }

    // ---------- fighting ----------

    /** It only fights monsters, never a creeper (it learned that the hard way), and not with a low battery. */
    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return can(Skill.FIGHT) && battery() >= TIRED && target instanceof net.minecraft.world.entity.monster.Enemy
                && !(target instanceof net.minecraft.world.entity.monster.Creeper);
    }

    /** Every blow costs battery. */
    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) drain(FIGHT_COST);
        return hit;
    }

    // ---------- naughty ----------

    /** Free to get up to something: not in a fight, not in danger, not told to stay or go home. */
    public boolean mayBeNaughty() {
        return !slept && !parked() && !goingHome && getTarget() == null && dawdleTicks == 0 && !inDanger() && assembly() == 0;
    }

    /** Where it last put a torch it moved (tests). */
    public @Nullable BlockPos movedTorch() {
        return naughty.movedTo();
    }

    /** Starts a trick now, if it can (tests). */
    public boolean beNaughty(NaughtyGoal.Trick trick) {
        return naughty.start(trick);
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
        stayHere(null);
        getNavigation().stop();
    }

    /** Stays around here: it potters about within a few blocks, and may play and chat with other bleeps. */
    public void stay() {
        goingHome = false;
        setOrderedToSit(true);
        stayHere(blockPosition());
        getNavigation().stop();
    }

    /** The spot it stays near, or null (following, or parked). */
    private void stayHere(@Nullable BlockPos at) {
        stayAt = at;
        if (at == null) clearHome();
        else setHomeTo(at, stayRadius());
    }

    public @Nullable BlockPos stayAt() {
        return stayAt;
    }

    /** Parked on its homebase (or told to stay before it could potter about): it keeps still. */
    public boolean parked() {
        return isOrderedToSit() && stayAt == null;
    }

    /** Busy with another bleep, a game or a find: staying can wait a moment. */
    private boolean busyWithOthers() {
        return wayTo != null || hunting() || shopAt != null || fishTicks > 0 || guardAt != null || welcomeTicks > 0 || hideMode != 0 || trickTicks > 0 || gazeTicks > 0 || workAt != null || talkingTo != null || fightingWith != null || greetTicks > 0 || playing() || fetching != null || showTicks > 0 || !getMainHandItem().isEmpty();
    }

    /** Drives home and stays there; a homeless bleep does not know where to go. */
    public boolean goHome() {
        if (!homeHere()) return false;
        setOrderedToSit(false);
        stayHere(null);
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
        stayHere(null); // on its pad it keeps still, to charge
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
        if (abandoned()) { // switched off, long ago: it waits
            this.entityData.set(ACTIVITY, Activity.ABANDONED.ordinal());
            this.entityData.set(EMOTION, Emotion.ASLEEP.ordinal());
            return;
        }
        if (obsession() == null) setObsession(getRandom().nextInt(Obsession.ALL.size())); // a bleep from before favourites
        if (assembly() > 0) {
            assembleTick();
            return;
        }
        boolean moving = getDeltaMovement().horizontalDistanceSqr() > 1.0e-4;
        if (moving) memory.distance += Math.sqrt(getDeltaMovement().horizontalDistanceSqr());
        if (memory.born < 0) memory.born = level().getGameTime(); // a bleep from before it remembered
        // a homebase that was broken: homeless, and sad about it
        if (tickCount % 20 == 0 && homeHere() && level().isLoaded(home().pos()) && !HomebaseBlock.is(level().getBlockState(home().pos()))) {
            setHome(null);
            feel(Emotion.SAD, 80);
            changeBond(Bond.HOMEBASE_BROKEN);
            remember("diary.bleep.homeless", "");
        }
        boolean home = onHome();
        if (slept) {
            sleptTicks++;
            if (!home && homeHere() && sleptTicks >= GOES_HOME_AFTER) {
                beamHome();
                home = true;
            }
            setBattery(battery() + (home ? homeCharge() : SLEEP_CHARGE));
            if (battery() >= FULL) { // awake again, and a little sad about it
                slept = false;
                feel(Emotion.SAD, 80);
                if (followAfterSleep) follow();
            }
        } else if (home) {
            setBattery(battery() + homeCharge());
        } else if (moving) {
            drain(DRIVE_COST * (home() == null ? HOMELESS_COST : 1));
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
            rememberFirst("llama", "diary.bleep.llama", "");
        }
        lightTheWay();
        tickDisplay(moving);
        tickWorld(moving);
        if (isTame() && !hasCustomName()) { // every bleep is called Bleep until you give it a name of its own
            super.setCustomName(Component.literal(DEFAULT_NAME));
            setCustomNameVisible(true);
        }
        tickMagnet();
        tickHands();
        if (tickCount % 20 == 0 && isOrderedToSit() && stayAt == null && !home && !goingHome) stayHere(blockPosition());
        tickTogether();
        tickWelcome();
        tickBirthday();
        tickWork();
        tickTrick();
        tickStars();
        tickCare();
        tickAdventure();
        tickTreasure();
        tickDreams();
        if (tickCount % 100 == 70 && isTame()) checkMilestones();
        if (home && tickCount % 200 == 0) checkComforts();
        if (tickCount % 200 == 0 && getOwner() instanceof Player owner && level() instanceof ServerLevel server) { // bleeps from before the limit, too
            io.hookdev.bleep.BleepOwners.get(server).add(owner.getUUID(), getUUID());
        }
        if (overchargeTicks > 0 && --overchargeTicks % 10 == 0 && level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.8, getZ(), 2, 0.3, 0.3, 0.3, 0.05);
        }
        if (getTarget() != null && (battery() < TIRED || !getTarget().isAlive())) setTarget(null);
        boolean quiet = !moving && fetching == null && scanning == null && getTarget() == null && getMainHandItem().isEmpty();
        quietTicks = quiet ? quietTicks + 1 : 0;
        if (tickCount % 200 == 0 && quietTicks > NAUGHTY_AFTER && !naughty.busy() && mayBeNaughty()
                && getRandom().nextFloat() < 0.05f + 0.3f * (1 - bond() / (float) Bond.MAX)) {
            naughty.startAny();
            quietTicks = 0;
        }
        if (!slept) {
            lookForNewMobs();
            if (tickCount % 40 == 0) lookForFavourite();
            if (tickCount % 10 == 5) lookForBall();
            if (tickCount % 40 == 0) noticeBiome();
            if (tickCount % 40 == 20) lookForFinds();
        }
        if (tickCount % 100 == 0) countDaysTogether();
        Emotion emotion = currentEmotion();
        Activity activity = currentActivity(home);
        // a new face says so; the everyday ones (happy, bored, tired) chatter now and then instead (getAmbientSound)
        if (emotion != shown && emotion != Emotion.HAPPY && emotion != Emotion.BORED && emotion != Emotion.TIRED) talk(emotion);
        shown = emotion;
        this.entityData.set(EMOTION, emotion.ordinal());
        this.entityData.set(ACTIVITY, activity.ordinal());
        think(emotion, activity);
    }

    /** One step of being put together: a clunk and sparks for each part, then it starts up. */
    private void assembleTick() {
        int left = assembly() - 1;
        this.entityData.set(ASSEMBLY, left);
        this.entityData.set(ACTIVITY, Activity.STARTING.ordinal());
        if (left % ASSEMBLE_STEP == 0 && level() instanceof ServerLevel level) {
            playSound(BleepContent.ASSEMBLE.get(), VOLUME, 0.8f + 0.1f * (ASSEMBLE_TICKS - left) / ASSEMBLE_STEP);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3 + 0.25 * (ASSEMBLE_TICKS - left) / ASSEMBLE_STEP, getZ(), 8, 0.3, 0.1, 0.3, 0.05);
        }
        if (left == 0) boot();
    }

    /**
     * Its display: an arrow home while it drives there, now and then a game of tic-tac-toe when it is bored, or a cookie
     * it would like when it has had no treat today; a shown thing ends after its ticks.
     */
    private void tickDisplay(boolean moving) {
        Display display = display();
        if (displayTicks > 0 && --displayTicks == 0) display = Display.FACE;
        if (goingHome) display = Display.HOME;
        else if (display == Display.HOME) display = Display.FACE;
        if (!goingHome && treasureMode == BURIED && displayTicks == 0) display = Display.TREASURE; // warmer, colder
        else if (display == Display.TREASURE && treasureMode != BURIED) display = Display.FACE;
        if (display == Display.FACE && !slept && news() && tickCount % 600 == 300) { // news: a ! now and then, not all the time
            showDisplay(Display.NEWS, 40);
            return;
        }
        if (display == Display.FACE && !slept && tickCount % 200 == 0) {
            if (emotion() == Emotion.BORED && getRandom().nextFloat() < 0.3f) {
                showDisplay(Display.GAME, 240);
                return;
            }
            today();
            if (emotion() == Emotion.HAPPY && giftsToday == 0 && getOwner() != null && distanceToSqr(getOwner()) < TOGETHER * TOGETHER && getRandom().nextFloat() < 0.08f) {
                showDisplay(Display.WANT, 80);
                return;
            }
            if ((emotion() == Emotion.HAPPY || emotion() == Emotion.BORED) && getRandom().nextFloat() < (hobby("playing") ? 0.25f : 0.12f) && ownerHasBall() && distanceToSqr(getOwner()) < TOGETHER * TOGETHER) {
                showDisplay(Display.BALL, 80);
                return;
            }
        }
        if (display.ordinal() != this.entityData.get(DISPLAY)) this.entityData.set(DISPLAY, display.ordinal());
    }

    /** Music, a golem, something to dig up. */
    private void tickWorld(boolean moving) {
        if (danceTicks > 0) danceTicks--;
        if (golemTicks > 0) golemTicks--;
        if (slept || bedside) return;
        if (tickCount % 20 == 0 && jukeboxPlaying()) { // music: dance
            if (danceTicks == 0) {
                rememberFirst("dance", "diary.bleep.dance", "");
                hobbyPoint("dancing");
                if (!level().getEntitiesOfClass(net.minecraft.world.entity.animal.allay.Allay.class, getBoundingBox().inflate(8)).isEmpty()) {
                    pinThought("thought.bleep.dance.allay");
                }
            }
            danceTicks = 60;
        }
        if (golemTicks == 0 && tickCount % 40 == 10) { // big brother
            var golem = level().getEntitiesOfClass(net.minecraft.world.entity.animal.golem.IronGolem.class, getBoundingBox().inflate(10)).stream().findFirst();
            if (golem.isPresent()) {
                getLookControl().setLookAt(golem.get());
                feel(Emotion.EXCITED, 40);
                pinThought("thought.bleep.golem." + getRandom().nextInt(2));
                rememberFirst("golem", "diary.bleep.golem", "");
                golemTicks = 2400;
            }
        }
        if (digTicks > 0) { // digging: bits of the ground fly, then it has found something
            if (level() instanceof ServerLevel server && tickCount % 5 == 0) {
                server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, level().getBlockState(blockPosition().below())),
                        getX(), getY() + 0.1, getZ(), 6, 0.3, 0.1, 0.3, 0.1);
            }
            if (--digTicks == 0) {
                setItemSlot(EquipmentSlot.MAINHAND, dugUp());
                pinThought("thought.bleep.dig");
                feel(Emotion.EXCITED, 40);
            }
            return;
        }
        if (tickCount % 600 == 300 && !moving && quietTicks > 200 && getMainHandItem().isEmpty() && mayBeNaughty() && soft(level().getBlockState(blockPosition().below()))
                && getRandom().nextFloat() < (hobby("digging") ? 0.45f : 0.15f)) {
            digTicks = 40;
            hobbyPoint("digging");
        }
        if (hobby("dancing") && danceTicks == 0 && tickCount % 600 == 200 && !moving && quietTicks > 200 && mayBeNaughty() && getRandom().nextFloat() < 0.3f) {
            danceTicks = 80; // no music needed: it just dances
            pinThought("thought.bleep.hobby.dance");
        }
    }

    /** Its magnet: it pulls in what lies about (not what its owner threw, not its favourite) into its storage. */
    private void tickMagnet() {
        boolean collecting = hobby("collecting") && quietTicks > 200 && stayAt == null; // a collector picks things up when it has nothing to do
        if (!(magnet() || collecting) || slept || battery() <= 0 || tickCount % 4 != 0) return;
        LivingEntity owner = getOwner();
        Obsession favourite = obsession();
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(MAGNET_RANGE), i -> i.isAlive() && !i.hasPickUpDelay() && i != fetching
                && (owner == null || i.getOwner() != owner) && (favourite == null || !favourite.matches().test(i.getItem())) && !isBall(i.getItem()) && storage.canAddItem(i.getItem()))) {
            if (distanceToSqr(item) < 1.5 * 1.5) {
                int before = item.getItem().getCount();
                ItemStack rest = storage.addItem(item.getItem().copy());
                if (rest.getCount() < before) {
                    lastPicked = item.getItem().getItem();
                    hobbyPoint("collecting");
                    playSound(SoundEvents.ITEM_PICKUP, 0.3f, 1.6f + getRandom().nextFloat() * 0.4f);
                }
                if (rest.isEmpty()) item.discard();
                else item.setItem(rest);
            } else {
                var pull = position().add(0, 0.4, 0).subtract(item.position()).normalize().scale(0.3);
                item.setDeltaMovement(item.getDeltaMovement().scale(0.5).add(pull));
                item.needsSync = true;
            }
        }
    }

    /**
     * Its hands: a weapon from its storage for a fight (put back after), and now and then it takes something out to
     * show its owner, happily (ShowGoal).
     */
    private void tickHands() {
        if (getTarget() != null) {
            disarmTicks = 0;
            if (ownHand && !armed) stow(); // it was showing something off: the fight comes first
            if (getMainHandItem().isEmpty()) {
                int slot = bestWeapon();
                if (slot >= 0) {
                    setItemSlot(EquipmentSlot.MAINHAND, storage.removeItem(slot, 1));
                    ownHand = armed = true;
                }
            }
            return;
        }
        if (armed && ++disarmTicks > 40) stow();
        if (showTicks > 0 && --showTicks == 0) stow();
        if (tickCount % 600 == 450 && !ownHand && getMainHandItem().isEmpty() && !storage.isEmpty() && free() && mayBeNaughty()
                && getOwner() instanceof Player owner && distanceToSqr(owner) < 16 * 16 && getRandom().nextFloat() < (lastPicked != null ? 0.5f : 0.15f)) {
            showOff();
        }
    }

    /** Takes something out of its storage to show its owner: the last thing its magnet found, or anything. */
    public boolean showOff() {
        if (ownHand || !getMainHandItem().isEmpty() || storage.isEmpty()) return false;
        int slot = -1;
        java.util.List<Integer> full = new java.util.ArrayList<>();
        for (int i = 0; i < storage.getContainerSize(); i++) {
            if (storage.getItem(i).isEmpty()) continue;
            full.add(i);
            if (lastPicked != null && storage.getItem(i).is(lastPicked)) slot = i;
        }
        if (slot < 0) slot = full.get(getRandom().nextInt(full.size()));
        setItemSlot(EquipmentSlot.MAINHAND, storage.removeItem(slot, 1));
        ownHand = true;
        showed = false;
        showTicks = SHOW_TICKS;
        lastPicked = null;
        return true;
    }

    /** Its best weapon in its storage (what hits hardest), or -1. */
    private int bestWeapon() {
        int best = -1;
        double most = 0;
        for (int i = 0; i < storage.getContainerSize(); i++) {
            ItemStack stack = storage.getItem(i);
            if (stack.isEmpty() || !stack.has(net.minecraft.core.component.DataComponents.WEAPON)) continue;
            double damage = stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
                    .compute(Attributes.ATTACK_DAMAGE, 0, EquipmentSlot.MAINHAND);
            if (best < 0 || damage > most) {
                best = i;
                most = damage;
            }
        }
        return best;
    }

    /** What it holds from its storage goes back in (or on the ground, if it is full). */
    private void stow() {
        ItemStack hand = getMainHandItem();
        if (ownHand && !hand.isEmpty()) {
            ItemStack rest = storage.addItem(hand.copy());
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            if (!rest.isEmpty() && level() instanceof ServerLevel server) spawnAtLocation(server, rest);
        }
        ownHand = armed = false;
        showTicks = disarmTicks = 0;
    }

    /** Holding a weapon from its storage. */
    public boolean armed() {
        return armed;
    }

    /** What it digs up: mostly bits and bobs, now and then something nice. */
    private ItemStack dugUp() {
        float roll = getRandom().nextFloat();
        if (roll < 0.03f) return new ItemStack(Items.EMERALD);
        if (roll < 0.15f) return new ItemStack(Items.GOLD_NUGGET, 1 + getRandom().nextInt(3));
        if (roll < 0.35f) return new ItemStack(Items.FLINT);
        if (roll < 0.55f) return new ItemStack(Items.BONE);
        if (roll < 0.75f) return new ItemStack(Items.CLAY_BALL, 2);
        return new ItemStack(Items.STICK);
    }

    private static boolean soft(BlockState state) {
        return earthy(state) || state.is(net.minecraft.tags.BlockTags.SAND) || state.is(Blocks.GRAVEL);
    }

    /** Dirt, grass, podzol, mycelium (grass is not in the dirt tag). */
    public static boolean earthy(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM);
    }

    /** A jukebox close by with a record in it. */
    private boolean jukeboxPlaying() {
        for (BlockPos pos : BlockPos.betweenClosed(blockPosition().offset(-6, -2, -6), blockPosition().offset(6, 3, 6))) {
            BlockState state = level().getBlockState(pos);
            if (state.getBlock() instanceof net.minecraft.world.level.block.JukeboxBlock && state.getValue(net.minecraft.world.level.block.JukeboxBlock.HAS_RECORD)) return true;
        }
        return false;
    }

    /** Its owner asleep in bed, in this world and not too far: it goes to sleep next to them. */
    private @Nullable BlockPos ownersBed() {
        if (!(getOwner() instanceof Player owner) || !owner.isSleeping() || distanceToSqr(owner) > 32 * 32) return null;
        return owner.getSleepingPos().orElse(null);
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
        return super.isImmobile() || asleep() || dawdleTicks > 0 || assembly() > 0 || bedside || digTicks > 0 || abandoned();
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
                        m -> !(m instanceof BleepEntity) && m.isAlive() && !memory.mobs.containsKey(mobId(m)) && hasLineOfSight(m))
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
        if (memory.mobs.containsKey(id)) return;
        memory.mobs.put(id, level().getGameTime());
        this.entityData.set(NEWS, true);
        if (memory.mobs.size() == 1) remember("diary.bleep.first_mob", mob.getType().getDescriptionId());
        if (SPECIAL_MOBS.contains(id)) remember("diary.bleep.big_mob", mob.getType().getDescriptionId());
        String key = mobThought(id);
        pinThought(key);
        if (getOwner() instanceof Player owner && distanceToSqr(owner) < TOGETHER * TOGETHER) {
            owner.sendOverlayMessage(Component.translatable("message.bleep.thinks", getDisplayName(), Component.translatableWithFallback(key, UNKNOWN_MOB)));
            changeBond(Bond.DISCOVERY);
        }
    }

    /** A new biome (for its Bleepedia), found together with its owner (a little bond). */
    private void noticeBiome() {
        String biome = level().getBiome(blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("");
        if (biome.isEmpty() || memory.biomes.containsKey(biome)) return;
        memory.biomes.put(biome, level().getGameTime());
        if (memory.biomes.size() == 1) return; // where it was built is no discovery
        this.entityData.set(NEWS, true);
        if (getOwner() != null && distanceToSqr(getOwner()) < TOGETHER * TOGETHER) changeBond(Bond.DISCOVERY);
    }

    /** Special blocks close by (Memory.FINDS): a find for its Bleepedia, and a line in its diary. */
    private void lookForFinds() {
        BlockPos here = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(here.offset(-5, -3, -5), here.offset(5, 3, 5))) {
            var id = BuiltInRegistries.BLOCK.getKey(level().getBlockState(pos).getBlock());
            if (!id.getNamespace().equals("minecraft") || !Memory.FINDS.contains(id.getPath()) || memory.finds.containsKey(id.toString())) continue;
            memory.finds.put(id.toString(), level().getGameTime());
            remember("diary.bleep.find", level().getBlockState(pos).getBlock().getDescriptionId());
            pinThought("find.bleep." + id.getPath());
            feel(Emotion.SURPRISED, 30);
            return; // one at a time
        }
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
        memory.daysTogether++;
        changeBond((int) apart * Bond.DAY_APART + Bond.DAY_TOGETHER);
        if (Bond.level(bond()) >= 1 && getRandom().nextFloat() < 0.35f) writeLetter();
        lastTogetherDay = day;
    }

    /**
     * Its favourite item lying about: it is going to get it (FetchGoal). From Buddy it also brings what else lies about
     * (loot, drops), but not what its owner threw away on purpose.
     */
    private void lookForFavourite() {
        Obsession favourite = obsession();
        if (favourite == null || fetching != null || !getMainHandItem().isEmpty() || naughty.busy()) return;
        fetching = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(FETCH_RANGE), item -> favourite.matches().test(item.getItem()))
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (fetching != null || !can(Skill.FETCH) || getOwner() == null) return;
        LivingEntity owner = getOwner();
        fetching = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(LOOT_RANGE), item -> item.getOwner() != owner && item.isAlive())
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    /** Picks up its favourite item (so excited it glitches), or a whole stack of anything else. */
    private void pickUp(ItemEntity item) {
        Obsession favourite = obsession();
        boolean loved = favourite != null && favourite.matches().test(item.getItem()), ball = isBall(item.getItem());
        for (BleepEntity other : level().getEntitiesOfClass(BleepEntity.class, getBoundingBox().inflate(12), b -> b != this && b.fetching == item)) other.lostTo(this);
        setItemSlot(EquipmentSlot.MAINHAND, item.getItem().split(loved || ball ? 1 : item.getItem().getCount()));
        if (ball) {
            feel(Emotion.EXCITED, 30);
            ballDecided = false;
            ballPasses = 0;
        }
        if (item.getItem().isEmpty()) item.discard();
        fetching = null;
        if (loved) {
            feel(Emotion.GLITCH, 40);
            reveal(getOwner() instanceof Player owner ? owner : null);
        }
    }

    /** ...and gives it to its owner anyway. */
    private void handOver(Player owner) {
        ItemStack stack = getMainHandItem().copy();
        boolean ball = isBall(stack), food = stack.has(net.minecraft.core.component.DataComponents.FOOD); // before it goes: adding it to an inventory empties the stack
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!owner.getInventory().add(stack)) {
            level().addFreshEntity(new ItemEntity(level(), owner.getX(), owner.getY(), owner.getZ(), stack));
        }
        feel(Emotion.EXCITED, 40);
        if (ball) played();
        if (food) pinThought("thought.bleep.care.eat");
        if (!purse.isEmpty()) { // the emeralds it did not spend
            if (!owner.getInventory().add(purse)) level().addFreshEntity(new ItemEntity(level(), owner.getX(), owner.getY(), owner.getZ(), purse));
            purse = ItemStack.EMPTY;
        }
    }

    // ---------- the ball ----------

    public static boolean isBall(ItemStack stack) {
        return stack.is(BleepContent.BALL.get());
    }

    /** A ball lying about: it is going to get it (FetchGoal), whatever its bond. */
    private void lookForBall() {
        if (fetching != null || !getMainHandItem().isEmpty() || naughty.busy() || getTarget() != null || !(getOwner() instanceof Player) || !free()) return;
        fetching = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(BALL_RANGE), i -> i.isAlive() && isBall(i.getItem()))
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (fetching != null) pinThought("thought.bleep.ball.0");
    }

    /** A ball thrown at it: caught, and it brings it back. */
    public boolean catchBall(ItemStack ball) {
        return catchBall(ball, 0, null);
    }

    public boolean catchBall(ItemStack ball, int passes, net.minecraft.world.entity.@Nullable Entity thrower) {
        if (!getMainHandItem().isEmpty() || asleep() || abandoned()) return false;
        setItemSlot(EquipmentSlot.MAINHAND, ball);
        feel(Emotion.EXCITED, 40);
        ballPasses = passes;
        ballDecided = false;
        if (thrower instanceof BleepEntity mate) { // a pass from a friend
            say("thought.bleep.ball.nice");
            befriend(this, mate, 1);
        } else {
            pinThought("thought.bleep.ball.caught");
        }
        return true;
    }

    /** Holding the ball: now and then it passes it to another bleep first (three passes at most), then it brings it back. */
    private boolean maybePassBall() {
        if (ballDecided || !isBall(getMainHandItem())) return false;
        ballDecided = true;
        if (ballPasses >= 3 || getRandom().nextFloat() >= 0.5f) return false;
        BleepEntity mate = level().getEntitiesOfClass(BleepEntity.class, getBoundingBox().inflate(10), b -> b != this && b.free() && b.getMainHandItem().isEmpty() && hasLineOfSight(b))
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (mate == null) return false;
        BallEntity ball = new BallEntity(level(), this, getMainHandItem().copy());
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        ball.setPos(getX(), getEyeY() - 0.3, getZ());
        double dx = mate.getX() - getX(), dz = mate.getZ() - getZ(), dy = mate.getY() + 0.6 - ball.getY(), d = Math.sqrt(dx * dx + dz * dz);
        ball.shoot(dx, dy + d * 0.2, dz, (float) (0.35 + d * 0.06), 2);
        ball.passes = ballPasses + 1;
        level().addFreshEntity(ball);
        io.hookdev.bleep.McVersion.swing(this);
        getLookControl().setLookAt(mate);
        say("thought.bleep.ball.pass");
        return true;
    }

    /** Brought the ball back: that was fun (a bond point, five a day). */
    private void played() {
        today();
        hobbyPoint("playing");
        pinThought("thought.bleep.ball." + (1 + getRandom().nextInt(3)));
        rememberFirst("ball", "diary.bleep.ball", "");
        if (playsToday < Bond.PLAYS_PER_DAY) {
            playsToday++;
            changeBond(Bond.PLAY);
        }
    }

    private boolean ownerHasBall() {
        if (!(getOwner() instanceof Player owner)) return false;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) if (isBall(owner.getInventory().getItem(i))) return true;
        return false;
    }

    // ---------- bleeps together ----------

    /** Another bleep got there first. */
    private void lostTo(BleepEntity winner) {
        fetching = null;
        getNavigation().stop();
        feel(Emotion.ANGRY, 40);
        pinThought("thought.bleep.together.mine." + getRandom().nextInt(3));
        rememberFirst("squabble", "diary.bleep.squabble", winner.getName().getString());
        winner.pinThought("thought.bleep.together.won");
        if (getRandom().nextFloat() < 0.35f) startFight(winner);
    }

    /** Its owner petted another bleep. */
    private void jealous() {
        if (jealousTicks > 0) return;
        jealousTicks = 400;
        feel(Emotion.SAD, 60);
        pinThought("thought.bleep.together.jealous." + getRandom().nextInt(3));
        rememberFirst("jealous", "diary.bleep.jealous", "");
    }

    public boolean playing() {
        return playTicks > 0 && partner != null;
    }

    /** Starts a game of tag with another bleep: this one is "it" (tests). */
    public boolean playWith(BleepEntity other) {
        if (other == this || playing() || other.playing() || !free() || !other.free() || fightingWith != null || other.fightingWith != null) return false;
        for (BleepEntity b : List.of(this, other)) {
            b.partner = b == this ? other : this;
            b.playTicks = 240;
            b.playCooldown = 1200;
            b.it = b == this;
            b.tagCooldown = 0;
            b.pinThought("thought.bleep.play." + b.getRandom().nextInt(3));
            b.rememberFirst("friend", "diary.bleep.friend", b.partner.getName().getString());
        }
        say("thought.bleep.tag.start");
        return true;
    }

    /** Whether it is "it" in a game of tag. */
    public boolean it() {
        return playing() && it;
    }

    // ---------- friends ----------

    /** How good friends it is with another bleep (below 0: not at all). */
    public int friendship(BleepEntity other) {
        Memory.Friend friend = memory.friends.get(other.getUUID().toString());
        return friend == null ? 0 : friend.points();
    }

    /** Better (or worse) friends, both ways; a new best friend or a new rival goes in the diary. */
    private static void befriend(BleepEntity a, BleepEntity b, int points) {
        for (BleepEntity self : List.of(a, b)) {
            BleepEntity other = self == a ? b : a;
            String id = other.getUUID().toString(), name = other.getName().getString();
            int before = self.friendship(other);
            Memory.Friend now = self.memory.befriend(id, name, points);
            if (before < Memory.Friend.BEST && now.points() >= Memory.Friend.BEST) self.rememberFirst("best_friend_" + id, "diary.bleep.best_friend", name);
            if (before > Memory.Friend.RIVAL && now.points() <= Memory.Friend.RIVAL) self.rememberFirst("rival_" + id, "diary.bleep.rival", name);
        }
    }

    /** Its best friend, if it is close by. */
    public @Nullable BleepEntity bestFriendNear(double range) {
        var best = memory.bestFriend();
        if (best == null) return null;
        return level().getEntitiesOfClass(BleepEntity.class, getBoundingBox().inflate(range), b -> b.getUUID().toString().equals(best.getKey())).stream().findFirst().orElse(null);
    }

    /** Says something: a thought, a blip, and its owner close by reads it above the hotbar. */
    private void say(String key) {
        pinThought(key);
        talk(emotion() == Emotion.ASLEEP ? Emotion.HAPPY : emotion());
        if (getOwner() instanceof Player owner && distanceToSqr(owner) < 24 * 24) {
            owner.sendOverlayMessage(Component.translatable("message.bleep.thinks", getDisplayName(), Component.translatable(key)));
        }
    }

    /** Happy, excited or just bored: a sad, angry or scared bleep does not say hi. */
    private boolean inTheMood() {
        Emotion emotion = currentEmotion(); // as it is now, not as it was shown last tick
        return emotion == Emotion.HAPPY || emotion == Emotion.EXCITED || emotion == Emotion.BORED;
    }

    /** Both say hi: HI and a heart on their screens, arms in the air. */
    private void greet(BleepEntity other) {
        for (BleepEntity b : List.of(this, other)) {
            b.greeting = b == this ? other : this;
            b.greetTicks = 60;
            b.feel(Emotion.EXCITED, 60);
            b.showDisplay(Display.HI, 60);
            b.rememberFirst("greet", "diary.bleep.greet", b.greeting.getName().getString());
        }
        say("thought.bleep.greet." + getRandom().nextInt(3));
        other.pinThought("thought.bleep.greet.back");
        befriend(this, other, 1);
    }

    /** A little chat, four lines, taking turns (tests: which chat). */
    public boolean startChat(BleepEntity other, int which) {
        if (other == this || talkingTo != null || other.talkingTo != null || fightingWith != null || other.fightingWith != null) return false;
        for (BleepEntity b : List.of(this, other)) {
            b.talkingTo = b == this ? other : this;
            b.talkTicks = CHAT_LINES * CHAT_STEP + 20;
            b.chat = which;
            b.leads = b == this;
            b.hobbyPoint("chatting");
        }
        return true;
    }

    public boolean talking() {
        return talkingTo != null;
    }

    /** A fight: angry faces, thunder clouds over their heads, and a few hits (tests). */
    public boolean startFight(BleepEntity other) {
        if (other == this || fightingWith != null || other.fightingWith != null || slept || other.slept || abandoned() || other.abandoned()) return false;
        for (BleepEntity b : List.of(this, other)) {
            b.fightingWith = b == this ? other : this;
            b.fightTicks = 120;
            b.talkingTo = null;
            b.playTicks = 0;
            b.partner = null;
            b.feel(Emotion.ANGRY, 120);
            b.rememberFirst("bleep_fight", "diary.bleep.bleep_fight", b.fightingWith.getName().getString());
        }
        say("thought.bleep.fight.start." + getRandom().nextInt(3));
        other.pinThought("thought.bleep.fight.start." + other.getRandom().nextInt(3));
        if (getOwner() instanceof Player owner && distanceToSqr(owner) < 24 * 24) {
            owner.sendOverlayMessage(Component.translatable("message.bleep.fighting", getDisplayName(), other.getDisplayName()));
        }
        befriend(this, other, -3);
        return true;
    }

    public boolean fighting() {
        return fightingWith != null;
    }

    /** The fight is over: both a little sad, and sorry. */
    private void endFight(String thought) {
        BleepEntity other = fightingWith;
        for (BleepEntity b : other == null ? List.of(this) : List.of(this, other)) {
            b.fightingWith = null;
            b.fightTicks = 0;
            b.feel(Emotion.SAD, 60);
        }
        say(thought);
        if (other != null) other.pinThought("thought.bleep.fight.after." + other.getRandom().nextInt(3));
    }

    /**
     * Life with other bleeps: saying hi to one it has not seen for a while, chats, tag, now and then a fight, cheering
     * up a sad one, and looking for its best friend when it is bored.
     */
    private void tickTogether() {
        if (playCooldown > 0) playCooldown--;
        if (cheerTicks > 0) cheerTicks--;
        if (jealousTicks > 0) jealousTicks--;
        if (tagCooldown > 0) tagCooldown--;
        if (greetTicks > 0 && --greetTicks == 0) greeting = null;
        if (playTicks > 0 && (--playTicks == 0 || partner == null || !partner.isAlive() || partner.slept)) {
            if (playTicks == 0 && partner != null && getId() < partner.getId()) befriend(this, partner, 2); // a good game
            playTicks = 0;
            partner = null;
        }
        if (playing() && it && tagCooldown == 0 && distanceToSqr(partner) < 1.4 * 1.4) { // tag! you are it
            it = false;
            partner.it = true;
            partner.tagCooldown = 30;
            feel(Emotion.EXCITED, 30);
            say("thought.bleep.tag.tagged");
            partner.pinThought("thought.bleep.tag.it");
        }
        if (talkingTo != null) tickChat();
        if (fightingWith != null) tickFight();
        if (slept) return;
        if (tickCount % 20 == 0) { // who is about: one not seen for a while gets a hi
            long now = level().getGameTime();
            for (BleepEntity other : level().getEntitiesOfClass(BleepEntity.class, getBoundingBox().inflate(12), b -> b != this && !b.slept && !b.abandoned())) {
                Long last = lastSeen.get(other.getUUID());
                if (last != null && now - last <= GREET_AFTER) { // seen it lately: no need to say hi again
                    lastSeen.put(other.getUUID(), now);
                } else if (greetTicks == 0 && other.greetTicks == 0 && free() && other.free() && fightingWith == null && !abandoned()
                        && inTheMood() && other.inTheMood()) { // not seen for a while: hi! (a busy one gets its hi a moment later)
                    lastSeen.put(other.getUUID(), now);
                    other.lastSeen.put(getUUID(), now);
                    greet(other);
                }
            }
        }
        if (tickCount % 200 == 0 && talkingTo == null && fightingWith == null && quietTicks > 100 && mayBeNaughty()) { // a chat
            BleepEntity best = bestFriendNear(8);
            float chance = (best != null ? 0.5f : 0.2f) * (hobby("chatting") ? 2 : 1);
            if (getRandom().nextFloat() < chance) {
                BleepEntity other = best != null && best.free() && best.talkingTo == null ? best
                        : level().getEntitiesOfClass(BleepEntity.class, getBoundingBox().inflate(8), b -> b != this && b.free() && b.talkingTo == null && !b.playing() && friendship(b) > Memory.Friend.RIVAL)
                                .stream().findFirst().orElse(null);
                if (other != null) startChat(other, getRandom().nextInt(CHATS));
            }
        }
        if (tickCount % 400 == 200 && fightingWith == null && quietTicks > 200 && mayBeNaughty()) { // rivals, and now and then strangers, fall out
            level().getEntitiesOfClass(BleepEntity.class, getBoundingBox().inflate(8), b -> b != this && b.free() && !b.slept && !b.abandoned()
                    && (friendship(b) <= Memory.Friend.RIVAL ? getRandom().nextFloat() < 0.3f : friendship(b) <= 0 && getRandom().nextFloat() < 0.05f))
                    .stream().findFirst().ifPresent(this::startFight);
        }
        if (tickCount % 100 == 25 && quietTicks > 400 && free() && !playing() && talkingTo == null) { // bored: where is my best friend?
            BleepEntity best = bestFriendNear(24);
            if (best != null && distanceToSqr(best) > 5 * 5) getNavigation().moveTo(best, 1.0);
        }
        if (tickCount % 200 == 100 && playTicks == 0 && playCooldown == 0 && quietTicks > 200 && mayBeNaughty() && getRandom().nextFloat() < (hobby("playing") ? 0.5f : 0.25f)) {
            level().getEntitiesOfClass(BleepEntity.class, getBoundingBox().inflate(10), b -> b != this && !b.slept && !b.abandoned() && b.playCooldown == 0)
                    .stream().findFirst().ifPresent(this::playWith);
        }
        Emotion emotion = emotion();
        if (tickCount % 100 == 50 && (emotion == Emotion.HAPPY || emotion == Emotion.EXCITED)) {
            for (BleepEntity other : level().getEntitiesOfClass(BleepEntity.class, getBoundingBox().inflate(8), b -> b != this && !b.slept && !b.abandoned() && b.cheerTicks == 0
                    && (b.emotion() == Emotion.SAD || b.emotion() == Emotion.BORED))) {
                other.cheerTicks = 1200;
                other.idleTicks = 0;
                other.feel(Emotion.HAPPY, 60);
                other.pinThought("thought.bleep.together.cheered");
            }
        }
    }

    // ---------- home comforts ----------

    /** The chat: every CHAT_STEP ticks the next line, taking turns (the one who started says the first). */
    private void tickChat() {
        if (talkingTo == null || !talkingTo.isAlive() || slept || --talkTicks <= 0) {
            if (talkingTo != null && leads && talkTicks <= 0) befriend(this, talkingTo, 1);
            talkingTo = null;
            return;
        }
        if (!leads || distanceToSqr(talkingTo) > 4 * 4) return;
        int elapsed = CHAT_LINES * CHAT_STEP + 20 - talkTicks;
        if (elapsed % CHAT_STEP != 0 || elapsed / CHAT_STEP >= CHAT_LINES) return;
        int line = elapsed / CHAT_STEP;
        BleepEntity speaker = line % 2 == 0 ? this : talkingTo;
        speaker.say("chat.bleep." + chat + "." + line);
        speaker.showDisplay(Display.TALK, CHAT_STEP - 10);
    }

    /** The fight: thunder clouds over their heads; now and then one hits the other (a shove and a buzz, a little battery). */
    private void tickFight() {
        if (fightingWith == null || !fightingWith.isAlive() || slept || fightingWith.slept || --fightTicks <= 0) {
            if (fightingWith != null && fightTicks <= 0 && getId() < fightingWith.getId()) endFight("thought.bleep.fight.after." + getRandom().nextInt(3));
            else if (fightingWith == null || !fightingWith.isAlive()) fightingWith = null;
            return;
        }
        if (level() instanceof ServerLevel server && tickCount % 5 == 0) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 1.6, getZ(), 1, 0.2, 0.1, 0.2, 0);
        }
        getLookControl().setLookAt(fightingWith);
        if ((fightTicks + (getId() % 2) * 10) % 20 == 0 && distanceToSqr(fightingWith) < 2.2 * 2.2) {
            io.hookdev.bleep.McVersion.swing(this);
            var shove = fightingWith.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.35);
            fightingWith.push(shove.x, 0.15, shove.z);
            fightingWith.needsSync = true;
            fightingWith.playSound(BleepContent.HURT.get(), VOLUME, fightingWith.getVoicePitch());
            fightingWith.setBattery(fightingWith.battery() - 0.5f);
            talk(Emotion.ANGRY);
        }
    }

    // ---------- chips ----------

    public boolean has(Chip chip) {
        return (this.entityData.get(CHIPS) & chip.bit()) != 0;
    }

    public int chips() {
        return this.entityData.get(CHIPS);
    }

    /** Installs a chip for good: its start-up screen for a moment, and then it feels the difference. */
    public void install(Chip chip) {
        this.entityData.set(CHIPS, chips() | chip.bit());
        applyChips();
        feel(Emotion.BOOT, 40);
        say("thought.bleep.chip." + chip.name().toLowerCase(java.util.Locale.ROOT));
        remember("diary.bleep.chip", "item.bleep." + chip.id());
    }

    /** Takes a chip out again: its owner gets it back. */
    public boolean uninstall(Chip chip, Player player) {
        if (!has(chip)) return false;
        this.entityData.set(CHIPS, chips() & ~chip.bit());
        applyChips();
        ItemStack item = new ItemStack(BleepContent.CHIPS.get(chip).get());
        if (!player.getInventory().add(item)) spawnAtLocation((ServerLevel) level(), item);
        feel(Emotion.SURPRISED, 30);
        say("thought.bleep.chip.removed");
        return true;
    }

    private static final Identifier SPEED_CHIP = Identifier.fromNamespaceAndPath("bleep", "speed_chip");

    /** The speed chip drives a third faster. */
    private void applyChips() {
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (has(Chip.SPEED)) speed.addOrReplacePermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SPEED_CHIP, 0.33,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else speed.removeModifier(SPEED_CHIP);
    }

    /** Battery used up: with the battery chip it lasts half as long again. */
    private void drain(float amount) {
        setBattery(battery() - amount * (has(Chip.BATTERY) ? 2 / 3f : 1));
    }

    // ---------- letters ----------

    /** A letter for its owner: a book, written from its diary, that it brings over. */
    public boolean writeLetter() {
        if (!(getOwner() instanceof Player owner) || !getMainHandItem().isEmpty() || ownHand || memory.diary.size() < 2 || slept) return false;
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("letter.bleep.dear", owner.getName()));
        int from = Math.max(0, memory.diary.size() - 3);
        for (Memory.Entry e : memory.diary.subList(from, memory.diary.size())) {
            lines.add(e.arg().isEmpty() ? Component.translatable(e.key()) : Component.translatable(e.key(), Component.translatable(e.arg())));
        }
        lines.add(Component.translatable("letter.bleep.end." + getRandom().nextInt(3)));
        lines.add(Component.translatable("letter.bleep.from", getName()));
        net.minecraft.network.chat.MutableComponent page = Component.empty();
        for (int i = 0; i < lines.size(); i++) page.append(i == 0 ? Component.empty() : Component.literal("\n\n")).append(lines.get(i));
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT, new net.minecraft.world.item.component.WrittenBookContent(
                net.minecraft.server.network.Filterable.passThrough(Component.translatable("letter.bleep.title").getString()), getName().getString(), 0,
                List.of(net.minecraft.server.network.Filterable.passThrough((Component) page)), true));
        setItemSlot(EquipmentSlot.MAINHAND, book);
        say("thought.bleep.letter");
        rememberFirst("letter", "diary.bleep.letter", "");
        return true;
    }

    // ---------- work, while it stays ----------

    public int jobs() {
        return this.entityData.get(JOBS);
    }

    public boolean doing(int job) {
        return (jobs() & job) != 0;
    }

    public void toggleJob(int job) {
        this.entityData.set(JOBS, jobs() ^ job);
        workAt = null;
    }

    /** How far it potters about (and works) from its spot. */
    public int stayRadius() {
        return this.entityData.get(RADIUS);
    }

    public void setStayRadius(int radius) {
        this.entityData.set(RADIUS, Mth.clamp(radius, MIN_RADIUS, MAX_RADIUS));
        if (stayAt != null) setHomeTo(stayAt, stayRadius());
    }

    /** Can it work now: staying somewhere (not parked), awake, nothing else going on. */
    private boolean atWork() {
        return stayAt != null && isOrderedToSit() && !slept && !abandoned() && free();
    }

    /** Its jobs: a ripe crop or a chest to go to, and at night, an eye out for monsters. */
    private void tickWork() {
        if (alarmCooldown > 0) alarmCooldown--;
        tickFishing();
        if (stayAt == null || !isOrderedToSit()) {
            workAt = null;
            return;
        }
        if (doing(JOB_GUARD) && tickCount % 20 == 5 && alarmCooldown == 0 && !slept && level().isDarkOutside()) keepGuard();
        if (workAt != null || tickCount % 40 != 15 || !atWork() || busyWithOthers()) return;
        if (tickCount % 2400 == 15) unreachable.clear();
        if (doing(JOB_FARMER) && find(WORK_CROP, pos -> level().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(level().getBlockState(pos)))) return;
        if (doing(JOB_TIDY) && hasTidyable() && find(WORK_CHEST, pos -> level().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
                || level().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity)) return;
        if (doing(JOB_SMELTER) && find(WORK_FURNACE, pos -> level().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace && furnaceNeedsMe(furnace))) return;
        if (doing(JOB_FISHER) && fishCooldown == 0 && find(WORK_FISH, pos -> waterNextTo(pos) != null)) {
            fishWater = waterNextTo(workAt);
            return;
        }
        if (doing(JOB_HERDER) && findAnimal()) return;
        if (doing(JOB_LUMBERJACK) && find(WORK_TREE, this::treeBase)) return;
        BlockState flower = flowerToPlant();
        if (doing(JOB_GARDENER) && flower != null && gardenSize() < GARDEN_SIZE) {
            find(WORK_GARDEN, pos -> Math.floorMod(pos.getX() - stayAt.getX() + pos.getZ() - stayAt.getZ(), 2) == 0 && !pos.equals(stayAt) // a chequered garden
                    && level().getBlockState(pos).isAir() && !level().getBlockState(pos.below()).is(Blocks.FARMLAND) && flower.canSurvive(level(), pos));
        }
    }

    /** The nearest place round its spot (within its radius) for this kind of work; true if it found one. */
    private boolean find(int kind, java.util.function.Predicate<BlockPos> fits) {
        int r = stayRadius();
        BlockPos best = null;
        double nearest = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(stayAt.offset(-r, -2, -r), stayAt.offset(r, 2, r))) {
            double d = pos.distSqr(blockPosition());
            if (d >= nearest || unreachable.contains(pos) || !fits.test(pos)) continue;
            best = pos.immutable();
            nearest = d;
        }
        if (best == null) return false;
        workAt = best;
        workKind = kind;
        return true;
    }

    // ---------- the herder, the lumberjack ----------

    /** A sheep to shear, or an animal to feed so it has young (with food for it in its storage, a mate close by, not too many already). */
    private boolean findAnimal() {
        int r = stayRadius();
        var animals = level().getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, new net.minecraft.world.phys.AABB(stayAt).inflate(r, 2, r),
                a -> a.isAlive() && !(a instanceof BleepEntity) && !a.isBaby() && !unreachable.contains(a.blockPosition()));
        net.minecraft.world.entity.animal.Animal best = null;
        double nearest = Double.MAX_VALUE;
        for (var animal : animals) {
            boolean shear = animal instanceof net.minecraft.world.entity.animal.sheep.Sheep sheep && sheep.readyForShearing();
            long kind = animals.stream().filter(a -> a.getType() == animal.getType()).count();
            boolean feed = !shear && animal.getAge() == 0 && animal.canFallInLove() && foodSlot(animal) >= 0 && kind >= 2 && kind < HERD_MAX;
            double d = distanceToSqr(animal);
            if ((shear || feed) && d < nearest) {
                best = animal;
                nearest = d;
            }
        }
        if (best == null) return false;
        workAnimal = best;
        workAt = best.blockPosition();
        workKind = WORK_ANIMAL;
        return true;
    }

    /** A slot in its storage with food this animal likes, or -1. */
    private int foodSlot(net.minecraft.world.entity.animal.Animal animal) {
        for (int i = 0; i < storage.getContainerSize(); i++) if (!storage.getItem(i).isEmpty() && animal.isFood(storage.getItem(i))) return i;
        return -1;
    }

    /** At the animal: shears a sheep (the wool goes in its storage), or feeds it (and it falls in love). */
    private void herd(net.minecraft.world.entity.animal.@Nullable Animal animal) {
        if (animal == null || !animal.isAlive() || distanceToSqr(animal) > 3 * 3) return;
        if (animal instanceof net.minecraft.world.entity.animal.sheep.Sheep sheep && sheep.readyForShearing()) {
            sheep.setSheared(true);
            var wool = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(sheep.getColor().getSerializedName() + "_wool"));
            ItemStack rest = storage.addItem(new ItemStack(wool, 1 + getRandom().nextInt(3)));
            if (!rest.isEmpty()) spawnAtLocation((ServerLevel) level(), rest);
            sheep.playSound(net.minecraft.sounds.SoundEvents.SHEEP_SHEAR, 1, 1);
            if (getRandom().nextFloat() < 0.3f) pinThought("thought.bleep.work.shear");
        } else {
            int slot = foodSlot(animal);
            if (slot < 0 || !animal.canFallInLove() || animal.getAge() != 0) return;
            storage.removeItem(slot, 1);
            animal.setInLove(null);
            if (getRandom().nextFloat() < 0.3f) pinThought("thought.bleep.work.herd");
        }
        rememberFirst("herder", "diary.bleep.herder", "");
    }

    /** The bottom log of a real tree: on the ground, with natural leaves round its top (not a log someone built with). */
    private boolean treeBase(BlockPos pos) {
        if (!level().getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS) || !earthy(level().getBlockState(pos.below()))) return false;
        BlockPos top = pos;
        for (int i = 0; i < 24 && level().getBlockState(top.above()).is(net.minecraft.tags.BlockTags.LOGS); i++) top = top.above();
        for (BlockPos at : BlockPos.betweenClosed(top.offset(-2, -1, -2), top.offset(2, 2, 2))) {
            BlockState leaf = level().getBlockState(at);
            if (leaf.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock && !leaf.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)) return true;
        }
        return false;
    }

    /** Fells the tree: every log of it (64 at most) into its storage, and a sapling from its storage in its place. The leaves fall by themselves. */
    private void chop(BlockPos base) {
        if (!(level() instanceof ServerLevel server) || !treeBase(base)) return;
        java.util.ArrayDeque<BlockPos> todo = new java.util.ArrayDeque<>(List.of(base));
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        int felled = 0;
        while (!todo.isEmpty() && felled < 64) {
            BlockPos at = todo.poll();
            if (!seen.add(at) || at.getY() < base.getY() || Math.abs(at.getX() - base.getX()) > 4 || Math.abs(at.getZ() - base.getZ()) > 4) continue;
            BlockState log = level().getBlockState(at);
            if (!log.is(net.minecraft.tags.BlockTags.LOGS)) continue;
            for (ItemStack drop : net.minecraft.world.level.block.Block.getDrops(log, server, at, null)) {
                ItemStack rest = storage.addItem(drop);
                if (!rest.isEmpty()) spawnAtLocation(server, rest);
            }
            level().levelEvent(2001, at, net.minecraft.world.level.block.Block.getId(log));
            level().setBlock(at, Blocks.AIR.defaultBlockState(), 3);
            felled++;
            for (BlockPos next : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 1, 1))) todo.add(next.immutable());
        }
        for (int i = 0; i < storage.getContainerSize(); i++) { // a new tree where the old one stood
            ItemStack sapling = storage.getItem(i);
            if (!sapling.is(net.minecraft.tags.ItemTags.SAPLINGS) || !(sapling.getItem() instanceof net.minecraft.world.item.BlockItem item)) continue;
            BlockState state = item.getBlock().defaultBlockState();
            if (!state.canSurvive(level(), base)) continue;
            level().setBlock(base, state, 3);
            storage.removeItem(i, 1);
            break;
        }
        playSound(net.minecraft.sounds.SoundEvents.WOOD_BREAK, 1, 0.9f);
        if (getRandom().nextFloat() < 0.4f) pinThought("thought.bleep.work.chop");
        rememberFirst("lumberjack", "diary.bleep.lumberjack", "");
    }

    // ---------- the fisher, the smelter, the gardener ----------

    /** A spot to fish from: open ground right next to still water. */
    private @Nullable BlockPos waterNextTo(BlockPos pos) {
        if (!level().getBlockState(pos).isAir() || !level().getBlockState(pos.below()).isFaceSturdy(level(), pos.below(), net.minecraft.core.Direction.UP)) return null;
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos water = pos.relative(side).below();
            if (level().getFluidState(water).is(net.minecraft.tags.FluidTags.WATER) && level().getFluidState(water).isSource()) return water.immutable();
            if (level().getFluidState(pos.relative(side)).is(net.minecraft.tags.FluidTags.WATER) && level().getFluidState(pos.relative(side)).isSource()) return pos.relative(side).immutable();
        }
        return null;
    }

    /** Out with its line: a bite comes after a few seconds. */
    private void startFishing() {
        fishTicks = 100 + getRandom().nextInt(80);
        if (getRandom().nextFloat() < 0.3f) pinThought("thought.bleep.work.fish");
    }

    /** Fishing: still, looking at the water, a splash now and then; then what it caught goes in its storage. */
    private void tickFishing() {
        if (fishCooldown > 0) fishCooldown--;
        if (fishTicks <= 0) return;
        getNavigation().stop();
        if (fishWater == null || !isOrderedToSit()) {
            fishTicks = 0;
            return;
        }
        getLookControl().setLookAt(net.minecraft.world.phys.Vec3.atCenterOf(fishWater));
        if (level() instanceof ServerLevel server && tickCount % 20 == 0) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.FISHING, fishWater.getX() + 0.5, fishWater.getY() + 1, fishWater.getZ() + 0.5, 3, 0.2, 0, 0.2, 0.02);
        }
        if (--fishTicks == 0 && level() instanceof ServerLevel server) {
            ItemStack catchOf = catchOf();
            ItemStack rest = storage.addItem(catchOf.copy());
            if (!rest.isEmpty()) spawnAtLocation(server, rest);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, fishWater.getX() + 0.5, fishWater.getY() + 1, fishWater.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.1);
            playSound(net.minecraft.sounds.SoundEvents.FISHING_BOBBER_SPLASH, 0.6f, 1.2f);
            io.hookdev.bleep.McVersion.swing(this);
            if (getRandom().nextFloat() < 0.3f || !catchOf.is(net.minecraft.tags.ItemTags.FISHES)) pinThought(catchOf.is(net.minecraft.tags.ItemTags.FISHES) ? "thought.bleep.work.fish_caught" : "thought.bleep.work.treasure");
            rememberFirst("fisher", "diary.bleep.fisher", "");
            memory.add("catches");
            fishCooldown = 200 + getRandom().nextInt(400);
            fishWater = null;
        }
    }

    /** What bites: mostly fish, now and then junk, once in a while a treasure. */
    private ItemStack catchOf() {
        float roll = getRandom().nextFloat();
        if (roll < 0.05f) {
            var treasure = List.of(Items.NAME_TAG, Items.SADDLE, Items.NAUTILUS_SHELL, Items.BOW);
            return new ItemStack(treasure.get(getRandom().nextInt(treasure.size())));
        }
        if (roll < 0.15f) {
            var junk = List.of(Items.STICK, Items.BONE, Items.STRING, Items.BOWL, Items.LILY_PAD, Items.INK_SAC);
            return new ItemStack(junk.get(getRandom().nextInt(junk.size())));
        }
        float fish = getRandom().nextFloat();
        return new ItemStack(fish < 0.6f ? Items.COD : fish < 0.85f ? Items.SALMON : fish < 0.98f ? Items.PUFFERFISH : Items.TROPICAL_FISH);
    }

    /** What a furnace cooks: a furnace smelts, a blast furnace blasts, a smoker smokes. */
    private net.minecraft.world.item.crafting.RecipeType<? extends net.minecraft.world.item.crafting.AbstractCookingRecipe> cooks(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace) {
        if (furnace instanceof net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity) return net.minecraft.world.item.crafting.RecipeType.BLASTING;
        if (furnace instanceof net.minecraft.world.level.block.entity.SmokerBlockEntity) return net.minecraft.world.item.crafting.RecipeType.SMOKING;
        return net.minecraft.world.item.crafting.RecipeType.SMELTING;
    }

    private boolean cookable(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace, ItemStack stack) {
        return !stack.isEmpty() && !keeps(stack) && level() instanceof ServerLevel server
                && server.recipeAccess().getRecipeFor(cooks(furnace), new net.minecraft.world.item.crafting.SingleRecipeInput(stack), server, (net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>) null).isPresent();
    }

    /** Fuel: what the furnace takes in its fuel slot (and that is not better smelted). */
    private boolean fuel(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace, ItemStack stack) {
        return !stack.isEmpty() && !stack.is(Items.BUCKET) && !keeps(stack) && furnace.canPlaceItem(1, stack) && !cookable(furnace, stack);
    }

    /** Something to take out, something to put in, or fuel it has and the furnace lacks. */
    private boolean furnaceNeedsMe(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace) {
        if (!furnace.getItem(2).isEmpty() && storage.canAddItem(furnace.getItem(2))) return true;
        boolean input = false, fuel = false;
        for (int i = 0; i < storage.getContainerSize(); i++) {
            ItemStack stack = storage.getItem(i);
            if (cookable(furnace, stack) && (furnace.getItem(0).isEmpty() || ItemStack.isSameItemSameComponents(furnace.getItem(0), stack))) input = true;
            if (fuel(furnace, stack) && (furnace.getItem(1).isEmpty() || ItemStack.isSameItemSameComponents(furnace.getItem(1), stack))) fuel = true;
        }
        return (input && furnace.getItem(0).getCount() < furnace.getItem(0).getMaxStackSize() && (furnace.getItem(0).isEmpty() || furnace.getItem(0).getCount() < 8))
                || (fuel && furnace.getItem(1).getCount() < 8 && (!furnace.getItem(0).isEmpty() || input));
    }

    /** At the furnace: what is done goes in its storage, more to smelt and fuel go in. */
    private void smelt(BlockPos pos) {
        if (!(level().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace)) return;
        ItemStack done = furnace.getItem(2);
        if (!done.isEmpty()) furnace.setItem(2, storage.addItem(done.copy()));
        for (int slot : new int[] {0, 1}) {
            for (int i = 0; i < storage.getContainerSize(); i++) {
                ItemStack stack = storage.getItem(i), there = furnace.getItem(slot);
                if (!(slot == 0 ? cookable(furnace, stack) : fuel(furnace, stack))) continue;
                if (there.isEmpty()) {
                    furnace.setItem(slot, stack.split(stack.getCount()));
                } else if (ItemStack.isSameItemSameComponents(there, stack)) {
                    int move = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
                    there.grow(move);
                    stack.shrink(move);
                } else {
                    continue;
                }
                storage.setItem(i, stack);
                break;
            }
        }
        furnace.setChanged();
        if (getRandom().nextFloat() < 0.3f) pinThought("thought.bleep.work.smelt");
        rememberFirst("smelter", "diary.bleep.smelter", "");
    }

    /** A flower from its storage (a small flower that can stand on its own), or -1. */
    private int flowerSlot() {
        for (int i = 0; i < storage.getContainerSize(); i++) {
            if (storage.getItem(i).getItem() instanceof net.minecraft.world.item.BlockItem block && block.getBlock().defaultBlockState().is(net.minecraft.tags.BlockTags.SMALL_FLOWERS)) return i;
        }
        return -1;
    }

    private @Nullable BlockState flowerToPlant() {
        int slot = flowerSlot();
        return slot < 0 ? null : ((net.minecraft.world.item.BlockItem) storage.getItem(slot).getItem()).getBlock().defaultBlockState();
    }

    /** How many flowers already grow round its spot. */
    private int gardenSize() {
        int r = stayRadius(), n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(stayAt.offset(-r, -2, -r), stayAt.offset(r, 2, r))) if (level().getBlockState(pos).is(net.minecraft.tags.BlockTags.SMALL_FLOWERS)) n++;
        return n;
    }

    /** Plants a flower from its storage here; with bone meal it gives the garden a sprinkle too. */
    private void garden(BlockPos pos) {
        int slot = flowerSlot();
        BlockState flower = flowerToPlant();
        if (slot < 0 || flower == null || !level().getBlockState(pos).isAir() || !flower.canSurvive(level(), pos)) return;
        level().setBlock(pos, flower, 3);
        storage.removeItem(slot, 1);
        playSound(net.minecraft.sounds.SoundEvents.GRASS_PLACE, 0.6f, 1.2f);
        if (getRandom().nextFloat() < 0.3f) pinThought("thought.bleep.work.garden");
        rememberFirst("gardener", "diary.bleep.gardener", "");
        for (int i = 0; i < storage.getContainerSize(); i++) {
            if (storage.getItem(i).is(Items.BONE_MEAL) && getRandom().nextFloat() < 0.3f) {
                if (net.minecraft.world.item.BoneMealItem.applyBonemeal(storage.getItem(i), level(), pos.below(), null)) level().levelEvent(1505, pos.below(), 15);
                break;
            }
        }
    }

    /** What it keeps when it tidies up: weapons, balls and its favourite item. */
    private boolean keeps(ItemStack stack) {
        Obsession favourite = obsession();
        return stack.has(net.minecraft.core.component.DataComponents.WEAPON) || isBall(stack) || (favourite != null && favourite.matches().test(stack));
    }

    private boolean hasTidyable() {
        for (int i = 0; i < storage.getContainerSize(); i++) if (!storage.getItem(i).isEmpty() && !keeps(storage.getItem(i))) return true;
        return false;
    }

    /** Arrived at its work: harvests the crop (and plants it again) or empties its storage into the chest. */
    private void work(BlockPos pos) {
        if (!(level() instanceof ServerLevel server)) return;
        int kind = workKind;
        workKind = 0;
        io.hookdev.bleep.McVersion.swing(this);
        switch (kind) {
            case WORK_FISH -> { startFishing(); return; }
            case WORK_FURNACE -> { smelt(pos); return; }
            case WORK_GARDEN -> { garden(pos); return; }
            case WORK_ANIMAL -> { herd(workAnimal); workAnimal = null; return; }
            case WORK_TREE -> { chop(pos); return; }
            default -> {}
        }
        BlockState state = level().getBlockState(pos);
        if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(state)) {
            java.util.List<ItemStack> drops = new java.util.ArrayList<>(net.minecraft.world.level.block.Block.getDrops(state, server, pos, null));
            for (ItemStack drop : drops) if (drop.is(crop.asItem())) { drop.shrink(1); break; } // one seed goes back in the ground
            level().levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
            level().setBlock(pos, crop.getStateForAge(0), 3);
            for (ItemStack drop : drops) {
                ItemStack rest = drop.isEmpty() ? ItemStack.EMPTY : storage.addItem(drop);
                if (!rest.isEmpty()) spawnAtLocation(server, rest);
            }
            if (getRandom().nextFloat() < 0.3f) pinThought("thought.bleep.work.harvest");
            rememberFirst("farmer", "diary.bleep.farmer", "");
            memory.add("harvests");
        } else if (level().getBlockEntity(pos) instanceof net.minecraft.world.Container chest) {
            boolean moved = false;
            for (int i = 0; i < storage.getContainerSize(); i++) {
                ItemStack stack = storage.getItem(i);
                if (stack.isEmpty() || keeps(stack)) continue;
                int before = stack.getCount();
                for (int j = 0; j < chest.getContainerSize() && !stack.isEmpty(); j++) {
                    ItemStack there = chest.getItem(j);
                    if (there.isEmpty()) {
                        chest.setItem(j, stack.split(stack.getCount()));
                    } else if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
                        int move = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
                        there.grow(move);
                        stack.shrink(move);
                    }
                }
                if (stack.getCount() != before) moved = true;
                storage.setItem(i, stack);
            }
            if (moved) {
                chest.setChanged();
                pinThought("thought.bleep.work.tidy");
                rememberFirst("tidy", "diary.bleep.tidy", "");
            }
        }
    }

    /** Guard: monsters near its post at night: an alarm, and its owner hears about it (a Buddy goes for them too). */
    private void keepGuard() {
        var post = new net.minecraft.world.phys.AABB(stayAt).inflate(stayRadius() + 8);
        var monster = level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, post, m -> m instanceof net.minecraft.world.entity.monster.Enemy && m.isAlive()).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (monster == null) return;
        alarmCooldown = 600;
        feel(Emotion.ANGRY, 60);
        for (int i = 0; i < 3; i++) playSound(BleepContent.VOICE.get(Emotion.SURPRISED).get(), 1.5f, 1.5f + i * 0.1f);
        pinThought("thought.bleep.work.alarm");
        rememberFirst("guard", "diary.bleep.guard", "");
        if (getOwner() instanceof Player owner) owner.sendSystemMessage(Component.translatable("message.bleep.alarm", getDisplayName(), monster.getDisplayName()));
        if (can(Skill.FIGHT) && wantsToAttack(monster, getOwner()) && distanceToSqr(monster) < (stayRadius() + 4) * (stayRadius() + 4)) setTarget(monster);
    }

    // ---------- play: tricks, hide and seek ----------

    /** Does a trick, if it knows it (and is free to). */
    public boolean doTrick(Trick t) {
        if (slept || abandoned() || trickTicks > 0 || hideMode != 0 || Bond.level(bond()) < t.level || getTarget() != null) return false;
        trick = t;
        trickTicks = t.ticks;
        getNavigation().stop();
        say("thought.bleep.trick." + t.id());
        switch (t) {
            case WAVE -> showDisplay(Display.HI, t.ticks);
            case DANCE -> danceTicks = t.ticks;
            default -> {}
        }
        return true;
    }

    private void tickTrick() {
        if (trickTicks <= 0 || trick == null) return;
        getNavigation().stop();
        switch (trick) {
            case SPIN -> { setYRot(getYRot() + 18); setYBodyRot(getYRot()); setYHeadRot(getYRot()); }
            case JUMP -> { if (trickTicks % 12 == 0 && onGround()) getJumpControl().jump(); }
            default -> {}
        }
        if (--trickTicks == 0) {
            if (trick == Trick.PLAY_DEAD) say("thought.bleep.trick.alive");
            feel(Emotion.EXCITED, 30);
            played();
            trick = null;
        }
    }

    public @Nullable Trick trick() {
        return trickTicks > 0 ? trick : null;
    }

    public int hideMode() {
        return hideMode;
    }

    /** Hide and seek, it hides: off to a spot its owner cannot see. */
    public boolean startHiding() {
        if (!(getOwner() instanceof Player owner) || slept || abandoned() || hideMode != 0 || distanceToSqr(owner) > 24 * 24) return false;
        hideSpot = hidingSpot(owner);
        if (hideSpot == null) return false;
        hideMode = HIDE_GOING;
        hideStart = level().getGameTime();
        hideUntil = hideStart + 3600;
        stopOtherThings();
        say("thought.bleep.hide.start");
        return true;
    }

    /** Hide and seek, you hide: it counts to ten with its eyes shut, then comes looking. */
    public boolean startSeeking() {
        if (!(getOwner() instanceof Player owner) || slept || abandoned() || hideMode != 0 || distanceToSqr(owner) > 24 * 24) return false;
        hideMode = SEEK_COUNTING;
        hideStart = level().getGameTime();
        hideUntil = hideStart + COUNT_TICKS + 2400;
        hideSpot = blockPosition();
        stopOtherThings();
        say("thought.bleep.seek.count.0");
        return true;
    }

    private void stopOtherThings() {
        getNavigation().stop();
        talkingTo = null;
        playTicks = 0;
        partner = null;
        workAt = null;
    }

    /** A spot 8 to 18 blocks from its owner that they cannot see, and that it can drive to. */
    private @Nullable BlockPos hidingSpot(Player owner) {
        return hidingSpot(owner, at -> true);
    }

    private @Nullable BlockPos hidingSpot(Player owner, java.util.function.Predicate<BlockPos> fits) {
        BlockPos fallback = null;
        for (int i = 0; i < 40; i++) {
            double angle = getRandom().nextDouble() * Math.PI * 2, distance = 6 + getRandom().nextDouble() * 8; // it plans routes up to 16 blocks
            int x = (int) Math.floor(owner.getX() + Math.cos(angle) * distance), z = (int) Math.floor(owner.getZ() + Math.sin(angle) * distance);
            BlockPos spot = new BlockPos(x, level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (Math.abs(spot.getY() - owner.getY()) > 6 || !fits.test(spot)) continue;
            var path = getNavigation().createPath(spot, 1);
            if (path == null || !path.canReach()) continue;
            if (fallback == null) fallback = spot;
            var seen = level().clip(new net.minecraft.world.level.ClipContext(owner.getEyePosition(), net.minecraft.world.phys.Vec3.atCenterOf(spot.above()),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, owner));
            if (seen.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return spot; // something in between: hidden
        }
        return fallback;
    }

    private void endHideAndSeek(String thought, boolean fun) {
        hideMode = 0;
        hideSpot = null;
        say(thought);
        feel(Emotion.EXCITED, 40);
        rememberFirst("hide_and_seek", "diary.bleep.hide_and_seek", "");
        if (fun) played();
    }

    // ---------- show the way ----------

    /** Off it goes, ahead of its owner: to its homebase, or (none in this world) to their bed. */
    public boolean showTheWay() {
        if (!(getOwner() instanceof net.minecraft.server.level.ServerPlayer owner) || slept || abandoned() || wayTo != null) return false;
        BlockPos to = null;
        boolean bed = false;
        if (home() != null && home().dimension().equals(level().dimension())) {
            to = home().pos();
        } else if (owner.getRespawnConfig() != null && owner.getRespawnConfig().respawnData().dimension().equals(level().dimension())) {
            to = owner.getRespawnConfig().respawnData().pos();
            bed = true;
        }
        if (to == null) {
            say("thought.bleep.way.nowhere");
            return false;
        }
        if (owner.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(to)) < 3 * 3) {
            say("thought.bleep.way.here");
            return false;
        }
        stopOtherThings();
        hideMode = 0;
        if (isOrderedToSit()) follow();
        wayTo = to;
        wayToBed = bed;
        wayUntil = level().getGameTime() + 6000;
        say("thought.bleep.way.start");
        return true;
    }

    public @Nullable BlockPos wayTo() {
        return wayTo;
    }

    private void endWay(String thought) {
        wayTo = null;
        getNavigation().stop();
        say(thought);
    }

    // ---------- dreams ----------

    /** On its homebase at night (in the Overworld): eyes shut, and now and then a dream. */
    public boolean dozing() {
        return !level().isClientSide() && level().dimension() == Level.OVERWORLD && level().isDarkOutside() && onHome() && getTarget() == null && !abandoned() && assembly() == 0;
    }

    public String dream() {
        return entityData.get(DREAM);
    }

    /** By day: what it sees goes into today's bits to dream of. At night on its homebase: a dream every half minute to a minute. */
    private void tickDreams() {
        if (tickCount % 100 == 37 && !slept && level() instanceof ServerLevel) {
            var near = level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(10), m -> !(m instanceof BleepEntity) && hasLineOfSight(m))
                    .stream().min(Comparator.comparingDouble(this::distanceToSqr));
            near.ifPresent(m -> remember(dayMobs, m.getType().getDescriptionId()));
            remember(dayScenes, scene(level().getBiome(blockPosition())));
        }
        if (!dozing()) {
            if (display() == Display.DREAM) showDisplay(Display.FACE, 0);
            return;
        }
        if (--dreamIn > 0 || display() != Display.FACE) return;
        dreamIn = 600 + getRandom().nextInt(600);
        dreamNow();
    }

    private static void remember(java.util.ArrayDeque<String> bits, String bit) {
        bits.remove(bit);
        bits.addLast(bit);
        while (bits.size() > 8) bits.removeFirst();
    }

    /** The kind of place, as a little scene on its screen. */
    private static String scene(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        if (biome.is(net.minecraft.tags.BiomeTags.IS_NETHER)) return "fire";
        if (biome.is(net.minecraft.tags.BiomeTags.IS_END)) return "void";
        if (biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER) || biome.is(net.minecraft.tags.BiomeTags.IS_BEACH)) return "waves";
        if (biome.is(net.minecraft.tags.BiomeTags.IS_BADLANDS) || biome.is(net.neoforged.neoforge.common.Tags.Biomes.IS_DESERT)) return "sand";
        if (biome.is(net.neoforged.neoforge.common.Tags.Biomes.IS_SNOWY)) return "snow";
        if (biome.is(net.minecraft.tags.BiomeTags.IS_FOREST) || biome.is(net.minecraft.tags.BiomeTags.IS_TAIGA) || biome.is(net.minecraft.tags.BiomeTags.IS_JUNGLE)) return "trees";
        if (biome.is(net.neoforged.neoforge.common.Tags.Biomes.IS_CAVE)) return "cave";
        return "hills";
    }

    /** A dream: a strange one now and then (a sign more on its ??? page), a falling star if it ever saw one, or a bit of the day. The first one each night goes in its diary. */
    public void dreamNow() {
        String dream;
        float roll = getRandom().nextFloat();
        List<String> knownMobs = new java.util.ArrayList<>(memory.mobs.keySet());
        if (roll < 0.1f) dream = "strange";
        else if (roll < 0.25f && memory.firsts.contains("star")) dream = "star";
        else if (roll < 0.6f && !dayScenes.isEmpty()) dream = "scene:" + dayScenes.toArray(String[]::new)[getRandom().nextInt(dayScenes.size())];
        else if (!dayMobs.isEmpty()) dream = "mob:" + dayMobs.toArray(String[]::new)[getRandom().nextInt(dayMobs.size())];
        else if (!knownMobs.isEmpty()) dream = "mob:" + BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(knownMobs.get(getRandom().nextInt(knownMobs.size())))).getDescriptionId();
        else dream = "scene:hills";
        entityData.set(DREAM, dream);
        showDisplay(Display.DREAM, 120);
        long night = level().getGameTime() / 24000;
        if (dreamtOn == night) return;
        dreamtOn = night;
        if (dream.equals("strange")) {
            remember("diary.bleep.dream.strange", "");
            addSigns(1);
        } else if (dream.equals("star")) {
            remember("diary.bleep.dream.star", "");
        } else if (dream.startsWith("mob:")) {
            remember("diary.bleep.dream.mob", dream.substring(4));
        } else {
            remember("diary.bleep.dream." + dream.substring(6), "");
        }
        rememberFirst("dream", "diary.bleep.dream.first", "");
    }

    // ---------- treasure hunt ----------

    public @Nullable BlockPos treasure() {
        return entityData.get(TREASURE).orElse(null);
    }

    /** Busy with the hunt (burying, digging it up again, counting or looking): when it is buried, it just follows and gives hints. */
    public boolean hunting() {
        return treasureMode != 0 && treasureMode != BURIED;
    }

    public int treasureMode() {
        return treasureMode;
    }

    /** It buries something: one thing from its storage, in soft ground somewhere its owner cannot see. */
    public boolean startBurying() {
        if (!(getOwner() instanceof Player owner) || slept || abandoned() || treasureMode != 0 || hideMode != 0 || distanceToSqr(owner) > 24 * 24) return false;
        List<Integer> slots = new java.util.ArrayList<>();
        for (int i = 0; i < storage.getContainerSize(); i++) if (!storage.getItem(i).isEmpty()) slots.add(i);
        if (slots.isEmpty()) {
            say("thought.bleep.treasure.nothing");
            return false;
        }
        BlockPos spot = hidingSpot(owner, at -> softGround(level().getBlockState(at.below())));
        if (spot == null || !softGround(level().getBlockState(spot.below()))) spot = softSpotNearby(); // no hidden spot: close by will do
        if (spot == null) {
            say("thought.bleep.treasure.no_spot");
            return false;
        }
        treasureItem = storage.getItem(slots.get(getRandom().nextInt(slots.size()))).split(1);
        entityData.set(TREASURE, Optional.of(spot.below()));
        treasureMode = BURYING;
        treasureTicks = 0;
        treasureStart = level().getGameTime();
        treasureUntil = treasureStart + 600; // to get there
        stopOtherThings();
        say("thought.bleep.treasure.start");
        return true;
    }

    /** Soft ground within five blocks of it that it can get to (the spot above it). */
    private @Nullable BlockPos softSpotNearby() {
        List<BlockPos> spots = new java.util.ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(blockPosition().offset(-5, -2, -5), blockPosition().offset(5, 1, 5))) {
            if (at.distSqr(blockPosition().below()) < 2 * 2 || !softGround(level().getBlockState(at)) || !level().getBlockState(at.above()).isAir()) continue;
            var path = getNavigation().createPath(at.above(), 1);
            if (path != null && path.canReach()) spots.add(at.above().immutable());
        }
        return spots.isEmpty() ? null : spots.get(getRandom().nextInt(spots.size()));
    }

    /** Ground you can dig with a shovel: dirt, grass, sand, gravel, snow. */
    private static boolean softGround(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL) && !state.hasBlockEntity();
    }

    /** You hide something: it shuts its eyes for twenty seconds. Toss it somewhere, or put it in a chest. */
    public boolean startFinding() {
        if (!(getOwner() instanceof Player owner) || slept || abandoned() || treasureMode != 0 || hideMode != 0 || distanceToSqr(owner) > 24 * 24) return false;
        treasureMode = FIND_COUNTING;
        treasureStart = level().getGameTime();
        hidden = null;
        stopOtherThings();
        say("thought.bleep.treasure.count");
        return true;
    }

    /** Its owner tossed something or opened a container while it counted: that is the treasure (the last one counts). */
    public void sawHidden(Object what) {
        if (treasureMode == FIND_COUNTING) hidden = what;
    }

    /** Its owner dug where it buried something: found! The thing pops out of the ground. */
    public boolean dugUp(Player player, BlockPos pos) {
        BlockPos at = treasure();
        if (treasureMode != BURIED || at == null || !player.equals(getOwner()) || !(pos.equals(at) || pos.equals(at.below()))) return false;
        net.minecraft.world.level.block.Block.popResource(level(), pos, treasureItem);
        treasureItem = ItemStack.EMPTY;
        endTreasure("thought.bleep.treasure.found", true);
        showDisplay(Display.SPARKLE, 50);
        return true;
    }

    private void endTreasure(String thought, boolean fun) {
        treasureMode = 0;
        entityData.set(TREASURE, Optional.empty());
        hidden = null;
        searchSpot = null;
        if (!treasureItem.isEmpty()) { // whatever is left goes back in its storage
            ItemStack rest = storage.addItem(treasureItem);
            if (!rest.isEmpty()) spawnAtLocation((ServerLevel) level(), rest);
            treasureItem = ItemStack.EMPTY;
        }
        say(thought);
        feel(Emotion.EXCITED, 40);
        rememberFirst("treasure", "diary.bleep.treasure", "");
        if (fun) played();
    }

    /** Buried: hints when its owner gets warmer or colder; after five minutes it digs the thing up itself. A chest lid it opened shuts again. */
    private void tickTreasure() {
        long now = level().getGameTime();
        if (lidAt != null && now >= lidCloses) {
            var state = level().getBlockState(lidAt);
            if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) level().blockEvent(lidAt, state.getBlock(), 1, 0);
            lidAt = null;
        }
        if (treasureMode != BURIED) return;
        if (treasureUntil == Long.MIN_VALUE) treasureUntil = now + HUNT_TICKS; // loaded from a save
        if (now >= treasureUntil) {
            treasureMode = UNBURYING;
            treasureTicks = 0;
            say("thought.bleep.treasure.give_up");
            return;
        }
        BlockPos at = treasure();
        if (at == null || !(getOwner() instanceof Player owner) || tickCount % 20 != 0) return;
        int warmth = io.hookdev.bleep.client.Warmth.of((float) Math.sqrt(owner.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at))));
        if (lastWarmth != 0 && warmth != lastWarmth) say(warmth == 5 ? "thought.bleep.treasure.hot" : warmth > lastWarmth ? "thought.bleep.treasure.warmer" : "thought.bleep.treasure.colder");
        lastWarmth = warmth;
    }

    /** A spot a few blocks off what it is looking for: it sniffs about there first. */
    private @Nullable BlockPos decoy(BlockPos near) {
        for (int i = 0; i < 12; i++) {
            double angle = getRandom().nextDouble() * Math.PI * 2, distance = 4 + getRandom().nextDouble() * 5;
            BlockPos spot = BlockPos.containing(near.getX() + Math.cos(angle) * distance, near.getY(), near.getZ() + Math.sin(angle) * distance);
            var path = getNavigation().createPath(spot, 1);
            if (path != null && path.canReach()) return spot;
        }
        return null;
    }

    // ---------- adventures ----------

    /** Emeralds from its owner: off to the nearest villager (24 blocks) to buy something. */
    private boolean goShopping(Player player, ItemStack emeralds) {
        if (shopAt != null || !getMainHandItem().isEmpty() || ownHand || slept) return false;
        var villager = level().getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, getBoundingBox().inflate(24), v -> v.isAlive() && !v.isBaby())
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        if (villager == null) {
            player.sendOverlayMessage(Component.translatable("message.bleep.no_villager", getDisplayName()));
            return false;
        }
        shopAt = villager;
        shopTicks = 0;
        purse = emeralds.copyAndClear(); // all of them: what it does not spend it brings back
        say("thought.bleep.shop.off");
        return true;
    }

    /** At the villager: a suspicious look, and then a trade (something it can pay for with emeralds alone). */
    private void trade() {
        var villager = shopAt;
        shopAt = null;
        if (villager == null) return;
        villager.setUnhappyCounter(40);
        villager.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1, 1);
        List<net.minecraft.world.item.trading.MerchantOffer> affordable = villager.getOffers().stream().filter(o -> !o.isOutOfStock() && o.getItemCostB().isEmpty()
                && o.getCostA().is(Items.EMERALD) && o.getCostA().getCount() <= purse.getCount()).toList();
        if (affordable.isEmpty()) {
            say("thought.bleep.shop.nothing");
        } else {
            var offer = affordable.get(getRandom().nextInt(affordable.size()));
            purse.shrink(offer.getCostA().getCount());
            setItemSlot(EquipmentSlot.MAINHAND, offer.getResult().copy()); // FetchGoal brings it over
            villager.notifyTrade(offer);
            say("thought.bleep.shop.bought");
            rememberFirst("shop", "diary.bleep.shop", "");
        }
        if (getMainHandItem().isEmpty() && !purse.isEmpty()) { // nothing bought: the emeralds go back
            setItemSlot(EquipmentSlot.MAINHAND, purse);
            purse = ItemStack.EMPTY;
        }
    }

    public boolean shopping() {
        return shopAt != null;
    }

    /** Caves: lava or a deep drop right by its owner, and (with a pickaxe in their hand) warm or cold beeps for ore. A minecart ride behind its owner's. */
    private void tickAdventure() {
        if (caveCooldown > 0) caveCooldown--;
        if (oreCooldown > 0) oreCooldown--;
        tickMinecart();
        if (slept || abandoned() || tickCount % 10 != 3 || !(getOwner() instanceof Player owner) || distanceToSqr(owner) > 24 * 24) return;
        BlockPos at = owner.blockPosition();
        boolean underground = !level().canSeeSky(at) && at.getY() < level().getSeaLevel();
        if (!underground) return;
        if (caveCooldown == 0) {
            boolean lava = false, drop = false;
            for (BlockPos pos : BlockPos.betweenClosed(at.offset(-3, -2, -3), at.offset(3, 1, 3))) {
                if (level().getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA)) { lava = true; break; }
            }
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos edge = at.relative(side);
                int depth = 0;
                while (depth < 6 && level().getBlockState(edge.below(depth)).isAir()) depth++;
                if (depth >= 6) drop = true;
            }
            if (lava || drop) {
                caveCooldown = 300;
                showDisplay(Display.ALERT, 40);
                say(lava ? "thought.bleep.cave.lava" : "thought.bleep.cave.drop");
                rememberFirst("cave", "diary.bleep.cave", "");
                return;
            }
        }
        if (oreCooldown == 0 && owner.getMainHandItem().is(net.minecraft.tags.ItemTags.PICKAXES)) {
            BlockPos nearest = null;
            double best = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(at.offset(-8, -8, -8), at.offset(8, 8, 8))) {
                if (!level().getBlockState(pos).is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) continue;
                double d = pos.distSqr(at);
                if (d < best) { best = d; nearest = pos.immutable(); }
            }
            if (nearest == null) return;
            double distance = Math.sqrt(best);
            oreCooldown = distance < 3 ? 25 : distance < 6 ? 45 : 70; // warmer: faster
            playSound(BleepContent.VOICE.get(Emotion.HAPPY).get(), 0.5f, (float) (0.8 + (8 - Math.min(8, distance)) / 8 * 1.2)); // and higher
            showDisplay(Display.ORE, 20);
            if (!memory.firsts.contains("ore_sense")) {
                rememberFirst("ore_sense", "diary.bleep.ore", "");
                pinThought("thought.bleep.cave.ore");
            }
        }
    }

    /** A minecart fan: its owner in a cart, so it hops in an empty one close by, and that cart follows theirs like a little train. */
    private void tickMinecart() {
        if (!(getOwner() instanceof Player owner) || slept) return;
        var ownerCart = owner.getVehicle() instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart c ? c : null;
        if (getVehicle() instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart mine) {
            if (ownerCart == null) {
                if (++cartWait > 40) { // its owner got out: so does it
                    stopRiding();
                    cartWait = 0;
                }
                return;
            }
            cartWait = 0;
            double gap = mine.distanceTo(ownerCart);
            var speed = ownerCart.getDeltaMovement();
            if (gap > 1.6 && gap < 10) mine.setDeltaMovement(speed.add(ownerCart.position().subtract(mine.position()).normalize().scale(0.05))); // keep up
            else if (gap <= 1.6) mine.setDeltaMovement(mine.getDeltaMovement().scale(0.5)); // do not bump
            if (moodTicks == 0) feel(Emotion.EXCITED, 40);
            if (tickCount % 200 == 0) pinThought("thought.bleep.minecart." + getRandom().nextInt(3));
            return;
        }
        if (ownerCart == null || tickCount % 10 != 7 || !free() || busyWithOthers() || distanceToSqr(owner) > 16 * 16 || isOrderedToSit()) return;
        level().getEntitiesOfClass(net.minecraft.world.entity.vehicle.minecart.AbstractMinecart.class, getBoundingBox().inflate(6), c -> c != ownerCart && c.getPassengers().isEmpty())
                .stream().min(Comparator.comparingDouble(this::distanceToSqr)).ifPresent(cart -> {
                    if (startRiding(cart)) {
                        say("thought.bleep.minecart.in");
                        rememberFirst("minecart", "diary.bleep.minecart", "");
                    }
                });
    }

    // ---------- care ----------

    /** A little picture on its screen for what its owner just did (not while it shows something else, and not too often). */
    public boolean react(Display reaction) {
        if (slept || abandoned() || assembly() > 0 || reactCooldown > 0 || display() != Display.FACE) return false;
        reactCooldown = 100;
        showDisplay(reaction, 50);
        Emotion voice = reaction == Display.TEAR ? Emotion.SAD : reaction == Display.CURIOUS || reaction == Display.SWEAT ? Emotion.SURPRISED : Emotion.HAPPY;
        playSound(BleepContent.VOICE.get(voice).get(), VOLUME * 0.7f, getVoicePitch());
        return true;
    }

    /** Its owner died: it goes to the spot and keeps their things safe until they come back. */
    public void guardGrave(BlockPos where) {
        if (abandoned() || assembly() > 0) return;
        guardAt = where.immutable();
        guardTicks = GUARD_TIME;
        slept = false;
        stopOtherThings();
        say("thought.bleep.grave.go");
    }

    public @Nullable BlockPos guarding() {
        return guardAt;
    }

    public List<ItemStack> kept() {
        return kept;
    }

    /**
     * Care for its owner: at a grave it gathers their things (and gives them back when they come); low on health or
     * food, it brings something to eat from its storage; a creeper about to go off close to them, and it sounds the alarm.
     */
    private void tickCare() {
        if (careCooldown > 0) careCooldown--;
        if (reactCooldown > 0) reactCooldown--;
        if (warnCooldown > 0) warnCooldown--;
        if (abandoned() || !(level() instanceof ServerLevel server)) return;
        Player owner = getOwner() instanceof Player p && p.isAlive() ? p : null;
        if (guardAt != null) {
            if (tickCount % 5 == 0) { // its owner's things, all of them: into a safe place
                for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(guardAt).inflate(GUARD_RANGE), ItemEntity::isAlive)) {
                    if (distanceToSqr(item) < 2.0 * 2.0) {
                        kept.add(item.getItem().copy());
                        item.discard();
                    } else {
                        var pull = position().add(0, 0.4, 0).subtract(item.position()).normalize().scale(0.35);
                        item.setDeltaMovement(item.getDeltaMovement().scale(0.5).add(pull));
                        item.needsSync = true;
                    }
                }
            }
            if (owner != null && distanceToSqr(owner) < 4 * 4 && tickCount % 10 == 0) giveBack(owner);
            else if (--guardTicks <= 0) guardAt = null; // a day, and it gives up waiting there (it still has their things)
        } else if (!kept.isEmpty() && owner != null && distanceToSqr(owner) < 4 * 4) {
            giveBack(owner);
        }
        if (owner == null || slept || distanceToSqr(owner) > 24 * 24) return;
        if (tickCount % 5 == 0 && warnCooldown == 0) { // look out!
            boolean danger = !level().getEntitiesOfClass(net.minecraft.world.entity.monster.Creeper.class, owner.getBoundingBox().inflate(7),
                    c -> c.isAlive() && (c.getSwellDir() > 0 || c.isIgnited() || c.distanceToSqr(owner) < 3 * 3)).isEmpty();
            if (danger) {
                warnCooldown = 200;
                showDisplay(Display.ALERT, 50);
                feel(Emotion.SURPRISED, 40);
                for (int i = 0; i < 2; i++) playSound(BleepContent.VOICE.get(Emotion.SURPRISED).get(), 1.6f, 1.8f + i * 0.2f);
                say("thought.bleep.care.creeper");
                rememberFirst("creeper_warning", "diary.bleep.creeper_warning", "");
            }
        }
        if (tickCount % 40 == 20 && careCooldown == 0 && guardAt == null && hideMode == 0) { // hurt or hungry?
            boolean hurt = owner.getHealth() <= 8, hungry = owner.getFoodData().getFoodLevel() <= 6;
            if (!hurt && !hungry) return;
            careCooldown = 1200;
            showDisplay(Display.CARE, 100);
            int food = -1;
            for (int i = 0; i < storage.getContainerSize() && food < 0; i++) if (storage.getItem(i).has(net.minecraft.core.component.DataComponents.FOOD)) food = i;
            if (food >= 0 && getMainHandItem().isEmpty() && !ownHand) { // it brings something to eat (FetchGoal hands it over)
                setItemSlot(EquipmentSlot.MAINHAND, storage.removeItem(food, 1));
                say("thought.bleep.care.food");
                rememberFirst("nurse", "diary.bleep.nurse", "");
            } else {
                say(hurt ? "thought.bleep.care.hurt" : "thought.bleep.care.hungry");
            }
        }
    }

    /** Its owner is back: everything it kept goes into their hands. */
    private void giveBack(Player owner) {
        if (!kept.isEmpty()) {
            for (ItemStack stack : kept) if (!owner.getInventory().add(stack) && level() instanceof ServerLevel server) spawnAtLocation(server, stack);
            kept.clear();
            say("thought.bleep.grave.back");
            feel(Emotion.EXCITED, 60);
            rememberFirst("grave", "diary.bleep.grave", "");
        }
        guardAt = null;
    }

    // ---------- hobbies, milestones ----------

    /** What can grow into a hobby (each a count in its memory). */
    public static final List<String> HOBBIES = List.of("stargazing", "digging", "collecting", "dancing", "chatting", "playing");
    /** How much it must have done something before it is its hobby, and by how much a new one must lead to take over. */
    public static final int HOBBY_AT = 15, HOBBY_LEAD = 10;

    public boolean hobby(String what) {
        return memory.hobby.equals(what);
    }

    /** It did something it might come to love: the one it does most (enough, and clearly) becomes its hobby. */
    public void hobbyPoint(String what) {
        memory.add(what);
        String top = HOBBIES.stream().max(Comparator.comparingInt(memory::count)).orElse("");
        int lead = memory.count(top) - (memory.hobby.isEmpty() ? 0 : memory.count(memory.hobby));
        if (top.equals(memory.hobby) || memory.count(top) < HOBBY_AT || (!memory.hobby.isEmpty() && lead < HOBBY_LEAD)) return;
        memory.hobby = top;
        pinThought("thought.bleep.hobby." + top);
        remember("diary.bleep.hobby", "hobby.bleep." + top);
    }

    /** Milestones it has just reached: a badge in its Bleepedia (and a ! for news). */
    private void checkMilestones() {
        for (Milestone m : Milestone.values()) {
            if (memory.milestones.containsKey(m.id()) || !m.earnedBy(this)) continue;
            memory.milestones.put(m.id(), level().getGameTime());
            this.entityData.set(NEWS, true);
            pinThought("thought.bleep.milestone");
            remember("diary.bleep.milestone", "milestone.bleep." + m.id());
        }
    }

    // ---------- the stars ----------

    /** A new strange sign on the ??? page of its Bleepedia (or a few). */
    public void addSigns(int n) {
        int before = memory.signs;
        memory.signs = Math.min(Memory.SIGNS, memory.signs + n);
        if (memory.signs > before) this.entityData.set(NEWS, true);
    }

    /** It saw a star fall close by: it stares at the spot, and wonders. */
    public void sawStarFall(BlockPos where) {
        if (slept || abandoned()) return;
        getLookControl().setLookAt(net.minecraft.world.phys.Vec3.atCenterOf(where));
        feel(Emotion.SURPRISED, 60);
        say("thought.bleep.star.fell");
        rememberFirst("star", "diary.bleep.star", "");
        addSigns(3);
    }

    /** On a clear night it sometimes looks up at the stars, still and quiet; at full moon, at midnight, all bleeps sing together. */
    private void tickStars() {
        if (gazeTicks > 0) {
            gazeTicks--;
            getNavigation().stop();
            getLookControl().setLookAt(getX() + getLookAngle().x, getEyeY() + 20, getZ() + getLookAngle().z);
        }
        if (slept || abandoned() || level().dimension() != Level.OVERWORLD || !level().isDarkOutside()) return;
        long clock = level().getOverworldClockTime(), night = clock / 24000, time = clock % 24000;
        // full moon (phase 0), midnight: the same four notes in every bleep at the same moment
        if (night % 8 == 0 && time >= 18000 && time < 18060) {
            int beat = (int) (time - 18000);
            float[] notes = {1.0f, 1.26f, 1.5f, 2.0f};
            if (beat % 12 == 0 && beat / 12 < notes.length) playSound(BleepContent.VOICE.get(Emotion.HAPPY).get(), 0.8f, notes[beat / 12]);
            if (beat == 0) {
                showDisplay(Display.STARS, 80);
                if (chorusNight != night) {
                    chorusNight = night;
                    pinThought("thought.bleep.chorus");
                    rememberFirst("chorus", "diary.bleep.chorus", "");
                    addSigns(2);
                }
            }
        }
        if (gazeTicks == 0 && tickCount % 600 == 300 && !level().isRaining() && level().canSeeSky(blockPosition().above()) && free() && !busyWithOthers()
                && quietTicks > 200 && getRandom().nextFloat() < (hobby("stargazing") ? 0.6f : 0.25f)) { // looking up
            gazeTicks = 120;
            hobbyPoint("stargazing");
            pinThought("thought.bleep.stars." + getRandom().nextInt(5));
            rememberFirst("stars", "diary.bleep.stars", "");
            if (gazeNight != night) {
                gazeNight = night;
                addSigns(1);
            }
        }
    }

    public boolean stargazing() {
        return gazeTicks > 0;
    }

    // ---------- welcome home, birthdays ----------

    /** Its owner back after a long time away (or logged in again): it races over, over the moon. */
    private void tickWelcome() {
        if (welcomeTicks > 0) welcomeTicks--;
        if (tickCount % 10 != 0 || slept || abandoned() || assembly() > 0) return;
        if (!(getOwner() instanceof Player owner) || distanceToSqr(owner) > 32 * 32) return;
        long now = level().getGameTime(), real = System.currentTimeMillis();
        if (lastWithOwner != Long.MIN_VALUE && (now - lastWithOwner > WELCOME_AFTER || real - lastWithOwnerReal > WELCOME_AFTER_REAL)) welcome();
        lastWithOwner = now;
        lastWithOwnerReal = real;
    }

    private void welcome() {
        welcomeTicks = 100;
        feel(Emotion.EXCITED, 100);
        showDisplay(Display.HI, 80);
        say("thought.bleep.welcome." + getRandom().nextInt(3));
        rememberFirst("welcome", "diary.bleep.welcome", "");
    }

    /** As if its owner was away this long (tests). */
    public void missedOwner(long ticks) {
        lastWithOwner = level().getGameTime() - ticks;
        lastWithOwnerReal = System.currentTimeMillis();
    }

    public boolean welcoming() {
        return welcomeTicks > 0;
    }

    /** Every BIRTHDAY_EVERY days of its life: a party hat, confetti, HAPPY BIRTHDAY across its screen. A cake is very welcome. */
    private void tickBirthday() {
        if (partyTicks > 0) {
            if (level() instanceof ServerLevel server && tickCount % 8 == 0) {
                int[] colours = {0xFF4F8B, 0x2DE2E6, 0xFFB547, 0xEEF1F4};
                server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(colours[getRandom().nextInt(colours.length)], 1.2f),
                        getX(), getY() + 1.5, getZ(), 6, 0.5, 0.3, 0.5, 0.02);
            }
            if (--partyTicks == 0) this.entityData.set(PARTY, false);
        }
        if (tickCount % 100 != 50 || memory.born < 0 || slept || abandoned()) return;
        long day = memory.day(level().getGameTime());
        if (birthdayOn(day) && lastBirthday != day) birthday(day);
    }

    /** Whether this day of its life is a birthday: day 31, 61, 91 ... */
    public static boolean birthdayOn(long day) {
        return day > 1 && (day - 1) % BIRTHDAY_EVERY == 0;
    }

    /** Its birthday party (public for the tests). */
    public void birthday(long day) {
        lastBirthday = day;
        partyTicks = 2400;
        this.entityData.set(PARTY, true);
        showDisplay(Display.BIRTHDAY, 240);
        feel(Emotion.EXCITED, 100);
        say("thought.bleep.birthday");
        remember("diary.bleep.birthday", String.valueOf(day - 1));
        addSigns(1);
        if (getOwner() instanceof Player owner) owner.sendSystemMessage(Component.translatable("message.bleep.birthday", getDisplayName(), day - 1));
    }

    /** Its birthday party is on. */
    public boolean party() {
        return this.entityData.get(PARTY);
    }

    /** A home with comforts charges a quarter faster for each one. */
    private float homeCharge() {
        return HOME_CHARGE * (1 + 0.25f * Integer.bitCount(comforts));
    }

    public int comforts() {
        return comforts;
    }

    /** Looks around its homebase: a bed, a toy (a ball, lying there or in an item frame), its own name on a sign. */
    public void checkComforts() {
        GlobalPos home = home();
        if (home == null || !home.dimension().equals(level().dimension())) return;
        BlockPos at = home.pos();
        int found = 0;
        String name = getName().getString().toLowerCase(java.util.Locale.ROOT);
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-4, -1, -4), at.offset(4, 2, 4))) {
            BlockState state = level().getBlockState(pos);
            if (state.is(net.minecraft.tags.BlockTags.BEDS)) found |= COMFORT_BED;
            if (state.is(net.minecraft.tags.BlockTags.ALL_SIGNS) && level().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign
                    && io.hookdev.bleep.McVersion.signText(sign).toLowerCase(java.util.Locale.ROOT).contains(name)) found |= COMFORT_SIGN;
        }
        var around = new net.minecraft.world.phys.AABB(at).inflate(4);
        if (!level().getEntitiesOfClass(ItemEntity.class, around, i -> isBall(i.getItem())).isEmpty()
                || !level().getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, around, f -> isBall(f.getItem())).isEmpty()) found |= COMFORT_TOY;
        int fresh = found & ~comforts;
        for (int bit : new int[] {COMFORT_BED, COMFORT_TOY, COMFORT_SIGN}) {
            if ((fresh & bit) == 0) continue;
            String which = bit == COMFORT_BED ? "bed" : bit == COMFORT_TOY ? "toy" : "sign";
            feel(Emotion.EXCITED, 40);
            pinThought("thought.bleep.home." + which);
            rememberFirst("home_" + which, "diary.bleep.home_" + which, "");
        }
        comforts = found;
        long day = today();
        if (Integer.bitCount(found) >= 2 && comfortDay != day) {
            comfortDay = day;
            changeBond(Bond.COMFORT);
        }
    }

    private Emotion currentEmotion() {
        if (slept || bedside || dozing() || hideMode == SEEK_COUNTING || treasureMode == FIND_COUNTING || trick() == Trick.PLAY_DEAD) return Emotion.ASLEEP; // eyes shut: counting, or playing dead
        if (moodTicks > 0) return mood;
        if (fightingWith != null) return Emotion.ANGRY;
        if (danceTicks > 0 || playing() || greetTicks > 0 || welcomeTicks > 0) return Emotion.EXCITED;
        if (overchargeTicks > 0) return (overchargeTicks / 10) % 2 == 0 ? Emotion.GLITCH : Emotion.EXCITED;
        if (getTarget() != null) return Emotion.ANGRY;
        if (battery() < TIRED) return Emotion.TIRED;
        if (idleTicks > (home() == null ? BORED_AFTER / 2 : BORED_AFTER)) return Emotion.BORED;
        return Emotion.HAPPY;
    }

    private Activity currentActivity(boolean home) {
        if (moodTicks > 0 && mood == Emotion.BOOT) return Activity.STARTING;
        if (home) return display() == Display.DREAM ? Activity.DREAMING : battery() < FULL ? Activity.CHARGING : slept || dozing() ? Activity.SLEEPING : Activity.AT_HOME;
        if (slept) return Activity.SLEEPING;
        if (bedside) return Activity.BEDSIDE;
        if (guardAt != null) return Activity.GUARDING;
        if (fishTicks > 0) return Activity.FISHING;
        if (shopAt != null) return Activity.SHOPPING;
        if (getVehicle() instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart) return Activity.RIDING;
        if (welcomeTicks > 0) return Activity.WELCOMING;
        if (hideMode != 0) return Activity.HIDE_AND_SEEK;
        if (treasureMode != 0) return Activity.TREASURE_HUNT;
        if (wayTo != null) return Activity.GUIDING;
        if (gazeTicks > 0) return Activity.STARGAZING;
        if (trickTicks > 0) return Activity.TRICK;
        if (workAt != null) return Activity.WORKING;
        if (fightingWith != null) return Activity.SQUABBLING;
        if (talkingTo != null) return Activity.CHATTING;
        if (playing()) return Activity.PLAYING;
        if (dawdleTicks > 0) return Activity.DAWDLING;
        if (digTicks > 0) return Activity.DIGGING;
        if (danceTicks > 0) return Activity.DANCING;
        if (getTarget() != null) return Activity.FIGHTING;
        if (overchargeTicks > 0) return Activity.OVERCHARGED;
        if (naughty.busy()) return Activity.NAUGHTY;
        if (goingHome) return Activity.GOING_HOME;
        if (llamaTicks > 0) return Activity.HIDING;
        if (!getMainHandItem().isEmpty() || fetching != null) return Activity.FETCHING;
        if (scanning != null) return Activity.LOOKING;
        return isOrderedToSit() ? Activity.STAYING : Activity.FOLLOWING;
    }

    /** Keeps this thought on its screen for a while, whatever else happens. */
    public void pinThought(String key) {
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
        String kind = activity == Activity.BEDSIDE || activity == Activity.DREAMING ? "dream" : activity == Activity.DANCING ? "dance" : activity == Activity.PLAYING ? "play" : emotion == Emotion.ASLEEP ? "asleep" : activity == Activity.CHARGING ? "charging"
                : activity == Activity.HIDING ? "llama" : activity == Activity.FIGHTING ? "fighting" : activity == Activity.OVERCHARGED ? "overcharged"
                : emotion == Emotion.BOOT ? "excited" : emotion.texture();
        // in a thunderstorm it hopes for lightning
        if ((kind.equals("happy") || kind.equals("bored")) && level().isThundering()) kind = "storm";
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
        if (overchargeTicks > 0) return super.getSpeed() * 1.5f;
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
        drain(damage * DAMAGE_COST * (has(Chip.SHIELD) ? 0.5f : 1));
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.LlamaSpit) { // spit in its circuits
            feel(Emotion.GLITCH, 50);
            pinThought("thought.bleep.spit");
            rememberFirst("spit", "diary.bleep.spit", "");
            return true;
        }
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
        if (abandoned()) {
            if (level().isClientSide()) return InteractionResult.SUCCESS;
            if (stack.is(Items.REDSTONE_BLOCK) && level() instanceof ServerLevel server && io.hookdev.bleep.BleepOwners.get(server).full(player.getUUID())) {
                player.sendOverlayMessage(Component.translatable("message.bleep.too_many", io.hookdev.bleep.BleepOwners.MAX));
            } else if (stack.is(Items.REDSTONE_BLOCK)) {
                stack.consume(1, player);
                repair(player);
            } else {
                player.sendOverlayMessage(Component.translatable("message.bleep.abandoned", getDisplayName()));
            }
            return InteractionResult.SUCCESS;
        }
        if (!isOwnedBy(player)) return super.mobInteract(player, hand);
        if (!stack.isEmpty()) {
            if (level().isClientSide()) return super.mobInteract(player, hand);
            if (asleep() && !charges(stack)) return super.mobInteract(player, hand);
            if (stack.is(Items.SHEARS)) return takeOff(player) ? InteractionResult.SUCCESS : super.mobInteract(player, hand);
            if (stack.is(BleepContent.POWER_BANK.get())) { // the bank is kept: it shows what is left
                usePowerBank(player, stack);
                return InteractionResult.SUCCESS;
            }
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

    /** Tamed (built, or an abandoned one woken up): on its owner's list of bleeps. */
    @Override
    public void tame(Player player) {
        super.tame(player);
        if (level() instanceof ServerLevel server) io.hookdev.bleep.BleepOwners.get(server).add(player.getUUID(), getUUID());
    }

    /** Name tags do nothing to a bleep: you rename it on its info screen. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, net.minecraft.world.phys.Vec3 location) {
        if (player.getItemInHand(hand).is(Items.NAME_TAG)) {
            if (!level().isClientSide()) player.sendOverlayMessage(Component.translatable("message.bleep.no_name_tag", getDisplayName()));
            return InteractionResult.SUCCESS;
        }
        return super.interact(player, hand, location);
    }

    /** Its name always shows above its head, like a player's. */
    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        setCustomNameVisible(name != null);
        // its new name scrolls across its display, like a ticker
        if (name != null && !level().isClientSide()) showDisplay(Display.NAME, 60 + name.getString().length() * 12);
    }

    /** A robot does not need air. */
    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource source) {
        return BleepContent.HURT.get();
    }

    /** Now and then it chatters, in the voice of how it feels; not while it is switched off or being built. */
    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        if (asleep() || assembly() > 0 || abandoned()) return null;
        var sound = BleepContent.VOICE.get(emotion());
        return sound == null ? null : sound.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return CHATTER_EVERY;
    }

    @Override
    protected float getSoundVolume() {
        return VOLUME;
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
        output.putInt("assembly", assembly());
        output.putInt("learned", learned);
        output.putInt("overcharge", overchargeTicks);
        output.putInt("color", color());
        output.putInt("hat", this.entityData.get(HAT));
        output.putInt("sticker", this.entityData.get(STICKER));
        output.putInt("wear", this.entityData.get(WEAR));
        output.putLong("bond_day", bondDay);
        output.putInt("pets_today", petsToday);
        output.putInt("gifts_today", giftsToday);
        output.putInt("fights_today", fightsToday);
        output.putLong("favourite_day", favouriteDay);
        output.putLong("last_together_day", lastTogetherDay);
        memory.save(output);
        output.putBoolean("news", news());
        output.putBoolean("abandoned", abandoned());
        storage.storeAsItemList(output.list("storage", ItemStack.CODEC));
        output.putBoolean("magnet", magnet());
        output.putBoolean("own_hand", ownHand);
        output.putBoolean("armed", armed);
        output.putInt("show", showTicks);
        output.putInt("plays_today", playsToday);
        output.putLong("last_with_owner", lastWithOwner);
        output.putLong("last_with_owner_real", lastWithOwnerReal);
        output.putLong("last_birthday", lastBirthday);
        output.putInt("party", partyTicks);
        output.putInt("jobs", jobs());
        output.putInt("stay_radius", stayRadius());
        output.putInt("chips", chips());
        output.putLong("gaze_night", gazeNight);
        output.putLong("chorus_night", chorusNight);
        if (guardAt != null) output.store("guard_at", BlockPos.CODEC, guardAt);
        output.putInt("guard_ticks", guardTicks);
        if (!kept.isEmpty()) output.store("kept", ItemStack.CODEC.listOf(), kept);
        if (!purse.isEmpty()) output.store("purse", ItemStack.CODEC, purse);
        if (!treasureItem.isEmpty() && treasure() != null && treasureMode >= BURIED && treasureMode <= UNBURYING) { // buried: the hunt goes on
            output.store("treasure", ItemStack.CODEC, treasureItem);
            output.store("treasure_at", BlockPos.CODEC, treasure());
        }
        if (stayAt != null) output.store("stay_at", BlockPos.CODEC, stayAt);
        output.putInt("comforts", comforts);
        output.putLong("comfort_day", comfortDay);
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
        this.entityData.set(ASSEMBLY, input.getIntOr("assembly", 0));
        learned = input.getIntOr("learned", 0);
        overchargeTicks = input.getIntOr("overcharge", 0);
        setColor(input.getIntOr("color", -1));
        this.entityData.set(HAT, input.getIntOr("hat", -1));
        this.entityData.set(STICKER, input.getIntOr("sticker", -1));
        this.entityData.set(WEAR, input.getIntOr("wear", 0));
        bondDay = input.getLongOr("bond_day", -1);
        petsToday = input.getIntOr("pets_today", 0);
        giftsToday = input.getIntOr("gifts_today", 0);
        fightsToday = input.getIntOr("fights_today", 0);
        favouriteDay = input.getLongOr("favourite_day", -1);
        lastTogetherDay = input.getLongOr("last_together_day", -1);
        memory.load(input);
        this.entityData.set(NEWS, input.getBooleanOr("news", false));
        this.entityData.set(ABANDONED, input.getBooleanOr("abandoned", false));
        input.list("storage", ItemStack.CODEC).ifPresent(storage::fromItemList);
        setMagnet(input.getBooleanOr("magnet", false));
        ownHand = input.getBooleanOr("own_hand", false);
        armed = input.getBooleanOr("armed", false);
        showTicks = input.getIntOr("show", 0);
        playsToday = input.getIntOr("plays_today", 0);
        lastWithOwner = input.getLongOr("last_with_owner", Long.MIN_VALUE);
        lastWithOwnerReal = input.getLongOr("last_with_owner_real", Long.MIN_VALUE);
        lastBirthday = input.getLongOr("last_birthday", -1);
        partyTicks = input.getIntOr("party", 0);
        this.entityData.set(JOBS, input.getIntOr("jobs", 0));
        this.entityData.set(RADIUS, Mth.clamp(input.getIntOr("stay_radius", STAY_RADIUS), MIN_RADIUS, MAX_RADIUS));
        this.entityData.set(CHIPS, input.getIntOr("chips", 0));
        gazeNight = input.getLongOr("gaze_night", -1);
        chorusNight = input.getLongOr("chorus_night", -1);
        guardAt = input.read("guard_at", BlockPos.CODEC).orElse(null);
        guardTicks = input.getIntOr("guard_ticks", 0);
        kept.clear();
        input.read("kept", ItemStack.CODEC.listOf()).ifPresent(kept::addAll);
        purse = input.read("purse", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        treasureItem = input.read("treasure", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        input.read("treasure_at", BlockPos.CODEC).filter(at -> !treasureItem.isEmpty()).ifPresentOrElse(at -> {
            entityData.set(TREASURE, Optional.of(at));
            treasureMode = BURIED;
            treasureUntil = Long.MIN_VALUE; // set again on its first tick (the game time is not known yet)
        }, () -> treasureItem = ItemStack.EMPTY);
        if (!purse.isEmpty() && getMainHandItem().isEmpty()) { // saved while shopping: it brings the emeralds back
            setItemSlot(EquipmentSlot.MAINHAND, purse);
            purse = ItemStack.EMPTY;
        }
        this.entityData.set(PARTY, partyTicks > 0);
        input.read("stay_at", BlockPos.CODEC).ifPresent(this::stayHere);
        comforts = input.getIntOr("comforts", 0);
        comfortDay = input.getLongOr("comfort_day", -1);
    }

    /** Not when it is told to stay, is going home, or is switched off. */
    private boolean free() {
        return !slept && !parked() && !goingHome && dawdleTicks == 0 && getTarget() == null && !naughty.busy() && !bedside && digTicks == 0 && hideMode == 0 && trickTicks == 0 && !hunting() && wayTo == null;
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

    /**
     * Staying: parked on its homebase it keeps still; anywhere else it potters about (its wander goal keeps to
     * STAY_RADIUS of its spot) and drives back when something took it further, once that is done.
     */
    private class StayGoal extends Goal {
        private int ticks;

        StayGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        private boolean away(double margin) {
            return distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(stayAt)) > margin * margin;
        }

        @Override
        public boolean canUse() {
            if (!isOrderedToSit() || slept) return false;
            return stayAt == null || (away(stayRadius() + 2) && !busyWithOthers());
        }

        @Override
        public boolean canContinueToUse() {
            if (!isOrderedToSit() || slept) return false;
            return stayAt == null || (away(2) && !busyWithOthers());
        }

        @Override
        public void start() {
            ticks = 0;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            if (stayAt == null) {
                getNavigation().stop();
            } else if (ticks++ % 10 == 0) {
                getNavigation().moveTo(stayAt.getX() + 0.5, stayAt.getY(), stayAt.getZ() + 0.5, 1.0);
            }
        }
    }

    /** At work: drives to the crop or the chest and does the job. */
    private class WorkGoal extends Goal {
        private int ticks;

        WorkGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return workAt != null && stayAt != null && !slept && getTarget() == null && talkingTo == null && fightingWith == null && hideMode == 0;
        }

        @Override
        public void start() {
            ticks = 0;
        }

        @Override
        public void tick() {
            if (workAt == null) return;
            if (workKind == WORK_ANIMAL) { // the animal walks about: after it
                if (workAnimal == null || !workAnimal.isAlive()) { workAt = null; return; }
                workAt = workAnimal.blockPosition();
            }
            getLookControl().setLookAt(net.minecraft.world.phys.Vec3.atCenterOf(workAt));
            if (distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(workAt)) < 2.0 * 2.0) {
                getNavigation().stop();
                work(workAt);
                workAt = null;
            } else if (ticks++ % 10 == 0) {
                boolean way = getNavigation().moveTo(workAt.getX() + 0.5, workAt.getY(), workAt.getZ() + 0.5, 1.0);
                if (!way || ticks > 300) { // cannot get there: another time
                    unreachable.add(workAt);
                    workAt = null;
                }
            }
        }
    }

    /** Hide and seek: off to its hiding spot and keeping still, or counting and then looking for its owner. */
    private class HideAndSeekGoal extends Goal {
        private int ticks;

        HideAndSeekGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return hideMode != 0 && getOwner() instanceof Player && !slept;
        }

        @Override
        public void stop() {
            if (hideMode != 0 && !(getOwner() instanceof Player)) { hideMode = 0; hideSpot = null; }
        }

        @Override
        public void tick() {
            if (!(getOwner() instanceof Player owner)) return;
            ticks++;
            long now = level().getGameTime();
            if (now >= hideUntil) {
                endHideAndSeek(hideMode <= HIDE_HIDDEN ? "thought.bleep.hide.won" : "thought.bleep.seek.gave_up", false);
                return;
            }
            boolean sees = distanceToSqr(owner) < 14 * 14 && hasLineOfSight(owner);
            switch (hideMode) {
                case HIDE_GOING -> {
                    if (hideSpot == null || distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(hideSpot)) < 1.5 * 1.5) {
                        getNavigation().stop();
                        hideMode = HIDE_HIDDEN;
                    } else if (ticks % 10 == 0) {
                        getNavigation().moveTo(hideSpot.getX() + 0.5, hideSpot.getY(), hideSpot.getZ() + 0.5, 1.3);
                    }
                }
                case HIDE_HIDDEN -> { // keeping still; a soft blip now and then, as a hint
                    getNavigation().stop();
                    if (ticks % 100 == 0) playSound(BleepContent.VOICE.get(Emotion.HAPPY).get(), 0.3f, getVoicePitch());
                    if (distanceToSqr(owner) < 3 * 3 && owner.hasLineOfSight(BleepEntity.this)) endHideAndSeek("thought.bleep.hide.found", true);
                }
                case SEEK_COUNTING -> {
                    getNavigation().stop();
                    int counted = (int) (now - hideStart);
                    int line = counted / 40;
                    if (line > 0 && line < COUNT_TICKS / 40 && !thought().equals("thought.bleep.seek.count." + line)) pinThought("thought.bleep.seek.count." + line);
                    if (counted >= COUNT_TICKS) {
                        hideMode = SEEK_LOOKING;
                        say("thought.bleep.seek.ready");
                    }
                }
                case SEEK_LOOKING -> {
                    if (sees) {
                        getLookControl().setLookAt(owner);
                        if (distanceToSqr(owner) < 2.5 * 2.5) endHideAndSeek("thought.bleep.seek.found", true);
                        else if (ticks % 5 == 0) getNavigation().moveTo(owner, 1.3);
                    } else if (ticks % 60 == 0 && hideSpot != null) { // looking about
                        double angle = getRandom().nextDouble() * Math.PI * 2, distance = 3 + getRandom().nextDouble() * 12;
                        getNavigation().moveTo(hideSpot.getX() + Math.cos(angle) * distance, hideSpot.getY(), hideSpot.getZ() + Math.sin(angle) * distance, 1.1);
                    }
                }
                default -> {}
            }
        }
    }

    /** Show the way: it drives ahead (a glowing trail behind it), waits when its owner falls behind, and says so when they are there. */
    private class WayGoal extends Goal {
        private int ticks, waited;

        WayGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return wayTo != null && !slept && getOwner() instanceof Player;
        }

        @Override
        public void tick() {
            if (wayTo == null || !(getOwner() instanceof Player owner) || !(level() instanceof ServerLevel server)) return;
            ticks++;
            var target = net.minecraft.world.phys.Vec3.atBottomCenterOf(wayTo);
            if (owner.distanceToSqr(target) < 3 * 3) {
                endWay(wayToBed ? "thought.bleep.way.bed" : "thought.bleep.way.home");
                feel(Emotion.EXCITED, 40);
                rememberFirst("way", "diary.bleep.way", "");
                return;
            }
            if (level().getGameTime() >= wayUntil || distanceToSqr(owner) > 48 * 48 || !owner.level().dimension().equals(level().dimension())) {
                endWay("thought.bleep.way.lost");
                return;
            }
            boolean there = distanceToSqr(target) < 2.5 * 2.5;
            if (distanceToSqr(owner) > 10 * 10 || there) { // waiting: for its owner to catch up, or at the door
                getNavigation().stop();
                getLookControl().setLookAt(owner);
                if (++waited % 200 == 40 && !there) say("thought.bleep.way.wait");
            } else {
                waited = 0;
                if (ticks % 10 == 0) getNavigation().moveTo(target.x, target.y, target.z, 1.2);
            }
            if (ticks % 3 == 0 && getDeltaMovement().horizontalDistanceSqr() > 1.0e-4) // the glowing trail
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, getX(), getY() + 0.15, getZ(), 1, 0.05, 0, 0.05, 0.002);
        }
    }

    /** Treasure hunt: off to bury it, digging it up again, or counting and then looking for what its owner hid. */
    private class TreasureGoal extends Goal {
        private int ticks;

        TreasureGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return hunting() && getOwner() instanceof Player && !slept;
        }

        @Override
        public void tick() {
            if (!(getOwner() instanceof Player owner) || !(level() instanceof ServerLevel server)) return;
            ticks++;
            long now = level().getGameTime();
            BlockPos at = treasure();
            switch (treasureMode) {
                case BURYING, UNBURYING -> {
                    if (at == null) { endTreasure("thought.bleep.treasure.lost", false); return; }
                    boolean there = distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(at.above())) < 1.8 * 1.8;
                    if (!there && treasureMode == BURYING && now >= treasureUntil) { // could not get there: right here will do
                        BlockPos here = blockPosition().below();
                        if (!softGround(level().getBlockState(here))) { endTreasure("thought.bleep.treasure.no_spot", false); return; }
                        entityData.set(TREASURE, Optional.of(here));
                        at = here;
                        there = true;
                    }
                    if (!there) {
                        if (ticks % 10 == 0) getNavigation().moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 1.3);
                        return;
                    }
                    getNavigation().stop();
                    getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
                    var ground = level().getBlockState(at);
                    if (treasureTicks % 5 == 0) {
                        server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, ground), at.getX() + 0.5, at.getY() + 1.05, at.getZ() + 0.5, 6, 0.25, 0.05, 0.25, 0.1);
                        playSound(ground.getSoundType().getHitSound(), 0.6f, 1);
                    }
                    if (++treasureTicks < 40) return;
                    if (treasureMode == BURYING) {
                        treasureMode = BURIED;
                        treasureUntil = now + HUNT_TICKS;
                        lastWarmth = 0;
                        say("thought.bleep.treasure.buried");
                    } else { // dug up again: it brings it over (FetchGoal)
                        ItemStack found = treasureItem;
                        treasureItem = ItemStack.EMPTY;
                        endTreasure("thought.bleep.treasure.here", false);
                        if (getMainHandItem().isEmpty()) setItemSlot(EquipmentSlot.MAINHAND, found);
                        else storage.addItem(found);
                    }
                }
                case FIND_COUNTING -> {
                    getNavigation().stop();
                    long counted = now - treasureStart;
                    if (counted == HIDE_TICKS / 2) pinThought("thought.bleep.treasure.count.half");
                    if (counted < HIDE_TICKS) return;
                    if (hidden == null) { endTreasure("thought.bleep.treasure.nothing_hidden", false); return; }
                    treasureMode = FINDING;
                    treasureUntil = now + 2400;
                    decoys = 1 + getRandom().nextInt(3);
                    searchSpot = decoy(target());
                    treasureTicks = 0;
                    say("thought.bleep.treasure.search");
                }
                case FINDING -> {
                    if (hidden instanceof ItemEntity item && !item.isAlive()) { endTreasure("thought.bleep.treasure.taken", false); return; }
                    if (now >= treasureUntil) { endTreasure("thought.bleep.treasure.lost", false); return; }
                    BlockPos goal = decoys > 0 && searchSpot != null ? searchSpot : target();
                    double reach = hidden instanceof BlockPos ? 2.2 : 1.6;
                    if (distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(goal)) > reach * reach) {
                        if (ticks % 10 == 0) getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.1);
                        if (++treasureTicks > 200) { decoys = 0; treasureTicks = 0; } // that spot is no good: straight to it
                        return;
                    }
                    getNavigation().stop();
                    if (decoys > 0 && searchSpot != null) { // sniffing about
                        if (treasureTicks < 1000) { treasureTicks = 1000; showDisplay(Display.CURIOUS, 30); }
                        if (++treasureTicks < 1030) return;
                        treasureTicks = 0;
                        if (--decoys > 0) searchSpot = decoy(target());
                        pinThought(decoys > 0 ? "thought.bleep.treasure.not_here" : "thought.bleep.treasure.sniff");
                        return;
                    }
                    if (hidden instanceof ItemEntity item) {
                        pickUp(item); // and FetchGoal brings it back
                    } else if (hidden instanceof BlockPos chest) {
                        var state = level().getBlockState(chest);
                        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
                            level().blockEvent(chest, state.getBlock(), 1, 1); // the lid opens: there it is!
                            lidAt = chest;
                            lidCloses = now + 40;
                        }
                        getLookControl().setLookAt(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);
                    }
                    String found = hidden instanceof BlockPos ? "thought.bleep.treasure.found_chest" : "thought.bleep.treasure.found_it";
                    endTreasure(found, true);
                    showDisplay(Display.SPARKLE, 50);
                }
                default -> {}
            }
        }

        private BlockPos target() {
            return hidden instanceof ItemEntity item ? item.blockPosition() : hidden instanceof BlockPos pos ? pos : blockPosition();
        }
    }

    /** Guarding its owner's things: to the spot where they died (beaming there if it is far), and waiting there. */
    private class GuardGoal extends Goal {
        private int ticks;

        GuardGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return guardAt != null && !abandoned();
        }

        @Override
        public void start() {
            ticks = 0;
            if (distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(guardAt)) > 48 * 48) { // too far to drive: a little flash, and it is there
                if (level() instanceof ServerLevel server) server.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.5, getZ(), 12, 0.3, 0.4, 0.3, 0.1);
                snapTo(guardAt.getX() + 0.5, guardAt.getY(), guardAt.getZ() + 0.5, getYRot(), 0);
            }
        }

        @Override
        public void tick() {
            if (guardAt == null) return;
            if (distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(guardAt)) > 2 * 2) {
                if (ticks++ % 10 == 0) getNavigation().moveTo(guardAt.getX() + 0.5, guardAt.getY(), guardAt.getZ() + 0.5, 1.3);
            } else {
                getNavigation().stop();
            }
        }
    }

    /** Off shopping: to the villager, and the trade; a minute at most, then it brings the emeralds back. */
    private class ShopGoal extends Goal {
        private int ticks;

        ShopGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return shopAt != null && !slept;
        }

        @Override
        public void tick() {
            if (shopAt == null) return;
            if (!shopAt.isAlive() || ++shopTicks > 600) { // it could not get there
                shopAt = null;
                if (getMainHandItem().isEmpty()) {
                    setItemSlot(EquipmentSlot.MAINHAND, purse);
                    purse = ItemStack.EMPTY;
                }
                return;
            }
            getLookControl().setLookAt(shopAt);
            if (distanceToSqr(shopAt) < 2.5 * 2.5) {
                getNavigation().stop();
                trade();
            } else if (ticks++ % 5 == 0) {
                getNavigation().moveTo(shopAt, 1.2);
            }
        }
    }

    /** Its owner is back: it races over and bounces about in front of them. */
    private class WelcomeGoal extends Goal {
        private int ticks;

        WelcomeGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return welcomeTicks > 0 && getOwner() instanceof Player && !parked() && !slept && getTarget() == null;
        }

        @Override
        public void tick() {
            if (!(getOwner() instanceof Player owner)) return;
            getLookControl().setLookAt(owner);
            if (distanceToSqr(owner) > 2.5 * 2.5) {
                if (ticks++ % 5 == 0) getNavigation().moveTo(owner, 1.4);
            } else {
                getNavigation().stop();
                if (ticks++ % 12 == 0 && onGround()) getJumpControl().jump(); // bouncing with joy
            }
        }
    }

    /** Saying hi, chatting or fighting: close to the other bleep, looking at it. */
    private class SocialGoal extends Goal {
        private int ticks;

        SocialGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        private @Nullable BleepEntity other() {
            return fightingWith != null ? fightingWith : talkingTo != null ? talkingTo : greetTicks > 0 ? greeting : null;
        }

        @Override
        public boolean canUse() {
            BleepEntity other = other();
            return other != null && other.isAlive() && !slept && getTarget() == null && !parked() && !goingHome;
        }

        @Override
        public void tick() {
            BleepEntity other = other();
            if (other == null) return;
            getLookControl().setLookAt(other);
            double close = fightingWith != null ? 1.5 : 2.5;
            if (distanceToSqr(other) > close * close) {
                if (ticks++ % 5 == 0) getNavigation().moveTo(other, fightingWith != null ? 1.2 : 1.0);
            } else {
                getNavigation().stop();
            }
        }
    }

    /** Playing tag with another bleep: the one who is "it" chases, the other runs; a touch and it is the other's turn. */
    private class PlayGoal extends Goal {
        private int ticks;

        PlayGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return playing() && partner.isAlive() && free() && distanceToSqr(partner) < 16 * 16;
        }

        @Override
        public void tick() {
            if (partner == null) return;
            getLookControl().setLookAt(partner);
            if (ticks++ % 5 != 0) return;
            if (it) { // after it!
                getNavigation().moveTo(partner, tagCooldown > 0 ? 0.8 : 1.3);
            } else { // away from it, a little to the side
                var away = position().subtract(partner.position()).multiply(1, 0, 1);
                if (away.lengthSqr() < 0.01) away = new net.minecraft.world.phys.Vec3(1, 0, 0);
                away = away.normalize().yRot((getRandom().nextFloat() - 0.5f) * 1.2f).scale(5);
                getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.2);
            }
        }
    }

    /** Something from its storage in its hand: it drives to its owner, holds it up, and puts it back. */
    private class ShowGoal extends Goal {
        private int ticks;

        ShowGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return showTicks > 0 && ownHand && !armed && free() && getOwner() instanceof Player;
        }

        @Override
        public void tick() {
            if (!(getOwner() instanceof Player owner)) return;
            getLookControl().setLookAt(owner);
            if (distanceToSqr(owner) < 2.5 * 2.5) {
                getNavigation().stop();
                if (!showed) { // look! a minute of pride, then it goes back in
                    showed = true;
                    showTicks = Math.min(showTicks, 60);
                    feel(Emotion.EXCITED, 50);
                    pinThought("thought.bleep.show." + getRandom().nextInt(3));
                }
            } else if (ticks++ % 5 == 0) {
                getNavigation().moveTo(owner, 1.1);
            }
        }
    }

    /** Its owner went to bed: it drives to the bed and sleeps beside it until morning. */
    private class BedsideGoal extends Goal {
        private int ticks;

        BedsideGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return !slept && !goingHome && getTarget() == null && ownersBed() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            ticks = 0;
        }

        @Override
        public void stop() {
            if (bedside) { // morning
                bedside = false;
                feel(Emotion.HAPPY, 20);
            }
        }

        @Override
        public void tick() {
            BlockPos bed = ownersBed();
            if (bed == null || bedside) return;
            if (distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(bed)) < 2.5 * 2.5 || ++ticks > 400) {
                getNavigation().stop();
                bedside = true;
                rememberFirst("bedside", "diary.bleep.bedside", "");
            } else if (ticks % 5 == 1) {
                BleepEntity best = bestFriendNear(16);
                if (best != null && best.bedside) getNavigation().moveTo(best.getX() + 0.9, best.getY(), best.getZ(), 1.0); // next to its best friend
                else getNavigation().moveTo(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 1.0);
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
            return !getMainHandItem().isEmpty() && !ownHand && getOwner() instanceof Player owner && distanceToSqr(owner) < 24 * 24;
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
                if (maybePassBall()) return;
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
