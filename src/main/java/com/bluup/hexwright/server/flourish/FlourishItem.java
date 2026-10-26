package com.bluup.hexwright.server.flourish;

import com.bluup.hexwright.common.flourish.Flourish;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FlourishItem extends Item {

    private final Flourish flourish;

    public FlourishItem(Flourish flourish, Properties properties) {
        super(properties);
        this.flourish = flourish;
    }

    public Flourish flourish() {
        return flourish;
    }

    public static @Nullable Flourish of(ItemStack stack) {
        return stack.getItem() instanceof FlourishItem item ? item.flourish : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hexwright.flourish.how")
            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
