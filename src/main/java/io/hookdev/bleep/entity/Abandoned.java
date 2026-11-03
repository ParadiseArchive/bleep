package io.hookdev.bleep.entity;

import io.hookdev.bleep.BleepContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

/**
 * Abandoned bleeps (design doc): an old bleep stands switched off, its paint faded, its name still on it. A block of
 * redstone wakes it up, and it is yours, with a past in its diary and an old photo. Super rare (owner, 31 Oct 2026):
 * only at special places, an abandoned mineshaft, trail ruins, an ancient city or a zombie village, and each of those
 * gets one roll when a player first walks into it (AbandonedSites).
 */
public final class Abandoned {
    /** How often a player is checked for standing in a special place (ticks). */
    public static final int EVERY = 100;
    /** A special place: its structures, its name for the rolls, one in how many has a bleep, only zombie villages. */
    public record Site(String name, List<ResourceKey<Structure>> structures, int oneIn, boolean zombie) {}
    public static final List<Site> SITES = List.of(
            new Site("mineshaft", List.of(BuiltinStructures.MINESHAFT, BuiltinStructures.MINESHAFT_MESA), 15, false),
            new Site("trail_ruins", List.of(BuiltinStructures.TRAIL_RUINS), 4, false),
            new Site("ancient_city", List.of(BuiltinStructures.ANCIENT_CITY), 3, false),
            new Site("zombie_village", List.of(BuiltinStructures.VILLAGE_PLAINS, BuiltinStructures.VILLAGE_DESERT, BuiltinStructures.VILLAGE_SAVANNA,
                    BuiltinStructures.VILLAGE_SNOWY, BuiltinStructures.VILLAGE_TAIGA), 2, true));
    /** Not right under the player's nose. */
    private static final int NOT_WITHIN = 10;
    private static final List<String> NAMES = List.of("Rusty", "Sprocket", "Pixel", "Widget", "Gizmo", "Blip", "Volt", "Cog", "Dynamo", "Ratchet", "Byte", "Nugget");
    /** Faded colours, as if the paint has been in the sun for years. */
    private static final int[] FADED = {0x8a6a4a, 0x6f7f6a, 0x7a6f8f, 0x8f7a5a, 0x5f7a86, 0x8a5f5f};
    /** Its past: diary.bleep.past.<n>, then PAST_LINES more, diary.bleep.past.<n>.<k>, one a day together. */
    public static final int PASTS = 4, PAST_LINES = 4;

    private Abandoned() {}

    /** Makes this bleep an abandoned one: switched off, faded, with an old name. */
    public static void setUp(BleepEntity bleep, RandomSource random) {
        bleep.setAbandoned(true);
        bleep.setCustomName(Component.literal(NAMES.get(random.nextInt(NAMES.size()))));
        bleep.setColor(FADED[random.nextInt(FADED.length)]);
        bleep.setObsession(random.nextInt(Obsession.ALL.size()));
    }

    /** A player walking into a special place for the first time rolls for an abandoned bleep in it, in the Overworld. */
    public static void maybeSpawn(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || !io.hookdev.bleep.BleepConfig.abandonedBleeps()) return;
        AbandonedSites sites = AbandonedSites.get(level);
        for (Site site : SITES) {
            StructureStart start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), h -> site.structures().stream().anyMatch(h::is));
            if (!start.isValid() || site.zombie() && !zombie(start)) continue;
            String key = site.name() + "@" + start.getChunkPos().pack();
            AbandonedSites.Roll roll = sites.roll(key, () -> player.getRandom().nextInt(site.oneIn()) == 0);
            if (roll != AbandonedSites.Roll.WON) continue;
            BlockPos at = spot(level, start, player);
            if (at != null && place(level, at, player.getRandom())) sites.placed(key);
        }
    }

    /** Puts an abandoned bleep here. */
    public static boolean place(ServerLevel level, BlockPos at, RandomSource random) {
        BleepEntity bleep = BleepContent.BLEEP.get().create(level, EntitySpawnReason.NATURAL);
        if (bleep == null) return false;
        bleep.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
        bleep.setPersistenceRequired();
        setUp(bleep, random);
        return level.addFreshEntity(bleep);
    }

    /** A village built from the zombie village pieces. */
    private static boolean zombie(StructureStart start) {
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof PoolElementStructurePiece pool && pool.getElement().toString().contains("/zombie/")) return true;
        }
        return false;
    }

    /** A free spot on a floor somewhere in the place, loaded, and not right next to the player; null if none came up. */
    private static @Nullable BlockPos spot(ServerLevel level, StructureStart start, ServerPlayer player) {
        List<StructurePiece> pieces = start.getPieces();
        RandomSource random = player.getRandom();
        for (int tries = 0; tries < 24 && !pieces.isEmpty(); tries++) {
            BoundingBox box = pieces.get(random.nextInt(pieces.size())).getBoundingBox();
            int x = box.minX() + random.nextInt(box.getXSpan()), z = box.minZ() + random.nextInt(box.getZSpan());
            if (!level.isLoaded(new BlockPos(x, box.minY(), z))) continue;
            for (int y = box.minY() + 1; y <= box.maxY(); y++) {
                BlockPos at = new BlockPos(x, y, z);
                if (level.getBlockState(at.below()).isSolidRender() && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
                        && level.getFluidState(at).isEmpty() && at.distSqr(player.blockPosition()) > NOT_WITHIN * NOT_WITHIN) return at;
            }
        }
        return null;
    }
}
