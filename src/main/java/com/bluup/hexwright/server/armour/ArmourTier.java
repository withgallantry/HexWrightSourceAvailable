package com.bluup.hexwright.server.armour;

import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

public enum ArmourTier implements ArmorMaterial {
    IRON("iron", 15, new int[]{2, 5, 6, 2}, 9, SoundEvents.ARMOR_EQUIP_IRON, 0.0f, 0.0f),
    GOLDEN("golden", 22, new int[]{2, 6, 7, 3}, 25, SoundEvents.ARMOR_EQUIP_GOLD, 1.0f, 0.0f),
    DIAMOND("diamond", 33, new int[]{3, 6, 8, 3}, 10, SoundEvents.ARMOR_EQUIP_DIAMOND, 2.0f, 0.0f),
    NETHERITE("netherite", 37, new int[]{3, 6, 8, 3}, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0f, 0.1f);

    private static final int[] HEALTH_PER_SLOT = {13, 15, 16, 11};

    private final String id;
    private final int durabilityMultiplier;
    private final int[] protection;
    private final int enchantmentValue;
    private final SoundEvent equipSound;
    private final float toughness;
    private final float knockbackResistance;

    ArmourTier(String id, int durabilityMultiplier, int[] protection, int enchantmentValue,
               SoundEvent equipSound, float toughness, float knockbackResistance) {
        this.id = id;
        this.durabilityMultiplier = durabilityMultiplier;
        this.protection = protection;
        this.enchantmentValue = enchantmentValue;
        this.equipSound = equipSound;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
    }

    public String id() {
        return id;
    }

    public static ArmourTier forGrade(PocketCasterData.Quality grade) {
        return switch (grade) {
            case CRUDE, SOUND -> IRON;
            case FINE -> GOLDEN;
            case EXQUISITE -> DIAMOND;
            case MASTERWORK -> NETHERITE;
        };
    }

    public PocketCasterData.Quality displayQuality() {
        return switch (this) {
            case IRON -> PocketCasterData.Quality.SOUND;
            case GOLDEN -> PocketCasterData.Quality.FINE;
            case DIAMOND -> PocketCasterData.Quality.EXQUISITE;
            case NETHERITE -> PocketCasterData.Quality.MASTERWORK;
        };
    }

    public boolean fireResistant() {
        return this == NETHERITE;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return HEALTH_PER_SLOT[type.getSlot().getIndex()] * durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return protection[type.getSlot().getIndex()];
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public SoundEvent getEquipSound() {
        return equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(HexwrightItems.CRYSTALITE);
    }

    @Override
    public String getName() {
        return this == GOLDEN ? "gold" : id;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockbackResistance;
    }
}
