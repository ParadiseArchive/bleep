package io.hookdev.bleep.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * A photo printed on a map (owner, 31 Oct 2026), to hang in an item frame: a locked map (it never draws the land),
 * white above and below like a polaroid, the photo across its whole width, every pixel the nearest colour a map has.
 */
public final class PhotoPrint {
    /** Where the photo goes on the 128 x 128 map: as wide as the map (128 x 72), in the middle. */
    private static final int PRINT_H = 128 * Memory.Photo.H / Memory.Photo.W, TOP = (128 - PRINT_H) / 2;
    /** Every colour a map can show (packed id 4..255, the first four are see-through), as ARGB. */
    private static final int[] PALETTE = new int[256];
    static {
        for (int id = 0; id < 256; id++) PALETTE[id] = id < 4 ? 0 : MapColor.getColorFromPackedId(id);
    }

    private PhotoPrint() {}

    /** A new map with this photo on it, named. */
    public static ItemStack print(ServerLevel level, Memory.Photo photo, Component name) {
        MapItemSavedData data = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, level.dimension()).locked();
        byte white = nearest(0xFFF4F4F4);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int px = x * Memory.Photo.W / 128, py = (y - TOP) * Memory.Photo.H / PRINT_H;
                boolean inside = y >= TOP && y < TOP + PRINT_H;
                data.colors[x + y * 128] = inside ? nearest(photo.colour(px, py)) : white;
            }
        }
        var id = level.getFreeMapId();
        level.setMapData(id, data);
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponents.MAP_ID, id);
        map.set(DataComponents.CUSTOM_NAME, name);
        return map;
    }

    /** The map colour closest to this one. */
    static byte nearest(int argb) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255, best = 4, bestDistance = Integer.MAX_VALUE;
        for (int id = 4; id < 256; id++) {
            int c = PALETTE[id];
            if (c == 0) continue;
            int dr = ((c >> 16) & 255) - r, dg = ((c >> 8) & 255) - g, db = (c & 255) - b;
            int distance = dr * dr * 3 + dg * dg * 4 + db * db * 2; // the eye cares most for green
            if (distance < bestDistance) {
                bestDistance = distance;
                best = id;
            }
        }
        return (byte) best;
    }
}
