package io.hookdev.bleep.network;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Memory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A bleep's Bleepedia, sent to its owner when they open it: what it found and when, its diary and its stats, and when
 * its owner last looked (newer finds get a NEW mark).
 */
public record BleepediaData(int entityId, String name, Map<String, Long> mobs, Map<String, Long> biomes, Map<String, Long> finds,
                            List<Memory.Entry> diary, long born, long now, long lastOpened, int daysTogether, int fightsWon, int timesEmpty,
                            int distance, String favourite, String bond, List<String> friends, int chips, int signs, Map<String, Long> milestones, String hobby) implements CustomPacketPayload {
    public static final Type<BleepediaData> TYPE = new Type<>(Bleep.id("bleepedia"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BleepediaData> CODEC = CustomPacketPayload.codec(BleepediaData::write, BleepediaData::read);

    /** Everything a bleep remembers, as it is now. */
    public static BleepediaData of(BleepEntity bleep) {
        Memory m = bleep.memory();
        var favourite = bleep.obsession();
        return new BleepediaData(bleep.getId(), bleep.getDisplayName().getString(), m.mobs, m.biomes, m.finds, m.diary, m.born,
                bleep.level().getGameTime(), m.lastOpened, m.daysTogether, m.fightsWon, m.timesEmpty, (int) m.distance,
                favourite != null && bleep.revealed() ? favourite.nameKey() : "", io.hookdev.bleep.entity.Bond.key(bleep.bond()), friends(m), bleep.chips(), m.signs, m.milestones, m.hobby);
    }

    /** Its friends, best first, as "level\tname" (friend.bleep.<level>): the ones it knows well, and its rivals. */
    private static List<String> friends(Memory m) {
        return m.friends.values().stream().filter(f -> !f.level().equals("met")).sorted(java.util.Comparator.comparingInt(Memory.Friend::points).reversed())
                .map(f -> f.level() + "\t" + f.name()).limit(8).toList();
    }

    private static void writeMap(RegistryFriendlyByteBuf buf, Map<String, Long> map) {
        buf.writeVarInt(map.size());
        map.forEach((k, v) -> { buf.writeUtf(k); buf.writeLong(v); });
    }

    private static Map<String, Long> readMap(RegistryFriendlyByteBuf buf) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = buf.readVarInt(); i > 0; i--) map.put(buf.readUtf(), buf.readLong());
        return map;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(name);
        writeMap(buf, mobs);
        writeMap(buf, biomes);
        writeMap(buf, finds);
        buf.writeVarInt(diary.size());
        for (Memory.Entry e : diary) { buf.writeLong(e.day()); buf.writeUtf(e.key()); buf.writeUtf(e.arg()); }
        buf.writeLong(born);
        buf.writeLong(now);
        buf.writeLong(lastOpened);
        buf.writeVarInt(daysTogether);
        buf.writeVarInt(fightsWon);
        buf.writeVarInt(timesEmpty);
        buf.writeVarInt(distance);
        buf.writeUtf(favourite);
        buf.writeUtf(bond);
        buf.writeVarInt(friends.size());
        friends.forEach(buf::writeUtf);
        buf.writeVarInt(chips);
        buf.writeVarInt(signs);
        writeMap(buf, milestones);
        buf.writeUtf(hobby);
    }

    private static BleepediaData read(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String name = buf.readUtf();
        var mobs = readMap(buf);
        var biomes = readMap(buf);
        var finds = readMap(buf);
        List<Memory.Entry> diary = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) diary.add(new Memory.Entry(buf.readLong(), buf.readUtf(), buf.readUtf()));
        long born = buf.readLong(), now = buf.readLong(), lastOpened = buf.readLong();
        int days = buf.readVarInt(), fights = buf.readVarInt(), empty = buf.readVarInt(), distance = buf.readVarInt();
        String favourite = buf.readUtf(), bond = buf.readUtf();
        List<String> friends = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) friends.add(buf.readUtf());
        return new BleepediaData(id, name, mobs, biomes, finds, diary, born, now, lastOpened, days, fights, empty, distance, favourite, bond, friends, buf.readVarInt(), buf.readVarInt(), readMap(buf), buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
