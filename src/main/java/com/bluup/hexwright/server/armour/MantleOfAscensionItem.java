package com.bluup.hexwright.server.armour;

import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.server.item.ArtifactItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MantleOfAscensionItem extends ArmorItem implements ArtifactItem, DyeableLeatherItem {

    public static final long MEDIA_PER_SECOND = MediaConstants.DUST_UNIT / 4;

    public MantleOfAscensionItem(Properties properties) {
        super(MageAttireMaterial.INSTANCE, Type.CHESTPLATE, properties);
    }

    public static boolean wornBy(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof MantleOfAscensionItem;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(ArtifactItem.COLOUR);
    }

    @Override
    public int getColor(ItemStack stack) {
        CompoundTag display = stack.getTagElement("display");
        return display != null && display.contains("color", Tag.TAG_ANY_NUMERIC)
            ? display.getInt("color")
            : 0xFFFFFF;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(ArtifactItem.tooltipLine());
        tooltip.add(Component.translatable("item.hexwright.mantle_of_ascension.tip")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.hexwright.mantle_of_ascension.tip.cost",
                String.format("%.2f", MEDIA_PER_SECOND / (double) MediaConstants.DUST_UNIT))
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
