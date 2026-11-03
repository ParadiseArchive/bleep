package io.hookdev.bleep.entity;

import java.util.Arrays;

/**
 * Music on note blocks (owner, 31 Oct 2026): a bleep plays a tune on a note block, in the sound of that note block (the
 * block under it picks the instrument, as always). Other bleeps nearby join in on note blocks of their own: the first
 * plays the tune, the second a harmony a third below, the third the bass. Old tunes nobody owns, and one of its own.
 * Notes are note block notes, 0 (F#3) to 24 (F#5), all in G major; one number a beat, "-" holds.
 */
public final class Music {
    /** Ticks a beat (150 beats a minute). */
    public static final int BEAT = 8;
    /** The parts of a band, in the order bleeps join. */
    public static final int MELODY = 0, HARMONY = 1, BASS = 2, PARTS = 3;
    /** tune.bleep.<id> */
    public static final String[] TUNES = {"twinkle", "joy", "jacques", "bleep"};
    private static final int[][] MELODIES = {
            parse("13 13 20 20 22 22 20 - 18 18 17 17 15 15 13 - 20 20 18 18 17 17 15 - 20 20 18 18 17 17 15 - 13 13 20 20 22 22 20 - 18 18 17 17 15 15 13 -"),
            parse("17 17 18 20 20 18 17 15 13 13 15 17 17 15 15 - 17 17 18 20 20 18 17 15 13 13 15 17 15 13 13 -"),
            parse("13 15 17 13 13 15 17 13 17 18 20 - 17 18 20 - 20 22 20 18 17 - 13 - 20 22 20 18 17 - 13 - 13 8 13 - 13 8 13 -"),
            parse("13 17 20 24 - 20 17 - 15 18 22 - 20 - - - 13 17 20 24 22 20 17 15 13 - 20 - 13 - - -"),
    };
    /** G major on a note block, low to high. */
    private static final int[] SCALE = {1, 3, 5, 6, 8, 10, 12, 13, 15, 17, 18, 20, 22, 24};

    private Music() {}

    private static int[] parse(String beats) {
        return Arrays.stream(beats.split(" ")).mapToInt(b -> b.equals("-") ? -1 : Integer.parseInt(b)).toArray();
    }

    /** How many beats this tune lasts. */
    public static int length(int tune) {
        return MELODIES[tune].length;
    }

    /** The note this part plays on this beat of the tune, or -1 for none. */
    public static int note(int tune, int part, int beat) {
        int[] melody = MELODIES[tune];
        if (beat < 0 || beat >= melody.length) return -1;
        int n = melody[beat];
        return switch (part) {
            case MELODY -> n;
            case HARMONY -> n < 0 ? -1 : third(n);
            default -> beat % 2 == 0 ? root(melody, beat) : -1;
        };
    }

    /** A third below, in the key. */
    private static int third(int n) {
        int i = Arrays.binarySearch(SCALE, n);
        return i >= 2 ? SCALE[i - 2] : n;
    }

    /** The bass: the low G, C or D that goes with the tune at this beat (the last note played, if it holds). */
    private static int root(int[] melody, int beat) {
        int n = -1;
        for (int b = beat; b >= 0 && n < 0; b--) n = melody[b];
        if (n < 0) return -1;
        return switch (n % 12) {
            case 6, 10 -> 6;   // C, E: C
            case 3, 0 -> 8;    // A, F#: D
            default -> 1;      // G, B, D: G
        };
    }

    /** A note block's pitch for this note, as vanilla plays it. */
    public static float pitch(int note) {
        return (float) Math.pow(2, (note - 12) / 12.0);
    }
}
