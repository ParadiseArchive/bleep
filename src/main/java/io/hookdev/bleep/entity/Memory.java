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
    /** The game time it was put together, the last time its owner opened the Bleepedia, and its stats. */
    public long born = -1, lastOpened = -1;
    public int daysTogether, fightsWon, timesEmpty;
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
        output.store("friends", Codec.unboundedMap(Codec.STRING, Friend.CODEC), friends);
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
        friends.clear();
        input.read("friends", Codec.unboundedMap(Codec.STRING, Friend.CODEC)).ifPresent(friends::putAll);
    }
}
