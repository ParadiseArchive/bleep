package io.hookdev.bleep.client;

import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Bond;
import io.hookdev.bleep.entity.Obsession;
import io.hookdev.bleep.network.BleepCommand;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * A bleep's info screen, opened by right-clicking your own bleep: its face as on its display, its name (type a new
 * one), mood and what it is doing, its battery and bond with you, its favourite item (??? until you find out), what it
 * is thinking, the Follow, Stay and Go Home buttons, and down the right its Bleepedia, its storage and its magnet. It
 * follows the bleep live and closes when the bleep is gone or too far away.
 */
public class BleepScreen extends Screen {
    private static final int W = 260, H = 204;
    private static final int SHELL = 0xFFEEF1F4, SHELL_EDGE = 0xFFB7C0C9, PANEL = 0xFF15181E, SCREEN = 0xFF08090C;
    private static final int TEXT = 0xFFEEF1F4, DIM = 0xFF8A93A0, GLOW = 0xFF8FF7FF, AMBER = 0xFFFFB547, PINK = 0xFFFF4F8B;
    /** Where the display sits in the face textures (128 x 128), and how big it is drawn here. */
    private static final float FACE_U = 18 / 128f, FACE_V = 18 / 128f, FACE_W = 20 / 128f, FACE_H = 10 / 128f;
    private static final int FACE_SCALE = 4;
    private static final net.minecraft.resources.Identifier MAGNET = io.hookdev.bleep.Bleep.id("textures/gui/magnet.png");

    private final BleepEntity bleep;
    private int left, top;
    private Button goHome, torch, book, chest, magnet, play, work, chips, way, yes, no;
    private net.minecraft.client.gui.components.EditBox name;

