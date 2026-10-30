package io.hookdev.bleep.entity;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The item a bleep is crazy about, picked at random when it starts up. Holding it makes the bleep drive circles round
 * you; it hints at it in its thoughts (thought.bleep.obsession.<id>) until you find out what it is.
 */
public record Obsession(String id, String icon, Predicate<ItemStack> matches) {
    public static final List<Obsession> ALL = List.of(
            item("amethyst_shard"),
            // any lightning rod, also the weathered ones
            new Obsession("lightning_rod", "lightning_rod", stack -> path(stack).endsWith("lightning_rod")),
            item("compass"),
            // any music disc
            new Obsession("music_disc", "music_disc_cat", stack -> stack.has(DataComponents.JUKEBOX_PLAYABLE)),
            item("glow_berries"),
            item("clock"),
            item("spyglass"),
            item("firework_rocket"),
            item("bell"),
            item("honeycomb"));

    private static Obsession item(String id) {
        return new Obsession(id, id, stack -> path(stack).equals(id));
    }

    private static String path(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    public static Obsession byIndex(int index) {
        return index >= 0 && index < ALL.size() ? ALL.get(index) : null;
    }

    /** An item to show for it, on the info screen. */
    public ItemStack iconStack() {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(icon)));
    }

    public String hintKey() {
        return "thought.bleep.obsession." + id;
    }

    public String nameKey() {
        return "obsession.bleep." + id;
    }
}
