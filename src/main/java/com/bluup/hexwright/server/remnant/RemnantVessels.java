package com.bluup.hexwright.server.remnant;

import com.bluup.hexwright.server.block.PlacedBottleBlockEntity;
import com.bluup.hexwright.server.fluid.HexidTankBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class RemnantVessels {

    private RemnantVessels() {
    }

    public static boolean isVessel(ItemStack stack) {
        return stack != null && stack.getItem() instanceof HexEngravedBottleItem;
    }

    public static boolean isVessel(@Nullable BlockEntity be) {
        return be instanceof HexidTankBlockEntity || be instanceof PlacedBottleBlockEntity;
    }
}
