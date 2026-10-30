package io.hookdev.bleep.client;

import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Bond;
import io.hookdev.bleep.entity.Trick;
import io.hookdev.bleep.network.BleepCommand;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Play with your bleep (the ball on its info screen): hide and seek either way round, and tricks, some for a closer bond. */
public class PlayScreen extends Screen {
    private static final int W = 232, H = 156, DIM = 0xFF8A93A0;
    private final BleepEntity bleep;
    private int left, top;

    public PlayScreen(BleepEntity bleep) {
        super(Component.translatable("screen.bleep.play"));
        this.bleep = bleep;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.bleep.play.hide"), b -> go(BleepCommand.HIDE)).bounds(left + 10, top + 40, 104, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.bleep.play.hide.tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.bleep.play.seek"), b -> go(BleepCommand.SEEK)).bounds(left + 118, top + 40, 104, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.bleep.play.seek.tip"))).build());
        Trick[] tricks = Trick.values();
        for (int i = 0; i < tricks.length; i++) {
            Trick trick = tricks[i];
            Button button = Button.builder(Component.translatable("trick.bleep." + trick.id()), b -> go(BleepCommand.TRICK + trick.ordinal()))
                    .bounds(left + 10 + (i % 3) * 72, top + 82 + (i / 3) * 24, 68, 20).build();
            button.active = Bond.level(bleep.bond()) >= trick.level;
            if (!button.active) button.setTooltip(Tooltip.create(Component.translatable("screen.bleep.play.learns", Component.translatable(Bond.key(trick.level * 25)))));
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.bleep.back"), b -> McVersionClient.setScreen(new BleepScreen(bleep))).bounds(left + 82, top + H - 28, 68, 20).build());
    }

    /** Off it goes: the screen closes, so you can watch (or run). */
    private void go(int command) {
        ClientPacketDistributor.sendToServer(new BleepCommand(bleep.getId(), command));
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        var player = minecraft.player;
        if (bleep.isRemoved() || player == null || bleep.distanceToSqr(player) > 16 * 16) onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        frame(g, font, left, top, W, H, Component.translatable("screen.bleep.play.title", bleep.getDisplayName()));
        g.text(font, Component.translatable("screen.bleep.play.hide_and_seek"), left + 10, top + 28, DIM, false);
        g.text(font, Component.translatable("screen.bleep.play.tricks"), left + 10, top + 70, DIM, false);
        super.extractRenderState(g, mouseX, mouseY, partialTicks);
    }

    /** The white shell round a dark panel, and a title. */
    static void frame(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int left, int top, int w, int h, Component title) {
        g.fill(left, top, left + w, top + h, 0xFFB7C0C9);
        g.fill(left + 1, top + 1, left + w - 1, top + h - 1, 0xFFEEF1F4);
        g.fill(left + 4, top + 4, left + w - 4, top + h - 4, 0xFF15181E);
        g.text(font, title, left + 10, top + 10, 0xFFEEF1F4, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
