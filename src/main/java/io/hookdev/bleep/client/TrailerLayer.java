package io.hookdev.bleep.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.hookdev.bleep.Bleep;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

/** Draws its trailer (TrailerModel) behind a bleep that has one; not on the water, where it sits in its boat. */
public class TrailerLayer extends RenderLayer<BleepRenderState, BleepModel> {
    private static final Identifier TEXTURE = Bleep.id("textures/entity/bleep/trailer.png");
    private final TrailerModel model;

    public TrailerLayer(RenderLayerParent<BleepRenderState, BleepModel> parent, TrailerModel model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, BleepRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !state.trailer || state.boating) return;
        renderColoredCutoutModel(model, TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
    }
}
