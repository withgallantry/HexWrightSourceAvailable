package com.bluup.hexwright.server.wardingbox;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WardersSpectaclesItem extends Item {

    private static final String TAG_SCRYING = "ScryingLens";

    public WardersSpectaclesItem(Properties properties) {
        super(properties);
    }

    public static boolean hasScryingLens(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_SCRYING);
    }

    public static void fitScryingLens(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(TAG_SCRYING, true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hexwright.warders_spectacles").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hexwright.warders_spectacles.readouts")
            .withStyle(ChatFormatting.DARK_GRAY));
        if (hasScryingLens(stack)) {
            tooltip.add(Component.translatable("tooltip.hexwright.warders_spectacles.scrying")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
