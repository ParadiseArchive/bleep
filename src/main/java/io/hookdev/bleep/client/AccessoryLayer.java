package io.hookdev.bleep.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.hookdev.bleep.Bleep;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

/** Draws the hat and sticker a bleep wears (AccessoryModel), in their own colours: dye does not paint them. */
public class AccessoryLayer extends RenderLayer<BleepRenderState, BleepModel> {
    private static final Identifier TEXTURE = Bleep.id("textures/entity/bleep/accessories.png");
    private final AccessoryModel model;

    public AccessoryLayer(RenderLayerParent<BleepRenderState, BleepModel> parent, AccessoryModel model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, BleepRenderState state, float yRot, float xRot) {
        if ((state.hat == null && state.sticker == null) || state.isInvisible) return;
        renderColoredCutoutModel(model, TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
    }
}
