package io.hookdev.bleep.loot;

import com.mojang.serialization.MapCodec;
import io.hookdev.bleep.BleepContent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;

/**
 * Puts the Bleep Core in chests around the world: in dungeons and abandoned mineshafts, deep down where the bedrock is
 * (the owner, 30 Oct 2026: easy enough to find early on; it can also be crafted, at a price, and the other parts are crafted). The places and chances live here
 * rather than in loot table files, because 26.3 and 26.1 read those differently.
 */
public final class BleepPartsModifier implements IGlobalLootModifier {
    public static final BleepPartsModifier INSTANCE = new BleepPartsModifier();
    public static final MapCodec<BleepPartsModifier> CODEC = MapCodec.unit(INSTANCE);

    /** A part, the chest it can be found in, and the chance per chest. */
    public record Find(Supplier<? extends Item> part, Identifier chest, float chance) {}

    public static final List<Find> FINDS = List.of(
            new Find(BleepContent.BLEEP_CORE, Identifier.withDefaultNamespace("chests/simple_dungeon"), 0.25f),
            new Find(BleepContent.BLEEP_CORE, Identifier.withDefaultNamespace("chests/abandoned_mineshaft"), 0.15f));

    private BleepPartsModifier() {}

    @Override
    public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!io.hookdev.bleep.BleepConfig.coreInChests()) return generatedLoot;
        Identifier table = context.getQueriedLootTableId();
        for (Find find : FINDS) {
            if (find.chest().equals(table) && context.getRandom().nextFloat() < find.chance()) generatedLoot.add(new ItemStack(find.part().get()));
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    public int priority() {
        return DEFAULT_PRIORITY;
    }
}
