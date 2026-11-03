package io.hookdev.bleep.entity;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The old photo an abandoned bleep still has (owner, 31 Oct 2026): it and its old owner, from its past
 * (diary.bleep.past.<n>), drawn here because nobody took a screenshot back then. Grey values first, then faded,
 * scratched and turned sepia: every one comes out a little different.
 */
public final class OldPhoto {
    private static final int W = Memory.Photo.W, H = Memory.Photo.H;
    private final float[] lum = new float[W * H];
    private final RandomSource random;

    private OldPhoto(RandomSource random) {
        this.random = random;
    }

    /** The photo for this past, as Memory.Photo pixels. */
    public static byte[] draw(int past, RandomSource random) {
        OldPhoto photo = new OldPhoto(random);
        switch (past) {
            case 0 -> photo.mine();
            case 1 -> photo.farm();
            case 2 -> photo.home();
            default -> photo.storm();
        }
        return photo.develop();
    }

    /** The miner, deep down: a mineshaft frame, rails and a torch. */
    private void mine() {
        fill(0, 0, W, H, 0.06f);
        fill(0, 42, W, H - 42, 0.2f);
        fill(20, 10, 3, 32, 0.45f);
        fill(72, 10, 3, 32, 0.45f);
        fill(18, 8, 59, 3, 0.45f);
        for (int x = 0; x < W; x += 6) fill(x, 47, 3, 4, 0.35f);
        fill(0, 47, W, 1, 0.55f);
        fill(0, 50, W, 1, 0.55f);
        fill(27, 18, 1, 5, 0.5f);
        glow(27, 17, 30, 0.45f);
        disc(27, 16, 1, 1f);
        person(38, 44, 0.3f);
        for (int i = 0; i < 6; i++) set(43 + i / 2, 30 - i, 0.6f); // a pickaxe up in the air
        fill(41, 25, 5, 1, 0.6f);
        bleep(50, 44);
    }

    /** The farm: a barn, a field, chickens, and the sun. */
    private void farm() {
        for (int y = 0; y < 30; y++) fill(0, y, W, 1, 0.85f - y * 0.005f);
        disc(80, 9, 5, 1f);
        fill(0, 30, W, H - 30, 0.45f);
        for (int y = 33; y < H; y += 4) fill(0, y, W, 2, 0.35f);
        fill(6, 16, 24, 16, 0.3f);
        for (int i = 0; i < 12; i++) fill(6 + i, 16 - i / 2, 24 - 2 * i, 1, 0.2f);
        fill(14, 23, 8, 9, 0.15f);
        for (int x = 34; x < W; x += 5) fill(x, 28, 1, 5, 0.25f);
        fill(34, 29, W - 34, 1, 0.25f);
        person(44, 46, 0);
        bleep(54, 46);
        for (int x : new int[] {68, 75, 84}) {
            fill(x, 43, 4, 3, 0.95f);
            set(x + 3, 42, 0.95f);
            set(x + 4, 42, 0.6f);
        }
    }

    /** Home: its spot by the door, a window, the two of them in front. */
    private void home() {
        fill(0, 0, W, H, 0.55f);
        for (int y = 3; y < 46; y += 4) fill(0, y, W, 1, 0.48f);
        fill(40, 14, 15, 32, 0.22f);
        fill(41, 15, 13, 1, 0.28f);
        set(51, 31, 0.85f);
        fill(66, 16, 15, 12, 0.85f);
        fill(73, 16, 1, 12, 0.4f);
        fill(66, 22, 15, 1, 0.4f);
        fill(0, 46, W, H - 46, 0.35f);
        fill(84, 40, 5, 6, 0.3f); // a flower pot
        disc(86, 37, 2, 0.75f);
        person(26, 48, 0);
        bleep(58, 48);
    }

