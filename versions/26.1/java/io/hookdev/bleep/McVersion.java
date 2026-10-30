package io.hookdev.bleep;

import net.minecraft.world.level.block.entity.SignBlockEntity;

/** What differs between Minecraft 26.1 and 26.3 outside the client (this is the 26.1 one). */
public final class McVersion {
    private McVersion() {}

    /** All the text on a sign, front and back. */
    public static String signText(SignBlockEntity sign) {
        StringBuilder text = new StringBuilder();
        for (var side : new net.minecraft.world.level.block.entity.SignText[] {sign.getFrontText(), sign.getBackText()}) {
            for (int line = 0; line < 4; line++) text.append(side.getMessage(line, false).getString()).append('\n');
        }
        return text.toString();
    }
}
