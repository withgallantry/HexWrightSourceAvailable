package com.bluup.hexwright.server.item;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class HexwrightItemTags {
    public static final TagKey<Item> PENTABOX_STORABLE = TagKey.create(Registries.ITEM, Hexwright.id("pentabox_storable"));

    public static final TagKey<Item> POWER_ORBS = TagKey.create(Registries.ITEM, Hexwright.id("power_orbs"));

    public static final TagKey<Item> WEAPON_RACK = TagKey.create(Registries.ITEM, Hexwright.id("weapon_rack"));

    private HexwrightItemTags() {
    }
}
