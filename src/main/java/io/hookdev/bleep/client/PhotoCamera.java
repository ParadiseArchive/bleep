package io.hookdev.bleep.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.hookdev.bleep.entity.Memory;
import io.hookdev.bleep.network.PhotoRequest;
import io.hookdev.bleep.network.PhotoTaken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * A bleep said Click!: a little picture of what its owner sees right now, in pixels. The frame is cropped to 16 : 9
 * from the top (the hotbar stays out) and shrunk to 96 x 54, each pixel the average of what it covers. (The first
 * try, 64 x 36 in 64 colours with a dither, was a blur of noise: owner, 31 Oct 2026.) Not while a screen is open.
 */
public final class PhotoCamera {
    private PhotoCamera() {}

    public static void snap(PhotoRequest request) {
        Minecraft minecraft = Minecraft.getInstance();
        if (McVersionClient.screenOpen() || minecraft.level == null) return;
        Screenshot.takeScreenshot(McVersionClient.mainTarget(), image -> {
            try (image) {
                ClientPacketDistributor.sendToServer(new PhotoTaken(request.entityId(), shrink(image)));
            }
        });
    }

    /** The picture, W x H, two bytes a pixel (Memory.Photo). */
    static byte[] shrink(NativeImage image) {
        int w = Memory.Photo.W, h = Memory.Photo.H;
        // the top 82% of the frame (above the hotbar), cut to 16 : 9 in the middle
        int srcH = Math.max(h, (int) (image.getHeight() * 0.82f)), srcW = Math.min(image.getWidth(), srcH * w / h);
        if (srcW < srcH * w / h) srcH = srcW * h / w;
        int x0 = (image.getWidth() - srcW) / 2;
        byte[] pixels = new byte[Memory.Photo.BYTES];
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int sx0 = x0 + px * srcW / w, sx1 = Math.max(sx0 + 1, x0 + (px + 1) * srcW / w);
                int sy0 = py * srcH / h, sy1 = Math.max(sy0 + 1, (py + 1) * srcH / h);
                long r = 0, g = 0, b = 0, n = 0;
                for (int y = sy0; y < sy1; y += 2) { // every other pixel is plenty
                    for (int x = sx0; x < sx1; x += 2) {
                        int c = image.getPixel(x, y);
                        r += (c >> 16) & 255;
                        g += (c >> 8) & 255;
                        b += c & 255;
                        n++;
                    }
                }
                Memory.Photo.put(pixels, px, py, (int) (r / n), (int) (g / n), (int) (b / n));
            }
        }
        return pixels;
    }
}
