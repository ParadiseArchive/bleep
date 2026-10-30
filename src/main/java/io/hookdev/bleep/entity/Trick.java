package io.hookdev.bleep.entity;

/** A trick it does when you ask (the Play screen); some it only learns as the bond grows. The order is sent: only add at the end. */
public enum Trick {
    SPIN(0, 20), JUMP(0, 36), WAVE(0, 40), DANCE(1, 80), PLAY_DEAD(2, 70);

    /** The bond level it needs (Bond.level: 1 Friend, 2 Buddy), and how long it takes. */
    public final int level, ticks;

    Trick(int level, int ticks) {
        this.level = level;
        this.ticks = ticks;
    }

    public static Trick byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : SPIN;
    }

    /** trick.bleep.<name>, and thought.bleep.trick.<name> for what it says. */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
