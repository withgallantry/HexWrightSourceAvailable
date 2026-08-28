package com.bluup.hexwright.server.talisman;

import com.bluup.hexwright.server.accessory.WornAccessories;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class WornTalismans {

    private WornTalismans() {
    }

    public static List<ItemStack> getWorn(ServerPlayer player) {
        List<ItemStack> contents = WornAccessories.slotContents(player, TalismanSlots.SLOT);
        int allowance = Math.min(TalismanSlots.allowance(player), contents.size());
        List<ItemStack> worn = new ArrayList<>();
        for (int i = 0; i < allowance; i++) {
            ItemStack stack = contents.get(i);
            if (stack.getItem() instanceof TalismanItem) {
                worn.add(stack);
            }
        }
        return worn;
    }
}
