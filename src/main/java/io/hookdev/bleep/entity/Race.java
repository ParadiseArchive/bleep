package io.hookdev.bleep.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.block.RaceFlagBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * A bleep race (owner, 31 Oct 2026). Right-click a start flag: your bleeps within ENTRANTS blocks (and those of other
 * players standing there) line up, 3, 2, 1, GO! They drive from the start to the nearest race flag, from there to the
 * nearest one not yet visited, and so on, and back to the start. The first one home wins; everyone near hears the
 * times, and the start flag keeps its course record. One bleep alone: a time trial against the record.
 */
public final class Race {
    /** How far from the start flag race flags count, how close bleeps must be to join, the most flags on a course. */
    public static final int COURSE = 32, ENTRANTS = 16, MAX_FLAGS = 12;
    /** Other players' bleeps join when their owner stands this close to the start flag. */
    public static final int AT_THE_START = 4;
    /** The countdown and how long a race may take at most (ticks). */
    public static final int COUNTDOWN = 60, TIMEOUT = 2400;
    private static final List<Race> RACES = new ArrayList<>();

    public final ServerLevel level;
    public final BlockPos start;
    /** The flags in order, the start last. */
    public final List<BlockPos> route;
    public final long go;
    private final List<BleepEntity> entrants = new ArrayList<>();
    private final List<String> finishers = new ArrayList<>();
    private final List<Long> times = new ArrayList<>();
    /** The names of those home, by id, and how many started. */
    private final Map<String, String> names = new HashMap<>();
    private int field;
    private boolean over;

    private Race(ServerLevel level, BlockPos start, List<BlockPos> route) {
        this.level = level;
        this.start = start;
        this.route = route;
        this.go = level.getGameTime() + COUNTDOWN;
    }

