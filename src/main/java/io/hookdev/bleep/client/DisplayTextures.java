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
            default -> "face";
        };
    }

    private static void draw(NativeImage image, BleepRenderState state) {
        switch (state.display) {
            case NAME -> ticker(image, state.name, (int) (state.ageInTicks / 3));
            case GAME -> ticTacToe(image, (int) (state.ageInTicks / 15), state.id);
            case HOME -> arrow(image, arrow(state.homeAngle));
            case WANT -> cookie(image, (int) (state.ageInTicks / 10) % 2 == 0);
            case NEWS -> news(image, (int) (state.ageInTicks / 8) % 2 == 0);
            case BALL -> ball(image, (int) (state.ageInTicks / 10) % 2 == 0);
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
