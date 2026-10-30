package io.hookdev.bleep.entity;

/**
 * What a bleep's display shows when it is not its face (client/DisplayTextures draws them). The order is sent to
 * clients: only add at the end.
 */
public enum Display {
    /** Its face, the usual. */
    FACE,
    /** Its new name, scrolling across like a ticker. */
    NAME,
    /** Bored: a game of tic-tac-toe against itself. */
    GAME,
    /** On its way home: an arrow that points there. */
    HOME,
    /** It would like a cookie, please. */
    WANT,
    /** Something new in its Bleepedia that its owner has not seen yet: a ! now and then. */
    NEWS;

    private static final Display[] VALUES = values();

    public static Display byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : FACE;
    }
}