    /** A race from this start flag, if there are race flags around it and bleeps to race. False (and a message) otherwise. */
    public static boolean start(ServerLevel level, BlockPos start, Player player) {
        if (RACES.stream().anyMatch(r -> r.start.equals(start) && r.level == level)) {
            player.sendOverlayMessage(Component.translatable("message.bleep.race.on"));
            return false;
        }
        List<BlockPos> flags = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(start.offset(-COURSE, -8, -COURSE), start.offset(COURSE, 8, COURSE))) {
            if (level.getBlockState(p).getBlock() instanceof RaceFlagBlock flag && !flag.start) flags.add(p.immutable());
        }
        if (flags.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.bleep.race.no_flags"));
            return false;
        }
        Race race = new Race(level, start, route(start, flags));
        List<BleepEntity> bleeps = level.getEntitiesOfClass(BleepEntity.class, new net.minecraft.world.phys.AABB(start).inflate(ENTRANTS),
                b -> b.getOwner() instanceof Player owner && (owner == player || owner.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(start)) < AT_THE_START * AT_THE_START) && b.mayRace());
        if (bleeps.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.bleep.race.nobody"));
            return false;
        }
        race.field = bleeps.size();
        for (int i = 0; i < bleeps.size(); i++) {
            race.entrants.add(bleeps.get(i));
            bleeps.get(i).joinRace(race, i, bleeps.size());
        }
        RACES.add(race);
        io.hookdev.bleep.Awards.award(player, "race");
        return true;
    }

    /** From the start to the nearest flag, then each time to the nearest one not yet visited (MAX_FLAGS at most), and back. */
    public static List<BlockPos> route(BlockPos start, List<BlockPos> flags) {
        List<BlockPos> left = new ArrayList<>(flags), route = new ArrayList<>();
        BlockPos at = start;
        while (!left.isEmpty() && route.size() < MAX_FLAGS) {
            BlockPos from = at;
            BlockPos next = left.stream().min(java.util.Comparator.comparingDouble(p -> p.distSqr(from))).orElseThrow();
            left.remove(next);
            route.add(next);
            at = next;
        }
        route.add(start);
        return route;
    }

    /** Every tick, for every race on: the countdown, then the end once all are home (or it took too long). */
    public static void tickAll(ServerLevel level) {
        for (Race race : List.copyOf(RACES)) if (race.level == level) race.tick();
    }

    private void tick() {
        long now = level.getGameTime(), toGo = go - now;
        if (toGo > 0 && toGo % 20 == 0) {
            say(Component.translatable("message.bleep.race.count", toGo / 20));
            level.playSound(null, start, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1, 1);
        } else if (toGo == 0) {
            say(Component.translatable("message.bleep.race.go"));
            level.playSound(null, start, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1, 2);
        }
        entrants.removeIf(b -> b.isRemoved() || b.race() != this);
        if (toGo < 0 && (entrants.isEmpty() || now - go > TIMEOUT)) end();
    }

    /** Home: its time, and the winner's cheer. */
    public void finish(BleepEntity bleep) {
        if (over || finishers.contains(bleep.getStringUUID())) return;
        finishers.add(bleep.getStringUUID());
        times.add(level.getGameTime() - go);
        if (finishers.size() == 1) {
            level.playSound(null, start, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.NEUTRAL, 1, 1);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK, start.getX() + 0.5, start.getY() + 1.5, start.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.1);
        }
        names.put(bleep.getStringUUID(), bleep.getDisplayName().getString());
        bleep.finishedRace(finishers.size(), field);
    }

    /** The results for everyone near, and a new course record if there is one. */
    private void end() {
        if (over) return;
        over = true;
        RACES.remove(this);
        for (BleepEntity b : entrants) b.leaveRace();
        if (finishers.isEmpty()) {
            say(Component.translatable("message.bleep.race.nobody_home"));
            return;
        }
        var results = Component.translatable("message.bleep.race.over");
        for (int i = 0; i < finishers.size(); i++) {
            results.append("  " + (i + 1) + ". ").append(names.get(finishers.get(i))).append(" " + seconds(times.get(i)));
        }
        say(results);
        Records records = Records.get(level);
        String key = level.dimension().identifier() + "@" + start.asLong();
        Best best = records.best.get(key);
        if (best == null || times.getFirst() < best.ticks()) {
            records.best.put(key, new Best(times.getFirst(), names.get(finishers.getFirst())));
            records.setDirty();
            if (best != null) say(Component.translatable("message.bleep.race.record", names.get(finishers.getFirst()), seconds(times.getFirst()), seconds(best.ticks())));
        } else {
            say(Component.translatable("message.bleep.race.to_beat", best.name(), seconds(best.ticks())));
        }
    }

    public boolean over() {
        return over;
    }

    /** How many finished, in order (tests). */
    public int finished() {
        return finishers.size();
    }

    private static String seconds(long ticks) {
        return String.format(java.util.Locale.ROOT, "%.1fs", ticks / 20.0);
    }

    /** To everyone within 48 blocks of the start. */
    private void say(Component message) {
        for (var player : level.players()) if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(start)) < 48 * 48) player.sendSystemMessage(message);
    }

    /** A course record: the time (ticks) and the bleep that set it. */
    public record Best(long ticks, String name) {
        public static final Codec<Best> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("ticks").forGetter(Best::ticks), Codec.STRING.fieldOf("name").forGetter(Best::name)).apply(i, Best::new));
    }

    /** The course records of every start flag (dimension @ position), saved with the world. */
    public static final class Records extends SavedData {
        private static final Codec<Records> CODEC = Codec.unboundedMap(Codec.STRING, Best.CODEC).xmap(Records::new, r -> r.best);
        public static final SavedDataType<Records> TYPE = new SavedDataType<>(Bleep.id("race_records"), Records::new, CODEC);
        private final Map<String, Best> best = new HashMap<>();

        public Records() {}

        private Records(Map<String, Best> saved) {
            best.putAll(saved);
        }

        public static Records get(ServerLevel level) {
            return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
        }
    }
}
