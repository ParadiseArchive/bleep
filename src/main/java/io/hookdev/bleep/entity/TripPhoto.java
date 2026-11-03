package io.hookdev.bleep.entity;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Photos from an expedition (owner, 31 Oct 2026): nobody's game was there to take a screenshot, so they are drawn here,
 * in colour, a little different every time. One of them is a selfie: the bleep itself in the front, in its own colour.
 */
public final class TripPhoto {
    private static final int W = Memory.Photo.W, H = Memory.Photo.H;
    private final float[] r = new float[W * H], g = new float[W * H], b = new float[W * H];
    private final RandomSource random;

    private TripPhoto(RandomSource random) {
        this.random = random;
    }

    /** A photo of this place (Expedition.PLACES), with the bleep in it (its colour, -1: white) or without. */
    public static byte[] draw(String place, int colour, boolean selfie, RandomSource random) {
        TripPhoto photo = new TripPhoto(random);
        switch (place) {
            case "forest" -> photo.forest();
            case "desert" -> photo.desert();
            case "mountains" -> photo.mountains();
            case "snowy" -> photo.snowy();
            case "ocean" -> photo.ocean();
            case "jungle" -> photo.jungle();
            case "swamp" -> photo.swamp();
            default -> photo.cave();
        }
        if (selfie) photo.bleep(12 + random.nextInt(14), H - 6, colour, place.equals("ocean"));
        return photo.develop();
    }

    private void forest() {
        sky(0x7fc8ff, 0xc9ecff, 30);
        hills(30, 0x5aa04a, 0x3f7f36);
        for (int i = 0; i < 7; i++) tree(6 + i * 14 + random.nextInt(6), 34 + random.nextInt(8), 0x6b4523, 0x2f7a2f);
        if (random.nextBoolean()) disc(80, 8, 4, 0xfff2a0);
    }

    private void desert() {
        sky(0xffd27f, 0xfff0c9, 26);
        disc(78, 10, 6, 0xfff6d0);
        hills(26, 0xe8cf8a, 0xd8b86a);
        int px = 56 + random.nextInt(16); // a pyramid far off
        for (int i = 0; i < 12; i++) fill(px - i, 18 + i, 2 * i + 1, 1, 0xd9b25e);
        for (int i = 0; i < 3; i++) cactus(8 + random.nextInt(40), 40 + random.nextInt(8));
    }

    private void mountains() {
        sky(0x8fd0ff, 0xe0f4ff, 36);
        for (int i = 0; i < 3; i++) peak(10 + i * 34 + random.nextInt(10), 6 + random.nextInt(8), 40, 0x8d949c, 0xf4f7fa);
        fill(0, 38, W, H - 38, 0x6aa84f);
        for (int x = 0; x < W; x += 3) fill(x, 38 + (x * 7) % 4, 1, 1, 0x7cc05c);
    }

    private void snowy() {
        sky(0xb7d4ea, 0xeaf3fa, 30);
        hills(30, 0xf2f6f9, 0xd6e2ec);
        for (int i = 0; i < 6; i++) spruce(8 + i * 16 + random.nextInt(6), 36 + random.nextInt(6));
        for (int i = 0; i < 60; i++) set(random.nextInt(W), random.nextInt(H), 0xffffff);
    }

    private void ocean() {
        sky(0x6ec3ff, 0xd2efff, 28);
        disc(70, 9, 5, 0xfff2a0);
        fill(0, 28, W, H - 28, 0x2f7fd0);
        for (int y = 30; y < H; y += 4) for (int x = (y * 3) % 7; x < W; x += 9) fill(x, y, 4, 1, 0x7fbff0);
        int ix = 60 + random.nextInt(20); // a little island with a palm
        fill(ix, 26, 16, 3, 0xe8cf8a);
        fill(ix + 7, 16, 1, 10, 0x8a6a3a);
        fill(ix + 3, 15, 9, 2, 0x3f9f3f);
    }

    private void jungle() {
        sky(0x9fd8a0, 0xd8f0c8, 20);
        for (int i = 0; i < 9; i++) tree(i * 11 + random.nextInt(5), 30 + random.nextInt(14), 0x5a3a1c, i % 2 == 0 ? 0x1f6a2a : 0x2f8a2f);
        for (int i = 0; i < 10; i++) fill(random.nextInt(W), 0, 1, 8 + random.nextInt(14), 0x2f7a2f); // vines
        set(30 + random.nextInt(40), 14 + random.nextInt(8), 0xe03a3a); // a parrot
        fill(0, H - 8, W, 8, 0x3f6a2a);
    }

    private void swamp() {
        sky(0x8fa88a, 0xc8d4b0, 26);
        fill(0, 26, W, H - 26, 0x3f5a3a);
        for (int i = 0; i < 4; i++) tree(8 + random.nextInt(80), 30, 0x4a3a2a, 0x4f6a3a);
        for (int i = 0; i < 8; i++) fill(random.nextInt(W), 32 + random.nextInt(20), 3, 1, 0x5f9a3f); // lily pads
        int hx = 60 + random.nextInt(16); // a hut on stilts
        fill(hx, 14, 14, 8, 0x6b4523);
        fill(hx + 2, 22, 1, 6, 0x4a3a2a);
        fill(hx + 11, 22, 1, 6, 0x4a3a2a);
        fill(hx + 5, 16, 3, 3, 0xffd27f);
    }

