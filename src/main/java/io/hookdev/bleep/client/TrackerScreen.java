package io.hookdev.bleep.client;

import io.hookdev.bleep.BleepSightings;
import io.hookdev.bleep.network.TrackerData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The Bleep Tracker's little screen: every bleep of yours, its colour, name, battery and what it is doing, and how far
 * and which way it is from you (the arrow turns as you turn). Scroll for more than eight.
 */
public class TrackerScreen extends Screen {
    private static final int W = 240, ROW = 24, ROWS = 8;
    private static final int SHELL = 0xFFEEF1F4, SHELL_EDGE = 0xFFB7C0C9, PANEL = 0xFF15181E, CARD = 0xFF22262E, SCREEN = 0xFF08090C;
    private static final int TEXT = 0xFFEEF1F4, DIM = 0xFF8A93A0, GLOW = 0xFF8FF7FF, AMBER = 0xFFFFB547;
    /** Which way, in eight steps clockwise from straight ahead. */
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private final TrackerData data;
    private int first, left, top, h;

    public TrackerScreen(TrackerData data) {
        super(Component.translatable("item.bleep.tracker"));
        this.data = data;
    }

    @Override
    protected void init() {
        h = 34 + Math.max(1, Math.min(ROWS, data.bleeps().size())) * ROW;
        left = (width - W) / 2;
        top = (height - h) / 2;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        first = Math.max(0, Math.min(Math.max(0, data.bleeps().size() - ROWS), first - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(g, mouseX, mouseY, partialTicks);
        g.fill(left, top, left + W, top + h, SHELL_EDGE);
        g.fill(left + 1, top + 1, left + W - 1, top + h - 1, SHELL);
        g.fill(left + 4, top + 4, left + W - 4, top + h - 4, PANEL);
        g.text(font, title, left + 10, top + 10, TEXT, true);
        String count = data.bleeps().size() + "";
        g.text(font, count, left + W - 10 - font.width(count), top + 10, GLOW, false);
        if (data.bleeps().isEmpty()) {
            g.text(font, Component.translatable("screen.bleep.tracker.none"), left + 10, top + 30, DIM, false);
            return;
        }
        var player = minecraft.player;
        String here = minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
        int y = top + 26;
        for (int i = first; i < Math.min(data.bleeps().size(), first + ROWS); i++, y += ROW) {
            BleepSightings.Seen bleep = data.bleeps().get(i);
            int x = left + 10;
            g.fill(x, y, left + W - 10, y + ROW - 3, CARD);
            g.fill(x + 3, y + 3, x + 18, y + 18, SCREEN); // its colour, as a little screen
            g.fill(x + 5, y + 5, x + 16, y + 16, bleep.colour() == -1 ? SHELL : 0xFF000000 | bleep.colour());
            g.text(font, bleep.name(), x + 22, y + 2, TEXT, false);
            g.text(font, Component.translatable(bleep.activity()), x + 22, y + 11, DIM, false);
            // its battery: a little bar
            int bx = x + 128, bw = 36;
            g.fill(bx, y + 4, bx + bw, y + 10, SCREEN);
            g.fill(bx + 1, y + 5, bx + 1 + Math.round((bw - 2) * Mth.clamp(bleep.battery() / 100f, 0, 1)), y + 9, bleep.battery() < 20 ? AMBER : GLOW);
            g.text(font, bleep.battery() + "%", bx, y + 11, DIM, false);
            // how far, and which way
            String where;
            if (player == null || !bleep.dimension().equals(here)) {
                where = Component.translatable("screen.bleep.tracker.elsewhere").getString();
            } else {
                double dx = bleep.x() + 0.5 - player.getX(), dz = bleep.z() + 0.5 - player.getZ();
                float angle = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90 - player.getYRot(); // 0: straight ahead
                int step = Math.floorMod(Math.round(angle / 45f), 8); // yaw turns clockwise: more is to the right
                where = ARROWS[step] + " " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " m";
            }
            g.text(font, where, left + W - 14 - font.width(where), y + 6, GLOW, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
