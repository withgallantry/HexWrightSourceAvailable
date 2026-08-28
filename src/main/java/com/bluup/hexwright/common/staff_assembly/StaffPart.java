package com.bluup.hexwright.common.staff_assembly;

import com.bluup.hexwright.common.staff_assembly.calc.EfficiencyRating;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public record StaffPart(StaffPartCategory category, String id, Component displayName, ItemStack icon, EfficiencyRating requiredQuality, Set<String> tags) {
    public static final String TAG_BOOK = "book";

    public static final String TAG_NAME_OMITS_STAFF = "name_omits_staff";

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
