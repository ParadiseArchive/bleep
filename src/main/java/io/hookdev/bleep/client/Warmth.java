package io.hookdev.bleep.client;

/** Treasure hunt: how warm its owner is, one bar (cold) to five (right on top of it). Used by the server for its hints and by the display. */
public final class Warmth {
    private Warmth() {}

    public static int of(float distance) {
        return distance < 2 ? 5 : distance < 4 ? 4 : distance < 7 ? 3 : distance < 11 ? 2 : 1;
    }
}
