package io.hookdev.bleep.client;

import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Memory;
import io.hookdev.bleep.network.BleepediaData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * A bleep's Bleepedia, opened from the book on its info screen: the mobs it met (their face, name and its remark; ???
 * for the ones it has not), the biomes it visited, the special blocks it found, its stats and its diary. Finds since
 * its owner last looked are marked NEW. A remark too long for its row ends in ...: click it to read it all, click again
 * to close it.
 */
public class BleepediaScreen extends Screen {
    private static final int W = 300, H = 214, ROW = 30, TOP = 52;
    private static final int SHELL = 0xFFEEF1F4, SHELL_EDGE = 0xFFB7C0C9, PANEL = 0xFF15181E, CARD = 0xFF22262E, SCREEN = 0xFF08090C;
    private static final int TEXT = 0xFFEEF1F4, DIM = 0xFF8A93A0, GLOW = 0xFF8FF7FF, AMBER = 0xFFFFB547;
    private static final String[] TABS = {"mobs", "biomes", "finds", "about", "diary"};

    /** One line on a page: an icon (a mob, a block, a colour or nothing), a title, a text, and whether it is found and new. */
    private record Row(@Nullable EntityType<?> mob, @Nullable ItemStack item, int colour, Component title, Component text, boolean known, boolean fresh) {}

    private final BleepediaData data;
    private final Map<EntityType<?>, LivingEntity> faces = new HashMap<>();
    /** Mobs that would not be drawn: a ? for them. */
    private final java.util.Set<EntityType<?>> broken = new java.util.HashSet<>();
    private final List<List<Row>> pages = new ArrayList<>();
    private final String[] counts = new String[TABS.length];
    private int tab, first;
    /** The row opened up to show its whole text, or -1; and where each row on screen was drawn (for clicks). */
    private int open = -1;
    private final List<int[]> drawn = new ArrayList<>();
    private int left, top;

    public BleepediaScreen(BleepediaData data) {
        super(Component.translatable("screen.bleep.bleepedia"));
        this.data = data;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        for (int i = 0; i < TABS.length; i++) {
            int t = i;
            Button button = Button.builder(Component.translatable("screen.bleep.tab." + TABS[i]), b -> { tab = t; first = 0; open = -1; rebuildWidgets(); })
                    .bounds(left + 8 + i * 57, top + 24, 55, 16).build();
            button.active = i != tab;
            addRenderableWidget(button);
        }
        if (pages.isEmpty()) build();
    }

    private boolean fresh(Long found) {
        return found != null && found > data.lastOpened();
    }

    /** Makes every page from what the bleep sent. */
    private void build() {
        // mobs: every kind it has a remark for, plus any other it met
        List<Row> mobs = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            String key = BleepEntity.mobThought(id.toString());
            Long found = data.mobs().get(id.toString());
            if (found == null && !Language.getInstance().has(key)) continue;
            boolean known = found != null;
            Component text = Language.getInstance().has(key) ? Component.translatable(key) : Component.translatable("thought.bleep.unknown_mob");
            mobs.add(new Row(type, null, 0, known ? type.getDescription() : Component.literal("???"), known ? quote(text) : Component.empty(), known, fresh(found)));
        }
        // the ones it met first, in the order it met them; then the ones still to meet, A to Z
        Comparator<Row> met = Comparator.comparing((Row r) -> data.mobs().getOrDefault(BuiltInRegistries.ENTITY_TYPE.getKey(r.mob()).toString(), Long.MAX_VALUE));
        mobs.sort(met.thenComparing(r -> r.mob().getDescription().getString()));
        pages.add(mobs);
        counts[0] = data.mobs().size() + " / " + mobs.size();

        // biomes: every biome there is (minus the void)
        List<Row> biomes = new ArrayList<>();
        var level = minecraft.level;
        if (level != null) {
            level.registryAccess().lookupOrThrow(Registries.BIOME).listElementIds().forEach(key -> {
                Identifier id = key.identifier();
                if (id.getPath().equals("the_void")) return;
                Long found = data.biomes().get(id.toString());
                String remark = "biome.bleep." + (id.getNamespace().equals("minecraft") ? id.getPath() : id.getNamespace() + "." + id.getPath());
                Component name = Component.translatable("biome." + id.getNamespace() + "." + id.getPath());
                Component text = Language.getInstance().has(remark) ? Component.translatable(remark) : Component.translatable("biome.bleep.unknown");
                biomes.add(new Row(null, null, colour(id.toString()), found != null ? name : Component.literal("???"), found != null ? quote(text) : Component.empty(), found != null, fresh(found)));
            });
        }
        biomes.sort(Comparator.comparing((Row r) -> !r.known()).thenComparing(r -> r.title().getString()));
        pages.add(biomes);
        counts[1] = data.biomes().size() + " / " + biomes.size();

