package com.bluup.hexwright.server.talisman;

import com.bluup.hexwright.server.accessory.WornAccessories;
import com.bluup.hexwright.server.armour.ArmourPowerToggle;
import com.bluup.hexwright.server.armour.ArmourSet;
import com.bluup.hexwright.server.armour.ArmourTier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public final class TalismanSlots {

    public static final String SLOT = "talisman";

    public static final int BASE_SLOTS = 3;

    private static final UUID BONUS_MODIFIER = UUID.fromString("6e3d1c48-8a1f-4d2e-9b74-5f0c2ad3e911");

    private TalismanSlots() {
    }

    public static int bonusFor(ArmourTier tier) {
        return switch (tier) {
            case IRON -> 1;
            case GOLDEN -> 2;
            case DIAMOND -> 3;
            case NETHERITE -> 4;
        };
    }

    public static int allowance(LivingEntity entity) {
        ArmourTier tier = ArmourPowerToggle.activeTier(entity, ArmourSet.VEILWALKER);
        return tier == null ? BASE_SLOTS : BASE_SLOTS + bonusFor(tier);
    }

    public static void sync(LivingEntity entity) {
        List<ItemStack> contents = WornAccessories.slotContents(entity, SLOT);
        if (contents.isEmpty()) {
            return;
        }
        int occupied = 0;
        for (int i = 0; i < contents.size(); i++) {
            if (!contents.get(i).isEmpty()) {
                occupied = i + 1;
            }
        }
        int wanted = allowance(entity) - BASE_SLOTS;
        int stranded = occupied - BASE_SLOTS;
        WornAccessories.setBonusSlots(entity, SLOT, BONUS_MODIFIER, Math.max(0, Math.max(wanted, stranded)));
    }
}
