package com.bluup.hexwright.server.effect;

import com.bluup.hexwright.Hexwright;
import at.petrak.hexcasting.common.lib.HexAttributes;
import com.bluup.hexwright.common.remnant.RemnantType;
import com.bluup.hexwright.server.weapon.Forgefire;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class HexwrightEffects {

    public static final class StatusEffect extends MobEffect {
        StatusEffect(int colour) {
            this(MobEffectCategory.BENEFICIAL, colour);
        }

        StatusEffect(MobEffectCategory category, int colour) {
            super(category, colour);
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return false;
        }
    }

    private static final Map<RemnantType, MobEffect> REMNANT_STATUSES = registerRemnantStatuses();

    public static final MobEffect SPIRIT_GOLEM = register("spirit_golem", new StatusEffect(0x5FC681));

    public static final MobEffect CAT_CONSTRUCT = register("cat_construct", new StatusEffect(0x3AE0B0));

    public static final MobEffect GUARDIAN_CONSTRUCT = register("guardian_construct", new StatusEffect(0x2F9EED));

    public static final MobEffect STUNNED = register("stunned", new StunnedEffect());

    public static final MobEffect SILENCED = register("silenced",
        new StatusEffect(MobEffectCategory.HARMFUL, 0x9B5CFF));

    public static final MobEffect FIRM_REALITY = register("firm_reality",
        new StatusEffect(MobEffectCategory.NEUTRAL, 0x8E9BC4));

    public static final MobEffect SOUL_CLEAVED = register("soul_cleaved", new SoulCleavedEffect());

    public static final MobEffect MEDIA_BURN = register("media_burn", new MediaBurnEffect());

    public static final MobEffect WEBBED = register("webbed", new WebbedEffect());

    public static final MobEffect TEMPERED = register("tempered",
        new MobEffect(MobEffectCategory.BENEFICIAL, 0xFF8A2B) {}
            .addAttributeModifier(Attributes.ATTACK_DAMAGE, "10e24a2d-f28c-47e9-8c9b-45858f04bfb2",
                Forgefire.TEMPER_ATTACK_DAMAGE, AttributeModifier.Operation.ADDITION)
            .addAttributeModifier(Attributes.ARMOR, "30fcaac5-07d1-47fc-969c-da34022269eb",
                Forgefire.TEMPER_ARMOUR, AttributeModifier.Operation.ADDITION)
            .addAttributeModifier(HexAttributes.MEDIA_CONSUMPTION_MODIFIER, "6b0f5e1a-93d4-4c6e-a2f7-0d8c3e51b924",
                -Forgefire.TEMPER_MEDIA_DISCOUNT, AttributeModifier.Operation.ADDITION));

    private HexwrightEffects() {
    }

    public static void register() {
    }

    public static @Nullable MobEffect remnantStatus(RemnantType type) {
        return REMNANT_STATUSES.get(type);
    }

    public static Map<RemnantType, MobEffect> remnantStatuses() {
        return Collections.unmodifiableMap(REMNANT_STATUSES);
    }

    public static void sync(ServerPlayer player, MobEffect status, boolean active, int remainingTicks) {
        boolean showing = player.hasEffect(status);
        if (active && !showing && (remainingTicks > 0 || remainingTicks == MobEffectInstance.INFINITE_DURATION)) {
            player.addEffect(instance(status, remainingTicks));
        } else if (!active && showing) {
            player.removeEffect(status);
        }
    }

    public static void restart(LivingEntity entity, MobEffect status, int remainingTicks) {
        entity.removeEffect(status);
        entity.addEffect(instance(status, remainingTicks));
    }

    private static MobEffectInstance instance(MobEffect status, int ticks) {
        return new MobEffectInstance(status, ticks, 0, false, false, true);
    }

    private static Map<RemnantType, MobEffect> registerRemnantStatuses() {
        Map<RemnantType, MobEffect> statuses = new EnumMap<>(RemnantType.class);
        for (RemnantType type : RemnantType.values()) {
            if (type.effect() == null && type != RemnantType.VITALITY) {
                statuses.put(type, register("remnant_" + type.lowerName(), new StatusEffect(type.tint())));
            }
        }
        return statuses;
    }

    private static MobEffect register(String name, MobEffect effect) {
        return Registry.register(BuiltInRegistries.MOB_EFFECT, Hexwright.id(name), effect);
    }
}
