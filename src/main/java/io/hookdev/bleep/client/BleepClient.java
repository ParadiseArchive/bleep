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
        });
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(BleepContent.BLEEP.get(), BleepRenderer::new));
        NeoForge.EVENT_BUS.addListener(BleepClient::tooltip);
    }

    /** Every Bleep item says what it is and where to find it (tooltip.bleep.<name>). */
    private static void tooltip(ItemTooltipEvent event) {
        var id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        String key = "tooltip.bleep." + id.getPath();
        if (!id.getNamespace().equals(Bleep.MODID) || !Language.getInstance().has(key)) return;
        for (String line : I18n.get(key).split("\n")) event.getToolTip().add(Component.literal(line).withStyle(ChatFormatting.GRAY));
    }
}
