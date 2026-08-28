package com.bluup.hexwright.server.armour;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public final class DomitorPowers {

    private DomitorPowers() {
    }

    public static float wardFraction(ArmourTier tier) {
        return switch (tier) {
            case IRON -> 0.25f;
            case GOLDEN -> 0.5f;
            case DIAMOND -> 0.75f;
            case NETHERITE -> 1.0f;
        };
    }

    public static boolean wards(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION);
    }

    public static float wardAgainst(LivingEntity wearer, DamageSource source) {
        if (!wards(source)) {
            return 0.0f;
        }
        ArmourTier tier = ArmourPowerToggle.activeTier(wearer, ArmourSet.DOMITOR);
        return tier == null ? 0.0f : wardFraction(tier);
    }
}
