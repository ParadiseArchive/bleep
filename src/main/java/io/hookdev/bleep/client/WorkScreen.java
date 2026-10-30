package io.hookdev.bleep.client;

import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.network.BleepCommand;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Its work while it stays (the hoe on its info screen): how far it goes from its spot, and its jobs: farmer (harvests
 * ripe crops and plants them again), guard (an alarm when monsters come at night), tidy up (its storage into a chest).
 */
public class WorkScreen extends Screen {
    private static final int W = 232, H = 178, TEXT = 0xFFEEF1F4, DIM = 0xFF8A93A0, GLOW = 0xFF8FF7FF;
    private static final int[] JOBS = {BleepEntity.JOB_FARMER, BleepEntity.JOB_GUARD, BleepEntity.JOB_TIDY};
    private static final String[] NAMES = {"farmer", "guard", "tidy"};
    private final BleepEntity bleep;
    private final Button[] toggles = new Button[JOBS.length];
    private int left, top;

    public WorkScreen(BleepEntity bleep) {
        super(Component.translatable("screen.bleep.work"));
        this.bleep = bleep;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> send(BleepCommand.RADIUS + bleep.stayRadius() - 1)).bounds(left + 140, top + 26, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> send(BleepCommand.RADIUS + bleep.stayRadius() + 1)).bounds(left + 196, top + 26, 20, 16).build());
        for (int i = 0; i < JOBS.length; i++) {
            int job = JOBS[i];
            toggles[i] = addRenderableWidget(Button.builder(Component.empty(), b -> send(BleepCommand.JOB + job)).bounds(left + 10, top + 52 + i * 32, 84, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.bleep.back"), b -> McVersionClient.setScreen(new BleepScreen(bleep))).bounds(left + 82, top + H - 28, 68, 20).build());
    }

    private void send(int command) {
        ClientPacketDistributor.sendToServer(new BleepCommand(bleep.getId(), command));
    }

    @Override
    public void tick() {
        super.tick();
        var player = minecraft.player;
        if (bleep.isRemoved() || player == null || bleep.distanceToSqr(player) > 16 * 16 || !bleep.isOrderedToSit()) onClose();
        for (int i = 0; i < JOBS.length; i++) {
            toggles[i].setMessage(Component.translatable("screen.bleep.work." + NAMES[i]).append(": ")
                    .append(Component.translatable(bleep.doing(JOBS[i]) ? "screen.bleep.on" : "screen.bleep.off")));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        PlayScreen.frame(g, font, left, top, W, H, Component.translatable("screen.bleep.work.title", bleep.getDisplayName()));
        g.text(font, Component.translatable("screen.bleep.work.radius"), left + 10, top + 30, DIM, false);
        String radius = bleep.stayRadius() + "";
        g.text(font, radius, left + 178 - font.width(radius) / 2, top + 30, GLOW, false);
        for (int i = 0; i < JOBS.length; i++) {
            g.textWithWordWrap(font, Component.translatable("screen.bleep.work." + NAMES[i] + ".tip"), left + 100, top + 52 + i * 32, W - 110, bleep.doing(JOBS[i]) ? TEXT : DIM);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
