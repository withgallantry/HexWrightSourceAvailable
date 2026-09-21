package com.bluup.hexwright.server.effect;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.remnant.RemnantType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class HexwrightEffects {

    public static final class StatusEffect extends MobEffect {
        StatusEffect(int colour) {
            super(MobEffectCategory.BENEFICIAL, colour);
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return false;
        }
    }

    private static final Map<RemnantType, MobEffect> REMNANT_STATUSES = registerRemnantStatuses();

    public static final MobEffect SPIRIT_GOLEM = register("spirit_golem", new StatusEffect(0x5FC681));

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
