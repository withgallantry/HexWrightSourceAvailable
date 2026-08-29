package com.bluup.hexwright.common.remnant;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Predicate;

public enum RemnantType {

    VITALITY("red", Attributes.MAX_HEALTH, 20.0),
    MIGHT("orange", Attributes.ATTACK_DAMAGE, 3.0),
    ALACRITY("light_blue", Attributes.MOVEMENT_SPEED, 0.23),
    AEGIS("light_gray", Attributes.ARMOR, 2.0),
    BALLAST("brown", Attributes.KNOCKBACK_RESISTANCE, 1.0),
    IMPACT("gray", Attributes.ATTACK_KNOCKBACK, 1.5),

    EMBER("yellow", LivingEntity::fireImmune),
    TIDEBREATH("cyan", victim ->
        victim.canBreatheUnderwater() || victim.getMobType() == MobType.WATER),
    VENOM("lime", victim -> victim.getMobType() == MobType.ARTHROPOD),
    GRAVESIGHT("blue", victim -> victim.getMobType() == MobType.UNDEAD),
    LEVITY("white", victim -> victim.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE)),

    SHARDSKIN("magenta"),
    ADAMANT("green"),
    RIFT("purple"),
    BLIGHT("black"),
    ASCENDANT("pink");

    public static final double BASE_DRAMS = 100.0;

    public static final double BOSS_DRAMS = 180.0;

    private static final double BASELINE_HEALTH = 20.0;

    public static double stature(LivingEntity victim) {
        double health = victim.getMaxHealth();
        if (health <= 0.0) {
            return MIN_STATURE;
        }
        double raw = Math.sqrt(health / BASELINE_HEALTH);
        return Math.max(MIN_STATURE, Math.min(MAX_STATURE, raw));
    }

    private static final double MIN_STATURE = 0.4;
    private static final double MAX_STATURE = 8.0;

    private static final double MIN_ATTRIBUTE_WEIGHT = 0.5;
    private static final double MAX_ATTRIBUTE_WEIGHT = 2.0;

    public enum Family {
        VITAL,
        TRAIT,
        BOSS
    }

    private final String colour;
    private final Family family;
    private final @Nullable Attribute attribute;
    private final @Nullable Predicate<LivingEntity> trait;
    private final double typicalValue;

    RemnantType(String colour, Attribute attribute, double typicalValue) {
        this.colour = colour;
        this.family = Family.VITAL;
        this.attribute = attribute;
        this.trait = null;
        this.typicalValue = typicalValue;
    }

    RemnantType(String colour, Predicate<LivingEntity> trait) {
        this.colour = colour;
        this.family = Family.TRAIT;
        this.attribute = null;
        this.trait = trait;
        this.typicalValue = 0.0;
    }

    RemnantType(String colour) {
        this.colour = colour;
        this.family = Family.BOSS;
        this.attribute = null;
        this.trait = null;
        this.typicalValue = 0.0;
    }

    public DyeColor dye() {
        return DyeColor.byName(colour, DyeColor.WHITE);
    }

    public Family family() {
        return family;
    }

    public String lowerName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "remnant.hexwright." + lowerName();
    }

    public Component label() {
        return Component.translatable(translationKey());
    }

    public ChatFormatting textColour() {
        return switch (family) {
            case VITAL -> ChatFormatting.AQUA;
            case TRAIT -> ChatFormatting.GREEN;
            case BOSS -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    public double dramsFrom(LivingEntity victim) {
        return switch (family) {
            case VITAL -> {
                AttributeInstance instance = victim.getAttribute(attribute);
                if (instance == null) {
                    yield 0.0;
                }
                double value = instance.getBaseValue();
                if (value <= 0.0) {
                    yield 0.0;
                }
                yield BASE_DRAMS * stature(victim) * attributeWeight(value);
            }
            case TRAIT -> trait != null && trait.test(victim) ? BASE_DRAMS * stature(victim) : 0.0;
            case BOSS -> 0.0;
        };
    }

    private double attributeWeight(double value) {
        if (this == VITALITY || typicalValue <= 0.0) {
            return 1.0;
        }
        double ratio = value / typicalValue;
        return Math.max(MIN_ATTRIBUTE_WEIGHT, Math.min(MAX_ATTRIBUTE_WEIGHT, ratio));
    }

    public @Nullable MobEffect effect() {
        return switch (this) {
            case MIGHT -> MobEffects.DAMAGE_BOOST;
            case ALACRITY -> MobEffects.MOVEMENT_SPEED;
            case AEGIS -> MobEffects.DAMAGE_RESISTANCE;
            case EMBER -> MobEffects.FIRE_RESISTANCE;
            case TIDEBREATH -> MobEffects.WATER_BREATHING;
            case GRAVESIGHT -> MobEffects.NIGHT_VISION;
            case LEVITY -> MobEffects.SLOW_FALLING;
            default -> null;
        };
    }

    public static @Nullable RemnantType byName(String name) {
        for (RemnantType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
