package io.hookdev.bleep.client;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Emotion;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Draws a bleep: the white body, and on its display the face for how it feels, lit up so it shows in the dark. Every
 * few seconds it blinks; the start-up screen runs through its frames.
 */
public class BleepRenderer extends MobRenderer<BleepEntity, BleepRenderState, BleepModel> {
    private static final Identifier TEXTURE = Bleep.id("textures/entity/bleep/bleep.png");
    /** Ticks between blinks, and how long a blink lasts. */
    private static final int BLINK_EVERY = 90, BLINK = 3;

    public BleepRenderer(EntityRendererProvider.Context context) {
        super(context, new BleepModel(context.bakeLayer(BleepModel.LAYER)), 0.45f);
        this.addLayer(new LivingEntityEmissiveLayer<>(this, BleepRenderer::face, (state, ageInTicks) -> 1.0f,
                new BleepModel(context.bakeLayer(BleepModel.LAYER)), RenderTypes::eyes, false));
    }

    /** The face on the display: textures/entity/bleep/face/<emotion>.png, with _blink while it blinks. */
    private static Identifier face(BleepRenderState state) {
        Emotion emotion = state.emotion;
        if (emotion == Emotion.BOOT) return Bleep.id("textures/entity/bleep/face/boot_" + ((int) (state.ageInTicks / 8) % 3) + ".png");
        boolean blink = emotion != Emotion.ASLEEP && emotion != Emotion.SURPRISED
                && (int) (state.ageInTicks + state.id * 37) % BLINK_EVERY < BLINK;
        return Bleep.id("textures/entity/bleep/face/" + emotion.texture() + (blink ? "_blink" : "") + ".png");
    }

    @Override
    public Identifier getTextureLocation(BleepRenderState state) {
        return TEXTURE;
    }

    @Override
    public BleepRenderState createRenderState() {
        return new BleepRenderState();
    }

    @Override
    public void extractRenderState(BleepEntity bleep, BleepRenderState state, float partialTicks) {
        super.extractRenderState(bleep, state, partialTicks);
        state.emotion = bleep.emotion();
        state.id = bleep.getId();
    }
}
