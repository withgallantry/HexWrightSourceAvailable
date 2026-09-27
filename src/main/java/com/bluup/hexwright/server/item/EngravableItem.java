package com.bluup.hexwright.server.item;

import at.petrak.hexcasting.api.item.IotaHolderItem;
import net.minecraft.world.item.ItemStack;

public interface EngravableItem extends IotaHolderItem {

    @Override
    default boolean writeable(ItemStack stack) {
        return canWrite(stack, null);
    }
}
