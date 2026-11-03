package com.bluup.hexwright.server.weapon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

public class EternalScytheItem extends EternalBladeItem {

    private static final int CLEAVE_COOLDOWN_TICKS = 30;

    public EternalScytheItem(int attackDamageModifier, float attackSpeedModifier,
                             Properties properties) {
        super(attackDamageModifier, attackSpeedModifier, CLEAVE_COOLDOWN_TICKS, properties);
    }

    @Override
    protected boolean castsOnCaught() {
        return false;
    }

    @Override
    protected boolean land(ServerLevel level, ServerPlayer player, InteractionHand hand) {
        return Cleave.cleave(level, player, cleaveReach());
    }

    @Override
    @Nullable
    protected Component swingTooltipExtra() {
        return Component.translatable("tooltip.hexwright.eternal_scythe.harvest");
    }
}
