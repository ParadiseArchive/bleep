package io.hookdev.bleep.compat.jei;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.BleepContent;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/**
 * JEI support: the homebase's recipe shows by itself; the core and the parts, which have no recipe, get a page that
 * says where they are found and how a bleep is put together. Only loaded when JEI is installed.
 */
@JeiPlugin
public final class BleepJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Bleep.id("jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        info(registration, BleepContent.BLEEP_CORE.get(), "bleep_core");
        info(registration, BleepContent.BLEEP_DISPLAY.get(), "bleep_display");
        info(registration, BleepContent.BLEEP_TRACKS.get(), "bleep_tracks");
        info(registration, BleepContent.BLEEP_ARMS.get(), "bleep_arms");
        info(registration, BleepContent.HOMEBASE.get(), "homebase");
    }

    /** An information page from jei.bleep.<name> in the language file. */
    private static void info(IRecipeRegistration registration, ItemLike item, String name) {
        registration.addIngredientInfo(item, Component.translatable("jei.bleep." + name));
    }
}
