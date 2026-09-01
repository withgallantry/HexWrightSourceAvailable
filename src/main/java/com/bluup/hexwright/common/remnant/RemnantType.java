package com.bluup.hexwright.common.remnant;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Predicate;

public enum RemnantType {

    VITALITY(0xB3312C, Attributes.MAX_HEALTH, 20.0),
    MIGHT(0xEB8844, Attributes.ATTACK_DAMAGE, 3.0),
    ALACRITY(0x6689D3, Attributes.MOVEMENT_SPEED, 0.23),
    AEGIS(0xABABAB, Attributes.ARMOR, 2.0),
    BALLAST(0x51301A, Attributes.KNOCKBACK_RESISTANCE, 1.0),
    IMPACT(0x434343, Attributes.ATTACK_KNOCKBACK, 1.5),
    BOUND(0x9ED62B, Attributes.JUMP_STRENGTH, 0.7),

    EMBER(0xDECF2A, LivingEntity::fireImmune),
    TIDEBREATH(0x287997, victim ->
        victim.canBreatheUnderwater() || victim.getMobType() == MobType.WATER),
    VENOM(0x41CD34, victim -> victim.getMobType() == MobType.ARTHROPOD),
    GRAVESIGHT(0x253192, victim -> victim.getMobType() == MobType.UNDEAD),
    LEVITY(0xF0F0F0, victim -> victim.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE)),
    GUILE(0xA98FC9, victim -> victim.getMobType() == MobType.ILLAGER),
    SLIPSTREAM(0x35C9A0, victim -> victim instanceof WaterAnimal),
    REPAST(0xD7A93F, victim -> victim.getType().getCategory() == MobCategory.CREATURE),
    RIME(0xC6ECFA, victim -> victim.getType().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES)),
    ELOQUENCE(0x17A85A, victim -> victim instanceof Villager villager
        && villager.getVillagerData().getProfession() != VillagerProfession.NONE),

    SHARDSKIN(0xC354CD),
    ADAMANT(0x3B511A),
    RIFT(0x7B2FBE),
    BLIGHT(0x1E1B1B),
    ASCENDANT(0xD88198),
    TREMOR(0x1FD4E6),
    INTERDICT(0x7E9AA8);

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

    private final int tint;
    private final Family family;
    private final @Nullable Attribute attribute;
    private final @Nullable Predicate<LivingEntity> trait;
    private final double typicalValue;

    RemnantType(int tint, Attribute attribute, double typicalValue) {
        this.tint = tint;
        this.family = Family.VITAL;
        this.attribute = attribute;
        this.trait = null;
        this.typicalValue = typicalValue;
    }

    RemnantType(int tint, Predicate<LivingEntity> trait) {
        this.tint = tint;
        this.family = Family.TRAIT;
        this.attribute = null;
        this.trait = trait;
        this.typicalValue = 0.0;
    }

    RemnantType(int tint) {
        this.tint = tint;
        this.family = Family.BOSS;
        this.attribute = null;
        this.trait = null;
        this.typicalValue = 0.0;
    }

    public int tint() {
        return tint;
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
            case BOUND -> MobEffects.JUMP;
            case EMBER -> MobEffects.FIRE_RESISTANCE;
            case TIDEBREATH -> MobEffects.WATER_BREATHING;
            case GRAVESIGHT -> MobEffects.NIGHT_VISION;
            case LEVITY -> MobEffects.SLOW_FALLING;
            case GUILE -> MobEffects.INVISIBILITY;
            case SLIPSTREAM -> MobEffects.DOLPHINS_GRACE;
            case REPAST -> MobEffects.SATURATION;
            case ELOQUENCE -> MobEffects.HERO_OF_THE_VILLAGE;
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
