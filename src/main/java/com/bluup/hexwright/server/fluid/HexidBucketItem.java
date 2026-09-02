package com.bluup.hexwright.server.fluid;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HexidBucketItem extends BucketItem {

    public HexidBucketItem(Fluid content, Properties properties) {
        super(content, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                               TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.hexwright.hexid_bucket",
                String.format("%,d", HexidFluids.MEDIA_PER_BUCKET),
                String.format("%.0f", HexidFluids.SATURATION * 100.0))
            .withStyle(ChatFormatting.GRAY));
    }
}
