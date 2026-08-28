package com.bluup.hexwright.server.network;

import com.bluup.hexwright.server.accessory.WornAccessories;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ResonantRingNetwork {

    private ResonantRingNetwork() {
    }

    public static List<ItemStack> worn(ServerPlayer player) {
        List<ItemStack> rings = new ArrayList<>();
        for (ItemStack stack : WornAccessories.allWorn(player)) {
            if (stack.getItem() instanceof ResonantRingItem) {
                rings.add(stack);
            }
        }
        return rings;
    }
}
