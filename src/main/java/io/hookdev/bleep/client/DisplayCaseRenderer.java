package io.hookdev.bleep.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.hookdev.bleep.block.DisplayCaseBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** What stands in a display case, floating in the middle behind the glass and turning slowly, as on a museum plinth. */
public class DisplayCaseRenderer implements BlockEntityRenderer<DisplayCaseBlockEntity, DisplayCaseRenderer.State> {
    public static class State extends BlockEntityRenderState {
        public @Nullable ItemStackRenderState item;
        public float turn;
    }

    private final ItemModelResolver itemModelResolver;

    public DisplayCaseRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DisplayCaseBlockEntity display, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(display, state, partialTicks, cameraPosition, breakProgress);
        state.item = null;
        if (display.isEmpty() || display.getLevel() == null) return;
        ItemStackRenderState item = new ItemStackRenderState();
        itemModelResolver.updateForTopItem(item, display.item(), ItemDisplayContext.FIXED, display.getLevel(), null, (int) display.getBlockPos().asLong());
        state.item = item;
        state.turn = (display.getLevel().getGameTime() + partialTicks) * 1.5f % 360;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.item == null) return;
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.6f, 0.5f);
        McVersionClient.turnY(poseStack, state.turn);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
