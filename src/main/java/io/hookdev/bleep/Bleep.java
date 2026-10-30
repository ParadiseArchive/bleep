package io.hookdev.bleep;

import com.mojang.logging.LogUtils;
import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.gametest.BleepGameTests;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
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
        LOGGER.info("Bleep loaded. Bleep bloop.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
