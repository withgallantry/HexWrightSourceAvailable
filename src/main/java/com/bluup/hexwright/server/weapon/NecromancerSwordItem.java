package com.bluup.hexwright.server.weapon;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NecromancerSwordItem extends EternalWakizashiItem {

    public NecromancerSwordItem(int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(attackDamageModifier, attackSpeedModifier, properties);
    }

    @Override
    protected boolean castsOnHit() {
        return false;
    }

    @Nullable
    public CompoundTag thrallHex(ItemStack stack) {
        CompoundTag hex = readIotaTag(stack);
        return hex == null || hex.isEmpty() ? null : hex;
    }

    @Override
    protected Component castTooltip() {
        return Component.translatable("tooltip.hexwright.necromancer_sword.cast");
    }

    @Override
    protected void appendExtraTooltip(ItemStack stack, List<Component> tooltip) {
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.necromancer_sword.raise",
            RisenThrallEntity.LIFETIME_TICKS / 20, RaiseDead.MAX_THRALLS), ChatFormatting.AQUA);
    }
}
