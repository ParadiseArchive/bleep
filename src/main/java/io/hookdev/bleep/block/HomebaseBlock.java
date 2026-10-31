package io.hookdev.bleep.block;

import io.hookdev.bleep.BleepContent;
import io.hookdev.bleep.entity.BleepEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A bleep's homebase: a charging pad with a post at the back. Place it near your bleep and it becomes its home: an
 * empty bleep goes there to charge, and "Go Home" sends it there. The light on the post is on while it charges; the
 * four bars under it show its own battery ({@link HomebaseBlockEntity}), filled with redstone or FE.
 */
public class HomebaseBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty CHARGING = BooleanProperty.create("charging");
    /** The bars of its own battery: 0 empty .. 4 (nearly) full. */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty POWER = net.minecraft.world.level.block.state.properties.IntegerProperty.create("power", 0, 4);
    /** How far from a new homebase your bleep may be to move in. */
    public static final double LINK_RANGE = 32;
    /** The pad and the post behind it, for a homebase that faces north. */
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
            Block.box(1, 0, 1, 15, 2, 15), Block.box(5, 2, 12, 11, 14, 15)));

    public HomebaseBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CHARGING, false).setValue(POWER, 0));
    }

    /** The pad faces you as you place it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HomebaseBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Placed: your nearest bleep without a home moves in. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide() && by instanceof Player player) moveIn(level, pos, player);
    }

    /** Right-click: whose home this is and how full its battery is; if nobody lives here yet, your nearest bleep moves in. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BleepEntity resident = resident(level, pos);
        if (resident != null) {
            if (io.hookdev.bleep.BleepConfig.homebaseNeedsPower() && level.getBlockEntity(pos) instanceof HomebaseBlockEntity home) {
                player.sendOverlayMessage(Component.translatable("message.bleep.homebase_of_battery", resident.getDisplayName(), Math.round(home.fraction() * 100)));
            } else {
                player.sendOverlayMessage(Component.translatable("message.bleep.homebase_of", resident.getDisplayName()));
            }
        } else {
            moveIn(level, pos, player);
        }
        return InteractionResult.SUCCESS;
    }

    /** Redstone or a block of it goes into its battery; a power bank put against it fills up again, from its battery. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof HomebaseBlockEntity home)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (HomebaseBlockEntity.fuel(stack) > 0 && io.hookdev.bleep.BleepConfig.homebaseNeedsPower()) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            ItemStack used = player.getAbilities().instabuild ? stack.copy() : stack;
            if (home.feed(used)) {
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.2f + home.fraction() * 0.6f);
                player.sendOverlayMessage(Component.translatable("message.bleep.homebase_filled", Math.round(home.fraction() * 100)));
            } else {
                player.sendOverlayMessage(Component.translatable("message.bleep.homebase_full"));
            }
            return InteractionResult.SUCCESS;
        }
        if (!stack.is(BleepContent.POWER_BANK.get())) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (stack.getDamageValue() == 0) {
            player.sendOverlayMessage(Component.translatable("message.bleep.power_bank_full"));
        } else {
            int got = (int) home.draw(stack.getDamageValue());
            if (got <= 0) {
                player.sendOverlayMessage(Component.translatable("message.bleep.homebase_no_power"));
                return InteractionResult.SUCCESS;
            }
            stack.setDamageValue(stack.getDamageValue() - got);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.6f);
            player.sendOverlayMessage(stack.getDamageValue() == 0 ? Component.translatable("message.bleep.power_bank_charged")
                    : Component.translatable("message.bleep.power_bank_part", 100 - stack.getDamageValue()));
        }
        return InteractionResult.SUCCESS;
    }

    /** Broken: its charge goes along in the item. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof HomebaseBlockEntity home && home.stored() > 0) {
            for (ItemStack drop : drops) if (drop.is(BleepContent.HOMEBASE_ITEM.get())) drop.set(BleepContent.STORED_ENERGY.get(), home.stored());
        }
        return drops;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** A comparator reads its battery. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof HomebaseBlockEntity home ? home.signal() : 0;
    }

    /** The bleep that lives here, if it is loaded. */
    public static @Nullable BleepEntity resident(Level level, BlockPos pos) {
        GlobalPos here = GlobalPos.of(level.dimension(), pos);
        List<BleepEntity> bleeps = level.getEntitiesOfClass(BleepEntity.class, new AABB(pos).inflate(64), b -> here.equals(b.home()));
        return bleeps.isEmpty() ? null : bleeps.getFirst();
    }

    /** The player's nearest bleep moves in here: one without a home first. Anyone who lived here moves out. */
    public static void moveIn(Level level, BlockPos pos, Player player) {
        List<BleepEntity> own = level.getEntitiesOfClass(BleepEntity.class, new AABB(pos).inflate(LINK_RANGE), b -> b.isOwnedBy(player));
        BleepEntity bleep = own.stream().min(Comparator.comparing((BleepEntity b) -> b.home() != null)
                .thenComparingDouble(b -> b.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)))).orElse(null);
        if (bleep == null) {
            player.sendOverlayMessage(Component.translatable("message.bleep.homebase_empty"));
            return;
        }
        BleepEntity old = resident(level, pos);
        if (old != null && old != bleep) old.setHome(null);
        bleep.setHome(GlobalPos.of(level.dimension(), pos));
        io.hookdev.bleep.Awards.award(player, "home_sweet_home");
        player.sendOverlayMessage(Component.translatable("message.bleep.moved_in", bleep.getDisplayName()));
    }

    /** The light on the post, while a bleep charges here. */
    public static void setCharging(Level level, BlockPos pos, boolean charging) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof HomebaseBlock && state.getValue(CHARGING) != charging) {
            level.setBlock(pos, state.setValue(CHARGING, charging), Block.UPDATE_ALL);
        }
    }

    public static boolean is(BlockState state) {
        return state.is(BleepContent.HOMEBASE.get());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CHARGING, POWER);
    }
}
