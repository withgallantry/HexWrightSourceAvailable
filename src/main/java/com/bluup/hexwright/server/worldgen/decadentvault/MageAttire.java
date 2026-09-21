package com.bluup.hexwright.server.worldgen.decadentvault;

import com.bluup.hexwright.server.item.HexwrightItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class MageAttire {

    private MageAttire() {
    }

    static List<ItemStack> roll(RandomSource random) {
        return random.nextBoolean() ? ascension() : passage();
    }

    private static List<ItemStack> ascension() {
        return List.of(
            new ItemStack(HexwrightItems.HAT_OF_ASCENSION),
            new ItemStack(HexwrightItems.MANTLE_OF_ASCENSION),
            ItemStack.EMPTY,
            ItemStack.EMPTY
        );
    }

    private static List<ItemStack> passage() {
        return List.of(
            new ItemStack(HexwrightItems.HAT_OF_PASSAGE),
            new ItemStack(HexwrightItems.CAPE_OF_PASSAGE),
            ItemStack.EMPTY,
            ItemStack.EMPTY
        );
    }
}
