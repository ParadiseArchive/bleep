package io.hookdev.bleep.item;

import io.hookdev.bleep.BleepOwners;
import io.hookdev.bleep.BleepSightings;
import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.network.TrackerData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Bleep Tracker (owner, 31 Oct 2026): right-click it and its little screen lists all your bleeps, their battery,
 * what they are doing, and an arrow to each (client/TrackerScreen). A bleep in a chunk that is not loaded shows where
 * and how it was last seen (BleepSightings).
 */
public class TrackerItem extends Item {
    public TrackerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server && server.connection.hasChannel(TrackerData.TYPE)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(server, new TrackerData(bleeps(server)));
        }
        return InteractionResult.SUCCESS;
    }

    /** The player's bleeps: the ones that are loaded as they are now, the others as they were last seen. */
    public static List<BleepSightings.Seen> bleeps(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        List<BleepSightings.Seen> list = new ArrayList<>();
        for (UUID id : BleepOwners.get(level).of(player.getUUID())) {
            BleepSightings.Seen seen = null;
            for (ServerLevel world : level.getServer().getAllLevels()) {
                if (world.getEntity(id) instanceof BleepEntity bleep) seen = bleep.sighting();
            }
            if (seen == null) seen = BleepSightings.get(level).last(id);
            if (seen != null) list.add(seen);
        }
        return list;
    }
}
