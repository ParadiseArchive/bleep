package io.hookdev.bleep.entity;

import java.util.function.Predicate;

/**
 * Milestones in a bleep's life (the Badges tab of its Bleepedia): each with an icon (an item) and what earns it.
 * milestone.bleep.<id> is its name, milestone.bleep.<id>.desc what to do. The ids are saved: only add.
 */
public enum Milestone {
    DRIVE_1K("minecraft:minecart", b -> b.memory().distance >= 1000),
    DRIVE_10K("minecraft:powered_rail", b -> b.memory().distance >= 10000),
    MOBS_10("minecraft:spyglass", b -> b.memory().mobs.size() >= 10),
    MOBS_50("minecraft:recovery_compass", b -> b.memory().mobs.size() >= 50),
    BIOMES_10("minecraft:compass", b -> b.memory().biomes.size() >= 10),
    BIOMES_30("minecraft:map", b -> b.memory().biomes.size() >= 30),
    FINDS_5("minecraft:diamond", b -> b.memory().finds.size() >= 5),
    FIRST_FIGHT("minecraft:wooden_sword", b -> b.memory().fightsWon >= 1),
    FIGHTS_100("minecraft:diamond_sword", b -> b.memory().fightsWon >= 100),
    BEST_FRIEND("minecraft:cookie", b -> b.memory().bestFriend() != null),
    BOND_BEST("minecraft:golden_apple", b -> b.bond() >= 75),
    DAYS_30("minecraft:clock", b -> b.memory().born >= 0 && b.memory().day(b.level().getGameTime()) > 30),
    DAYS_100("minecraft:cake", b -> b.memory().born >= 0 && b.memory().day(b.level().getGameTime()) > 100),
    STAR("bleep:star_shard", b -> b.memory().firsts.contains("star")),
    ALL_SIGNS("minecraft:ender_eye", b -> b.memory().signs >= Memory.SIGNS),
    PLAYS_50("bleep:ball", b -> b.memory().count("playing") >= 50),
    CATCHES_50("minecraft:fishing_rod", b -> b.memory().count("catches") >= 50),
    HARVESTS_100("minecraft:wheat", b -> b.memory().count("harvests") >= 100),
    EMPTY_10("minecraft:redstone", b -> b.memory().timesEmpty >= 10),
    HOBBY("minecraft:painting", b -> !b.memory().hobby.isEmpty());

    /** Its icon: an item id. */
    public final String icon;
    private final Predicate<BleepEntity> earned;

    Milestone(String icon, Predicate<BleepEntity> earned) {
        this.icon = icon;
        this.earned = earned;
    }

    public boolean earnedBy(BleepEntity bleep) {
        return earned.test(bleep);
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
