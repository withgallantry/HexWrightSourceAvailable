package com.bluup.hexwright.server.coalescence;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.accessory.WornAccessories;
import com.bluup.hexwright.server.block.WorktableRecipes;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Set;

public final class CoalescenceDenials {

    public static final TagKey<Item> NOT_COALESCABLE =
        TagKey.create(Registries.ITEM, Hexwright.id("not_coalescable"));

    public static final TagKey<Item> VOIDED_BY_COALESCER =
        TagKey.create(Registries.ITEM, Hexwright.id("voided_by_coalescer"));

    private static volatile Set<Item> forgeOutputs;

    private CoalescenceDenials() {
    }

    public static boolean isDenied(Item item) {
        if (item == null || item == Items.AIR) {
            return false;
        }
        return item.builtInRegistryHolder().is(NOT_COALESCABLE)
            || forgeOutputs().contains(item)
            || WornAccessories.isAccessory(item);
    }

    public static boolean isVoided(Item item) {
        return item != null && item != Items.AIR && item.builtInRegistryHolder().is(VOIDED_BY_COALESCER);
    }

    private static Set<Item> forgeOutputs() {
        Set<Item> cached = forgeOutputs;
        if (cached == null) {
            cached = Set.copyOf(WorktableRecipes.INSTANCE.allOutputItems());
            forgeOutputs = cached;
        }
        return cached;
    }
}
