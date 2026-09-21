package com.bluup.hexwright.server.armour;

import com.bluup.hexwright.server.item.HexwrightItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

public enum MageAttireMaterial implements ArmorMaterial {
    INSTANCE;

    private static final int[] HEALTH_PER_SLOT = {13, 15, 16, 11};

    private static final int[] PROTECTION = {3, 6, 8, 3};

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return HEALTH_PER_SLOT[type.getSlot().getIndex()] * 33;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return PROTECTION[type.getSlot().getIndex()];
    }

    @Override
    public int getEnchantmentValue() {
        return 10;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(HexwrightItems.CRYSTALITE);
    }

    @Override
    public String getName() {
        return "leather";
    }

    @Override
    public float getToughness() {
        return 2.0f;
    }

    @Override
    public float getKnockbackResistance() {
        return 0.0f;
    }
}
