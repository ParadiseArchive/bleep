package io.hookdev.bleep.network;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.BleepSightings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** To the player who looks at their Bleep Tracker: each of their bleeps as it is now, or as it was last seen. */
public record TrackerData(List<BleepSightings.Seen> bleeps) implements CustomPacketPayload {
    public static final Type<TrackerData> TYPE = new Type<>(Bleep.id("tracker"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrackerData> CODEC = CustomPacketPayload.codec(TrackerData::write, TrackerData::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(bleeps.size());
        for (BleepSightings.Seen s : bleeps) {
            buf.writeUtf(s.name());
            buf.writeUtf(s.dimension());
            buf.writeInt(s.x());
            buf.writeInt(s.y());
            buf.writeInt(s.z());
            buf.writeVarInt(s.battery());
            buf.writeUtf(s.activity());
            buf.writeInt(s.colour());
        }
    }

    private static TrackerData read(RegistryFriendlyByteBuf buf) {
        List<BleepSightings.Seen> bleeps = new ArrayList<>();
        for (int i = Math.min(buf.readVarInt(), 100); i > 0; i--) {
            bleeps.add(new BleepSightings.Seen(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readUtf(), buf.readInt()));
        }
        return new TrackerData(bleeps);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
