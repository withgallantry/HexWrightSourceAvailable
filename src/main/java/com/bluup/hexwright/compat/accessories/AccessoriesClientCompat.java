package com.bluup.hexwright.compat.accessories;

import com.bluup.hexwright.client.accessory.InertAccessorySlots;
import io.wispforest.accessories.api.menu.AccessoriesBasedSlot;
import net.minecraft.world.inventory.Slot;

public final class AccessoriesClientCompat {

    private AccessoriesClientCompat() {
    }

    public static void register() {
        InertAccessorySlots.register(AccessoriesClientCompat::indexIn);
    }

    private static int indexIn(Slot slot, String slotName) {
        if (!(slot instanceof AccessoriesBasedSlot accessorySlot)
            || !accessorySlot.accessoriesContainer.getSlotName().equals(slotName)) {
            return -1;
        }
        return accessorySlot.getContainerSlot();
    }
}
