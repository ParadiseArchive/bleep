package io.hookdev.bleep.entity;

import io.hookdev.bleep.BleepContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Abandoned bleeps (design doc): now and then, out in the world, an old bleep stands switched off, its paint faded, its
 * name still on it. A block of redstone wakes it up, and it is yours, with a past in its diary.
 */
public final class Abandoned {
    /** How often a player's surroundings get the chance of one (ticks), the chance, and how far from the player. */
    public static final int EVERY = 6000;
    public static final float CHANCE = 0.25f;
    private static final int NEAR = 24, FAR = 48, NONE_WITHIN = 96;
    private static final List<String> NAMES = List.of("Rusty", "Sprocket", "Pixel", "Widget", "Gizmo", "Blip", "Volt", "Cog", "Dynamo", "Ratchet", "Byte", "Nugget");
    /** Faded colours, as if the paint has been in the sun for years. */
    private static final int[] FADED = {0x8a6a4a, 0x6f7f6a, 0x7a6f8f, 0x8f7a5a, 0x5f7a86, 0x8a5f5f};
    /** Its past: diary.bleep.past.<n>. */
    public static final int PASTS = 4;

    private Abandoned() {}

    /** Makes this bleep an abandoned one: switched off, faded, with an old name. */
    public static void setUp(BleepEntity bleep, RandomSource random) {
        bleep.setAbandoned(true);
        bleep.setCustomName(Component.literal(NAMES.get(random.nextInt(NAMES.size()))));
        bleep.setColor(FADED[random.nextInt(FADED.length)]);
        bleep.setObsession(random.nextInt(Obsession.ALL.size()));
    }

    /** Maybe puts an abandoned bleep somewhere out of sight of this player, in the Overworld. */
    public static void maybeSpawn(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || player.getRandom().nextFloat() >= CHANCE) return;
        if (!level.getEntitiesOfClass(BleepEntity.class, new AABB(player.blockPosition()).inflate(NONE_WITHIN), BleepEntity::abandoned).isEmpty()) return;
        RandomSource random = player.getRandom();
        double angle = random.nextDouble() * Math.PI * 2, distance = NEAR + random.nextInt(FAR - NEAR);
        int x = (int) (player.getX() + Math.cos(angle) * distance), z = (int) (player.getZ() + Math.sin(angle) * distance);
        if (!level.isLoaded(new BlockPos(x, 0, z))) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos at = new BlockPos(x, y, z);
        if (!level.getBlockState(at.below()).isSolidRender() || !level.getFluidState(at).isEmpty() || !level.getBlockState(at).isAir()) return;
        BleepEntity bleep = BleepContent.BLEEP.get().create(level, EntitySpawnReason.NATURAL);
        if (bleep == null) return;
        bleep.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360, 0);
        setUp(bleep, random);
        level.addFreshEntity(bleep);
    }
}
