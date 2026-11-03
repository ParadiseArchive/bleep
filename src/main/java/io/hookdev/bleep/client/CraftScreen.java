package io.hookdev.bleep.client;

import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Memory;
import io.hookdev.bleep.network.BleepCommand;
import io.hookdev.bleep.network.CraftData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.input.MouseButtonEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * What a best friend learned to make by watching you craft (owner, 31 Oct 2026): six to a row, each with how many it
 * could make from its storage now. Click one: it makes one; shift-click: as many as it can.
 */
public class CraftScreen extends Screen {
    private static final int W = 232, H = 168, COLUMNS = 6, CELL = 34, DIM = 0xFF8A93A0, GLOW = 0xFF8FF7FF, TEXT = 0xFFEEF1F4;
    private final BleepEntity bleep;
    private final CraftData data;
    private int left, top;

    public CraftScreen(BleepEntity bleep, CraftData data) {
        super(Component.translatable("screen.bleep.craft"));
        this.bleep = bleep;
        this.data = data;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.bleep.back"), b -> McVersionClient.setScreen(new BleepScreen(bleep))).bounds(left + 82, top + H - 28, 68, 20).build());
    }

    private int cellX(int i) {
        return left + 14 + (i % COLUMNS) * CELL;
    }

    private int cellY(int i) {
        return top + 28 + (i / COLUMNS) * CELL;
    }

    private int over(double mx, double my) {
        for (int i = 0; i < data.crafts().size(); i++) {
            if (mx >= cellX(i) && mx < cellX(i) + 30 && my >= cellY(i) && my < cellY(i) + 30) return i;
        }
        return -1;
    }

    static ItemStack stack(String id, int count) {
        Identifier key = Identifier.tryParse(id);
        Item item = key == null ? null : BuiltInRegistries.ITEM.getValue(key);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, count));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int i = over(event.x(), event.y());
        if (i >= 0 && data.makeable().get(i) > 0) {
            ClientPacketDistributor.sendToServer(new BleepCommand(bleep.getId(), (event.hasShiftDown() ? BleepCommand.CRAFT_ALL : BleepCommand.CRAFT) + i));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void tick() {
        super.tick();
        var player = minecraft.player;
        if (bleep.isRemoved() || player == null || bleep.distanceToSqr(player) > 16 * 16) onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        PlayScreen.frame(g, font, left, top, W, H, Component.translatable("screen.bleep.craft.title", bleep.getDisplayName()));
        List<Memory.Craft> crafts = data.crafts();
        if (crafts.isEmpty()) {
            int y = top + 30;
            for (var line : font.split(Component.translatable("screen.bleep.craft.none"), W - 28)) {
                g.text(font, line, left + 14, y, DIM, false);
                y += 10;
            }
        }
        for (int i = 0; i < crafts.size(); i++) {
            int x = cellX(i), y = cellY(i), n = data.makeable().get(i);
            g.fill(x, y, x + 30, y + 30, n > 0 ? 0xFF2C313A : 0xFF1B1E24);
            g.item(stack(crafts.get(i).result(), crafts.get(i).count()), x + 7, y + 4);
            g.itemDecorations(font, stack(crafts.get(i).result(), crafts.get(i).count()), x + 7, y + 4);
            g.text(font, "x" + n, x + 30 - font.width("x" + n) - 1, y + 21, n > 0 ? GLOW : DIM, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTicks);
        int i = over(mouseX, mouseY);
        if (i >= 0) g.setComponentTooltipForNextFrame(font, tooltip(crafts.get(i), data.makeable().get(i)), mouseX, mouseY);
    }

    /** What it makes, what that takes, and how many it could make now. */
    private List<Component> tooltip(Memory.Craft craft, int makeable) {
        List<Component> lines = new ArrayList<>();
        lines.add(stack(craft.result(), craft.count()).getHoverName().copy().withColor(TEXT));
        Map<String, Integer> needs = new LinkedHashMap<>();
        for (String item : craft.items()) if (!item.isEmpty()) needs.merge(item, 1, Integer::sum);
        needs.forEach((item, count) -> lines.add(Component.literal(count + " x ").append(stack(item, 1).getHoverName()).withColor(DIM)));
        lines.add(makeable > 0 ? Component.translatable("screen.bleep.craft.can", makeable).withColor(GLOW) : Component.translatable("screen.bleep.craft.missing").withColor(DIM));
        return lines;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
