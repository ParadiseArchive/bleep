package io.hookdev.bleep.client;

import io.hookdev.bleep.BleepContent;
import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Chip;
import io.hookdev.bleep.network.BleepCommand;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Its chips (the chip on its info screen): each kind with what it does, whether it is in, and a Remove button that
 * takes it out again (you get it back). Installing is a right-click on the bleep with the chip.
 */
public class ChipScreen extends Screen {
    private static final int W = 232, H = 170, ROW = 36, TEXT = 0xFFEEF1F4, DIM = 0xFF8A93A0, GLOW = 0xFF8FF7FF;
    private final BleepEntity bleep;
    private final Button[] remove = new Button[Chip.values().length];
    private int left, top;

    public ChipScreen(BleepEntity bleep) {
        super(Component.translatable("screen.bleep.chips"));
        this.bleep = bleep;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        for (Chip chip : Chip.values()) {
            int i = chip.ordinal();
            remove[i] = addRenderableWidget(Button.builder(Component.translatable("screen.bleep.chips.remove"),
                    b -> ClientPacketDistributor.sendToServer(new BleepCommand(bleep.getId(), BleepCommand.REMOVE_CHIP + i))).bounds(left + W - 70, top + 30 + i * ROW, 60, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.bleep.back"), b -> McVersionClient.setScreen(new BleepScreen(bleep))).bounds(left + 82, top + H - 28, 68, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        var player = minecraft.player;
        if (bleep.isRemoved() || player == null || bleep.distanceToSqr(player) > 16 * 16) onClose();
        for (Chip chip : Chip.values()) remove[chip.ordinal()].visible = bleep.has(chip);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        PlayScreen.frame(g, font, left, top, W, H, Component.translatable("screen.bleep.chips.title", bleep.getDisplayName()));
        for (Chip chip : Chip.values()) {
            int y = top + 28 + chip.ordinal() * ROW;
            boolean in = bleep.has(chip);
            g.fill(left + 10, y - 2, left + W - 10, y + ROW - 6, 0xFF22262E);
            g.item(new ItemStack(BleepContent.CHIPS.get(chip).get()), left + 14, y + 2);
            g.text(font, Component.translatable("item.bleep." + chip.id()), left + 36, y + 1, in ? TEXT : DIM, false);
            g.textWithWordWrap(font, Component.translatable("screen.bleep.chips." + chip.name().toLowerCase(java.util.Locale.ROOT)), left + 36, y + 12, W - 120, DIM);
            if (!in) g.text(font, Component.translatable("screen.bleep.chips.not_in"), left + W - 70, y + 6, DIM, false);
            else g.text(font, Component.translatable("screen.bleep.chips.in"), left + W - 70 - font.width(Component.translatable("screen.bleep.chips.in")) - 6, y + 6, GLOW, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
