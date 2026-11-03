package io.hookdev.bleep.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.util.Mth;

/**
 * What a bleep remembers, for its Bleepedia: every kind of mob, biome and special block it found (and when, so new ones
 * can be marked), its diary of big moments, and its stats.
 */
public final class Memory {
    /** The special blocks it notices (its "finds"), each with a remark: find.bleep.<name>. */
    public static final List<String> FINDS = List.of(
            "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "ancient_debris", "budding_amethyst", "lava", "spawner", "trial_spawner",
            "vault", "beacon", "jukebox", "enchanting_table", "lodestone", "bell", "sculk_shrieker", "sponge", "cake", "crafter",
            "creaking_heart", "nether_portal", "end_portal_frame", "respawn_anchor", "conduit", "dragon_egg");
    /** How many diary entries it keeps: the oldest go first. */
    public static final int DIARY_SIZE = 60;

    /** A line in its diary: the day of its life, a key in the language file, and something to fill in (a key too, or empty). */
    public record Entry(long day, String key, String arg) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(Entry::day), Codec.STRING.fieldOf("key").forGetter(Entry::key),
                Codec.STRING.fieldOf("arg").forGetter(Entry::arg)).apply(i, Entry::new));
    }

    private static final Codec<Map<String, Long>> FOUND = Codec.unboundedMap(Codec.STRING, Codec.LONG);

    /**
     * A photo it took (owner, 31 Oct 2026): the game time, the moment (photo.bleep.<moment>: sunset, star, friend,
     * biome), something to fill in (a biome or a friend's name, or empty), and the picture itself, W x H pixels, two
     * bytes each (RGB 565: five bits of red, six of green, five of blue).
     */
    public record Photo(long time, String moment, String arg, byte[] pixels) {
        public static final int W = 96, H = 54, BYTES = W * H * 2;
        public static final Codec<Photo> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("time").forGetter(Photo::time), Codec.STRING.fieldOf("moment").forGetter(Photo::moment),
                Codec.STRING.fieldOf("arg").forGetter(Photo::arg),
                Codec.BYTE_BUFFER.xmap(b -> { byte[] a = new byte[b.remaining()]; b.get(a); return a; }, java.nio.ByteBuffer::wrap).fieldOf("pixels").forGetter(Photo::pixels))
                .apply(i, Photo::new));

        /** A pixel as an opaque ARGB colour. */
        public int colour(int x, int y) {
            int i = (y * W + x) * 2, v = (pixels[i] & 255) << 8 | pixels[i + 1] & 255;
            return 0xFF000000 | ((v >> 11) & 31) * 255 / 31 << 16 | ((v >> 5) & 63) * 255 / 63 << 8 | (v & 31) * 255 / 31;
        }

        /** An ARGB colour as the two bytes of a pixel. */
        public static void put(byte[] pixels, int x, int y, int r, int g, int b) {
            int v = Math.round(r * 31 / 255f) << 11 | Math.round(g * 63 / 255f) << 5 | Math.round(b * 31 / 255f), i = (y * W + x) * 2;
            pixels[i] = (byte) (v >> 8);
            pixels[i + 1] = (byte) v;
        }
    }

    /**
     * Something it watched its owner craft (a best friend): the grid as it was laid out (width x height, item ids, ""
     * for an empty slot), and what came out.
     */
    public record Craft(int width, int height, List<String> items, String result, int count) {
        public static final Codec<Craft> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("width").forGetter(Craft::width), Codec.INT.fieldOf("height").forGetter(Craft::height),
                Codec.STRING.listOf().fieldOf("items").forGetter(Craft::items), Codec.STRING.fieldOf("result").forGetter(Craft::result),
                Codec.INT.fieldOf("count").forGetter(Craft::count)).apply(i, Craft::new));
    }

    /** How many crafts it remembers: the oldest it has not made in a while go first. */
    public static final int CRAFTS = 18;
    public final List<Craft> crafts = new ArrayList<>();

    /** How many photos it keeps: the oldest go first. */
    public static final int PHOTOS = 12;

    /** Another bleep it knows: its name (as it was last) and how good friends they are. */
    public record Friend(String name, int points) {
        public static final Codec<Friend> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Friend::name), Codec.INT.fieldOf("points").forGetter(Friend::points)).apply(i, Friend::new));
        /** From these points: a friend, a best friend; at or below RIVAL, a rival. */
        public static final int FRIEND = 5, BEST = 15, RIVAL = -5;

        /** friend.bleep.<level>: best, friend, rival, or met. */
        public String level() {
            return points >= BEST ? "best" : points >= FRIEND ? "friend" : points <= RIVAL ? "rival" : "met";
        }
    }

    /** The other bleeps it knows, by id. */
    public final Map<String, Friend> friends = new LinkedHashMap<>();

    /** Kind of mob, biome or find (by id), and the game time it was found. */
    public final Map<String, Long> mobs = new LinkedHashMap<>(), biomes = new LinkedHashMap<>(), finds = new LinkedHashMap<>();
    public final List<Entry> diary = new ArrayList<>();
    public final List<Photo> photos = new ArrayList<>();
    /** The game time it was put together, the last time its owner opened the Bleepedia, and its stats. */
    public long born = -1, lastOpened = -1;
    public int daysTogether, fightsWon, timesEmpty;
    /** Strange signs on the ??? page of its Bleepedia: they come one by one, from the stars (SIGNS at most). */
    public int signs;
    /** Its owner's answers to its questions (yes or no), and when it last came back to each. */
    public final Map<String, Boolean> answers = new LinkedHashMap<>();
    public final Map<String, Long> recalled = new LinkedHashMap<>();
    public static final int SIGNS = 64;
    /** An abandoned bleep's past (Abandoned.PASTS, -1: none) and how much of it it has told in its diary so far. */
    public int past = -1, pastTold;
    /** How often it did things (its hobbies: stargazing, digging, collecting, dancing, chatting, playing; and catches, harvests). */
    public final Map<String, Integer> counts = new LinkedHashMap<>();
    /** Its hobby (one of the counts), or empty. */
    public String hobby = "";
    /** Milestones it reached (Milestone id), and the game time it did. */
    public final Map<String, Long> milestones = new LinkedHashMap<>();

    public int count(String what) {
        return counts.getOrDefault(what, 0);
    }

    public void add(String what) {
        counts.merge(what, 1, Integer::sum);
    }
    public double distance;
    /** Things that only make the diary the first time. */
    public final List<String> firsts = new ArrayList<>();

    /** The day of its life, counting from 1. */
    public long day(long gameTime) {
        return born < 0 ? 1 : (gameTime - born) / Bond.DAY + 1;
    }

    /** Writes in its diary. */
    public void write(long gameTime, String key, String arg) {
        diary.add(new Entry(day(gameTime), key, arg));
        while (diary.size() > DIARY_SIZE) diary.removeFirst();
    }

    /** Writes in its diary, but only the first time this happens. True if it was the first time. */
    public boolean first(long gameTime, String what, String key, String arg) {
        if (firsts.contains(what)) return false;
        firsts.add(what);
        write(gameTime, key, arg);
        return true;
    }

    /** Keeps a photo; the oldest goes when there are too many, but never the one from before (an abandoned bleep's). */
    public void addPhoto(Photo photo) {
        photos.add(photo);
        while (photos.size() > PHOTOS) {
            int oldest = 0;
            while (oldest < photos.size() - 1 && photos.get(oldest).moment().equals("old")) oldest++;
            photos.remove(oldest);
        }
    }

    /** Better (or worse) friends with another bleep; returns the friend as it is now. */
    public Friend befriend(String id, String name, int points) {
        Friend before = friends.get(id);
        Friend now = new Friend(name, Mth.clamp((before == null ? 0 : before.points()) + points, -50, 50));
        friends.put(id, now);
        return now;
    }

    /** Its best friend (the most points, at least BEST), or null. */
    public java.util.Map.@org.jspecify.annotations.Nullable Entry<String, Friend> bestFriend() {
        return friends.entrySet().stream().filter(e -> e.getValue().points() >= Friend.BEST).max(java.util.Comparator.comparingInt(e -> e.getValue().points())).orElse(null);
    }

    public void save(ValueOutput output) {
        output.store("mobs_found", FOUND, mobs);
        output.store("biomes_found", FOUND, biomes);
        output.store("finds", FOUND, finds);
        output.store("diary", Entry.CODEC.listOf(), diary);
        output.store("firsts", Codec.STRING.listOf(), firsts);
        output.putLong("born", born);
        output.putLong("last_opened", lastOpened);
        output.putInt("days_together", daysTogether);
        output.putInt("fights_won", fightsWon);
        output.putInt("times_empty", timesEmpty);
        output.putDouble("distance", distance);
        output.putInt("signs", signs);
        output.store("counts", Codec.unboundedMap(Codec.STRING, Codec.INT), counts);
        output.putString("hobby", hobby);
        output.store("milestones", FOUND, milestones);
        output.store("friends", Codec.unboundedMap(Codec.STRING, Friend.CODEC), friends);
        output.store("answers", Codec.unboundedMap(Codec.STRING, Codec.BOOL), answers);
        output.store("recalled", FOUND, recalled);
        output.store("photos", Photo.CODEC.listOf(), photos);
        output.store("crafts", Craft.CODEC.listOf(), crafts);
        output.putInt("past", past);
        output.putInt("past_told", pastTold);
    }

    public void load(ValueInput input) {
        mobs.clear();
        input.read("mobs_found", FOUND).ifPresent(mobs::putAll);
        biomes.clear();
        input.read("biomes_found", FOUND).ifPresent(biomes::putAll);
        finds.clear();
        input.read("finds", FOUND).ifPresent(finds::putAll);
        diary.clear();
        input.read("diary", Entry.CODEC.listOf()).ifPresent(diary::addAll);
        firsts.clear();
        input.read("firsts", Codec.STRING.listOf()).ifPresent(firsts::addAll);
        born = input.getLongOr("born", -1);
        lastOpened = input.getLongOr("last_opened", -1);
        daysTogether = input.getIntOr("days_together", 0);
        fightsWon = input.getIntOr("fights_won", 0);
        timesEmpty = input.getIntOr("times_empty", 0);
        distance = input.getDoubleOr("distance", 0);
        signs = input.getIntOr("signs", 0);
        counts.clear();
        input.read("counts", Codec.unboundedMap(Codec.STRING, Codec.INT)).ifPresent(counts::putAll);
        hobby = input.getStringOr("hobby", "");
        milestones.clear();
        input.read("milestones", FOUND).ifPresent(milestones::putAll);
        friends.clear();
        input.read("friends", Codec.unboundedMap(Codec.STRING, Friend.CODEC)).ifPresent(friends::putAll);
        answers.clear();
        input.read("answers", Codec.unboundedMap(Codec.STRING, Codec.BOOL)).ifPresent(answers::putAll);
        recalled.clear();
        input.read("recalled", FOUND).ifPresent(recalled::putAll);
        crafts.clear();
        input.read("crafts", Craft.CODEC.listOf()).ifPresent(crafts::addAll);
        past = input.getIntOr("past", -1);
        pastTold = input.getIntOr("past_told", 0);
        photos.clear();
        input.read("photos", Photo.CODEC.listOf()).ifPresent(list -> list.stream().filter(p -> p.pixels().length == Photo.BYTES).forEach(photos::add)); // not the first, smaller kind
    }
}
