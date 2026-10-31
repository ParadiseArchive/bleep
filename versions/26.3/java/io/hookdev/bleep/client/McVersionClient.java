package io.hookdev.bleep.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The client calls that differ between Minecraft versions, as in 26.3. versions/26.1/java has the same class for 26.1.2. */
public final class McVersionClient {
    private McVersionClient() {}

    /** A screen (an inventory, a menu) is open over the world. */
    public static boolean screenOpen() {
        return Minecraft.getInstance().gui.screen() != null;
    }

    /** What the game draws the world and the hud into. */
    public static com.mojang.blaze3d.pipeline.RenderTarget mainTarget() {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget();
    }

    /** Turns what is drawn next about the up axis. */
    public static void turnY(com.mojang.blaze3d.vertex.PoseStack poseStack, float degrees) {
        poseStack.rotateDegrees(com.mojang.math.Axis.YP, degrees);
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }
}
