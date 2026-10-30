package io.hookdev.bleep.entity;

/**
 * What a bleep can wear (design doc: hats, stickers, an antenna with a bow): one hat on its head and one sticker on its
 * front. Each is an item of the same name (bleep:<name>). The order is saved and sent to clients: only add at the end.
 */
public enum Accessory {
    PARTY_HAT(true), TOP_HAT(true), ANTENNA(true), STICKER_STAR(false), STICKER_HEART(false), STICKER_BOLT(false);

    private static final Accessory[] VALUES = values();

    /** A hat goes on its head; the rest are stickers on its front. */
    public final boolean hat;

    Accessory(boolean hat) {
        this.hat = hat;
    }

    public static Accessory byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }

    /** Its item and model part name. */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
