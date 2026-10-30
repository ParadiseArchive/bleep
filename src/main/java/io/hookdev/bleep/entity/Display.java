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
    NEWS,
    /** Its owner has a ball with them: it would like to play. */
    BALL,
    /** Talking with another bleep: a speech bubble. */
    TALK,
    /** Meeting another bleep again: HI, and a heart. */
    HI,
    /** Its birthday: HAPPY BIRTHDAY, scrolling across. */
    BIRTHDAY,
    /** Stars: at full moon, at midnight, all bleeps sing together. */
    STARS,
    /** Its owner is hurt or hungry: a red heart. */
    CARE,
    /** Look out! (a creeper behind its owner) */
    ALERT,
    /** Reactions to what its owner does: they eat (a little heart), open a chest (?), fall (sweat), kill an animal (a tear), level up or earn an advancement (sparkles). */
    YUM, CURIOUS, SWEAT, TEAR, SPARKLE,
    /** Ore close by (its owner holds a pickaxe): a gem. */
    ORE,
    /** Treasure hunt: warm and cold bars (how close its owner is to what it buried), an arrow when they are far off. */
    TREASURE,
    /** A dream, asleep on its homebase at night: a mob it saw (its name), a place (a little scene), a falling star, or something strange. */
    DREAM;

    private static final Display[] VALUES = values();

    public static Display byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : FACE;
    }
}
