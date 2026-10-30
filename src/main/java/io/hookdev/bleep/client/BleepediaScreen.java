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
 * its owner last looked are marked NEW.
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
            Button button = Button.builder(Component.translatable("screen.bleep.tab." + TABS[i]), b -> { tab = t; first = 0; rebuildWidgets(); })
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

    private int rowHeight() {
        return tab == 3 ? 14 : ROW;
    }

    private int visibleRows() {
        return (H - TOP - 8) / rowHeight();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int max = Math.max(0, pages.get(tab).size() - visibleRows());
        first = Math.max(0, Math.min(max, first - (int) Math.signum(scrollY)));
        return true;
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
        int y = top + TOP, h = rowHeight(), x = left + 10, w = W - 20;
        if (rows.isEmpty()) g.text(font, Component.translatable("screen.bleep.nothing_yet"), x, y + 4, DIM, false);
        for (int i = first; i < rows.size() && i < first + visibleRows(); i++, y += h) {
            Row row = rows.get(i);
            if (tab == 3) { // about me: a label and its value
                g.text(font, row.title(), x, y, DIM, false);
                g.text(font, row.text(), x + 120, y, TEXT, false);
                continue;
            }
            g.fill(x, y, x + w, y + h - 3, CARD);
            int textX = x + 6;
            if (tab != 4) { // an icon on the left
                g.fill(x + 2, y + 2, x + 25, y + 25, SCREEN);
                textX = x + 30;
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
            g.text(font, font.substrByWidth(row.text(), w - (textX - x) - 4).getString(), textX, y + 14, DIM, false);
        }
        if (rows.size() > visibleRows()) {
            String more = (first + 1) + "-" + Math.min(rows.size(), first + visibleRows()) + " / " + rows.size();
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
