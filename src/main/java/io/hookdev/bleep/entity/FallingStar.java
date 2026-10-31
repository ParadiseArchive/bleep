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

    /** A spot 32 to 64 blocks away on natural ground (grass, dirt, sand, stone, snow), with nothing built near it. */
    public static @Nullable BlockPos landing(ServerLevel level, BlockPos near, RandomSource random) {
        for (int i = 0; i < 12; i++) {
            double angle = random.nextDouble() * Math.PI * 2, distance = NEAR + random.nextInt(FAR - NEAR);
            int x = (int) Math.floor(near.getX() + Math.cos(angle) * distance), z = (int) Math.floor(near.getZ() + Math.sin(angle) * distance);
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            BlockState ground = level.getBlockState(at.below());
            if (!(BleepEntity.earthy(ground) || ground.is(BlockTags.SAND) || ground.is(BlockTags.BASE_STONE_OVERWORLD) || ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.GRAVEL))) continue;
            if (!level.getFluidState(at).isEmpty() || built(level, at)) continue;
            return at;
        }
        return null;
    }

    /** Anything built close by (a chest, a sign, a bed: anything with a block entity)? Then not here. */
    private static boolean built(ServerLevel level, BlockPos at) {
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-6, -3, -6), at.offset(6, 3, 6))) if (level.getBlockEntity(pos) != null) return true;
        return false;
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
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null) continue;
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
            else if (level.getBlockState(floor).getDestroySpeed(level, floor) >= 0 && random.nextFloat() < 0.6f) level.setBlock(floor, starstone, 3);
        }
        level.setBlock(bottom.below(), starstone, 3);
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
