package io.hookdev.bleep;

import com.mojang.serialization.MapCodec;
import io.hookdev.bleep.block.HomebaseBlock;
import io.hookdev.bleep.entity.Accessory;
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
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Bleep.MODID);
    public static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Bleep.MODID);
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

    /** What a bleep can wear: hats, stickers, glasses, a jacket, a cape and things for its neck, each an item of its own. */
    public static final Map<Accessory, DeferredItem<Item>> ACCESSORIES = new EnumMap<>(Accessory.class);
    static {
        for (Accessory accessory : Accessory.values()) {
            ACCESSORIES.put(accessory, ITEMS.registerSimpleItem(accessory.id(), p -> p.stacksTo(accessory.slot == Accessory.Slot.STICKER ? 64 : 1)));
        }
    }

    /** The accessory this item is, or null. */
    public static Accessory accessory(net.minecraft.world.item.ItemStack stack) {
        for (var entry : ACCESSORIES.entrySet()) if (stack.is(entry.getValue().get())) return entry.getKey();
        return null;
    }

    /** A whistle: your bleeps come to you, or stay (sneaking). */
    public static final DeferredItem<io.hookdev.bleep.item.WhistleItem> WHISTLE = ITEMS.registerItem("whistle", io.hookdev.bleep.item.WhistleItem::new, p -> p.stacksTo(1));

    /** The Bleep Tracker: all your bleeps on a little screen, with an arrow to each. */
    public static final DeferredItem<io.hookdev.bleep.item.TrackerItem> TRACKER = ITEMS.registerItem("tracker", io.hookdev.bleep.item.TrackerItem::new, p -> p.stacksTo(1));

    /** A trailer to hitch behind your bleep: its storage becomes a big chest. */
    public static final DeferredItem<Item> TRAILER = ITEMS.registerSimpleItem("trailer", p -> p.stacksTo(1));

    /** A ball to throw for your bleep: it bounces, and your bleep brings it back. */
    public static final DeferredItem<io.hookdev.bleep.item.BallItem> BALL = ITEMS.registerItem("ball", io.hookdev.bleep.item.BallItem::new, p -> p.stacksTo(16));
    public static final DeferredHolder<EntityType<?>, EntityType<io.hookdev.bleep.entity.BallEntity>> BALL_ENTITY = ENTITIES.register("ball",
            id -> EntityType.Builder.<io.hookdev.bleep.entity.BallEntity>of(io.hookdev.bleep.entity.BallEntity::new, MobCategory.MISC).sized(0.25f, 0.25f)
                    .clientTrackingRange(4).updateInterval(5).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    /** Upgrade chips, installed for good: speed, shield, a bigger battery. */
    public static final Map<io.hookdev.bleep.entity.Chip, DeferredItem<Item>> CHIPS = new EnumMap<>(io.hookdev.bleep.entity.Chip.class);
    static {
        for (io.hookdev.bleep.entity.Chip chip : io.hookdev.bleep.entity.Chip.values()) {
            CHIPS.put(chip, ITEMS.registerSimpleItem(chip.id(), p -> p.stacksTo(16).rarity(Rarity.UNCOMMON)));
        }
    }

    /** The chip this item is, or null. */
    public static io.hookdev.bleep.entity.@org.jspecify.annotations.Nullable Chip chip(net.minecraft.world.item.ItemStack stack) {
        for (var entry : CHIPS.entrySet()) if (stack.is(entry.getValue().get())) return entry.getKey();
        return null;
    }

    /** One full charge for a bleep, carried along: its bar shows what is left. It fills up again on a homebase. */
    public static final DeferredItem<Item> POWER_BANK = ITEMS.registerSimpleItem("power_bank", p -> p.durability(100).rarity(Rarity.UNCOMMON));

    /** A bleep's charging pad: an empty bleep goes there to charge, and the Go Home button sends it there. */
    public static final DeferredBlock<HomebaseBlock> HOMEBASE = BLOCKS.registerBlock("homebase", HomebaseBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> state.getValue(HomebaseBlock.CHARGING) ? 7 : 0));
    public static final DeferredItem<BlockItem> HOMEBASE_ITEM = ITEMS.registerSimpleBlockItem(HOMEBASE);
    /** The homebase's own battery: FE, filled with redstone or by cables. */
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<io.hookdev.bleep.block.HomebaseBlockEntity>> HOMEBASE_ENTITY =
            BLOCK_ENTITIES.register("homebase", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(io.hookdev.bleep.block.HomebaseBlockEntity::new, HOMEBASE.get()));
    /** The FE in a broken homebase, kept in the item. */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<Integer>> STORED_ENERGY = COMPONENTS.register("stored_energy",
            () -> net.minecraft.core.component.DataComponentType.<Integer>builder().persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());

    /** Race flags: the chequered start (right-click: race!) and the course. */
    public static final DeferredBlock<io.hookdev.bleep.block.RaceFlagBlock> START_FLAG = BLOCKS.registerBlock("start_flag", p -> new io.hookdev.bleep.block.RaceFlagBlock(p, true),
            p -> p.mapColor(MapColor.WOOL).strength(0.5f).sound(SoundType.WOOD).noCollision().noOcclusion());
    public static final DeferredItem<BlockItem> START_FLAG_ITEM = ITEMS.registerSimpleBlockItem(START_FLAG);
    public static final DeferredBlock<io.hookdev.bleep.block.RaceFlagBlock> RACE_FLAG = BLOCKS.registerBlock("race_flag", p -> new io.hookdev.bleep.block.RaceFlagBlock(p, false),
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(0.5f).sound(SoundType.WOOD).noCollision().noOcclusion());
    public static final DeferredItem<BlockItem> RACE_FLAG_ITEM = ITEMS.registerSimpleBlockItem(RACE_FLAG);

    /** A display case: its bleep's museum, by its homebase. */
    public static final DeferredBlock<io.hookdev.bleep.block.DisplayCaseBlock> DISPLAY_CASE = BLOCKS.registerBlock("display_case", io.hookdev.bleep.block.DisplayCaseBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.GLASS).noOcclusion());
    public static final DeferredItem<BlockItem> DISPLAY_CASE_ITEM = ITEMS.registerSimpleBlockItem(DISPLAY_CASE);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<io.hookdev.bleep.block.DisplayCaseBlockEntity>> DISPLAY_CASE_ENTITY =
            BLOCK_ENTITIES.register("display_case", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(io.hookdev.bleep.block.DisplayCaseBlockEntity::new, DISPLAY_CASE.get()));

    /** From the stars: glowing rock with strange signs on it (it lines a crater), and the fallen star in the middle. */
    public static final DeferredBlock<io.hookdev.bleep.block.StarBlock> STARSTONE = BLOCKS.registerBlock("starstone", p -> new io.hookdev.bleep.block.StarBlock(p, false),
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(2.5f, 6.0f).sound(SoundType.AMETHYST).lightLevel(state -> 5).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> STARSTONE_ITEM = ITEMS.registerSimpleBlockItem(STARSTONE);
    public static final DeferredBlock<io.hookdev.bleep.block.StarBlock> FALLEN_STAR = BLOCKS.registerBlock("fallen_star", p -> new io.hookdev.bleep.block.StarBlock(p, true),
            p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0f, 6.0f).sound(SoundType.AMETHYST).lightLevel(state -> 13).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> FALLEN_STAR_ITEM = ITEMS.registerSimpleBlockItem(FALLEN_STAR);
    /** A sliver of a fallen star: eight of them and a block of diamond make a Bleep Core. */
    public static final DeferredItem<Item> STAR_SHARD = ITEMS.registerSimpleItem("star_shard", p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredHolder<EntityType<?>, EntityType<io.hookdev.bleep.entity.FallingStar>> FALLING_STAR = ENTITIES.register("falling_star",
            id -> EntityType.Builder.<io.hookdev.bleep.entity.FallingStar>of(io.hookdev.bleep.entity.FallingStar::new, MobCategory.MISC).sized(0.5f, 0.5f)
                    .clientTrackingRange(16).updateInterval(1).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

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
                output.accept(DISPLAY_CASE_ITEM.get());
                output.accept(START_FLAG_ITEM.get());
                output.accept(RACE_FLAG_ITEM.get());
                output.accept(STAR_SHARD.get());
                output.accept(STARSTONE_ITEM.get());
                output.accept(FALLEN_STAR_ITEM.get());
                output.accept(POWER_BANK.get());
                output.accept(BALL.get());
                output.accept(WHISTLE.get());
                output.accept(TRACKER.get());
                output.accept(TRAILER.get());
                CHIPS.values().forEach(item -> output.accept(item.get()));
                ACCESSORIES.values().forEach(item -> output.accept(item.get()));
            })
            .build());

    private BleepContent() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        COMPONENTS.register(modEventBus);
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
        SOUNDS.register(modEventBus);
        LOOT_MODIFIERS.register(modEventBus);
    }
}
