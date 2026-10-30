package io.hookdev.bleep.entity;

/**
 * An upgrade chip, installed for good by right-clicking a bleep with it: one of each. The order is saved (as bits):
 * only add at the end.
 */
public enum Chip {
    /** It drives a third faster. */
    SPEED,
    /** Hits cost it half the battery. */
    SHIELD,
    /** Its battery lasts half as long again. */
    BATTERY;

    public int bit() {
        return 1 << ordinal();
    }

    /** item.bleep.<id>, and thought.bleep.chip.<name> for what it says. */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT) + "_chip";
    }
}
