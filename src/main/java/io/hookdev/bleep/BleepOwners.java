package io.hookdev.bleep;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Who owns which bleeps, saved with the world (also the ones in chunks that are not loaded), so a player can have at
 * most {@link #MAX}. Owner's choice (30 Oct 2026): any more and it really turns into a fight.
 */
public final class BleepOwners extends SavedData {
    public static final int MAX = 10;
    private static final Codec<BleepOwners> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.STRING_CODEC.listOf())
            .xmap(BleepOwners::new, BleepOwners::asMap);
    public static final SavedDataType<BleepOwners> TYPE = new SavedDataType<>(Bleep.id("owners"), BleepOwners::new, CODEC);

    private final Map<UUID, Set<UUID>> bleeps = new HashMap<>();

    public BleepOwners() {}

    private BleepOwners(Map<UUID, List<UUID>> saved) {
        saved.forEach((owner, list) -> bleeps.put(owner, new LinkedHashSet<>(list)));
    }

    private Map<UUID, List<UUID>> asMap() {
        Map<UUID, List<UUID>> map = new HashMap<>();
        bleeps.forEach((owner, set) -> map.put(owner, new ArrayList<>(set)));
        return map;
    }

    public static BleepOwners get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** How many bleeps this player has. */
    public int count(UUID owner) {
        return bleeps.getOrDefault(owner, Set.of()).size();
    }

    public boolean full(UUID owner) {
        return count(owner) >= MAX;
    }

    /** This bleep is this player's (and nobody else's). */
    public void add(UUID owner, UUID bleep) {
        if (bleeps.getOrDefault(owner, Set.of()).contains(bleep)) return;
        remove(bleep);
        bleeps.computeIfAbsent(owner, o -> new LinkedHashSet<>()).add(bleep);
        setDirty();
    }

    /** This bleep is gone for good. */
    public void remove(UUID bleep) {
        for (Set<UUID> set : bleeps.values()) if (set.remove(bleep)) setDirty();
        bleeps.values().removeIf(Set::isEmpty);
    }
}
