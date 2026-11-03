package io.hookdev.bleep.entity;

import io.hookdev.bleep.BleepContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A falling star (the lore: bleeps are a gift from the stars). On a clear night, very rarely, one comes down near a
 * player: a trail of light across the sky, then a small crater of glowing starstone with a fallen star in the middle,
 * which may hold a Bleep Core. Nobody knows why. Bleeps close by saw it, and wonder.
 */
public class FallingStar extends Entity {
    /** How often a player's night sky gets the chance of one (ticks); the chance is 1 in BleepConfig.fallingStars(). */
    public static final int EVERY = 1200;
    private static final int FALL_TICKS = 50, NEAR = 32, FAR = 64, CRATER = 3;
    /** Never on anything built (owner, 3 Nov 2026): all must be natural this far round the spot, from below to above the trees. */
    public static final int CLEAR = 10, CLEAR_BELOW = 4, CLEAR_ABOVE = 16;
    /** And never this close to where a player sleeps (their respawn point): a house of logs or dirt looks natural. */
    public static final int HOME = 48;
    /** What nature makes by itself, besides the block tags in natural(). */
    private static final java.util.Set<net.minecraft.world.level.block.Block> NATURAL = java.util.Set.of(
            Blocks.WATER, Blocks.LAVA, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM,
            Blocks.MUD, Blocks.MOSS_BLOCK, Blocks.PALE_MOSS_BLOCK, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.GRAVEL, Blocks.CLAY, Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.CALCITE, Blocks.OBSIDIAN,
            Blocks.MAGMA_BLOCK, Blocks.POWDER_SNOW, Blocks.SMOOTH_BASALT, Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.DRIPSTONE_BLOCK,
            Blocks.POINTED_DRIPSTONE, Blocks.SUGAR_CANE, Blocks.CACTUS, Blocks.CACTUS_FLOWER, Blocks.PUMPKIN, Blocks.MELON, Blocks.BAMBOO,
            Blocks.KELP, Blocks.KELP_PLANT, Blocks.SEA_PICKLE, Blocks.LILY_PAD, Blocks.SWEET_BERRY_BUSH, Blocks.COCOA, Blocks.BROWN_MUSHROOM,
            Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM, Blocks.AZALEA,
            Blocks.FLOWERING_AZALEA, Blocks.MOSS_CARPET, Blocks.PALE_MOSS_CARPET, Blocks.PALE_HANGING_MOSS, Blocks.BIG_DRIPLEAF,
            Blocks.BIG_DRIPLEAF_STEM, Blocks.SMALL_DRIPLEAF, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT, Blocks.SPORE_BLOSSOM,
            Blocks.MANGROVE_ROOTS, Blocks.BEE_NEST, Blocks.LEAF_LITTER, Blocks.FIREFLY_BUSH, Blocks.BUSH, Blocks.SHORT_DRY_GRASS,
            Blocks.TALL_DRY_GRASS, Blocks.WILDFLOWERS, Blocks.PINK_PETALS);
    private BlockPos target = BlockPos.ZERO;
    private Vec3 start = Vec3.ZERO;
    private int age;

    public FallingStar(EntityType<? extends FallingStar> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Maybe a star falls near this player: night, clear, in the Overworld, on natural ground. */
    public static void maybeFall(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || !level.isDarkOutside() || level.isRaining()
                || io.hookdev.bleep.BleepConfig.fallingStars() == 0 || player.getRandom().nextInt(io.hookdev.bleep.BleepConfig.fallingStars()) != 0) return;
        BlockPos at = landing(level, player.blockPosition(), player.getRandom());
        if (at != null) fall(level, at);
    }

