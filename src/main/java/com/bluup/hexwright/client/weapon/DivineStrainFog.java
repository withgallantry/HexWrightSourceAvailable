package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.server.effect.HexwrightEffects;
import com.bluup.hexwright.server.item.DivineStrain;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class DivineStrainFog {

    private static final float NEAREST = 5.0F;

    private static final float REACH = 160.0F;

    private DivineStrainFog() {
    }

    public static float severity(Camera camera) {
        if (!(camera.getEntity() instanceof LivingEntity living)) {
            return 0.0F;
        }
        MobEffectInstance strain = living.getEffect(HexwrightEffects.DIVINE_STRAIN);
        return strain == null ? 0.0F : Mth.clamp(strain.getDuration() / (float) DivineStrain.MAX_CLEAR_TICKS, 0.0F, 1.0F);
    }

    public static float fogEnd(float severity) {
        float clear = 1.0F - severity;
        return NEAREST + REACH * clear * clear;
    }

    public static float darkness(float severity) {
        return 1.0F - (fogEnd(severity) - NEAREST) / REACH;
    }
}
