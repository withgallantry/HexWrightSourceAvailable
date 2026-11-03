package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.item.ArtifactItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ForgeHammerItem extends BattleHammerItem implements ArtifactItem {

    static final float FORGEFIRE_RADIUS = 4.5F;

    static final int FORGEFIRE_COOLDOWN_TICKS = 300;

    public ForgeHammerItem(int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(EternalTier.ETERNAL, attackDamageModifier, attackSpeedModifier,
            FORGEFIRE_RADIUS, FORGEFIRE_COOLDOWN_TICKS, properties);
    }

    @Override
    protected List<LivingEntity> slam(ServerLevel level, ServerPlayer player, BlockPos impact, float radius) {
        return Forgefire.strike(level, player, impact, radius);
    }

    @Override
    protected Component swingTooltip() {
        return Component.translatable("tooltip.hexwright.forge_hammer.forgefire",
            Component.keybind("key.use"), blocks(FORGEFIRE_RADIUS), (int) Forgefire.SCORCH_DAMAGE,
            Forgefire.SCORCH_SECONDS);
    }

    @Override
    @Nullable
    protected Component swingTooltipExtra() {
        return Component.translatable("tooltip.hexwright.forge_hammer.tempered",
            Forgefire.TEMPER_TICKS / 20, (int) Forgefire.TEMPER_ATTACK_DAMAGE, (int) Forgefire.TEMPER_ARMOUR,
            Math.round(Forgefire.TEMPER_MEDIA_DISCOUNT * 100));
    }

    @Override
    protected Component castTooltip() {
        return Component.translatable("tooltip.hexwright.forge_hammer.cast");
    }
}
