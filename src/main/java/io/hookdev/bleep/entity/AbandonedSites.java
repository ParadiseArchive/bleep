package io.hookdev.bleep.entity;

import com.mojang.serialization.Codec;
import io.hookdev.bleep.Bleep;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The special places that already had their roll for an abandoned bleep, saved with the world: each place rolls once.
 * A won roll waits until a spot for the bleep turns up, then it is placed.
 */
public final class AbandonedSites extends SavedData {
    public enum Roll { LOST, WON, PLACED }

    private static final Codec<AbandonedSites> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(AbandonedSites::new, s -> s.rolls);
    public static final SavedDataType<AbandonedSites> TYPE = new SavedDataType<>(Bleep.id("abandoned_sites"), AbandonedSites::new, CODEC);

    /** Place (Abandoned.Site name @ chunk of its start) and its Roll, by ordinal. */
    private final Map<String, Integer> rolls = new HashMap<>();

    public AbandonedSites() {}

    private AbandonedSites(Map<String, Integer> saved) {
        rolls.putAll(saved);
    }

    public static AbandonedSites get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** This place's roll: rolled now (true: won) the first time, after that the same answer. */
    public Roll roll(String place, BooleanSupplier dice) {
        Integer known = rolls.get(place);
        if (known != null) return Roll.values()[known];
        Roll roll = dice.getAsBoolean() ? Roll.WON : Roll.LOST;
        rolls.put(place, roll.ordinal());
        setDirty();
        return roll;
    }

    public void placed(String place) {
        rolls.put(place, Roll.PLACED.ordinal());
        setDirty();
    }
}
