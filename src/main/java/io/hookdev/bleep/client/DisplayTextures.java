package io.hookdev.bleep.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.entity.Display;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * A live display for the bleeps whose screen shows something other than a face: its name as a ticker, a game of
 * tic-tac-toe, an arrow home, a cookie it would like, a ! for news. One texture per bleep, the size of the face textures
 * (128 x 128, the display at 18..38, 18..28), drawn again when what it shows changes.
 */
public final class DisplayTextures {
    private static final int SIZE = 128, X = 18, Y = 18, W = 20, H = 10;
    private static final int OFF = 0x00000000, GLOW = 0xFF8FF7FF, DIM = 0xFF2F8F96, AMBER = 0xFFFFB547, PINK = 0xFFFF4F8B, BROWN = 0xFFB97A3E, CHIP = 0xFF5A3A1E;

    private record Screen(Identifier id, DynamicTexture texture, String[] last) {}

    private static final Map<Integer, Screen> SCREENS = new HashMap<>();

    private DisplayTextures() {}

    /** The texture for this bleep's display, drawn for this moment. */
    public static Identifier texture(BleepRenderState state) {
        Screen screen = SCREENS.computeIfAbsent(state.id, id -> {
            Identifier name = Bleep.id("display/" + id);
            DynamicTexture texture = new DynamicTexture(() -> "Bleep display " + id, SIZE, SIZE, true);
            Minecraft.getInstance().getTextureManager().register(name, texture);
            return new Screen(name, texture, new String[]{""});
        });
        String key = key(state);
        if (!key.equals(screen.last()[0])) { // only draw again when the picture changes
            screen.last()[0] = key;
            NativeImage image = screen.texture().getPixels();
            image.fillRect(0, 0, SIZE, SIZE, OFF);
            draw(image, state);
            screen.texture().upload();
        }
        return screen.id();
    }

    /** Forgets a bleep that is gone. */
    public static void forget(int id) {
        Screen screen = SCREENS.remove(id);
        if (screen != null) Minecraft.getInstance().getTextureManager().release(screen.id());
    }

    /** What decides the picture: so it is only drawn when this changes. */
    private static String key(BleepRenderState state) {
        int step = (int) (state.ageInTicks / 3);
        return switch (state.display) {
            case NAME -> "name" + state.name + step;
            case GAME -> "game" + (int) (state.ageInTicks / 15);
            case HOME -> "home" + arrow(state.homeAngle);
            case WANT -> "want" + ((int) (state.ageInTicks / 10) % 2);
            case NEWS -> "news" + ((int) (state.ageInTicks / 8) % 2);
            case BALL -> "ball" + ((int) (state.ageInTicks / 10) % 2);
            case TALK -> "talk" + ((int) (state.ageInTicks / 6) % 4);
            case HI -> "hi" + ((int) (state.ageInTicks / 10) % 2);
            case BIRTHDAY -> "birthday" + step;
            case STARS -> "stars" + ((int) (state.ageInTicks / 5) % 4);
            case CARE -> "care" + ((int) (state.ageInTicks / 10) % 2);
            case ALERT -> "alert" + ((int) (state.ageInTicks / 4) % 2);
            case YUM, CURIOUS, SWEAT, TEAR, SPARKLE -> state.display.name() + ((int) (state.ageInTicks / 8) % 2);
            default -> "face";
        };
    }

