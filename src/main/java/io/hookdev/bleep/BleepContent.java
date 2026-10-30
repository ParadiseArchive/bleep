package io.hookdev.bleep;

import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.item.BleepCoreItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Everything Bleep adds to the game. */
public final class BleepContent {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Bleep.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Bleep.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Bleep.MODID);

    /** A bleep: about as wide as a block and a little taller. */
    public static final DeferredHolder<EntityType<?>, EntityType<BleepEntity>> BLEEP = ENTITIES.register("bleep",
            id -> EntityType.Builder.<BleepEntity>of(BleepEntity::new, MobCategory.CREATURE).sized(0.8f, 1.25f).eyeHeight(1.05f)
                    .clientTrackingRange(10).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    /**
     * The heart of a bleep. For now it boots a bleep where you use it; later a bleep is put together from the core and
     * the parts found around the world.
     */
    public static final DeferredItem<BleepCoreItem> BLEEP_CORE = ITEMS.registerItem("bleep_core", BleepCoreItem::new,
            p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.bleep"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> BLEEP_CORE.get().getDefaultInstance())
            .displayItems((parameters, output) -> output.accept(BLEEP_CORE.get()))
            .build());

    private BleepContent() {}

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
    }
}
