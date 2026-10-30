package io.hookdev.bleep.client;

import io.hookdev.bleep.entity.Emotion;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the renderer needs to draw a bleep this frame. */
public class BleepRenderState extends LivingEntityRenderState {
    public Emotion emotion = Emotion.HAPPY;
    /** Its entity id: each bleep blinks at its own moment. */
    public int id;
}
