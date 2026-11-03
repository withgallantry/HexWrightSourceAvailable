package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.item.ArtifactItem;

public class EternalHammerItem extends BattleHammerItem implements ArtifactItem {

    public EternalHammerItem(int attackDamageModifier, float attackSpeedModifier,
                             Properties properties) {
        super(EternalTier.ETERNAL, attackDamageModifier, attackSpeedModifier,
            GroundSlam.NETHERITE_RADIUS, GroundSlam.NETHERITE_COOLDOWN_TICKS, properties);
    }
}
