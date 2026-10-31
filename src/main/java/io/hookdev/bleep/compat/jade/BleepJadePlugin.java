package io.hookdev.bleep.compat.jade;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.block.HomebaseBlock;
import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Bond;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade support: look at a bleep and see its battery, mood, what it is doing and your bond, instead of a health bar it
 * does not have; look at a homebase and see who lives there. Only loaded when Jade is installed.
 */
@WailaPlugin(Bleep.MODID)
public class BleepJadePlugin implements IWailaPlugin {
    private static final Identifier BLEEP = Bleep.id("bleep"), HOMEBASE = Bleep.id("homebase");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(HomeData.INSTANCE, HomebaseBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(BleepLines.INSTANCE, BleepEntity.class);
        registration.registerBlockComponent(HomeLines.INSTANCE, HomebaseBlock.class);
        // a bleep has a battery, not health: no hearts
        registration.addTooltipCollectedCallback((box, accessor) -> {
            if (accessor instanceof EntityAccessor entity && entity.getEntity() instanceof BleepEntity) box.getTooltip().remove(JadeIds.MC_ENTITY_HEALTH);
        });
    }

    /** Everything a bleep shows is already on the client (its synced data), so no server data is needed. */
    enum BleepLines implements IEntityComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof BleepEntity bleep)) return;
            if (bleep.assembly() > 0) {
                tooltip.add(Component.translatable("activity.bleep.starting").withStyle(ChatFormatting.GRAY));
                return;
            }
            float battery = bleep.battery();
            tooltip.add(Component.translatable("screen.bleep.battery").append(" " + Math.round(battery) + "%")
                    .withStyle(battery < BleepEntity.TIRED ? ChatFormatting.GOLD : ChatFormatting.AQUA));
            tooltip.add(Component.translatable("emotion.bleep." + bleep.emotion().texture()).append(" · ")
                    .append(Component.translatable(bleep.activity().key())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("screen.bleep.bond").append(": ").append(Component.translatable(Bond.key(bleep.bond())))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        @Override
        public Identifier getUid() {
            return BLEEP;
        }
    }

    /** Server side: who lives here (the bleep keeps where its home is), and how full its battery is. */
    enum HomeData implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            BleepEntity resident = HomebaseBlock.resident(accessor.getLevel(), accessor.getPosition());
            if (resident != null) data.putString("bleep_resident", resident.getDisplayName().getString());
            if (io.hookdev.bleep.BleepConfig.homebaseNeedsPower() && accessor.getBlockEntity() instanceof io.hookdev.bleep.block.HomebaseBlockEntity home) {
                data.putInt("bleep_battery", Math.round(home.fraction() * 100));
            }
        }

        @Override
        public Identifier getUid() {
            return HOMEBASE;
        }
    }

    enum HomeLines implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            String resident = accessor.getServerData().getStringOr("bleep_resident", "");
            tooltip.add(resident.isEmpty() ? Component.translatable("jade.bleep.nobody_home").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("message.bleep.homebase_of", resident).withStyle(ChatFormatting.AQUA));
            int battery = accessor.getServerData().getIntOr("bleep_battery", -1);
            if (battery >= 0) tooltip.add(Component.translatable("jade.bleep.homebase_battery", battery).withStyle(battery == 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            if (accessor.getBlockState().getValue(HomebaseBlock.CHARGING)) {
                tooltip.add(Component.translatable("activity.bleep.charging").withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public Identifier getUid() {
            return HOMEBASE;
        }
    }
}
