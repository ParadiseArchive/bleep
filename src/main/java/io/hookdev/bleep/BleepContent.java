package io.hookdev.bleep;

import com.mojang.serialization.MapCodec;
import io.hookdev.bleep.block.HomebaseBlock;
import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Emotion;
import io.hookdev.bleep.item.BleepCoreItem;
import io.hookdev.bleep.loot.BleepPartsModifier;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Everything Bleep adds to the game. */
public final class BleepContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Bleep.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Bleep.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Bleep.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Bleep.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Bleep.MODID);
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Bleep.MODID);

    /** A bleep: about as wide as a block and a little taller. */
    public static final DeferredHolder<EntityType<?>, EntityType<BleepEntity>> BLEEP = ENTITIES.register("bleep",
            id -> EntityType.Builder.<BleepEntity>of(BleepEntity::new, MobCategory.CREATURE).sized(0.8f, 1.25f).eyeHeight(1.05f)
                    .clientTrackingRange(10).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    /**
     * The heart of a bleep, made of bedrock: found deep down, or crafted at a price. Use it on the ground with the other three parts in your inventory and a
     * bleep is put together there (in creative, the parts are not needed).
     */
    public static final DeferredItem<BleepCoreItem> BLEEP_CORE = ITEMS.registerItem("bleep_core", BleepCoreItem::new,
            p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    /** The other parts, crafted: its face, its tracks and its arms. */
    public static final DeferredItem<Item> BLEEP_DISPLAY = ITEMS.registerSimpleItem("bleep_display", p -> p.stacksTo(16).rarity(Rarity.RARE));
    public static final DeferredItem<Item> BLEEP_TRACKS = ITEMS.registerSimpleItem("bleep_tracks", p -> p.stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> BLEEP_ARMS = ITEMS.registerSimpleItem("bleep_arms", p -> p.stacksTo(16).rarity(Rarity.RARE));

    /** One full charge for a bleep, carried along: its bar shows what is left. It fills up again on a homebase. */
    public static final DeferredItem<Item> POWER_BANK = ITEMS.registerSimpleItem("power_bank", p -> p.durability(100).rarity(Rarity.UNCOMMON));

    /** A bleep's charging pad: an empty bleep goes there to charge, and the Go Home button sends it there. */
    public static final DeferredBlock<HomebaseBlock> HOMEBASE = BLOCKS.registerBlock("homebase", HomebaseBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> state.getValue(HomebaseBlock.CHARGING) ? 7 : 0));
    public static final DeferredItem<BlockItem> HOMEBASE_ITEM = ITEMS.registerSimpleBlockItem(HOMEBASE);

    /** Its voice: a blip for every mood (tools/make-sounds.mjs: a new mood needs a new sound), a hurt buzz, and the clunk of being built. */
    public static final Map<Emotion, DeferredHolder<SoundEvent, SoundEvent>> VOICE = new EnumMap<>(Emotion.class);
    static {
        for (Emotion emotion : Emotion.values()) {
            VOICE.put(emotion, SOUNDS.register("entity.bleep." + emotion.texture(), SoundEvent::createVariableRangeEvent));
        }
    }
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = SOUNDS.register("entity.bleep.hurt", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> ASSEMBLE = SOUNDS.register("entity.bleep.assemble", SoundEvent::createVariableRangeEvent);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<BleepPartsModifier>> PARTS_IN_CHESTS =
            LOOT_MODIFIERS.register("parts", () -> BleepPartsModifier.CODEC);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.bleep"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> BLEEP_CORE.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(BLEEP_CORE.get());
                output.accept(BLEEP_DISPLAY.get());
                output.accept(BLEEP_TRACKS.get());
                output.accept(BLEEP_ARMS.get());
                output.accept(HOMEBASE_ITEM.get());
                output.accept(POWER_BANK.get());
            })
            .build());

    private BleepContent() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
        SOUNDS.register(modEventBus);
        LOOT_MODIFIERS.register(modEventBus);
    }
}
