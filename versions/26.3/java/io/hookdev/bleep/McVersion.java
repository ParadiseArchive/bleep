package io.hookdev.bleep;

import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;

/** What differs between Minecraft 26.1 and 26.3 outside the client (this is the 26.3 one). */
public final class McVersion {
    private McVersion() {}

    /** All the text on a sign, front and back. */
    public static String signText(SignBlockEntity sign) {
        StringBuilder text = new StringBuilder();
        for (SignTextSlot slot : SignTextSlot.values()) {
            for (var line : sign.getText(slot).getMessages(false)) text.append(line.getString()).append('\n');
        }
        return text.toString();
    }
}
