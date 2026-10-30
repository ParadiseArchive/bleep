package io.hookdev.bleep.client;

import io.hookdev.bleep.entity.Emotion;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;

/** What the renderer needs to draw a bleep this frame, with what it holds. */
public class BleepRenderState extends ArmedEntityRenderState {
    public Emotion emotion = Emotion.HAPPY;
    /** Treasure hunt: which way the treasure is from the bleep, and how far the player is from it. */
    public float treasureAngle = Float.NaN, treasureDistance = 99;
    /** What it dreams of (see BleepEntity.dream), and for a mob its name. */
    public String dream = "", dreamName = "";
    /** Its entity id: each bleep blinks at its own moment. */
    public int id;
    /** Something in its hand: it holds both arms out to carry it. */
    public boolean carrying;
    /** Ticks left until it is put together (0: it is whole). */
    public int assembly;
    /** Its colour (ARGB, -1 for its own white), what it wears, what its display shows, its name and where home is (degrees, NaN if unknown). */
    public int color = -1;
    public io.hookdev.bleep.entity.Accessory hat, sticker;
    public io.hookdev.bleep.entity.Display display = io.hookdev.bleep.entity.Display.FACE;
    public String name = "";
    public float homeAngle = Float.NaN;
    /** Dancing to music; switched off and forgotten (an abandoned bleep). */
    public boolean dancing, abandoned;
}
