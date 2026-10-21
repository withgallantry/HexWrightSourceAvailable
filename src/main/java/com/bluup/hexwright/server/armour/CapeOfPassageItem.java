package com.bluup.hexwright.server.armour;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.client.armour.ClientArmourWearer;
import com.bluup.hexwright.server.accessory.WornAccessories;
import com.bluup.hexwright.server.item.ArtifactItem;
import com.bluup.hexwright.server.weapon.WeaponTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CapeOfPassageItem extends Item implements ArtifactItem, IotaHolderItem {

    public static final long MEDIA_PER_STEP = MediaConstants.DUST_UNIT / 2;

    public CapeOfPassageItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static ItemStack wornSet(@Nullable LivingEntity wearer) {
        if (wearer == null) {
            return null;
        }
        return wornHat(wearer) ? wornCape(wearer) : null;
    }

    public static int wornPieces(@Nullable LivingEntity wearer) {
        if (wearer == null) {
            return 0;
        }
        return (wornCape(wearer) != null ? 1 : 0) + (wornHat(wearer) ? 1 : 0);
    }

    @Nullable
    private static ItemStack wornCape(LivingEntity wearer) {
        for (ItemStack stack : WornAccessories.slotContents(wearer, "cape")) {
            if (stack.getItem() instanceof CapeOfPassageItem) {
                return stack;
            }
        }
        return null;
    }

    private static boolean wornHat(LivingEntity wearer) {
        for (ItemStack stack : WornAccessories.slotContents(wearer, "hat")) {
            if (stack.getItem() instanceof HatOfPassageItem) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(ArtifactItem.COLOUR);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(ArtifactItem.tooltipLine());
        appendSetTooltip(level, tooltip);
        tooltip.add(Component.empty());
        if (ArmourPowerToggle.getPattern(stack) == null) {
            WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.cape_of_passage.sigil_unbound"),
                ChatFormatting.DARK_GRAY);
        } else {
            IotaHolderItem.appendHoverText(this, stack, tooltip, flag);
        }
    }

    static void appendSetTooltip(@Nullable Level level, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.hexwright.armour.set",
                wornPieces(tooltipWearer(level)), 2)
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.hexwright.armour.set_power", 2,
                Component.translatable("tooltip.hexwright.cape_of_passage.power"))
            .withStyle(ChatFormatting.DARK_PURPLE));
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.cape_of_passage.power.how"),
            ChatFormatting.GRAY);
        tooltip.add(Component.translatable("tooltip.hexwright.cape_of_passage.power.cost",
                String.format("%.1f", MEDIA_PER_STEP / (double) MediaConstants.DUST_UNIT))
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Nullable
    private static LivingEntity tooltipWearer(@Nullable Level level) {
        if (level == null || !level.isClientSide) {
            return null;
        }
        return ClientArmourWearer.localPlayer();
    }


    @Override
    public @Nullable CompoundTag readIotaTag(ItemStack stack) {
        return ArmourPowerToggle.getPatternTag(stack);
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
            ArmourPowerToggle.setPattern(stack, patternIota.getPattern());
        } else {
            ArmourPowerToggle.clearPattern(stack);
        }
    }
}
