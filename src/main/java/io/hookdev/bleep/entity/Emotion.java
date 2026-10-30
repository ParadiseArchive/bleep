package io.hookdev.bleep.entity;

/** What a bleep's display shows. The order is saved and sent to clients: only ever add at the end. */
public enum Emotion {
    HAPPY, EXCITED, SAD, ANGRY, BORED, SURPRISED,
    /** Battery below 20%. */
    TIRED,
    /** Battery empty: the screen is off but for a last sleepy face. */
    ASLEEP,
    /** The first time the screen comes on. */
    BOOT,
    /** Dawdling over an order: it does it anyway in a moment. */
    STUBBORN,
    /** Too excited (it found its favourite thing), or zapped. */
    GLITCH;

    private static final Emotion[] VALUES = values();

    public static Emotion byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : HAPPY;
    }

    /** The name of its face texture, textures/entity/bleep/face/<name>.png. */
    public String texture() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
