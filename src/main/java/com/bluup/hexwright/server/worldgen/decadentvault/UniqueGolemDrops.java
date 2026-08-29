package com.bluup.hexwright.server.worldgen.decadentvault;

import com.bluup.hexwright.server.item.HexwrightItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class UniqueGolemDrops {

    private UniqueGolemDrops() {
    }

    static ItemStack roll(RandomSource random) {
        List<Item> weapons = HexwrightItems.ETERNAL_WEAPONS;
        int roll = random.nextInt(weapons.size() * 2 + 1);
        Item item = roll >= weapons.size() * 2 ? HexwrightItems.WORLD_CRYSTAL : weapons.get(roll / 2);
        return new ItemStack(item);
    }
}
