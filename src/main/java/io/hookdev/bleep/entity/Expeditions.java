package io.hookdev.bleep.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.BleepContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Expeditions (owner, 31 Oct 2026): a bleep sent off goes away for a Minecraft day. Meanwhile it is not in the world at
 * all: it waits here, saved with the world (its own save, as it was), and comes back next to its owner once the day is
 * over and they are online, with finds from where it went, two photos and a travel story.
 */
public final class Expeditions extends SavedData {
    /** How long a trip takes (ticks: a Minecraft day). */
    public static final long TRIP = 24000;
    /** Where it may go: place.bleep.<place>, and the biome it puts in its Bleepedia. */
    public static final List<String> PLACES = List.of("forest", "desert", "mountains", "snowy", "ocean", "jungle", "swamp", "cave");
    private static final Map<String, String> BIOMES = Map.of("forest", "minecraft:forest", "desert", "minecraft:desert", "mountains", "minecraft:jagged_peaks",
            "snowy", "minecraft:snowy_plains", "ocean", "minecraft:ocean", "jungle", "minecraft:jungle", "swamp", "minecraft:swamp", "cave", "minecraft:lush_caves");
    /** What it may bring back from each place: an item and how many at most. */
    private record Find(Item item, int most) {}
    private static final Map<String, List<Find>> FINDS = Map.of(
            "forest", List.of(new Find(Items.APPLE, 3), new Find(Items.OAK_SAPLING, 2), new Find(Items.BROWN_MUSHROOM, 2), new Find(Items.SWEET_BERRIES, 6), new Find(Items.FEATHER, 3)),
            "desert", List.of(new Find(Items.CACTUS, 3), new Find(Items.DEAD_BUSH, 2), new Find(Items.GOLD_NUGGET, 6), new Find(Items.RABBIT_HIDE, 2), new Find(Items.SAND, 8)),
            "mountains", List.of(new Find(Items.EMERALD, 1), new Find(Items.COAL, 6), new Find(Items.IRON_NUGGET, 8), new Find(Items.RAW_COPPER, 3), new Find(Items.FLINT, 3)),
            "snowy", List.of(new Find(Items.SNOWBALL, 8), new Find(Items.ICE, 2), new Find(Items.SWEET_BERRIES, 6), new Find(Items.SPRUCE_SAPLING, 2), new Find(Items.RABBIT_FOOT, 1)),
            "ocean", List.of(new Find(Items.KELP, 8), new Find(Items.PRISMARINE_SHARD, 4), new Find(Items.SEA_PICKLE, 2), new Find(Items.COD, 3), new Find(Items.NAUTILUS_SHELL, 1)),
            "jungle", List.of(new Find(Items.COCOA_BEANS, 4), new Find(Items.BAMBOO, 6), new Find(Items.MELON_SLICE, 5), new Find(Items.JUNGLE_SAPLING, 2), new Find(Items.VINE, 4)),
            "swamp", List.of(new Find(Items.LILY_PAD, 3), new Find(Items.SLIME_BALL, 3), new Find(Items.CLAY_BALL, 6), new Find(Items.BLUE_ORCHID, 2), new Find(Items.MUD, 4)),
            "cave", List.of(new Find(Items.AMETHYST_SHARD, 4), new Find(Items.RAW_IRON, 3), new Find(Items.COAL, 6), new Find(Items.GLOW_BERRIES, 4), new Find(Items.POINTED_DRIPSTONE, 2)));
    /** Now and then something special, wherever it went (one in this many trips). */
    private static final int SPECIAL = 8;

    /** A bleep away: whose it is, when it is back (game time), where it went, and the bleep itself as it was saved. */
    public record Trip(UUID owner, long back, String place, CompoundTag bleep) {
        public static final Codec<Trip> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(Trip::owner), Codec.LONG.fieldOf("back").forGetter(Trip::back),
                Codec.STRING.fieldOf("place").forGetter(Trip::place), CompoundTag.CODEC.fieldOf("bleep").forGetter(Trip::bleep)).apply(i, Trip::new));
    }

    private static final Codec<Expeditions> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Trip.CODEC).xmap(Expeditions::new, e -> e.trips);
    public static final SavedDataType<Expeditions> TYPE = new SavedDataType<>(Bleep.id("expeditions"), Expeditions::new, CODEC);

    private final Map<UUID, Trip> trips = new HashMap<>();

    public Expeditions() {}

    private Expeditions(Map<UUID, Trip> saved) {
        trips.putAll(saved);
    }

    public static Expeditions get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void leave(UUID bleep, Trip trip) {
        trips.put(bleep, trip);
        setDirty();
    }

    public boolean away(UUID bleep) {
        return trips.containsKey(bleep);
    }

    /**
     * This player's bleeps whose day is over come back next to them (all of them, now: tests). The bleeps that came back.
     */
    public List<BleepEntity> comeBack(ServerPlayer owner, boolean now) {
        List<BleepEntity> back = new ArrayList<>();
        ServerLevel level = (ServerLevel) owner.level();
        long time = level.getGameTime();
        for (var entry : List.copyOf(trips.entrySet())) {
            Trip trip = entry.getValue();
            if (!trip.owner().equals(owner.getUUID()) || !now && time < trip.back()) continue;
            var input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), trip.bleep());
            var entity = net.minecraft.world.entity.EntityType.create(BleepContent.BLEEP.get(), input, level, EntitySpawnReason.LOAD).orElse(null);
            trips.remove(entry.getKey());
            setDirty();
            if (!(entity instanceof BleepEntity bleep)) continue;
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            bleep.snapTo(owner.getX() + Math.cos(angle) * 2, owner.getY(), owner.getZ() + Math.sin(angle) * 2, owner.getYRot() + 180, 0);
            if (!level.addFreshEntity(bleep)) continue;
            bleep.backFromTrip(owner, trip.place());
            back.add(bleep);
        }
        return back;
    }

    /** What it brings back from this place: three to six finds, now and then something special. */
    public static List<ItemStack> finds(String place, RandomSource random) {
        List<Find> pool = FINDS.getOrDefault(place, FINDS.get("forest"));
        List<ItemStack> found = new ArrayList<>();
        for (int i = 3 + random.nextInt(4); i > 0; i--) {
            Find find = pool.get(random.nextInt(pool.size()));
            found.add(new ItemStack(find.item(), 1 + random.nextInt(find.most())));
        }
        if (random.nextInt(SPECIAL) == 0) found.add(new ItemStack(random.nextBoolean() ? Items.DIAMOND : Items.NAME_TAG));
        return found;
    }

    /** The biome it saw there, for its Bleepedia. */
    public static String biome(String place) {
        return BIOMES.getOrDefault(place, "minecraft:forest");
    }
}
