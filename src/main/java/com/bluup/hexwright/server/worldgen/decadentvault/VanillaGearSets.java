package com.bluup.hexwright.server.worldgen.decadentvault;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

final class VanillaGearSets {

    private VanillaGearSets() {
    }

    static List<ItemStack> netherite() {
        return List.of(
            new ItemStack(Items.NETHERITE_HELMET),
            new ItemStack(Items.NETHERITE_CHESTPLATE),
            new ItemStack(Items.NETHERITE_LEGGINGS),
            new ItemStack(Items.NETHERITE_BOOTS)
        );
    }

    static List<ItemStack> enchantedDiamond(RandomSource random) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        ItemStack legs = new ItemStack(Items.DIAMOND_LEGGINGS);
        ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
        int protection = 1 + random.nextInt(3);
        helmet.enchant(Enchantments.ALL_DAMAGE_PROTECTION, protection);
        chest.enchant(Enchantments.ALL_DAMAGE_PROTECTION, protection);
        legs.enchant(Enchantments.ALL_DAMAGE_PROTECTION, protection);
        boots.enchant(Enchantments.ALL_DAMAGE_PROTECTION, protection);
        boots.enchant(Enchantments.FALL_PROTECTION, 1 + random.nextInt(2));
        helmet.enchant(Enchantments.UNBREAKING, 1 + random.nextInt(2));
        return List.of(helmet, chest, legs, boots);
    }
}