    private void cave() {
        fill(0, 0, W, H, 0x22252a);
        for (int i = 0; i < 10; i++) { // stalactites
            int x = random.nextInt(W), h = 4 + random.nextInt(10);
            for (int k = 0; k < h; k++) fill(x - (h - k) / 4, k, 1 + (h - k) / 2, 1, 0x3b3f45);
        }
        fill(0, H - 10, W, 10, 0x3b3f45);
        for (int i = 0; i < 6; i++) { // amethyst
            int x = random.nextInt(W), y = H - 12 - random.nextInt(6);
            fill(x, y, 2, 3, 0xb58cff);
            set(x, y - 1, 0xd8c0ff);
        }
        int tx = 20 + random.nextInt(50);
        glow(tx, 22, 26, 0xffb547, 0.35f);
        fill(tx, 22, 1, 4, 0x8a6a3a);
        set(tx, 21, 0xffe14a);
    }

    // ---------- pieces ----------

    /** From top to bottom over the first rows, then the bottom colour all the way down (nothing is left black). */
    private void sky(int top, int bottom, int to) {
        for (int y = 0; y < H; y++) fill(0, y, W, 1, mix(top, bottom, Math.min(1, y / (float) to)));
    }

    private void hills(int from, int light, int dark) {
        float phase = random.nextFloat() * 6;
        for (int x = 0; x < W; x++) {
            int top = from + (int) (Math.sin(x / 12f + phase) * 3);
            fill(x, top, 1, H - top, light);
            fill(x, top + 6 + (int) (Math.sin(x / 7f) * 2), 1, H, dark);
        }
    }

    private void tree(int x, int y, int trunk, int leaves) {
        fill(x, y - 8, 2, 8, trunk);
        disc(x + 1, y - 11, 4, leaves);
    }

    private void spruce(int x, int y) {
        fill(x, y - 3, 1, 3, 0x5a3a1c);
        for (int i = 0; i < 10; i++) fill(x - i / 2, y - 13 + i, i + 1, 1, i % 3 == 0 ? 0xf2f6f9 : 0x1f4a2f);
    }

    private void cactus(int x, int y) {
        fill(x, y - 9, 2, 9, 0x3f9f3f);
        fill(x - 2, y - 6, 2, 1, 0x3f9f3f);
        fill(x - 2, y - 8, 1, 2, 0x3f9f3f);
        fill(x + 2, y - 5, 2, 1, 0x3f9f3f);
        fill(x + 3, y - 7, 1, 2, 0x3f9f3f);
    }

    private void peak(int x, int top, int bottom, int rock, int snow) {
        for (int y = top; y < bottom; y++) {
            int half = (y - top) * 3 / 2;
            fill(x - half, y, half * 2 + 1, 1, y - top < 5 ? snow : rock);
        }
    }

    /** The bleep in its own colour: tracks, body, a dark screen with two eyes; in a boat on the water. */
    private void bleep(int x, int y, int colour, boolean boat) {
        int shell = colour < 0 ? 0xeef1f4 : colour;
        if (boat) {
            fill(x - 2, y - 2, 12, 3, 0x9c6b3a);
            fill(x - 2, y - 3, 12, 1, 0x2de2e6);
        } else {
            fill(x, y - 2, 8, 2, 0x2c2f34);
        }
        fill(x, y - 7, 8, 5, shell);
        fill(x + 1, y - 12, 6, 5, shell);
        fill(x + 2, y - 11, 4, 3, 0x08090c);
        set(x + 2, y - 10, 0x8ff7ff);
        set(x + 5, y - 10, 0x8ff7ff);
        fill(x - 1, y - 7, 1, 3, 0x3b3f45); // its arms, one waving
        fill(x + 8, y - 10, 1, 3, 0x3b3f45);
    }

    /** A little grain, and the photo as RGB 565. */
    private byte[] develop() {
        byte[] pixels = new byte[Memory.Photo.BYTES];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = y * W + x;
            float n = (random.nextFloat() - 0.5f) * 0.05f;
            Memory.Photo.put(pixels, x, y, (int) (255 * Mth.clamp(r[i] + n, 0, 1)), (int) (255 * Mth.clamp(g[i] + n, 0, 1)), (int) (255 * Mth.clamp(b[i] + n, 0, 1)));
        }
        return pixels;
    }

    private void set(int x, int y, int rgb) {
        if (x < 0 || x >= W || y < 0 || y >= H) return;
        int i = y * W + x;
        r[i] = (rgb >> 16 & 255) / 255f;
        g[i] = (rgb >> 8 & 255) / 255f;
        b[i] = (rgb & 255) / 255f;
    }

    private void fill(int x, int y, int w, int h, int rgb) {
        for (int j = y; j < y + h; j++) for (int k = x; k < x + w; k++) set(k, j, rgb);
    }

    private void disc(int cx, int cy, int rad, int rgb) {
        for (int j = -rad; j <= rad; j++) for (int k = -rad; k <= rad; k++) if (k * k + j * j <= rad * rad) set(cx + k, cy + j, rgb);
    }

    /** Warm light round a spot. */
    private void glow(int cx, int cy, int rad, int rgb, float strength) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            float d = (float) Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
            if (d >= rad) continue;
            float a = strength * (1 - d / rad);
            int i = y * W + x;
            r[i] = Math.min(1, r[i] + a * (rgb >> 16 & 255) / 255f);
            g[i] = Math.min(1, g[i] + a * (rgb >> 8 & 255) / 255f);
            b[i] = Math.min(1, b[i] + a * (rgb & 255) / 255f);
        }
    }

    private static int mix(int a, int c, float t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255, cr = c >> 16 & 255, cg = c >> 8 & 255, cb = c & 255;
        return (int) (ar + (cr - ar) * t) << 16 | (int) (ag + (cg - ag) * t) << 8 | (int) (ab + (cb - ab) * t);
    }
}
