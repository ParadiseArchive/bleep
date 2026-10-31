package io.hookdev.bleep;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * World settings for pack makers (set by the server, shared with players), as in Setpoint. The defaults are the mod as
 * it was designed; every getter falls back to them while the config is not loaded (in game tests, for one).
 */
public final class BleepConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_BLEEPS, MAGNET_RANGE, FALLING_STARS, FE_PER_PERCENT;
    public static final ModConfigSpec.DoubleValue BATTERY_DRAIN;
    public static final ModConfigSpec.BooleanValue CORE_IN_CHESTS, ABANDONED_BLEEPS, HOMEBASE_NEEDS_POWER;

    /** The defaults. */
    public static final int MAX = 10, MAGNET = 6, STARS = 40, FE = 1000;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("bleeps");
        MAX_BLEEPS = builder.comment("How many bleeps one player may have.").defineInRange("maxPerPlayer", MAX, 1, 100);
        BATTERY_DRAIN = builder.comment("How fast a bleep's battery runs down while it drives and when it is hit: 1 as designed, 0.5 half as fast, 0 never.")
                .defineInRange("batteryDrain", 1.0, 0.0, 10.0);
        MAGNET_RANGE = builder.comment("How far a bleep's magnet reaches, in blocks.").defineInRange("magnetRange", MAGNET, 1, 16);
        builder.pop();
        builder.push("homebase");
        HOMEBASE_NEEDS_POWER = builder.comment("A homebase charges a bleep from its own battery, filled with redstone, a block of redstone or FE from cables.",
                "Set to false and every homebase charges for free.").define("needsPower", true);
        FE_PER_PERCENT = builder.comment("FE a homebase gives for one percent of a bleep's battery. A dust of redstone is worth 10 percent, a block 90.",
                "A full homebase holds 100 percent: one full charge.").defineInRange("fePerPercent", FE, 1, 100_000);
        builder.pop();
        builder.push("world");
        CORE_IN_CHESTS = builder.comment("Bleep Cores in dungeon and mineshaft chests. Set to false if your pack hands them out another way;",
                "falling stars and the crafting recipe still give them.").define("coreInChests", true);
        FALLING_STARS = builder.comment("On a clear night each player has a 1 in this many chance a minute that a star falls nearby. 0: never.")
                .defineInRange("fallingStarOneIn", STARS, 0, 100_000);
        ABANDONED_BLEEPS = builder.comment("Now and then an old, abandoned bleep stands somewhere, to wake with a block of redstone.")
                .define("abandonedBleeps", true);
        builder.pop();
        SPEC = builder.build();
    }

    private BleepConfig() {}

    public static int maxBleeps() {
        return SPEC.isLoaded() ? MAX_BLEEPS.get() : MAX;
    }

    public static float batteryDrain() {
        return SPEC.isLoaded() ? BATTERY_DRAIN.get().floatValue() : 1;
    }

    public static int magnetRange() {
        return SPEC.isLoaded() ? MAGNET_RANGE.get() : MAGNET;
    }

    public static boolean homebaseNeedsPower() {
        return !SPEC.isLoaded() || HOMEBASE_NEEDS_POWER.get();
    }

    public static int fePerPercent() {
        return SPEC.isLoaded() ? FE_PER_PERCENT.get() : FE;
    }

    public static boolean coreInChests() {
        return !SPEC.isLoaded() || CORE_IN_CHESTS.get();
    }

    /** 1 in this many; 0 is never. */
    public static int fallingStars() {
        return SPEC.isLoaded() ? FALLING_STARS.get() : STARS;
    }

    public static boolean abandonedBleeps() {
        return !SPEC.isLoaded() || ABANDONED_BLEEPS.get();
    }
}
