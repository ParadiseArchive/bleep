package io.hookdev.bleep.entity;

/** What a bleep is doing, for its info screen. The order is sent to clients: only ever add at the end. */
public enum Activity {
    FOLLOWING, STAYING, GOING_HOME, CHARGING, AT_HOME, SLEEPING, HIDING, STARTING, DAWDLING, FETCHING, LOOKING, FIGHTING, NAUGHTY, OVERCHARGED, DANCING, BEDSIDE, DIGGING, ABANDONED, PLAYING, CHATTING, SQUABBLING, WELCOMING, WORKING, HIDE_AND_SEEK, TRICK, STARGAZING, GUARDING;

    private static final Activity[] VALUES = values();

    public static Activity byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : FOLLOWING;
    }

    /** Its line on the info screen, activity.bleep.<name>. */
    public String key() {
        return "activity.bleep." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