    private static void draw(NativeImage image, BleepRenderState state) {
        switch (state.display) {
            case NAME -> ticker(image, state.name, (int) (state.ageInTicks / 3));
            case BIRTHDAY -> ticker(image, "HAPPY BIRTHDAY", (int) (state.ageInTicks / 3));
            case STARS -> stars(image, (int) (state.ageInTicks / 5) % 4);
            case CARE -> care(image, (int) (state.ageInTicks / 10) % 2 == 0);
            case ALERT -> alert(image, (int) (state.ageInTicks / 4) % 2 == 0);
            case YUM -> yum(image, (int) (state.ageInTicks / 8) % 2 == 0);
            case CURIOUS -> curious(image, (int) (state.ageInTicks / 8) % 2 == 0);
            case SWEAT -> sweat(image, (int) (state.ageInTicks / 8) % 2 == 0);
            case TEAR -> tear(image, (int) (state.ageInTicks / 8) % 2 == 0);
            case SPARKLE -> sparkle(image, (int) (state.ageInTicks / 8) % 2 == 0);
            case GAME -> ticTacToe(image, (int) (state.ageInTicks / 15), state.id);
            case HOME -> arrow(image, arrow(state.homeAngle));
            case WANT -> cookie(image, (int) (state.ageInTicks / 10) % 2 == 0);
            case NEWS -> news(image, (int) (state.ageInTicks / 8) % 2 == 0);
            case BALL -> ball(image, (int) (state.ageInTicks / 10) % 2 == 0);
            case TALK -> talk(image, (int) (state.ageInTicks / 6) % 4);
            case HI -> hi(image, (int) (state.ageInTicks / 10) % 2 == 0);
            default -> {}
        }
    }

    private static void set(NativeImage image, int x, int y, int colour) {
        if (x >= 0 && x < W && y >= 0 && y < H) image.setPixel(X + x, Y + y, colour);
    }

    // ---------- its name, scrolling across ----------

    /** A 3 x 5 pixel font: five rows of three, # lit. */
    private static final Map<Character, String> FONT = new HashMap<>();
    static {
        String[] glyphs = {
            "A ### #.# ### #.# #.#", "B ##. #.# ##. #.# ##.", "C ### #.. #.. #.. ###", "D ##. #.# #.# #.# ##.", "E ### #.. ##. #.. ###",
            "F ### #.. ##. #.. #..", "G ### #.. #.# #.# ###", "H #.# #.# ### #.# #.#", "I ### .#. .#. .#. ###", "J ..# ..# ..# #.# ###",
            "K #.# #.# ##. #.# #.#", "L #.. #.. #.. #.. ###", "M #.# ### ### #.# #.#", "N ##. #.# #.# #.# #.#", "O ### #.# #.# #.# ###",
            "P ### #.# ### #.. #..", "Q ### #.# #.# ### ..#", "R ### #.# ##. #.# #.#", "S ### #.. ### ..# ###", "T ### .#. .#. .#. .#.",
            "U #.# #.# #.# #.# ###", "V #.# #.# #.# #.# .#.", "W #.# #.# ### ### #.#", "X #.# #.# .#. #.# #.#", "Y #.# #.# .#. .#. .#.",
            "Z ### ..# .#. #.. ###", "0 ### #.# #.# #.# ###", "1 .#. ##. .#. .#. ###", "2 ### ..# ### #.. ###", "3 ### ..# .## ..# ###",
            "4 #.# #.# ### ..# ..#", "5 ### #.. ### ..# ###", "6 ### #.. ### #.# ###", "7 ### ..# ..# ..# ..#", "8 ### #.# ### #.# ###",
            "9 ### #.# ### ..# ###", "! .#. .#. .#. ... .#.", "? ### ..# .## ... .#.", ". ... ... ... ... .#.", "- ... ... ### ... ...",
        };
        for (String glyph : glyphs) FONT.put(glyph.charAt(0), glyph.substring(2).replace(" ", ""));
    }

    private static void ticker(NativeImage image, String name, int step) {
        String text = name.toUpperCase(java.util.Locale.ROOT);
        int width = text.length() * 4, offset = W - step % (width + W);
        for (int i = 0; i < text.length(); i++) {
            String glyph = FONT.get(text.charAt(i));
            if (glyph == null) continue;
            for (int g = 0; g < 15; g++) if (glyph.charAt(g) == '#') set(image, offset + i * 4 + g % 3, 2 + g / 3, GLOW);
        }
        for (int x = 0; x < W; x++) set(image, x, 8, DIM); // the ticker's rail
    }

    // ---------- tic-tac-toe against itself ----------