    public BleepScreen(BleepEntity bleep) {
        super(bleep.getDisplayName());
        this.bleep = bleep;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int y = top + H - 28, w = 76;
        addRenderableWidget(Button.builder(Component.translatable("screen.bleep.follow"), b -> send(BleepCommand.FOLLOW)).bounds(left + 10, y, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.bleep.stay"), b -> send(BleepCommand.STAY)).bounds(left + 92, y, w, 20).build());
        goHome = addRenderableWidget(Button.builder(Component.translatable("screen.bleep.go_home"), b -> send(BleepCommand.GO_HOME)).bounds(left + 174, y, w, 20).build());
        // down the right: its Bleepedia (a ! while there is news), its storage, its magnet
        book = addRenderableWidget(Button.builder(Component.empty(), b -> send(BleepCommand.OPEN_BOOK)).bounds(left + W - 32, top + 8, 22, 22)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.bleep.bleepedia"))).build());
        chest = addRenderableWidget(Button.builder(Component.empty(), b -> send(BleepCommand.OPEN_STORAGE)).bounds(left + W - 32, top + 32, 22, 22)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.bleep.storage"))).build());
        magnet = addRenderableWidget(Button.builder(Component.empty(), b -> send(BleepCommand.MAGNET)).bounds(left + W - 32, top + 56, 22, 22).build());
        // and two more screens: games and tricks, and its work while it stays
        play = addRenderableWidget(Button.builder(Component.empty(), b -> McVersionClient.setScreen(new PlayScreen(bleep))).bounds(left + W - 32, top + 80, 22, 22)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.bleep.play"))).build());
        work = addRenderableWidget(Button.builder(Component.empty(), b -> McVersionClient.setScreen(new WorkScreen(bleep))).bounds(left + W - 32, top + 104, 22, 22).build());
        chips = addRenderableWidget(Button.builder(Component.empty(), b -> McVersionClient.setScreen(new ChipScreen(bleep))).bounds(left + W - 32, top + 128, 22, 22).build());
        way = addRenderableWidget(Button.builder(Component.empty(), b -> { send(BleepCommand.SHOW_WAY); onClose(); }).bounds(left + W - 32, top + 152, 22, 22)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.bleep.show_way"))).build());
        // its name: click it and type a new one (Enter, or closing the screen, saves it)
        name = addRenderableWidget(new net.minecraft.client.gui.components.EditBox(font, left + 100, top + 11, 120, 10, Component.translatable("screen.bleep.name")));
        name.setBordered(false);
        name.setMaxLength(io.hookdev.bleep.network.BleepRename.MAX);
        name.setTextColor(TEXT);
        name.setValue(currentName());
        name.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.bleep.rename")));
        // only while it asks something: Yes and No, in the corner of the thought box
        yes = addRenderableWidget(Button.builder(Component.translatable("screen.bleep.yes"), b -> send(BleepCommand.ANSWER_YES)).bounds(left + 144, top + 132, 36, 14).build());
        no = addRenderableWidget(Button.builder(Component.translatable("screen.bleep.no"), b -> send(BleepCommand.ANSWER_NO)).bounds(left + 182, top + 132, 36, 14).build());
        // only while it carries a torch: take it back
        torch = addRenderableWidget(Button.builder(Component.translatable("screen.bleep.take_torch"), b -> send(BleepCommand.TAKE_TORCH)).bounds(left + W - 112, top + 92, 70, 16).build());
    }

    private void send(int command) {
        ClientPacketDistributor.sendToServer(new BleepCommand(bleep.getId(), command));
    }

    private String currentName() {
        return bleep.getCustomName() == null ? BleepEntity.DEFAULT_NAME : bleep.getCustomName().getString();
    }

    /** Sends the typed name, if it changed. */
    private void saveName() {
        if (name == null || name.getValue().trim().equals(currentName())) return;
        ClientPacketDistributor.sendToServer(new io.hookdev.bleep.network.BleepRename(bleep.getId(), name.getValue()));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (name.isFocused() && event.isConfirmation()) {
            saveName();
            name.setFocused(false);
            setFocused(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        saveName();
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        var player = minecraft.player;
        if (bleep.isRemoved() || player == null || bleep.distanceToSqr(player) > 16 * 16) onClose();
        goHome.active = bleep.home() != null;
        torch.visible = bleep.getOffhandItem().is(net.minecraft.world.item.Items.TORCH);
        yes.visible = no.visible = !bleep.question().isEmpty();
        net.minecraft.network.chat.MutableComponent installed = Component.translatable("screen.bleep.chips");
        for (io.hookdev.bleep.entity.Chip chip : io.hookdev.bleep.entity.Chip.values()) {
            if (bleep.has(chip)) installed.append("\n- ").append(Component.translatable("item.bleep." + chip.id()));
        }
        chips.setTooltip(net.minecraft.client.gui.components.Tooltip.create(bleep.chips() == 0 ? Component.translatable("screen.bleep.chips.none") : installed));
        work.active = bleep.isOrderedToSit() && !bleep.onHome();
        work.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(work.active ? "screen.bleep.work" : "screen.bleep.work.stay_first")));
        magnet.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(bleep.magnet() ? "screen.bleep.magnet.on" : "screen.bleep.magnet.off")));
        if (!name.isFocused() && !name.getValue().equals(currentName())) name.setValue(currentName());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        // the white shell of a bleep round a dark panel
        g.fill(left, top, left + W, top + H, SHELL_EDGE);
        g.fill(left + 1, top + 1, left + W - 1, top + H - 1, SHELL);
        g.fill(left + 4, top + 4, left + W - 4, top + H - 4, PANEL);

        // its face, as on its display
        int fx = left + 10, fy = top + 10, fw = 20 * FACE_SCALE, fh = 10 * FACE_SCALE;
        g.fill(fx - 2, fy - 2, fx + fw + 2, fy + fh + 2, SHELL_EDGE);
        g.fill(fx, fy, fx + fw, fy + fh, SCREEN);
        g.blit(BleepRenderer.face(bleep.emotion(), bleep.tickCount + partialTicks, bleep.getId()), fx, fy, fx + fw, fy + fh,
                FACE_U, FACE_U + FACE_W, FACE_V, FACE_V + FACE_H);

        int tx = fx + fw + 10;
        g.fill(tx, fy + 10, tx + 120, fy + 11, name.isFocused() ? GLOW : 0xFF2C313A); // a line under its name: you can type there
        g.text(font, Component.translatable("screen.bleep.mood").append(": ").append(Component.translatable("emotion.bleep." + bleep.emotion().texture())), tx, fy + 15, GLOW, false);
        g.text(font, Component.translatable(bleep.activity().key()), tx, fy + 27, DIM, false);

        // battery and bond
        int by = fy + fh + 12;
        float battery = bleep.battery();
        bar(g, by, Component.translatable("screen.bleep.battery"), battery / BleepEntity.FULL, battery < BleepEntity.TIRED ? AMBER : GLOW,
                Component.literal(Math.round(battery) + "%"));
        int bond = bleep.bond();
        bar(g, by + 14, Component.translatable("screen.bleep.bond"), bond / (float) Bond.MAX, PINK, Component.translatable(Bond.key(bond)));

        // its favourite item, once you know it
        int oy = by + 30;
        g.text(font, Component.translatable("screen.bleep.favourite"), left + 10, oy + 4, DIM, false);
        Obsession favourite = bleep.obsession();
        if (favourite != null && bleep.revealed()) {
            g.item(favourite.iconStack(), left + 62, oy);
            g.text(font, font.substrByWidth(Component.translatable(favourite.nameKey()), 62).getString(), left + 82, oy + 4, TEXT, false);
        } else {
            g.text(font, "???", left + 62, oy + 4, TEXT, false);
        }

        // what it is thinking
        int qy = oy + 22, qh = 34;
        g.fill(left + 10, qy, left + W - 38, qy + qh, 0xFF22262E);
        if (!bleep.question().isEmpty()) { // a question: room for the buttons
            g.textWithWordWrap(font, Component.translatable("question.bleep." + bleep.question()), left + 16, qy + 4, 124, GLOW);
        } else if (!bleep.thought().isEmpty()) {
            // a mob from another mod has no line of its own
            String key = Language.getInstance().has(bleep.thought()) ? bleep.thought() : "thought.bleep.unknown_mob";
            Component thought = Component.literal("“").append(Component.translatable(key)).append("”");
            g.textWithWordWrap(font, thought, left + 16, qy + 6, W - 60, TEXT);
        }

        super.extractRenderState(g, mouseX, mouseY, partialTicks);
        g.item(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK), book.getX() + 3, book.getY() + 3);
        g.item(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST), chest.getX() + 3, chest.getY() + 3);
        g.blit(MAGNET, magnet.getX() + 3, magnet.getY() + 3, magnet.getX() + 19, magnet.getY() + 19, 0, 1, 0, 1);
        g.item(new net.minecraft.world.item.ItemStack(io.hookdev.bleep.BleepContent.BALL.get()), play.getX() + 3, play.getY() + 3);
        g.item(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HOE), work.getX() + 3, work.getY() + 3);
        g.item(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COMPASS), way.getX() + 3, way.getY() + 3);
        g.item(new net.minecraft.world.item.ItemStack(io.hookdev.bleep.BleepContent.CHIPS.get(io.hookdev.bleep.entity.Chip.SPEED).get()), chips.getX() + 3, chips.getY() + 3);
        if (bleep.chips() != 0) g.text(font, Integer.bitCount(bleep.chips()) + "", chips.getX() + 15, chips.getY() + 13, GLOW, true); // how many are in
        if (bleep.magnet()) g.fill(magnet.getX() + 3, magnet.getY() + 19, magnet.getX() + 19, magnet.getY() + 20, GLOW); // on: a glowing line under it
        if (bleep.news() && (bleep.tickCount / 10) % 2 == 0) g.text(font, "!", book.getX() + 16, book.getY() + 1, AMBER, true);
    }

    /** A labelled bar: the label, the bar filled this far (0 to 1), and a value after it. */
    private void bar(GuiGraphicsExtractor g, int y, Component label, float fill, int colour, Component value) {
        int x = left + 10, bx = left + 62, bw = 104;
        g.text(font, label, x, y, DIM, false);
        g.fill(bx, y, bx + bw, y + 8, SCREEN);
        g.fill(bx + 1, y + 1, bx + 1 + Math.round((bw - 2) * Math.max(0, Math.min(1, fill))), y + 7, colour);
        g.text(font, value, bx + bw + 6, y, TEXT, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
