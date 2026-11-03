package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.item.ArtifactItem;

public class EternalBladeItem extends BattleAxeItem implements ArtifactItem {

    public EternalBladeItem(int attackDamageModifier, float attackSpeedModifier,
                            Properties properties) {
        this(attackDamageModifier, attackSpeedModifier, Cleave.NETHERITE_COOLDOWN_TICKS, properties);
    }

    protected EternalBladeItem(int attackDamageModifier, float attackSpeedModifier,
                               int cleaveCooldownTicks, Properties properties) {
        super(EternalTier.ETERNAL, attackDamageModifier, attackSpeedModifier,
            Cleave.NETHERITE_REACH, cleaveCooldownTicks, properties);
    }

    private static final WeaponSlash EMBER_CLEAVE_SLASH = CLEAVE_SLASH.withStyle(SlashStyle.EMBER);

    @Override
    protected WeaponSlash cleaveSlash() {
        return EMBER_CLEAVE_SLASH;
    }
}
