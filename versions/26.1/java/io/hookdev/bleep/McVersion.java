package io.hookdev.bleep;

import net.minecraft.world.level.block.entity.SignBlockEntity;

/** What differs between Minecraft 26.1 and 26.3 outside the client (this is the 26.1 one). */
public final class McVersion {
    private McVersion() {}

    /** The kind of config that belongs to a world and is shared with its players. */
    public static final net.neoforged.fml.config.ModConfig.Type WORLD_CONFIG = net.neoforged.fml.config.ModConfig.Type.SERVER;

    /** All the text on a sign, front and back. */
    public static String signText(SignBlockEntity sign) {
        StringBuilder text = new StringBuilder();
        for (var side : new net.minecraft.world.level.block.entity.SignText[] {sign.getFrontText(), sign.getBackText()}) {
            for (int line = 0; line < 4; line++) text.append(side.getMessage(line, false).getString()).append('\n');
        }
        return text.toString();
    }

    /** Swings its arm, for everyone to see. */
    public static void swing(net.minecraft.world.entity.LivingEntity entity) {
        entity.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }
}