    /** A game played out move by move, a new one when it is done. Which moves comes from its id and the game's number. */
    private static void ticTacToe(NativeImage image, int step, int id) {
        int game = step / 12, moves = Math.min(step % 12, 9);
        java.util.Random random = new java.util.Random(id * 31L + game);
        int[] board = new int[9];
        java.util.List<Integer> free = new java.util.ArrayList<>(java.util.List.of(0, 1, 2, 3, 4, 5, 6, 7, 8));
        java.util.Collections.shuffle(free, random);
        for (int m = 0; m < moves && m < free.size(); m++) {
            board[free.get(m)] = m % 2 == 0 ? 1 : 2;
            if (won(board) != 0) break;
        }
        // the grid: three cells of 3 x 3 with lines between, on the left of the screen
        for (int i = 0; i < 9; i++) { set(image, 4 + 3, i, DIM); set(image, 4 + 7, i, DIM); }
        for (int i = 0; i < 11; i++) { set(image, 4 + i, 2, DIM); set(image, 4 + i, 6, DIM); }
        for (int cell = 0; cell < 9; cell++) {
            int cx = 4 + (cell % 3) * 4, cy = (cell / 3) * 4 - 1;
            if (board[cell] == 1) { set(image, cx, cy + 1, GLOW); set(image, cx + 2, cy + 1, GLOW); set(image, cx + 1, cy + 2, GLOW); set(image, cx, cy + 3, GLOW); set(image, cx + 2, cy + 3, GLOW); }
            if (board[cell] == 2) for (int[] p : new int[][]{{1, 1}, {0, 2}, {2, 2}, {1, 3}}) set(image, cx + p[0], cy + p[1], AMBER);
        }
        int winner = won(board);
        if (winner != 0) set(image, 18, 4, winner == 1 ? GLOW : AMBER); // a little light for whoever won
    }

