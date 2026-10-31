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
 * to close it. Its photos are on a page of their own, six at a time: click one to see it big.
 */
public class BleepediaScreen extends Screen {
    private static final int W = 336, H = 240, ROW = 30, TOP = 52;
    private static final int SHELL = 0xFFEEF1F4, SHELL_EDGE = 0xFFB7C0C9, PANEL = 0xFF15181E, CARD = 0xFF22262E, SCREEN = 0xFF08090C;
    private static final int TEXT = 0xFFEEF1F4, DIM = 0xFF8A93A0, GLOW = 0xFF8FF7FF, AMBER = 0xFFFFB547;
    private static final String[] TABS = {"mobs", "biomes", "finds", "about", "diary", "badges", "signs", "photos"};
    private static final int SIGNS_TAB = 6, PHOTOS_TAB = 7, GLYPH_COLS = 8, GLYPH_W = 34, GLYPH_H = 18;
    /** Photos on their page: pixel for pixel, three by two; one opened up at three times. */
    private static final int PHOTO_W = 96, PHOTO_H = 54, PHOTO_GAP = 10, PHOTO_ROW = PHOTO_H + 16, PHOTOS_A_PAGE = 6, BIG = 3;

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
    /** The page of photos, the one opened up (or -1), and a texture for each photo. */
    private int photoPage, bigPhoto = -1;
    private final Map<Integer, Identifier> photoTextures = new HashMap<>();

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
            Button button = Button.builder(Component.translatable("screen.bleep.tab." + TABS[i]), b -> { tab = t; first = 0; open = -1; bigPhoto = -1; rebuildWidgets(); })
                    .bounds(left + 8 + i * 40, top + 24, 38, 16).build();
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
        about.add(new Row(null, null, 0, Component.translatable("screen.bleep.about.hobby"),
                data.hobby().isEmpty() ? Component.translatable("screen.bleep.about.no_hobby") : Component.translatable("hobby.bleep." + data.hobby()), true, false));
        net.minecraft.network.chat.MutableComponent chips = Component.empty();
        for (io.hookdev.bleep.entity.Chip chip : io.hookdev.bleep.entity.Chip.values()) {
            if ((data.chips() & chip.bit()) == 0) continue;
            if (!chips.getSiblings().isEmpty()) chips.append(", ");
            chips.append(Component.translatable("chip.bleep." + chip.name().toLowerCase(java.util.Locale.ROOT)));
        }
        about.add(new Row(null, null, 0, Component.translatable("screen.bleep.about.chips"), chips.getSiblings().isEmpty() ? Component.translatable("screen.bleep.about.no_chips") : chips, true, false));
        if (data.friends().isEmpty()) {
            about.add(new Row(null, null, 0, Component.translatable("screen.bleep.about.friends"), Component.translatable("screen.bleep.about.no_friends"), true, false));
        }
        for (String friend : data.friends()) { // best friend, friend or rival, and its name
            String[] parts = friend.split("\t", 2);
            about.add(new Row(null, null, 0, Component.translatable("friend.bleep." + parts[0]), Component.literal(parts.length > 1 ? parts[1] : ""), true, false));
        }
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
        // milestones: each badge with its icon, what earns it, and the day it did
        List<Row> badges = new ArrayList<>();
        for (io.hookdev.bleep.entity.Milestone m : io.hookdev.bleep.entity.Milestone.values()) {
            Long at = data.milestones().get(m.id());
            var icon = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(m.icon)));
            Component desc = Component.translatable("milestone.bleep." + m.id() + ".desc");
            Component text = at == null ? desc : Component.translatable("screen.bleep.day", data.born() < 0 ? 1 : (at - data.born()) / 24000 + 1).append(": ").append(desc);
            badges.add(new Row(null, icon, 0, Component.translatable("milestone.bleep." + m.id()), text, at != null, fresh(at)));
        }
        pages.add(badges);
        counts[5] = data.milestones().size() + " / " + io.hookdev.bleep.entity.Milestone.values().length;
        pages.add(new ArrayList<>()); // the signs are drawn, not listed
        counts[SIGNS_TAB] = data.signs() + " / " + Memory.SIGNS;
        pages.add(new ArrayList<>()); // so are the photos
        counts[PHOTOS_TAB] = data.photos().size() + " / " + Memory.PHOTOS;
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
        if (tab == PHOTOS_TAB) {
            int pages = Math.max(1, (data.photos().size() + PHOTOS_A_PAGE - 1) / PHOTOS_A_PAGE);
            photoPage = Math.max(0, Math.min(pages - 1, photoPage - (int) Math.signum(scrollY)));
            bigPhoto = -1;
            return true;
        }
        first = Math.max(0, Math.min(lastFirst(), first - (int) Math.signum(scrollY)));
        return true;
    }

    /** A click on a remark that does not fit opens it up; a click on an open one closes it. */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (tab == PHOTOS_TAB) { // a photo opens up; a click anywhere closes it again, a click on Print prints it
            if (bigPhoto >= 0 && overPrint(event.x(), event.y())) {
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new io.hookdev.bleep.network.BleepCommand(data.entityId(), io.hookdev.bleep.network.BleepCommand.PRINT_PHOTO + bigPhoto));
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1));
                return true;
            }
            int clicked = bigPhoto >= 0 ? -1 : photoAt(event.x(), event.y());
            if (bigPhoto < 0 && clicked < 0) return false;
            bigPhoto = clicked;
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1));
            return true;
        }
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

        if (tab == SIGNS_TAB) {
            drawSigns(g);
            return;
        }
        if (tab == PHOTOS_TAB) {
            drawPhotos(g, mouseX, mouseY);
            return;
        }
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

    /**
     * The ??? page: 64 places, question marks until a sign appears. The signs are no alphabet anybody knows: each is
     * a little figure of strokes, always the same for its place, and nobody can read them.
     */
    private void drawSigns(GuiGraphicsExtractor g) {
        drawn.clear();
        int x0 = left + 14, y0 = top + TOP;
        for (int i = 0; i < Memory.SIGNS; i++) {
            int x = x0 + (i % GLYPH_COLS) * GLYPH_W, y = y0 + (i / GLYPH_COLS) * GLYPH_H;
            if (i >= data.signs()) {
                g.text(font, "?", x + 13, y + 4, 0xFF3A404A, false);
                continue;
            }
            long seed = i * 2654435761L + 97;
            for (int s = 0; s < 4; s++) { // a few strokes on a 5 x 7 grid
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                int a = (int) ((seed >>> 33) % 35), len = 2 + (int) ((seed >>> 20) & 3);
                boolean down = ((seed >>> 10) & 1) == 0;
                int gx = a % 5, gy = a / 5;
                for (int k = 0; k < len; k++) {
                    int px = Math.min(4, gx + (down ? 0 : k)), py = Math.min(6, gy + (down ? k : 0));
                    g.fill(x + 12 + px * 2, y + 2 + py * 2, x + 14 + px * 2, y + 4 + py * 2, GLOW);
                }
            }
        }
        g.text(font, Component.translatable("screen.bleep.signs.hint"), left + 10, top + H - 11, DIM, false);
    }

    /** Where the photo at this place on its page is drawn: x, y. */
    private int photoX(int slot) {
        return left + 14 + (slot % 3) * (PHOTO_W + PHOTO_GAP);
    }

    private int photoY(int slot) {
        return top + TOP + (slot / 3) * PHOTO_ROW;
    }

    /** The photo under the mouse, or -1. */
    private int photoAt(double mx, double my) {
        for (int slot = 0; slot < PHOTOS_A_PAGE; slot++) {
            int i = photoPage * PHOTOS_A_PAGE + slot;
            if (i >= data.photos().size()) break;
            if (mx >= photoX(slot) && mx < photoX(slot) + PHOTO_W && my >= photoY(slot) && my < photoY(slot) + PHOTO_H) return i;
        }
        return -1;
    }

    /** The Print button under an opened photo: x, y, width, height. */
    private int[] printButton() {
        int w = Memory.Photo.W * BIG, h = Memory.Photo.H * BIG, x = left + (W - w) / 2, y = top + TOP - 4;
        int bw = font.width(Component.translatable("screen.bleep.print")) + 12;
        return new int[] {x + w - bw, y + h + 4, bw, 14};
    }

    private boolean overPrint(double mx, double my) {
        int[] b = printButton();
        return mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
    }

    /** A photo's texture, made the first time it is shown. */
    private Identifier photoTexture(int i) {
        return photoTextures.computeIfAbsent(i, n -> {
            Memory.Photo photo = data.photos().get(n);
            Identifier id = io.hookdev.bleep.Bleep.id("photo/" + data.entityId() + "_" + n + "_" + photo.time());
            var texture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "Bleep photo " + id, Memory.Photo.W, Memory.Photo.H, false);
            for (int y = 0; y < Memory.Photo.H; y++) for (int x = 0; x < Memory.Photo.W; x++) texture.getPixels().setPixel(x, y, photo.colour(x, y));
            texture.upload();
            minecraft.getTextureManager().register(id, texture);
            return id;
        });
    }

    /** What a photo shows, and its day. */
    private Component caption(Memory.Photo photo) {
        long day = data.born() < 0 ? 1 : (photo.time() - data.born()) / 24000 + 1;
        return Component.translatable("screen.bleep.day", day).append(": ").append(BleepEntity.photoMoment(photo));
    }

    /** Its photos, six to a page with what each shows under it; one opened up fills the page. */
    private void drawPhotos(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        var photos = data.photos();
        if (photos.isEmpty()) {
            int y = top + TOP + 4;
            for (var line : font.split(Component.translatable("screen.bleep.no_photos"), W - 20)) {
                g.text(font, line, left + 10, y, DIM, false);
                y += 10;
            }
            return;
        }
        if (bigPhoto >= 0 && bigPhoto < photos.size()) {
            int w = Memory.Photo.W * BIG, h = Memory.Photo.H * BIG, x = left + (W - w) / 2, y = top + TOP - 4;
            g.fill(x - 3, y - 3, x + w + 3, y + h + 3, SHELL);
            g.blit(photoTexture(bigPhoto), x, y, x + w, y + h, 0, 1, 0, 1);
            g.text(font, caption(photos.get(bigPhoto)), x, y + h + 7, TEXT, false);
            int[] b = printButton(); // Print: on an empty map, to hang in a frame
            boolean hover = overPrint(mouseX, mouseY);
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], hover ? GLOW : SHELL_EDGE);
            g.fill(b[0] + 1, b[1] + 1, b[0] + b[2] - 1, b[1] + b[3] - 1, hover ? 0xFF2A2F38 : CARD);
            g.text(font, Component.translatable("screen.bleep.print"), b[0] + 6, b[1] + 3, hover ? GLOW : TEXT, false);
            return;
        }
        for (int slot = 0; slot < PHOTOS_A_PAGE; slot++) {
            int i = photoPage * PHOTOS_A_PAGE + slot;
            if (i >= photos.size()) break;
            int x = photoX(slot), y = photoY(slot);
            boolean hover = mouseX >= x && mouseX < x + PHOTO_W && mouseY >= y && mouseY < y + PHOTO_H;
            g.fill(x - 2, y - 2, x + PHOTO_W + 2, y + PHOTO_H + 2, hover ? GLOW : SHELL); // a white frame, like a polaroid
            g.blit(photoTexture(i), x, y, x + PHOTO_W, y + PHOTO_H, 0, 1, 0, 1);
            Component caption = caption(photos.get(i));
            String text = font.width(caption) > PHOTO_W ? font.substrByWidth(caption, PHOTO_W - font.width("...")).getString() + "..." : caption.getString();
            g.text(font, text, x, y + PHOTO_H + 4, hover ? TEXT : DIM, false);
        }
        int pages = (photos.size() + PHOTOS_A_PAGE - 1) / PHOTOS_A_PAGE;
        if (pages > 1) {
            String page = (photoPage + 1) + " / " + pages;
            g.text(font, page, left + W - 10 - font.width(page), top + H - 2 - 9, DIM, false);
        }
    }

    /** The photos' textures go when the book closes. */
    @Override
    public void removed() {
        photoTextures.values().forEach(id -> minecraft.getTextureManager().release(id));
        photoTextures.clear();
        super.removed();
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
