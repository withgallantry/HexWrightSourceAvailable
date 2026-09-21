package com.bluup.hexwright.server.powerorb;

import com.bluup.hexwright.server.accessory.WornAccessories;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class PowerOrbSlot {

    public static final String SLOT = "power_orb";

    private PowerOrbSlot() {
    }

    public static ItemStack worn(@Nullable LivingEntity entity) {
        if (entity == null) {
            return ItemStack.EMPTY;
        }
        for (ItemStack stack : WornAccessories.slotContents(entity, SLOT)) {
            if (stack.getItem() instanceof PowerOrbItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack worn(@Nullable LivingEntity entity, PowerOrbPower power) {
        ItemStack orb = worn(entity);
        return orb.getItem() instanceof PowerOrbItem item && item.power() == power ? orb : ItemStack.EMPTY;
    }
}
