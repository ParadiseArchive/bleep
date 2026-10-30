package io.hookdev.bleep.item;

import io.hookdev.bleep.BleepContent;
import io.hookdev.bleep.entity.BleepEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The Bleep Core, the heart of a bleep. Made of bedrock, so a bleep can never break. Use it on the ground with a
 * display, tracks and arms in your inventory, and a bleep is put together there, part by part, until its screen comes
 * on for the first time. In creative the other parts are not needed.
 */
public class BleepCoreItem extends Item {
    public BleepCoreItem(Item.Properties properties) {
        super(properties);
    }

    /** The other parts a bleep is made of. */
    public static List<Item> parts() {
        return List.of(BleepContent.BLEEP_DISPLAY.get(), BleepContent.BLEEP_TRACKS.get(), BleepContent.BLEEP_ARMS.get());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (!(context.getLevel() instanceof ServerLevel level) || player == null) return InteractionResult.SUCCESS;
        if (io.hookdev.bleep.BleepOwners.get(level).full(player.getUUID())) { // ten is plenty
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.bleep.too_many", io.hookdev.bleep.BleepOwners.MAX));
            return InteractionResult.FAIL;
        }
        if (!player.isCreative()) {
            List<Item> missing = new ArrayList<>();
            for (Item part : parts()) if (find(player, part) < 0) missing.add(part);
            if (!missing.isEmpty()) {
                MutableComponent names = Component.empty();
                for (int i = 0; i < missing.size(); i++) names.append(i == 0 ? "" : ", ").append(missing.get(i).getName(new ItemStack(missing.get(i))));
                player.sendOverlayMessage(Component.translatable("message.bleep.missing_parts", names));
                return InteractionResult.FAIL;
            }
            for (Item part : parts()) player.getInventory().getItem(find(player, part)).shrink(1);
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        BleepEntity bleep = BleepContent.BLEEP.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (bleep == null) return InteractionResult.FAIL;
        // facing the player who built it
        bleep.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180, 0);
        bleep.setYHeadRot(bleep.getYRot());
        bleep.setYBodyRot(bleep.getYRot());
        bleep.tame(player);
        bleep.assemble();
        level.addFreshEntity(bleep);
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }

    /** The inventory slot holding this part, or -1. */
    private static int find(Player player, Item part) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (inventory.getItem(slot).is(part)) return slot;
        return -1;
    }
}
