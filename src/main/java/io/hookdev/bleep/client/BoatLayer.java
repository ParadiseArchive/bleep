package io.hookdev.bleep.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.hookdev.bleep.Bleep;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

/** Draws the little boat (BoatModel) under a bleep on the water, or its sled (SledModel) on snow, in their own colours. */
public class BoatLayer extends RenderLayer<BleepRenderState, BleepModel> {
    private static final Identifier TEXTURE = Bleep.id("textures/entity/bleep/boat.png"), SLED = Bleep.id("textures/entity/bleep/sled.png");
    private final BoatModel model;
    private final SledModel sled;

    public BoatLayer(RenderLayerParent<BleepRenderState, BleepModel> parent, BoatModel model, SledModel sled) {
        super(parent);
        this.model = model;
        this.sled = sled;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, BleepRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        if (state.boating) renderColoredCutoutModel(model, TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
        else if (state.sledding) renderColoredCutoutModel(sled, SLED, poseStack, collector, lightCoords, state, -1, 1);
    }
}
