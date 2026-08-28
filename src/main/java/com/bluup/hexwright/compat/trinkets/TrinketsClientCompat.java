package com.bluup.hexwright.compat.trinkets;

import com.bluup.hexwright.client.accessory.InertAccessorySlots;
import dev.emi.trinkets.TrinketSlot;
import net.minecraft.world.inventory.Slot;

public final class TrinketsClientCompat {

    private TrinketsClientCompat() {
    }

    public static void register() {
        InertAccessorySlots.register(TrinketsClientCompat::indexIn);
    }

    private static int indexIn(Slot slot, String slotName) {
        if (!(slot instanceof TrinketSlot trinketSlot)
            || !trinketSlot.getType().getName().equals(slotName)) {
            return -1;
        }
        return slot.getContainerSlot();
    }
}
