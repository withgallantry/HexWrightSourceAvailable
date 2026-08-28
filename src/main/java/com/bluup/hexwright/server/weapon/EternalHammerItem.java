package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.item.ArtifactItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class EternalHammerItem extends BattleHammerItem implements ArtifactItem {

    public EternalHammerItem(int attackDamageModifier, float attackSpeedModifier,
                             Properties properties) {
        super(EternalTier.ETERNAL, attackDamageModifier, attackSpeedModifier,
            GroundSlam.NETHERITE_RADIUS, GroundSlam.NETHERITE_COOLDOWN_TICKS, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return ArtifactItem.name(super.getName(stack));
    }
}
