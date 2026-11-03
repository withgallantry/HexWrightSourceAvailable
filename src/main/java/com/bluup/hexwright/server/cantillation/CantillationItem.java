package com.bluup.hexwright.server.cantillation;

import com.bluup.hexwright.common.cantillation.Cantillation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CantillationItem extends Item {

    private final Cantillation cantillation;

    public CantillationItem(Cantillation cantillation, Properties properties) {
        super(properties);
        this.cantillation = cantillation;
    }

    public static @Nullable Cantillation of(ItemStack stack) {
        return stack.getItem() instanceof CantillationItem item ? item.cantillation : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hexwright.cantillation.how")
            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
