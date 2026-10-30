package io.hookdev.bleep.block;

import io.hookdev.bleep.BleepContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

/**
 * Starstone and the fallen star (FallingStar): what they give is worked out here, not in a loot table, because 26.1
 * and 26.3 write loot tables differently. Starstone: one or two star shards. The fallen star: three to five, and half
 * the time a Bleep Core.
 */
public class StarBlock extends Block {
    private final boolean heart;

    public StarBlock(Properties properties, boolean heart) {
        super(properties);
        this.heart = heart;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        RandomSource random = params.getLevel().getRandom();
        List<ItemStack> drops = new ArrayList<>();
        drops.add(new ItemStack(BleepContent.STAR_SHARD.get(), heart ? 3 + random.nextInt(3) : 1 + random.nextInt(2)));
        if (heart && random.nextFloat() < 0.5f) drops.add(new ItemStack(BleepContent.BLEEP_CORE.get()));
        return drops;
    }
}
