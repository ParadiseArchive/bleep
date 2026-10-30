package io.hookdev.bleep.entity;

/**
 * The bond between a bleep and its owner, 0 to 100, in four levels. What raises and lowers it, from the design doc;
 * the caps per day keep it from being farmed.
 */
public final class Bond {
    public static final int MAX = 100;
    /** Where each level starts: New, Friend, Buddy, Best friend. */
    private static final int[] LEVELS = {0, 25, 50, 75};
    private static final String[] NAMES = {"new", "friend", "buddy", "best_friend"};
    /** How often it does not listen straight away, per level: it dawdles, then does it anyway. */
    private static final float[] DAWDLE = {0.25f, 0.15f, 0.08f, 0.03f};

    public static final int PET = 1, PETS_PER_DAY = 5;
    public static final int GIFT = 3, GIFTS_PER_DAY = 5;
    public static final int FAVOURITE = 10;
    public static final int FIGHT = 2, FIGHTS_PER_DAY = 5;
    public static final int DISCOVERY = 1;
    public static final int DAY_TOGETHER = 1, DAY_APART = -2, DAYS_APART_COUNTED = 5;
    public static final int SWITCHED_OFF = -3, HIT_BY_OWNER = -5, HOMEBASE_BROKEN = -10;
    /** A minecraft day, in ticks. */
    public static final long DAY = 24000;

    private Bond() {}

    /** 0 New, 1 Friend, 2 Buddy, 3 Best friend. */
    public static int level(int bond) {
        int level = 0;
        while (level + 1 < LEVELS.length && bond >= LEVELS[level + 1]) level++;
        return level;
    }

    /** The level's key in the language file, bond.bleep.<name>. */
    public static String key(int bond) {
        return "bond.bleep." + NAMES[level(bond)];
    }

    public static float dawdleChance(int bond) {
        return DAWDLE[level(bond)];
    }
}
