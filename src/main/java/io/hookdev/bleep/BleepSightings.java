package io.hookdev.bleep;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Where every bleep was last seen, saved with the world, for the Bleep Tracker: also a bleep far away in a chunk that is
 * not loaded shows where it was and how it was doing. Each bleep writes its line every five seconds.
 */
public final class BleepSightings extends SavedData {
    /** A bleep as it was last seen: its name, world and spot, battery (percent), what it did (activity key) and colour. */
    public record Seen(String name, String dimension, int x, int y, int z, int battery, String activity, int colour) {
        public static final Codec<Seen> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Seen::name), Codec.STRING.fieldOf("dimension").forGetter(Seen::dimension),
                Codec.INT.fieldOf("x").forGetter(Seen::x), Codec.INT.fieldOf("y").forGetter(Seen::y), Codec.INT.fieldOf("z").forGetter(Seen::z),
                Codec.INT.fieldOf("battery").forGetter(Seen::battery), Codec.STRING.fieldOf("activity").forGetter(Seen::activity),
                Codec.INT.fieldOf("colour").forGetter(Seen::colour)).apply(i, Seen::new));
    }

    private static final Codec<BleepSightings> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Seen.CODEC).xmap(BleepSightings::new, s -> s.seen);
    public static final SavedDataType<BleepSightings> TYPE = new SavedDataType<>(Bleep.id("sightings"), BleepSightings::new, CODEC);

    private final Map<UUID, Seen> seen = new HashMap<>();

    public BleepSightings() {}

    private BleepSightings(Map<UUID, Seen> saved) {
        seen.putAll(saved);
    }

    public static BleepSightings get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void saw(UUID bleep, Seen now) {
        if (now.equals(seen.put(bleep, now))) return;
        setDirty();
    }

    public @Nullable Seen last(UUID bleep) {
        return seen.get(bleep);
    }

    public void forget(UUID bleep) {
        if (seen.remove(bleep) != null) setDirty();
    }
}
