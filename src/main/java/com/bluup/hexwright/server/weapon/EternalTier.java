package com.bluup.hexwright.server.weapon;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;

public enum EternalTier implements Tier {
    ETERNAL;

    @Override
    public int getUses() {
        return 0;
    }

    @Override
    public float getSpeed() {
        return Tiers.NETHERITE.getSpeed();
    }

    @Override
    public float getAttackDamageBonus() {
        return Tiers.NETHERITE.getAttackDamageBonus();
    }

    @Override
    public int getLevel() {
        return Tiers.NETHERITE.getLevel();
    }

    @Override
    public int getEnchantmentValue() {
        return Tiers.NETHERITE.getEnchantmentValue();
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }
}
