package com.bluup.hexwright.server.powerorb;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.server.item.ArtifactItem;
import com.bluup.hexwright.server.weapon.WeaponTooltips;
import com.bluup.hexwright.server.portal.VoidTearManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PowerOrbItem extends Item implements IotaHolderItem, DyeableLeatherItem, ArtifactItem {

    public static final TextColor SILVER = TextColor.fromRgb(0xCDD5E0);

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
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(style -> style.withColor(SILVER));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(ArtifactItem.tooltipLine(SILVER));
        WeaponTooltips.add(tooltip, Component.translatable(this.power.subtitleKey()), ChatFormatting.GRAY);
        switch (this.power) {
            case GOLEM -> appendConstructTooltip(stack, level, tooltip, ConstructOrbPower.GOLEM);
            case CAT -> appendConstructTooltip(stack, level, tooltip, ConstructOrbPower.CAT);
            case GUARDIAN -> appendConstructTooltip(stack, level, tooltip, ConstructOrbPower.GUARDIAN);
            case SANCTUARY -> appendSanctuaryTooltip(stack, level, tooltip);
            case TEETH -> appendTeethTooltip(tooltip);
            case DUST -> appendDustTooltip(tooltip);
        }
        if (this.power.bindsSigil()) {
            appendSigilTooltip(stack, tooltip, flag);
        }
    }

    private void appendConstructTooltip(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                        ConstructOrbPower construct) {
        String key = this.power.subtitleKey();
        WeaponTooltips.add(tooltip, Component.translatable(key + ".power"), ChatFormatting.DARK_AQUA);
        long left = level == null ? 0L : PowerOrbData.cooldownLeft(stack, level.getGameTime());
        if (left > 0) {
            WeaponTooltips.add(tooltip, Component.translatable(key + ".recovering", (left + 19) / 20),
                ChatFormatting.GRAY);
        } else if (PowerOrbData.holdsConstruct(stack)) {
            WeaponTooltips.add(tooltip, Component.translatable(key + (PowerOrbData.isActive(stack) ? ".out" : ".held"),
                    String.format("%.0f", Math.ceil(PowerOrbData.constructHealth(stack))),
                    String.format("%.0f", construct.maxHealth())),
                ChatFormatting.GREEN);
        } else {
            WeaponTooltips.add(tooltip, Component.translatable(key + ".cost",
                    ConstructOrbPower.SUMMON_COST / MediaConstants.DUST_UNIT, construct.slainCooldownTicks() / 20),
                ChatFormatting.DARK_GRAY);
        }
    }

    private static void appendSanctuaryTooltip(ItemStack stack, @Nullable Level level, List<Component> tooltip) {
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.power_orb.sanctuary.power"),
            ChatFormatting.DARK_AQUA);
        long left = level == null ? 0L : PowerOrbData.cooldownLeft(stack, level.getGameTime());
        if (left > 0) {
            WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.power_orb.sanctuary.recovering",
                    (left + 19) / 20),
                ChatFormatting.GRAY);
        } else {
            WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.power_orb.sanctuary.cost",
                    SanctuaryOrbPower.CAST_COST / MediaConstants.DUST_UNIT,
                    SanctuaryOrbPower.COOLDOWN_TICKS / 20),
                ChatFormatting.DARK_GRAY);
        }
    }

    private static void appendTeethTooltip(List<Component> tooltip) {
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.power_orb.teeth.power"),
            ChatFormatting.DARK_AQUA);
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.power_orb.teeth.cost",
                String.format("%.2f", (double) TeethOrbPower.TEAR_COST / MediaConstants.DUST_UNIT),
                VoidTearManager.MAX_TEARS_PER_CASTER),
            ChatFormatting.DARK_GRAY);
    }

    private static void appendDustTooltip(List<Component> tooltip) {
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.power_orb.dust.power"),
            ChatFormatting.DARK_AQUA);
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.power_orb.dust.licence"),
            ChatFormatting.DARK_GRAY);
    }

    private void appendSigilTooltip(ItemStack stack, List<Component> tooltip, TooltipFlag flag) {
        if (PowerOrbData.getPattern(stack) == null) {
            WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.power_orb.sigil_unbound"),
                ChatFormatting.DARK_GRAY);
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
        return this.power.bindsSigil();
    }

    @Override
    public boolean canWrite(ItemStack stack, @Nullable Iota iota) {
        return this.power.bindsSigil() && (iota == null || iota instanceof PatternIota);
    }

    @Override
    public void writeDatum(ItemStack stack, @Nullable Iota iota) {
        if (!this.power.bindsSigil()) {
            return;
        }
        if (iota instanceof PatternIota patternIota) {
            PowerOrbData.setPattern(stack, patternIota.getPattern());
        } else {
            PowerOrbData.clearPattern(stack);
        }
    }
}
