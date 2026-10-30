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
        BleepGameTests.register(modEventBus);
        modEventBus.addListener((EntityAttributeCreationEvent event) -> event.put(BleepContent.BLEEP.get(), BleepEntity.createAttributes().build()));
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1").playToServer(BleepCommand.TYPE, BleepCommand.CODEC, BleepCommand::handle));
        NeoForge.EVENT_BUS.addListener(Bleep::comeHomeAfterRespawn);
        NeoForge.EVENT_BUS.addListener(Bleep::fightWon);
        LOGGER.info("Bleep loaded. Bleep bloop.");
    }

    /**
     * A player who respawns near their bleep's homebase (their bed is there) finds their bleep waiting at home, wherever
     * it was left.
     */
    private static void comeHomeAfterRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity().level() instanceof ServerLevel level)) return;
        var player = event.getEntity();
        for (BleepEntity bleep : level.getEntities(BleepContent.BLEEP.get(), b -> b.isOwnedBy(player) && b.homeHere())) {
            if (bleep.home().pos().closerToCenterThan(player.position(), 32)) bleep.beamHome();
        }
    }

    /** A player beat a monster: their bleeps close by won that fight together with them. */
    private static void fightWon(LivingDeathEvent event) {
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
