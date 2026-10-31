package io.hookdev.bleep.block;

import io.hookdev.bleep.BleepConfig;
import io.hookdev.bleep.BleepContent;
import io.hookdev.bleep.entity.BleepEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.EmptyResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The homebase's own battery (owner, 31 Oct 2026: it has to be filled): a bleep charges from it, and an empty one
 * charges nothing. Filled with redstone (10 % of a bleep's battery a dust, 90 % a block) or with FE from any mod's
 * cables; full, it holds one full charge. Pack makers can make it free again (BleepConfig.homebaseNeedsPower). The
 * charge goes along in the item when it is broken. Hoppers and pipes reach a parked bleep's storage through it, and
 * redstone they bring goes into its battery.
 */
public class HomebaseBlockEntity extends BlockEntity {
    /** What a dust and a block of redstone are worth, in percent of a bleep's battery. */
    public static final int REDSTONE = 10, REDSTONE_BLOCK = 90;

    private final class Store extends SimpleEnergyHandler {
        Store() {
            super(capacity(), capacity(), 0);
        }

        /** Follows the config: a full homebase is one full charge. */
        Store fit() {
            capacity = capacity();
            maxInsert = capacity;
            if (energy > capacity) set(capacity);
            return this;
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            changed();
        }
    }

    private final Store energy = new Store();
    /** Part of an FE still owed by charging, so small steps add up. */
    private float owed;

    public HomebaseBlockEntity(BlockPos pos, BlockState state) {
        super(BleepContent.HOMEBASE_ENTITY.get(), pos, state);
    }

    public static int capacity() {
        return 100 * BleepConfig.fePerPercent();
    }

    public EnergyHandler energy() {
        return energy.fit();
    }

    public int stored() {
        return energy.fit().getAmountAsInt();
    }

    /** How full it is, 0 to 1. */
    public float fraction() {
        return stored() / (float) capacity();
    }

    /** Fills it straight away (redstone by hand, a test). */
    public void add(int fe) {
        energy.fit().set(Math.min(capacity(), stored() + fe));
    }

    /**
     * A bleep on it wants this many percent of its battery: takes the FE for it and says how many percent it got. Free
     * when the config says so.
     */
    public float draw(float percent) {
        if (!BleepConfig.homebaseNeedsPower()) return percent;
        float want = percent * BleepConfig.fePerPercent() + owed;
        int fe = (int) want;
        int have = stored();
        if (have <= 0) return 0;
        if (fe > have) {
            energy.set(0);
            owed = 0;
            return have / (float) BleepConfig.fePerPercent();
        }
        owed = want - fe;
        if (fe > 0) energy.set(have - fe);
        return percent;
    }

    /** Can it give anything at all? */
    public boolean hasPower() {
        return !BleepConfig.homebaseNeedsPower() || stored() > 0;
    }

    /** What a stack is worth as fuel, in FE each, or 0. */
    public static int fuel(ItemStack stack) {
        return stack.is(Items.REDSTONE) ? REDSTONE * BleepConfig.fePerPercent() : stack.is(Items.REDSTONE_BLOCK) ? REDSTONE_BLOCK * BleepConfig.fePerPercent() : 0;
    }

    /** Redstone or a block of it, used on the pad: in it goes, as long as some of it fits. True if it took one. */
    public boolean feed(ItemStack stack) {
        int fe = fuel(stack);
        if (fe == 0 || stored() >= capacity()) return false;
        add(fe);
        stack.shrink(1);
        return true;
    }

    // ---------- hoppers and pipes ----------

    /** Redstone in, for its battery: hoppers and pipes can fill it. */
    private final ResourceHandler<ItemResource> fuelSlot = new ResourceHandler<>() {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            return ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return 64;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return fuel(resource.toStack()) > 0;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            int each = fuel(resource.toStack());
            if (each == 0 || amount <= 0) return 0;
            Store store = energy.fit();
            int room = store.getCapacityAsInt() - store.getAmountAsInt();
            int count = Math.min(amount, (room + each - 1) / each); // the last one may fill it to the brim
            if (count <= 0) return 0;
            store.insert(Math.min(room, count * each), transaction);
            return count;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    };

    /** For hoppers and pipes: redstone for its battery, then the storage of the bleep parked on it. */
    public ResourceHandler<ItemResource> items() {
        BleepEntity bleep = parked();
        return new CombinedResourceHandler<>(fuelSlot, bleep == null ? EmptyResourceHandler.instance() : VanillaContainerWrapper.of(bleep.storage()));
    }

    /** The bleep that lives here and stands on it, keeping still. */
    public @Nullable BleepEntity parked() {
        if (level == null) return null;
        List<BleepEntity> bleeps = level.getEntitiesOfClass(BleepEntity.class, new AABB(worldPosition).inflate(0.5, 1, 0.5), b -> b.parked() && b.onHome() && b.home().pos().equals(worldPosition));
        return bleeps.isEmpty() ? null : bleeps.getFirst();
    }

    // ---------- the bars on its post, and comparators ----------

    private void changed() {
        setChanged();
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        int bars = stored() <= 0 ? 0 : Math.min(4, 1 + (int) (fraction() * 4));
        if (state.getBlock() instanceof HomebaseBlock && state.getValue(HomebaseBlock.POWER) != bars) {
            level.setBlock(worldPosition, state.setValue(HomebaseBlock.POWER, bars), Block.UPDATE_CLIENTS);
        }
        level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
    }

    /** For a comparator: 0 empty, 15 full. */
    public int signal() {
        int stored = stored();
        return stored <= 0 ? 0 : 1 + Math.round(fraction() * 14);
    }

    // ---------- the charge goes along in the item ----------

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (stored() > 0) components.set(BleepContent.STORED_ENERGY.get(), stored());
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Integer stored = components.get(BleepContent.STORED_ENERGY.get());
        if (stored != null) energy.fit().set(Math.max(0, Math.min(capacity(), stored)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
    }
}
