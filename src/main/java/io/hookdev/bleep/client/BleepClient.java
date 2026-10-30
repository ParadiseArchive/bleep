package io.hookdev.bleep.client;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.BleepContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only setup. Never loaded on a dedicated server. */
@Mod(value = Bleep.MODID, dist = Dist.CLIENT)
public class BleepClient {
    public BleepClient(IEventBus modEventBus) {
        modEventBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) -> event.registerLayerDefinition(BleepModel.LAYER, BleepModel::createBodyLayer));
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(BleepContent.BLEEP.get(), BleepRenderer::new));
    }
}
