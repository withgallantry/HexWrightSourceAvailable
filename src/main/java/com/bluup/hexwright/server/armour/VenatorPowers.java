package com.bluup.hexwright.server.armour;

import com.bluup.hexwright.server.weapon.GreatBowItem;
import net.minecraft.world.entity.LivingEntity;

public final class VenatorPowers {

    private VenatorPowers() {
    }

    public static double cooldownReduction(ArmourTier tier) {
        return switch (tier) {
            case IRON -> 0.10;
            case GOLDEN -> 0.20;
            case DIAMOND -> 0.30;
            case NETHERITE -> 0.40;
        };
    }

    public static double arrowRefundChance(ArmourTier tier) {
        return switch (tier) {
            case IRON -> 0.30;
            case GOLDEN -> 0.50;
            case DIAMOND -> 0.70;
            case NETHERITE -> 0.90;
        };
    }

    private static ArmourTier wornTier(LivingEntity wearer) {
        return ArmourPowerToggle.activeTier(wearer, ArmourSet.VENATOR);
    }

    public static int hexCooldownFor(LivingEntity archer, int baseCooldownTicks) {
        ArmourTier tier = wornTier(archer);
        if (tier == null) {
            return baseCooldownTicks;
        }
        return Math.max(1, (int) Math.round(baseCooldownTicks * (1.0 - cooldownReduction(tier))));
    }

    public static boolean refundsArrow(LivingEntity archer) {
        ArmourTier tier = wornTier(archer);
        return tier != null && archer.getRandom().nextDouble() < arrowRefundChance(tier);
    }
}
