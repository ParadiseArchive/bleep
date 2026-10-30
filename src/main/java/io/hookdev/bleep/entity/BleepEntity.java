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
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
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
    private static final EntityDataAccessor<Integer> DISPLAY = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> NEWS = SynchedEntityData.defineId(BleepEntity.class, EntityDataSerializers.BOOLEAN);

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
            Map.entry("stubborn", 3), Map.entry("glitch", 2), Map.entry("fighting", 3), Map.entry("overcharged", 3), Map.entry("storm", 2));
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
    /** Ticks left of being overcharged by lightning. */
    private int overchargeTicks;
    /** Ticks left of what its display shows instead of its face. */
    private int displayTicks;
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
        this.goalSelector.addGoal(3, new SitWhenOrderedToGoal(this));
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
        builder.define(OBSESSION, -1);
        builder.define(REVEALED, false);
        builder.define(ASSEMBLY, 0);
        builder.define(COLOR, -1);
        builder.define(HAT, -1);
        builder.define(STICKER, -1);
        builder.define(DISPLAY, 0);
        builder.define(NEWS, false);
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

    /** Puts this on; what it wore in that place goes back to the player. */
    public void wear(Player player, Accessory accessory) {
        Accessory old = accessory.hat ? hat() : sticker();
        if (old != null) giveBack(player, new ItemStack(BleepContent.ACCESSORIES.get(old).get()));
        this.entityData.set(accessory.hat ? HAT : STICKER, accessory.ordinal());
        feel(Emotion.EXCITED, 40);
    }

    /** Shears take off its hat and sticker, back to the player. False if it wears nothing. */
    public boolean takeOff(Player player) {
        boolean any = false;
        for (Accessory worn : new Accessory[]{hat(), sticker()}) {
            if (worn == null) continue;
            giveBack(player, new ItemStack(BleepContent.ACCESSORIES.get(worn).get()));
            any = true;
        }
        this.entityData.set(HAT, -1);
        this.entityData.set(STICKER, -1);
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
        if (hit) setBattery(battery() - FIGHT_COST);
        return hit;
    }

    // ---------- naughty ----------

    /** Free to get up to something: not in a fight, not in danger, not told to stay or go home. */
    public boolean mayBeNaughty() {
        return !slept && !isOrderedToSit() && !goingHome && getTarget() == null && dawdleTicks == 0 && !inDanger() && assembly() == 0;
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
            rememberFirst("llama", "diary.bleep.llama", "");
        }
        lightTheWay();
        tickDisplay(moving);
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
        }
        if (display.ordinal() != this.entityData.get(DISPLAY)) this.entityData.set(DISPLAY, display.ordinal());
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
        return super.isImmobile() || asleep() || dawdleTicks > 0 || assembly() > 0;
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
        boolean loved = favourite != null && favourite.matches().test(item.getItem());
        setItemSlot(EquipmentSlot.MAINHAND, item.getItem().split(loved ? 1 : item.getItem().getCount()));
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
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!owner.getInventory().add(stack)) {
            level().addFreshEntity(new ItemEntity(level(), owner.getX(), owner.getY(), owner.getZ(), stack));
        }
        feel(Emotion.EXCITED, 40);
    }

    private Emotion currentEmotion() {
        if (slept) return Emotion.ASLEEP;
        if (moodTicks > 0) return mood;
        if (overchargeTicks > 0) return (overchargeTicks / 10) % 2 == 0 ? Emotion.GLITCH : Emotion.EXCITED;
        if (getTarget() != null) return Emotion.ANGRY;
        if (battery() < TIRED) return Emotion.TIRED;
        if (idleTicks > (home() == null ? BORED_AFTER / 2 : BORED_AFTER)) return Emotion.BORED;
        return Emotion.HAPPY;
    }

    private Activity currentActivity(boolean home) {
        if (moodTicks > 0 && mood == Emotion.BOOT) return Activity.STARTING;
        if (home) return battery() < FULL ? Activity.CHARGING : slept ? Activity.SLEEPING : Activity.AT_HOME;
        if (slept) return Activity.SLEEPING;
        if (dawdleTicks > 0) return Activity.DAWDLING;
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
        String kind = emotion == Emotion.ASLEEP ? "asleep" : activity == Activity.CHARGING ? "charging"
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

    /** A name tag gives a bleep a name that always shows, like a player's. */
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
        if (asleep() || assembly() > 0) return null;
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
        output.putLong("bond_day", bondDay);
        output.putInt("pets_today", petsToday);
        output.putInt("gifts_today", giftsToday);
        output.putInt("fights_today", fightsToday);
        output.putLong("favourite_day", favouriteDay);
        output.putLong("last_together_day", lastTogetherDay);
        memory.save(output);
        output.putBoolean("news", news());
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
        bondDay = input.getLongOr("bond_day", -1);
        petsToday = input.getIntOr("pets_today", 0);
        giftsToday = input.getIntOr("gifts_today", 0);
        fightsToday = input.getIntOr("fights_today", 0);
        favouriteDay = input.getLongOr("favourite_day", -1);
        lastTogetherDay = input.getLongOr("last_together_day", -1);
        memory.load(input);
        this.entityData.set(NEWS, input.getBooleanOr("news", false));
    }

    /** Not when it is told to stay, is going home, or is switched off. */
    private boolean free() {
        return !slept && !isOrderedToSit() && !goingHome && dawdleTicks == 0 && getTarget() == null && !naughty.busy();
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
