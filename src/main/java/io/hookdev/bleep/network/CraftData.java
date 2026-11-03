package io.hookdev.bleep.network;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.entity.Memory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** To its owner, for the craft screen: what a bleep learned to make by watching, and how many of each it could make now. */
public record CraftData(int entityId, List<Memory.Craft> crafts, List<Integer> makeable) implements CustomPacketPayload {
    public static final Type<CraftData> TYPE = new Type<>(Bleep.id("crafts"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftData> CODEC = CustomPacketPayload.codec(CraftData::write, CraftData::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(crafts.size());
        for (int i = 0; i < crafts.size(); i++) {
            Memory.Craft c = crafts.get(i);
            buf.writeVarInt(c.width());
            buf.writeVarInt(c.height());
            buf.writeVarInt(c.items().size());
            for (String item : c.items()) buf.writeUtf(item);
            buf.writeUtf(c.result());
            buf.writeVarInt(c.count());
            buf.writeVarInt(makeable.get(i));
        }
    }

    private static CraftData read(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        List<Memory.Craft> crafts = new ArrayList<>();
        List<Integer> makeable = new ArrayList<>();
        for (int i = Math.min(buf.readVarInt(), Memory.CRAFTS); i > 0; i--) {
            int w = buf.readVarInt(), h = buf.readVarInt();
            List<String> items = new ArrayList<>();
            for (int k = Math.min(buf.readVarInt(), 9); k > 0; k--) items.add(buf.readUtf());
            crafts.add(new Memory.Craft(w, h, items, buf.readUtf(), buf.readVarInt()));
            makeable.add(buf.readVarInt());
        }
        return new CraftData(id, crafts, makeable);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
