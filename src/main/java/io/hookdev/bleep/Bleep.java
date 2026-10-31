package io.hookdev.bleep;

import com.mojang.logging.LogUtils;
import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.gametest.BleepGameTests;
import io.hookdev.bleep.network.BleepCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/**
 * Bleep: small robots on tracks with a display for a face. Pets with a character, that never die: they only run out
 * of battery.
 */
@Mod(Bleep.MODID)
public class Bleep {
    public static final String MODID = "bleep";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Bleep(IEventBus modEventBus, ModContainer modContainer) {
        BleepContent.register(modEventBus);
        modContainer.registerConfig(McVersion.WORLD_CONFIG, BleepConfig.SPEC);
        BleepGameTests.register(modEventBus);
        modEventBus.addListener((EntityAttributeCreationEvent event) -> event.put(BleepContent.BLEEP.get(), BleepEntity.createAttributes().build()));
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("2").playToServer(BleepCommand.TYPE, BleepCommand.CODEC, BleepCommand::handle)
                .playToServer(io.hookdev.bleep.network.PhotoTaken.TYPE, io.hookdev.bleep.network.PhotoTaken.CODEC, io.hookdev.bleep.network.PhotoTaken::handle)
                .playToClient(io.hookdev.bleep.network.PhotoRequest.TYPE, io.hookdev.bleep.network.PhotoRequest.CODEC)
                .playToClient(io.hookdev.bleep.network.TrackerData.TYPE, io.hookdev.bleep.network.TrackerData.CODEC)
                .playToServer(io.hookdev.bleep.network.BleepRename.TYPE, io.hookdev.bleep.network.BleepRename.CODEC, io.hookdev.bleep.network.BleepRename::handle)
                .playToClient(io.hookdev.bleep.network.BleepediaData.TYPE, io.hookdev.bleep.network.BleepediaData.CODEC));
        modEventBus.addListener(Bleep::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(Bleep::comeHomeAfterRespawn);
        NeoForge.EVENT_BUS.addListener(Bleep::fightWon);
        NeoForge.EVENT_BUS.addListener(Bleep::guardTheGrave);
        // treasure hunt: digging where it buried something; tossing a thing or opening a container while it counts
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.block.BreakBlockEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level) for (BleepEntity bleep : level.getEntitiesOfClass(BleepEntity.class, new net.minecraft.world.phys.AABB(event.getPos()).inflate(64), b -> b.treasure() != null)) bleep.dugUp(event.getPlayer(), event.getPos());
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.item.ItemTossEvent event) -> hid(event.getPlayer(), event.getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) -> {
            if (event.getLevel().getBlockEntity(event.getPos()) instanceof net.minecraft.world.Container) hid(event.getEntity(), event.getPos().immutable());
        });
        // little reactions on its screen to what its owner does
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) -> {
            if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player && event.getItem().has(net.minecraft.core.component.DataComponents.FOOD)) react(player, io.hookdev.bleep.entity.Display.YUM);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open event) -> {
            if (event.getContainer() instanceof net.minecraft.world.inventory.ChestMenu) {
                react(event.getEntity(), io.hookdev.bleep.entity.Display.CURIOUS);
                recall(event.getEntity(), "tidy");
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingFallEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player && event.getDistance() > 4) react(player, io.hookdev.bleep.entity.Display.SWEAT);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerXpEvent.LevelChange event) -> {
            if (event.getLevels() > 0) react(event.getEntity(), io.hookdev.bleep.entity.Display.SPARKLE);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent event) -> {
            if (event.getAdvancement().value().display().isPresent()) react(event.getEntity(), io.hookdev.bleep.entity.Display.SPARKLE); // not the recipe ones
        });
        // now and then an abandoned bleep somewhere out of sight of a player
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player && player.tickCount % io.hookdev.bleep.entity.Abandoned.EVERY == 0 && player.tickCount > 0) {
                io.hookdev.bleep.entity.Abandoned.maybeSpawn(player);
            }
            // and on a clear night, very rarely, a star falls
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player && player.tickCount % io.hookdev.bleep.entity.FallingStar.EVERY == 600) {
                io.hookdev.bleep.entity.FallingStar.maybeFall(player);
            }
        });
        LOGGER.info("Bleep loaded. Bleep bloop.");
    }

    /** A homebase takes FE from any mod's cables, and redstone and a parked bleep's things from hoppers and pipes. */
    private static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, BleepContent.HOMEBASE_ENTITY.get(), (home, side) -> home.energy());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, BleepContent.HOMEBASE_ENTITY.get(), (home, side) -> home.items());
    }

    /**
     * A player who respawns near their bleep's homebase (their bed is there) finds their bleep waiting at home, wherever
     * it was left.
     */
    private static void comeHomeAfterRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity().level() instanceof ServerLevel level)) return;
        var player = event.getEntity();
        for (BleepEntity bleep : level.getEntities(BleepContent.BLEEP.get(), b -> b.isOwnedBy(player) && b.homeHere() && b.guarding() == null)) {
            if (bleep.home().pos().closerToCenterThan(player.position(), 32)) bleep.beamHome();
        }
    }

    /** A player died: their bleeps (within 64 blocks) go to the spot and keep their things safe for them. */
    private static void guardTheGrave(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        for (BleepEntity bleep : level.getEntitiesOfClass(BleepEntity.class, player.getBoundingBox().inflate(64), b -> b.isOwnedBy(player))) {
            bleep.guardGrave(player.blockPosition());
        }
    }

    /** Its owner hid something while their bleep counted (an ItemEntity or a container's position). */
    private static void hid(net.minecraft.world.entity.player.Player player, Object what) {
        if (!(player.level() instanceof ServerLevel level)) return;
        for (BleepEntity bleep : level.getEntitiesOfClass(BleepEntity.class, player.getBoundingBox().inflate(32), b -> b.isOwnedBy(player) && b.treasureMode() == BleepEntity.FIND_COUNTING)) bleep.sawHidden(what);
    }

    /** Something its owner did reminds their bleeps close by of an answer they gave. */
    private static void recall(net.minecraft.world.entity.player.Player player, String question) {
        if (!(player.level() instanceof ServerLevel level)) return;
        for (BleepEntity bleep : level.getEntitiesOfClass(BleepEntity.class, player.getBoundingBox().inflate(16), b -> b.isOwnedBy(player))) bleep.recall(question);
    }

    /** Its owner did something: their bleeps within 16 blocks react on their screens. */
    public static void react(net.minecraft.world.entity.player.Player player, io.hookdev.bleep.entity.Display reaction) {
        if (!(player.level() instanceof ServerLevel level)) return;
        for (BleepEntity bleep : level.getEntitiesOfClass(BleepEntity.class, player.getBoundingBox().inflate(16), b -> b.isOwnedBy(player))) bleep.react(reaction);
    }

    /** A player beat a monster: their bleeps close by won that fight together with them. (An animal: a tear.) */
    private static void fightWon(LivingDeathEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.animal.Animal && !(event.getEntity() instanceof BleepEntity)
                && event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            react(player, io.hookdev.bleep.entity.Display.TEAR);
            if (event.getEntity().getType() == net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("pig"))) recall(player, "pigs");
            return;
        }
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Creeper && event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player player) recall(player, "creepers");
        if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy) || !(event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player player)
                || !(event.getEntity().level() instanceof ServerLevel level)) return;
        for (BleepEntity bleep : level.getEntitiesOfClass(BleepEntity.class, event.getEntity().getBoundingBox().inflate(BleepEntity.TOGETHER), b -> b.isOwnedBy(player))) {
            bleep.foughtTogether();
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