    /** A spot 32 to 64 blocks away on natural ground (grass, dirt, sand, stone, snow), with nothing built near it and nobody's bed. */
    public static @Nullable BlockPos landing(ServerLevel level, BlockPos near, RandomSource random) {
        for (int i = 0; i < 12; i++) {
            double angle = random.nextDouble() * Math.PI * 2, distance = NEAR + random.nextInt(FAR - NEAR);
            int x = (int) Math.floor(near.getX() + Math.cos(angle) * distance), z = (int) Math.floor(near.getZ() + Math.sin(angle) * distance);
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            BlockState ground = level.getBlockState(at.below());
            if (!(BleepEntity.earthy(ground) || ground.is(BlockTags.SAND) || ground.is(BlockTags.BASE_STONE_OVERWORLD) || ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.GRAVEL))) continue;
            if (!level.getFluidState(at).isEmpty() || nearAHome(level, at) || built(level, at)) continue;
            return at;
        }
        return null;
    }

    /** Anything not natural close by (planks, a path, a torch, a fence, a chest...)? Then not here; nor where it is not loaded. */
    public static boolean built(ServerLevel level, BlockPos at) {
        for (int dx = -CLEAR; dx <= CLEAR; dx += 2 * CLEAR) for (int dz = -CLEAR; dz <= CLEAR; dz += 2 * CLEAR) if (!level.isLoaded(at.offset(dx, 0, dz))) return true;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-CLEAR, -CLEAR_BELOW, -CLEAR), at.offset(CLEAR, CLEAR_ABOVE, CLEAR))) {
            if (!natural(level.getBlockState(pos))) return true;
        }
        return false;
    }

    /** Within HOME of a player's respawn point (a bed, a respawn anchor) in this world? */
    public static boolean nearAHome(ServerLevel level, BlockPos at) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            var respawn = player.getRespawnConfig();
            if (respawn == null) continue;
            var where = respawn.respawnData().globalPos();
            if (where.dimension() == level.dimension() && where.pos().distSqr(at) < HOME * HOME) return true;
        }
        return false;
    }

    /** Made by nature, not by a player: the ground, stone, ores, trees, plants, snow and ice, water. Anything else counts as built. */
    public static boolean natural(BlockState state) {
        if (state.isAir() || NATURAL.contains(state.getBlock())) return true;
        if (state.is(BlockTags.LOGS)) { // trees, but not stripped logs or wood (bark all round): those are cut by a player
            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            return !name.startsWith("stripped_") && !name.endsWith("_wood");
        }
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.SNOW) || state.is(BlockTags.ICE) || state.is(BlockTags.TERRACOTTA)
                || state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES) || (state.is(BlockTags.REPLACEABLE) && !state.is(Blocks.FIRE));
    }

    /** Whether the crater may take this block: natural, breakable, and no water to drain. Never anything built. */
    public static boolean diggable(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && natural(state) && state.getFluidState().isEmpty() && state.getDestroySpeed(level, pos) >= 0;
    }

    /** Sends a star down onto this spot (tests, and maybeFall). */
    public static void fall(ServerLevel level, BlockPos at) {
        FallingStar star = BleepContent.FALLING_STAR.get().create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (star == null) return;
        RandomSource random = level.getRandom();
        star.target = at;
        star.start = new Vec3(at.getX() + 0.5 + (random.nextDouble() - 0.5) * 120, Math.min(level.getMaxY() - 2, at.getY() + 90), at.getZ() + 0.5 + (random.nextDouble() - 0.5) * 120);
        star.setPos(star.start);
        level.addFreshEntity(star);
        level.playSound(null, at, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.AMBIENT, 2.0f, 0.5f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        if (age == 0 && start.equals(Vec3.ZERO)) { // summoned (/summon bleep:falling_star ~ ~60 ~): straight down onto the ground below
            start = position();
            target = new BlockPos(getBlockX(), server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, getBlockX(), getBlockZ()), getBlockZ());
        }
        age++;
        Vec3 end = Vec3.atBottomCenterOf(target);
        setPos(start.lerp(end, Math.min(1, age / (double) FALL_TICKS)));
        // a trail of light, seen from far away
        server.sendParticles(ParticleTypes.END_ROD, true, true, getX(), getY(), getZ(), 4, 0.15, 0.15, 0.15, 0.01);
        server.sendParticles(ParticleTypes.FIREWORK, true, true, getX(), getY(), getZ(), 2, 0.3, 0.3, 0.3, 0.02);
        if (age >= FALL_TICKS) {
            impact(server, target);
            discard();
        }
    }

    /** It lands: a small crater lined with starstone, a fallen star in the middle; players and bleeps close by notice. */
    public static void impact(ServerLevel level, BlockPos at) {
        level.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 1.2f, 1.6f);
        level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 2.0f, 0.6f);
        level.sendParticles(ParticleTypes.EXPLOSION, true, true, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3, 1, 0.5, 1, 0);
        level.sendParticles(ParticleTypes.END_ROD, true, true, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 60, 1.5, 1, 1.5, 0.15);
        BlockPos bottom = crater(level, at);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(Vec3.atCenterOf(at)) < 128 * 128) player.sendSystemMessage(Component.translatable("message.bleep.star_fell"));
        }
        for (BleepEntity bleep : level.getEntitiesOfClass(BleepEntity.class, new AABB(at).inflate(64), b -> !b.abandoned())) bleep.sawStarFall(bottom);
    }

    /** Scoops out a bowl, lines it with starstone (here and there), and puts the fallen star at the bottom. Returns where. */
    public static BlockPos crater(ServerLevel level, BlockPos at) {
        BlockState starstone = BleepContent.STARSTONE.get().defaultBlockState();
        for (int dx = -CRATER; dx <= CRATER; dx++) for (int dz = -CRATER; dz <= CRATER; dz++) for (int dy = -2; dy <= 2; dy++) {
            if (dx * dx + dz * dz + dy * dy * 2.5 > CRATER * CRATER + 1) continue;
            BlockPos pos = at.offset(dx, dy, dz);
            BlockState state = level.getBlockState(pos);
            if (!diggable(level, pos, state)) continue;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        RandomSource random = level.getRandom();
        BlockPos bottom = at;
        for (int dx = -CRATER; dx <= CRATER; dx++) for (int dz = -CRATER; dz <= CRATER; dz++) {
            if (dx * dx + dz * dz > CRATER * CRATER + 1) continue;
            BlockPos pos = at.offset(dx, 2, dz);
            while (pos.getY() > at.getY() - 6 && level.getBlockState(pos.below()).isAir()) pos = pos.below();
            BlockPos floor = pos.below();
            if (dx == 0 && dz == 0) bottom = pos;
            else if (diggable(level, floor, level.getBlockState(floor)) && random.nextFloat() < 0.6f) level.setBlock(floor, starstone, 3);
        }
        if (diggable(level, bottom.below(), level.getBlockState(bottom.below()))) level.setBlock(bottom.below(), starstone, 3);
        level.setBlock(bottom, BleepContent.FALLEN_STAR.get().defaultBlockState(), 3);
        return bottom;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    /** A star in the sky is not saved: if the world closes while it falls, it never lands. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }
}
