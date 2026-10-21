package com.bluup.hexwright.server.portal;

import com.bluup.hexwright.server.accessory.WornAccessories;
import com.bluup.hexwright.server.item.HexwrightItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class WorldCrystalSlot {

    public static final String SLOT = "world_crystal";

    private WorldCrystalSlot() {
    }

    public static boolean isCarried(@Nullable LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.getMainHandItem().is(HexwrightItems.WORLD_CRYSTAL)
            || entity.getOffhandItem().is(HexwrightItems.WORLD_CRYSTAL)) {
            return true;
        }
        for (ItemStack stack : WornAccessories.slotContents(entity, SLOT)) {
            if (stack.is(HexwrightItems.WORLD_CRYSTAL)) {
                return true;
            }
        }
        return false;
    }
}