    /** The lightning tower, in a storm: the two of them on top, waiting. */
    private void storm() {
        for (int y = 0; y < H; y++) fill(0, y, W, 1, 0.38f + y * 0.004f);
        for (int i = 0; i < 9; i++) disc(random.nextInt(W), random.nextInt(14), 4 + random.nextInt(5), 0.26f + random.nextFloat() * 0.12f);
        int bx = 76 + random.nextInt(10);
        for (int y = 0, x = bx; y < 40; y++) { // a bolt far away
            set(x, y, 1f);
            if (y % 5 == 4) x += random.nextBoolean() ? 2 : -2;
        }
        fill(0, 44, W, H - 44, 0.12f);
        fill(34, 24, 22, H - 24, 0.14f);
        for (int y = 27; y < H; y += 5) fill(34, y, 22, 1, 0.1f);
        fill(44, 6, 1, 18, 0.12f); // its lightning rod
        person(37, 24, 0);
        bleep(47, 24);
        for (int i = 0; i < 70; i++) { // rain
            int x = random.nextInt(W), y = random.nextInt(H);
            for (int k = 0; k < 3; k++) add(x - k, y + k, 0.18f);
        }
    }

    /** A person, feet at y; lighter by this much where it is dark. */
    private void person(int x, int y, float lighter) {
        fill(x, y - 6, 4, 6, 0.2f + lighter);
        fill(x, y - 12, 4, 6, 0.32f + lighter);
        fill(x - 1, y - 12, 1, 5, 0.32f + lighter);
        fill(x + 4, y - 12, 1, 5, 0.32f + lighter);
        fill(x, y - 16, 4, 4, 0.62f + lighter);
        fill(x, y - 16, 4, 1, 0.25f + lighter);
    }

    /** A bleep, its tracks on y: the screen lit, two eyes. */
    private void bleep(int x, int y) {
        fill(x, y - 2, 8, 2, 0.15f);
        fill(x, y - 7, 8, 5, 0.5f);
        fill(x + 1, y - 6, 6, 3, 0.85f);
        set(x + 2, y - 5, 0.1f);
        set(x + 5, y - 5, 0.1f);
    }

    /** Fades it, a dark rim, grain, scratches and a stain, in sepia: two bytes a pixel. */
    private byte[] develop() {
        int scratches = 2 + random.nextInt(3);
        for (int i = 0; i < scratches; i++) {
            int x = random.nextInt(W), from = random.nextInt(H), length = 6 + random.nextInt(H);
            for (int y = from; y < Math.min(H, from + length); y++) add(x, y, 0.25f);
        }
        int sx = random.nextInt(W), sy = random.nextInt(H);
        byte[] pixels = new byte[Memory.Photo.BYTES];
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            float l = 0.16f + lum[y * W + x] * 0.68f;
            float dx = (x - W / 2f) / (W / 2f), dy = (y - H / 2f) / (H / 2f);
            l *= 1 - 0.5f * (float) Math.pow((dx * dx + dy * dy) / 2, 1.2);
            if ((x - sx) * (x - sx) + (y - sy) * (y - sy) < 49) l += 0.07f; // a stain
            l = Mth.clamp(l + (random.nextFloat() - 0.5f) * 0.1f, 0, 1);
            Memory.Photo.put(pixels, x, y, (int) (255 * Math.min(1, l * 1.08f + 0.06f)), (int) (255 * Math.min(1, l * 0.92f + 0.04f)), (int) (255 * Math.min(1, l * 0.7f + 0.02f)));
        }
        return pixels;
    }

    private void set(int x, int y, float v) {
        if (x >= 0 && x < W && y >= 0 && y < H) lum[y * W + x] = v;
    }

    private void add(int x, int y, float v) {
        if (x >= 0 && x < W && y >= 0 && y < H) lum[y * W + x] = Math.min(1, lum[y * W + x] + v);
    }

    private void fill(int x, int y, int w, int h, float v) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) set(i, j, v);
    }

    private void disc(int cx, int cy, int r, float v) {
        for (int j = -r; j <= r; j++) for (int i = -r; i <= r; i++) if (i * i + j * j <= r * r) set(cx + i, cy + j, v);
    }

    /** Light round a spot, fading out over r pixels. */
    private void glow(int cx, int cy, int r, float v) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            float d = (float) Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
            if (d < r) add(x, y, v * (1 - d / r));
        }
    }
}
