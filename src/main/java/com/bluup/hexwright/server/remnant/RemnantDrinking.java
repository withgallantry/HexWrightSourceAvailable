package com.bluup.hexwright.server.remnant;

import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.common.remnant.RemnantType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

public final class RemnantDrinking {

    private static final int TICKS_PER_DRAM = 2;

    private static final double DRAMS_PER_HALF_HEART = 20.0;

    private static final double AMPLIFIER_STEP = 200.0;

    private static final int MAX_AMPLIFIER = 2;

    private RemnantDrinking() {
    }

    public static int durationTicks(Remnant remnant) {
        return Math.max(20, (int) (remnant.drams() * TICKS_PER_DRAM));
    }

    public static int amplifier(Remnant remnant) {
        return Math.min(MAX_AMPLIFIER, (int) (remnant.drams() / AMPLIFIER_STEP));
    }

    public static float healing(Remnant remnant) {
        return (float) (remnant.drams() / DRAMS_PER_HALF_HEART);
    }

    public static void drink(Player player, Remnant remnant) {
        RemnantType type = remnant.type();

        if (type == RemnantType.VITALITY) {
            player.heal(healing(remnant));
            return;
        }

        MobEffect effect = type.effect();
        if (effect != null) {
            player.addEffect(new MobEffectInstance(effect, durationTicks(remnant), amplifier(remnant)));
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            RemnantBuffs.grant(serverPlayer, type, durationTicks(remnant), remnant.drams());
        }
    }

    public static MutableComponent describe(Remnant remnant) {
        if (remnant.type() == RemnantType.VITALITY) {
            return Component.translatable("tooltip.hexwright.remnant.instant",
                String.format("%.1f", healing(remnant) / 2.0f));
        }
        return Component.translatable("tooltip.hexwright.remnant.duration",
            durationTicks(remnant) / 20, amplifier(remnant) + 1);
    }
}
