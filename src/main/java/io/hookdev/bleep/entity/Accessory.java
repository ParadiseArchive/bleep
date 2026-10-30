package io.hookdev.bleep.entity;

/**
 * What a bleep can wear (design doc: hats, stickers, an antenna with a bow; owner, 30 Oct 2026: cool sunglasses, a
 * leather jacket, a cape and more): one thing in each slot. Each is an item of the same name (bleep:<name>). The order
 * is saved and sent to clients: only add at the end.
 */
public enum Accessory {
    PARTY_HAT(Slot.HAT), TOP_HAT(Slot.HAT), ANTENNA(Slot.HAT), STICKER_STAR(Slot.STICKER), STICKER_HEART(Slot.STICKER), STICKER_BOLT(Slot.STICKER),
    SUNGLASSES(Slot.EYES), LEATHER_JACKET(Slot.BODY), CAPE(Slot.BACK), BOW_TIE(Slot.NECK), SCARF(Slot.NECK), CROWN(Slot.HAT);

    private static final Accessory[] VALUES = values();

    /** Where it goes: on its head (a hat), its front (a sticker), over its screen (glasses), round its body, on its back, round its neck. */
    public enum Slot {
        HAT, STICKER, EYES, BODY, BACK, NECK;

        /** Worn on its head (it moves with the head), or else on its body. */
        public boolean onHead() {
            return this == HAT || this == EYES;
        }
    }

    public final Slot slot;
    /** A hat goes on its head. */
    public final boolean hat;

    Accessory(Slot slot) {
        this.slot = slot;
        this.hat = slot == Slot.HAT;
    }

    public static Accessory byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }

    /** Its item and model part name. */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
