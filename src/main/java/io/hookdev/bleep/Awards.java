package io.hookdev.bleep;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The advancements (owner, 31 Oct 2026). The root (a Bleep Core) and Make a Wish (a star shard) come from the
 * inventory; the others have an impossible criterion and are awarded here, when it happens.
 */
public final class Awards {
    private Awards() {}

    public static void award(@Nullable Player player, String id) {
        if (!(player instanceof ServerPlayer server)) return;
        var advancement = ((ServerLevel) server.level()).getServer().getAdvancements().get(Bleep.id(id));
        if (advancement != null) server.getAdvancements().award(advancement, "done");
    }
}
