package com.bluup.hexwright.client.accessory;

import com.bluup.hexwright.server.talisman.TalismanSlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

public final class InertAccessorySlots {

    public interface Probe {
        int indexIn(Slot slot, String slotName);
    }

    private static final List<Probe> PROBES = new ArrayList<>();

    private InertAccessorySlots() {
    }

    public static void register(Probe probe) {
        PROBES.add(probe);
    }

    public static boolean isInertTalismanSlot(Slot slot) {
        if (PROBES.isEmpty()) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        for (Probe probe : PROBES) {
            int index = probe.indexIn(slot, TalismanSlots.SLOT);
            if (index >= 0) {
                return index >= TalismanSlots.allowance(player);
            }
        }
        return false;
    }
}