        // special blocks
        List<Row> finds = new ArrayList<>();
        for (String find : Memory.FINDS) {
            Long found = data.finds().get("minecraft:" + find);
            var block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(find));
            ItemStack icon = find.equals("lava") ? new ItemStack(Items.LAVA_BUCKET) : find.equals("nether_portal") ? new ItemStack(Items.OBSIDIAN) : new ItemStack(block);
            finds.add(new Row(null, icon, 0, found != null ? block.getName() : Component.literal("???"),
                    found != null ? quote(Component.translatable("find.bleep." + find)) : Component.empty(), found != null, fresh(found)));
        }
        pages.add(finds);
        counts[2] = data.finds().size() + " / " + finds.size();

        // about me: its stats
        List<Row> about = new ArrayList<>();
        long day = data.born() < 0 ? 1 : (data.now() - data.born()) / 24000 + 1;
        stat(about, "day", String.valueOf(day));
        stat(about, "together", String.valueOf(data.daysTogether()));
        stat(about, "distance", data.distance() + " blocks");
        stat(about, "mobs", counts[0]);
        stat(about, "biomes", counts[1]);
        stat(about, "finds", counts[2]);
        stat(about, "fights", String.valueOf(data.fightsWon()));
        stat(about, "empty", String.valueOf(data.timesEmpty()));
        about.add(new Row(null, null, 0, Component.translatable("screen.bleep.about.bond"), Component.translatable(data.bond()), true, false));
        about.add(new Row(null, null, 0, Component.translatable("screen.bleep.favourite"),
                data.favourite().isEmpty() ? Component.literal("???") : Component.translatable(data.favourite()), true, false));
        pages.add(about);
        counts[3] = "";

        // its diary, newest first
        List<Row> diary = new ArrayList<>();
        for (int i = data.diary().size() - 1; i >= 0; i--) {
            Memory.Entry e = data.diary().get(i);
            Component text = e.arg().isEmpty() ? Component.translatable(e.key()) : Component.translatable(e.key(), Component.translatable(e.arg()));
            diary.add(new Row(null, null, 0, Component.translatable("screen.bleep.day", e.day()), text, true, false));
        }
        pages.add(diary);
        counts[4] = String.valueOf(data.diary().size());
    }

    private static void stat(List<Row> rows, String key, String value) {
        rows.add(new Row(null, null, 0, Component.translatable("screen.bleep.about." + key), Component.literal(value), true, false));
    }

    private static Component quote(Component text) {
        return Component.literal("“").append(text).append("”");
    }

    /** A colour of its own for each biome, from its name. */
    private static int colour(String id) {
        float hue = (id.hashCode() & 0xFFFF) / 65535f;
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.45f, 0.7f) & 0xFFFFFF;
    }

    /** The bottom of the list, above the page count. */
    private int bottom() {
        return top + H - 12;
    }

    /** Where a row's text starts, and how wide it may be. */
    private int textX(int x) {
        return tab == 4 ? x + 6 : x + 30;
    }

    private int textWidth() {
        int x = left + 10;
        return W - 20 - (textX(x) - x) - 4;
    }

    private boolean tooLong(Row row) {
        return tab != 3 && font.width(row.text()) > textWidth();
    }

    /** A row is 30 high; an opened one grows with its lines; About me is a list of 14 high lines. */
    private int rowHeight(int i) {
        if (tab == 3) return 14;
        if (i != open) return ROW;
        return Math.max(ROW, 14 + font.split(pages.get(tab).get(i).text(), textWidth()).size() * 10 + 6);
    }

    /** How many rows from this one fit. */
    private int fitting(int from) {
        int y = top + TOP, n = 0;
        for (int i = from; i < pages.get(tab).size() && y + rowHeight(i) - 3 <= bottom(); i++, n++) y += rowHeight(i);
        return n;
    }

    /** The furthest it scrolls: until the last row shows. */
    private int lastFirst() {
        int size = pages.get(tab).size(), f = size - 1;
        while (f > 0 && fitting(f - 1) >= size - (f - 1)) f--;
        return Math.max(0, f);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        first = Math.max(0, Math.min(lastFirst(), first - (int) Math.signum(scrollY)));
        return true;
    }

    /** A click on a remark that does not fit opens it up; a click on an open one closes it. */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        for (int[] row : drawn) {
            if (event.x() < left + 10 || event.x() >= left + W - 10 || event.y() < row[1] || event.y() >= row[2]) continue;
            int i = row[0];
            if (i == open) {
                open = -1;
            } else if (tooLong(pages.get(tab).get(i))) {
                open = i;
                while (first < i && fitting(first) < i - first + 1) first++; // scroll so the whole of it shows
            } else {
                return false;
            }
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1));
            return true;
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        g.fill(left, top, left + W, top + H, SHELL_EDGE);
        g.fill(left + 1, top + 1, left + W - 1, top + H - 1, SHELL);
        g.fill(left + 4, top + 4, left + W - 4, top + H - 4, PANEL);
        g.text(font, Component.translatable("screen.bleep.bleepedia_of", data.name()), left + 10, top + 10, TEXT, true);
        String count = counts[tab];
        if (count != null && !count.isEmpty()) g.text(font, count, left + W - 10 - font.width(count), top + 10, GLOW, false);
        super.extractRenderState(g, mouseX, mouseY, partialTicks);

        List<Row> rows = pages.get(tab);
        int y = top + TOP, x = left + 10, w = W - 20, shown = 0;
        drawn.clear();
        if (rows.isEmpty()) g.text(font, Component.translatable("screen.bleep.nothing_yet"), x, y + 4, DIM, false);
        for (int i = first; i < rows.size(); i++) {
            int h = rowHeight(i);
            if (y + h - 3 > bottom()) break;
            Row row = rows.get(i);
            shown++;
            if (tab == 3) { // about me: a label and its value
                g.text(font, row.title(), x, y, DIM, false);
                g.text(font, row.text(), x + 120, y, TEXT, false);
                y += h;
                continue;
            }
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h - 3 && (i == open || tooLong(row));
            g.fill(x, y, x + w, y + h - 3, hover ? 0xFF2A2F38 : CARD);
            drawn.add(new int[] {i, y, y + h - 3});
            int textX = textX(x);
            if (tab != 4) { // an icon on the left
                g.fill(x + 2, y + 2, x + 25, y + 25, SCREEN);
                if (!row.known()) {
                    g.text(font, "?", x + 11, y + 9, DIM, false);
                } else if (row.mob() != null) {
                    face(g, row.mob(), x + 3, y + 3, x + 24, y + 24);
                } else if (row.item() != null) {
                    g.item(row.item(), x + 5, y + 5);
                } else {
                    g.fill(x + 5, y + 5, x + 22, y + 22, row.colour());
                }
            }
            g.text(font, row.title(), textX, y + 3, row.known() ? (tab == 4 ? GLOW : TEXT) : DIM, false);
            if (row.fresh()) g.text(font, Component.translatable("screen.bleep.new"), x + w - 4 - font.width(Component.translatable("screen.bleep.new")), y + 3, AMBER, false);
            if (i == open) { // all of it, line by line
                int ly = y + 14;
                for (var line : font.split(row.text(), textWidth())) {
                    g.text(font, line, textX, ly, DIM, false);
                    ly += 10;
                }
            } else if (tooLong(row)) { // as much as fits, and ... to say there is more
                g.text(font, font.substrByWidth(row.text(), textWidth() - font.width("...")).getString() + "...", textX, y + 14, DIM, false);
            } else {
                g.text(font, row.text(), textX, y + 14, DIM, false);
            }
            y += h;
        }
        if (shown < rows.size()) {
            String more = (first + 1) + "-" + (first + shown) + " / " + rows.size();
            g.text(font, more, left + W - 10 - font.width(more), top + H - 2 - 9, DIM, false);
        }
    }

    /** The mob itself, drawn so its head fills the box. */
    private void face(GuiGraphicsExtractor g, EntityType<?> type, int x0, int y0, int x1, int y1) {
        LivingEntity mob = broken.contains(type) ? null : faces.computeIfAbsent(type, t -> {
            try {
                if (minecraft.level == null || !(t.create(minecraft.level, EntitySpawnReason.LOAD) instanceof LivingEntity living)) return null;
                living.setId(-1000 - faces.size()); // never in the world, but renderers ask for its id: one no real entity has
                return living;
            } catch (RuntimeException e) {
                return null;
            }
        });
        if (mob != null) {
            try {
                float head = Math.max(0.45f, Math.min(mob.getBbWidth(), mob.getBbHeight()) * 0.8f);
                int size = Math.max(4, (int) ((x1 - x0) / head));
                float offsetY = mob.getEyeHeight() - mob.getBbHeight() / 2;
                InventoryScreen.renderEntityInInventoryFollowsAngle(g, x0, y0, x1, y1, size, offsetY, -0.35f, 0, mob);
                return;
            } catch (RuntimeException e) { // a mob (maybe another mod's) that will not be drawn like this: a ? from now on
                broken.add(type);
            }
        }
        g.text(font, "?", x0 + 8, y0 + 6, DIM, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
