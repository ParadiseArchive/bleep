package io.hookdev.bleep.client;

import io.hookdev.bleep.entity.Emotion;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;

/** What the renderer needs to draw a bleep this frame, with what it holds. */
public class BleepRenderState extends ArmedEntityRenderState {
    public Emotion emotion = Emotion.HAPPY;
    /** Its entity id: each bleep blinks at its own moment. */
    public int id;
    /** Something in its hand: it holds both arms out to carry it. */
    public boolean carrying;
    /** Ticks left until it is put together (0: it is whole). */
    public int assembly;
}
