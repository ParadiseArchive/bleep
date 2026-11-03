package io.hookdev.bleep.client;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.BleepContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Client-only setup. Never loaded on a dedicated server. */
@Mod(value = Bleep.MODID, dist = Dist.CLIENT)
public class BleepClient {
    public BleepClient(IEventBus modEventBus) {
        modEventBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) -> {
            event.registerLayerDefinition(BleepModel.LAYER, BleepModel::createBodyLayer);
            event.registerLayerDefinition(AccessoryModel.LAYER, AccessoryModel::createLayer);
            event.registerLayerDefinition(BoatModel.LAYER, BoatModel::createLayer);
            event.registerLayerDefinition(SledModel.LAYER, SledModel::createLayer);
            event.registerLayerDefinition(TrailerModel.LAYER, TrailerModel::createLayer);
        });
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(BleepContent.BLEEP.get(), BleepRenderer::new));
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(BleepContent.FALLING_STAR.get(), net.minecraft.client.renderer.entity.NoopRenderer::new)); // only its trail of particles
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(BleepContent.BALL_ENTITY.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new));
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerBlockEntityRenderer(BleepContent.DISPLAY_CASE_ENTITY.get(), DisplayCaseRenderer::new));
        NeoForge.EVENT_BUS.addListener(BleepClient::tooltip);
        // the Bleepedia arrives when its owner opens it
        modEventBus.addListener((net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent event) ->
                event.register(io.hookdev.bleep.network.BleepediaData.TYPE, (data, context) -> ClientHooks.openBleepedia(data)));
        // a bleep says Click!: a tiny picture of what its owner sees
        modEventBus.addListener((net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent event) ->
                event.register(io.hookdev.bleep.network.PhotoRequest.TYPE, (request, context) -> PhotoCamera.snap(request)));
        // what it learned to make, for the craft screen
        modEventBus.addListener((net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent event) ->
                event.register(io.hookdev.bleep.network.CraftData.TYPE, (data, context) -> ClientHooks.openCrafts(data)));
        // the Bleep Tracker's screen
        modEventBus.addListener((net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent event) ->
                event.register(io.hookdev.bleep.network.TrackerData.TYPE, (data, context) -> McVersionClient.setScreen(new TrackerScreen(data))));
    }

    /** Every Bleep item says what it is and where to find it (tooltip.bleep.<name>). */
    private static void tooltip(ItemTooltipEvent event) {
        var id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        String key = "tooltip.bleep." + id.getPath();
        if (!id.getNamespace().equals(Bleep.MODID) || !Language.getInstance().has(key)) return;
        for (String line : I18n.get(key).split("\n")) event.getToolTip().add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        Integer stored = event.getItemStack().get(BleepContent.STORED_ENERGY.get()); // a homebase keeps its charge
        if (stored != null) event.getToolTip().add(Component.translatable("tooltip.bleep.homebase_stored",
                Math.round(100f * stored / io.hookdev.bleep.block.HomebaseBlockEntity.capacity())).withStyle(ChatFormatting.AQUA));
    }
}
