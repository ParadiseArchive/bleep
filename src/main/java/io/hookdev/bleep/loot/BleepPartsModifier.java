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
 * Puts the parts of a bleep in chests around the world (the design doc's places): the core in an ominous vault of the
 * Trial Chambers, the display in the Ancient City, the tracks at a village smith, the arms in a bastion. The places and
 * chances live here rather than in loot table files, because 26.3 and 26.1 read those differently.
 */
public final class BleepPartsModifier implements IGlobalLootModifier {
    public static final BleepPartsModifier INSTANCE = new BleepPartsModifier();
    public static final MapCodec<BleepPartsModifier> CODEC = MapCodec.unit(INSTANCE);

    /** A part, the chest it can be found in, and the chance per chest. */
    public record Find(Supplier<? extends Item> part, Identifier chest, float chance) {}

    public static final List<Find> FINDS = List.of(
            new Find(BleepContent.BLEEP_CORE, Identifier.withDefaultNamespace("chests/trial_chambers/reward_ominous"), 0.10f),
            new Find(BleepContent.BLEEP_DISPLAY, Identifier.withDefaultNamespace("chests/ancient_city"), 0.25f),
            new Find(BleepContent.BLEEP_TRACKS, Identifier.withDefaultNamespace("chests/village/village_weaponsmith"), 0.30f),
            new Find(BleepContent.BLEEP_TRACKS, Identifier.withDefaultNamespace("chests/village/village_toolsmith"), 0.30f),
            new Find(BleepContent.BLEEP_ARMS, Identifier.withDefaultNamespace("chests/bastion_treasure"), 0.50f),
            new Find(BleepContent.BLEEP_ARMS, Identifier.withDefaultNamespace("chests/bastion_other"), 0.20f),
            new Find(BleepContent.BLEEP_ARMS, Identifier.withDefaultNamespace("chests/bastion_bridge"), 0.20f));

    private BleepPartsModifier() {}

    @Override
    public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
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
