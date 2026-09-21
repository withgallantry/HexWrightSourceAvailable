package com.bluup.hexwright.server.powerorb;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.misc.MediaConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PowerOrbItem extends Item implements IotaHolderItem, DyeableLeatherItem {

    private final PowerOrbPower power;

    public PowerOrbItem(PowerOrbPower power, Properties properties) {
        super(properties);
        this.power = power;
    }

    public PowerOrbPower power() {
        return this.power;
    }

    @Override
    public int getColor(ItemStack stack) {
        CompoundTag display = stack.getTagElement("display");
        return display != null && display.contains("color", Tag.TAG_ANY_NUMERIC)
            ? display.getInt("color")
            : this.power.defaultColour();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(this.power.subtitleKey()).withStyle(ChatFormatting.GRAY));
        switch (this.power) {
            case GOLEM -> appendGolemTooltip(stack, tooltip);
            case SANCTUARY -> appendSanctuaryTooltip(stack, level, tooltip);
        }
        appendSigilTooltip(stack, tooltip, flag);
    }

    private static void appendGolemTooltip(ItemStack stack, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.hexwright.power_orb.golem.power")
            .withStyle(ChatFormatting.DARK_AQUA));
        if (PowerOrbData.holdsGolem(stack)) {
            tooltip.add(Component.translatable(PowerOrbData.isActive(stack)
                        ? "tooltip.hexwright.power_orb.golem.out"
                        : "tooltip.hexwright.power_orb.golem.held",
                    String.format("%.0f", Math.ceil(PowerOrbData.golemHealth(stack))),
                    String.format("%.0f", SpiritGolemEntity.MAX_HEALTH))
                .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("tooltip.hexwright.power_orb.golem.cost",
                    GolemOrbPower.SUMMON_COST / MediaConstants.DUST_UNIT)
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void appendSanctuaryTooltip(ItemStack stack, @Nullable Level level, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.hexwright.power_orb.sanctuary.power")
            .withStyle(ChatFormatting.DARK_AQUA));
        long left = level == null ? 0L : PowerOrbData.cooldownLeft(stack, level.getGameTime());
        if (left > 0) {
            tooltip.add(Component.translatable("tooltip.hexwright.power_orb.sanctuary.recovering",
                    (left + 19) / 20)
                .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.hexwright.power_orb.sanctuary.cost",
                    SanctuaryOrbPower.CAST_COST / MediaConstants.DUST_UNIT,
                    SanctuaryOrbPower.COOLDOWN_TICKS / 20)
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private void appendSigilTooltip(ItemStack stack, List<Component> tooltip, TooltipFlag flag) {
        if (PowerOrbData.getPattern(stack) == null) {
            tooltip.add(Component.translatable("tooltip.hexwright.power_orb.sigil_unbound")
                .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        IotaHolderItem.appendHoverText(this, stack, tooltip, flag);
    }


    @Override
    public @Nullable CompoundTag readIotaTag(ItemStack stack) {
        return PowerOrbData.getPatternTag(stack);
    }

    @Override
    public boolean writeable(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canWrite(ItemStack stack, @Nullable Iota iota) {
        return iota == null || iota instanceof PatternIota;
    }

    @Override
    public void writeDatum(ItemStack stack, @Nullable Iota iota) {
        if (iota instanceof PatternIota patternIota) {
            PowerOrbData.setPattern(stack, patternIota.getPattern());
        } else {
            PowerOrbData.clearPattern(stack);
        }
    }
}