    private static int won(int[] b) {
        int[][] lines = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};
        for (int[] l : lines) if (b[l[0]] != 0 && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]];
        return 0;
    }

    // ---------- an arrow home ----------

    /** One of eight directions (0 is ahead, counting clockwise), or -1 when it does not know where home is. */
    private static int arrow(float angle) {
        if (Float.isNaN(angle)) return -1;
        return Math.floorMod(Math.round(angle / 45f), 8);
    }

    private static void arrow(NativeImage image, int direction) {
        if (direction < 0) {
            ticker(image, "?", 0);
            return;
        }
        // an arrow drawn on a 7 x 7 grid, turned in 45 degree steps
        // its right is your left as you look at its screen: the arrow turns the other way
        double a = Math.toRadians(-direction * 45);
        double cx = 9.5, cy = 4.5;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
            // in the arrow's own frame: up the screen is ahead
            double u = dx * Math.cos(a) + dy * Math.sin(a), v = -dx * Math.sin(a) + dy * Math.cos(a);
            boolean shaft = Math.abs(u) < 0.8 && v > -1 && v < 3.6;
            boolean head = v <= -0.5 && v > -4 && Math.abs(u) < (v + 4.2) * 0.9;
            if (shaft || head) set(image, x, y, GLOW);
        }
        set(image, 1, 8, DIM); set(image, 18, 8, DIM);
    }

    // ---------- please, a cookie ----------

    private static void cookie(NativeImage image, boolean blink) {
        String[] icon = {".###.", "#####", "##o##", "#o###", "####o", ".###."};
        for (int y = 0; y < icon.length; y++) for (int x = 0; x < 5; x++) {
            char c = icon[y].charAt(x);
            if (c != '.') set(image, 5 + x, 2 + y, c == 'o' ? CHIP : BROWN);
        }
        if (blink) { // a pleading question mark
            String glyph = FONT.get('?');
            for (int g = 0; g < 15; g++) if (glyph.charAt(g) == '#') set(image, 13 + g % 3, 2 + g / 3, GLOW);
        }
    }

    // ---------- shall we play? a ball ----------

    private static void ball(NativeImage image, boolean blink) {
        String[] icon = {".pp.", "pwpp", "gggg", ".gg."};
        int bounce = blink ? 0 : 1;
        for (int y = 0; y < icon.length; y++) for (int x = 0; x < 4; x++) {
            char c = icon[y].charAt(x);
            if (c != '.') set(image, 6 + x, 2 + bounce + y, c == 'p' ? PINK : c == 'g' ? GLOW : 0xFFEEF1F4);
        }
        set(image, 6, 8, DIM); set(image, 7, 8, DIM); set(image, 8, 8, DIM); set(image, 9, 8, DIM); // the ground it bounces on
        if (blink) {
            String glyph = FONT.get('?');
            for (int g = 0; g < 15; g++) if (glyph.charAt(g) == '#') set(image, 13 + g % 3, 2 + g / 3, AMBER);
        }
    }

    // ---------- talking to another bleep, saying hi ----------

    /** A speech bubble, its dots coming one by one. */
    private static void talk(NativeImage image, int dots) {
        for (int x = 4; x <= 15; x++) { set(image, x, 1, GLOW); set(image, x, 6, GLOW); }
        for (int y = 2; y <= 5; y++) { set(image, 3, y, GLOW); set(image, 16, y, GLOW); }
        set(image, 6, 7, GLOW); set(image, 5, 8, GLOW); // its tail
        for (int i = 0; i < Math.min(dots, 3); i++) set(image, 7 + i * 3, 4, AMBER);
    }

    private static void hi(NativeImage image, boolean beat) {
        for (int i = 0; i < 2; i++) {
            String glyph = FONT.get("HI".charAt(i));
            for (int g = 0; g < 15; g++) if (glyph.charAt(g) == '#') set(image, 3 + i * 4 + g % 3, 2 + g / 3, GLOW);
        }
        String[] heart = beat ? new String[] {".#.#.", "#####", "#####", ".###.", "..#.."} : new String[] {".....", ".#.#.", ".###.", "..#..", "....."};
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) if (heart[y].charAt(x) == '#') set(image, 12 + x, 2 + y, PINK);
    }

    // ---------- stars: a little constellation, twinkling ----------

    private static void stars(NativeImage image, int twinkle) {
        int[][] stars = {{2, 2}, {6, 1}, {10, 3}, {14, 2}, {17, 5}, {4, 6}, {9, 7}, {13, 6}};
        for (int i = 0; i < stars.length; i++) {
            int x = stars[i][0], y = stars[i][1];
            boolean bright = (i + twinkle) % 4 == 0;
            set(image, x, y, bright ? 0xFFFFFFFF : GLOW);
            if (bright) { set(image, x - 1, y, DIM); set(image, x + 1, y, DIM); set(image, x, y - 1, DIM); set(image, x, y + 1, DIM); }
        }
        for (int x = 3; x < 6; x++) set(image, x, 4, DIM); // a line between two of them
    }

    // ---------- care: a red heart (beating); look out: a big red ! (flashing) ----------

    private static final int RED = 0xFFFF3B4E;

    private static void care(NativeImage image, boolean beat) {
        String[] heart = beat ? new String[] {".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."} : new String[] {".......", ".##.##.", ".#####.", "..###..", "...#...", "......."};
        for (int y = 0; y < heart.length; y++) for (int x = 0; x < 7; x++) if (heart[y].charAt(x) == '#') set(image, 7 + x, 2 + y, RED);
    }

    private static void alert(NativeImage image, boolean on) {
        int colour = on ? RED : AMBER;
        for (int y = 1; y <= 6; y++) { set(image, 9, y, colour); set(image, 10, y, colour); }
        set(image, 9, 8, colour); set(image, 10, 8, colour);
        if (on) for (int y = 2; y <= 7; y++) { set(image, 3, y, DIM); set(image, 16, y, DIM); }
    }

    // ---------- reactions: little pictures for what its owner does ----------

    private static final int DROP = 0xFF5AB4FF, WHITE = 0xFFFFFFFF;

    /** Happy eyes and a little pink heart: yum. */
    private static void yum(NativeImage image, boolean beat) {
        for (int x : new int[] {4, 9}) { set(image, x, 3, GLOW); set(image, x + 1, 2, GLOW); set(image, x + 2, 3, GLOW); } // ^ ^
        String[] heart = {".#.#.", "#####", ".###.", "..#.."};
        int dy = beat ? 0 : 1;
        for (int y = 0; y < 4; y++) for (int x = 0; x < 5; x++) if (heart[y].charAt(x) == '#') set(image, 13 + x, 4 + y + dy, PINK);
    }

    /** One eye up, and a big question mark. */
    private static void curious(NativeImage image, boolean tilt) {
        set(image, 4, tilt ? 3 : 4, GLOW); set(image, 5, tilt ? 3 : 4, GLOW); set(image, 8, 4, GLOW); set(image, 9, 4, GLOW);
        String glyph = FONT.get('?');
        for (int g = 0; g < 15; g++) { // twice the size: each pixel of the glyph is two by two
            if (glyph.charAt(g) != '#') continue;
            int x = 12 + (g % 3) * 2, y = (g / 3) * 2;
            set(image, x, y, AMBER); set(image, x + 1, y, AMBER); set(image, x, y + 1, AMBER); set(image, x + 1, y + 1, AMBER);
        }
    }

    /** Squeezed eyes and drops of sweat. */
    private static void sweat(NativeImage image, boolean drip) {
        set(image, 4, 3, GLOW); set(image, 5, 4, GLOW); set(image, 4, 5, GLOW); // >
        set(image, 10, 3, GLOW); set(image, 9, 4, GLOW); set(image, 10, 5, GLOW); // <
        int dy = drip ? 0 : 2;
        set(image, 15, 1 + dy, DROP); set(image, 14, 2 + dy, DROP); set(image, 15, 2 + dy, DROP); set(image, 16, 2 + dy, DROP); set(image, 15, 3 + dy, DROP);
        set(image, 12, 5, DROP); set(image, 12, 6, DROP);
    }

    /** Sad eyes, a tear running down. */
    private static void tear(NativeImage image, boolean fall) {
        for (int x = 5; x <= 7; x++) set(image, x, 3, DIM);
        for (int x = 12; x <= 14; x++) set(image, x, 3, DIM);
        set(image, 6, 4, GLOW); set(image, 13, 4, GLOW);
        int y = fall ? 5 : 7;
        set(image, 6, y, DROP); set(image, 6, y + 1, DROP); set(image, 5, y + 1, DROP); set(image, 7, y + 1, DROP);
        for (int x = 8; x <= 11; x++) set(image, x, 8, DIM); // a wobbly mouth
    }

    /** Sparkles: well done! */
    private static void sparkle(NativeImage image, boolean twinkle) {
        int[][] stars = {{4, 3}, {10, 5}, {15, 2}};
        for (int i = 0; i < stars.length; i++) {
            int x = stars[i][0], y = stars[i][1], c = (i % 2 == 0) == twinkle ? WHITE : AMBER;
            set(image, x, y, c); set(image, x - 1, y, AMBER); set(image, x + 1, y, AMBER); set(image, x, y - 1, AMBER); set(image, x, y + 1, AMBER);
        }
    }

    // ---------- news in the Bleepedia ----------

    private static void news(NativeImage image, boolean on) {
        int colour = on ? AMBER : PINK;
        for (int y = 1; y <= 5; y++) { set(image, 9, y, colour); set(image, 10, y, colour); }
        set(image, 9, 7, colour); set(image, 10, 7, colour); set(image, 9, 8, colour); set(image, 10, 8, colour);
    }

    /** The angle from where it faces to its home, in degrees clockwise, or NaN without a home here. */
    public static float homeAngle(float yaw, double dx, double dz) {
        if (dx * dx + dz * dz < 0.5) return 0;
        float towards = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
        return Mth.wrapDegrees(towards - yaw);
    }

    /** For a display that shows its face after all. */
    public static boolean shows(Display display) {
        return display != Display.FACE;
    }
}
