package com.bluup.hexwright.server.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

public interface ArtifactItem {

    ChatFormatting COLOUR = ChatFormatting.RED;

    default boolean isArtifact(ItemStack stack) {
        return true;
    }

    static boolean is(ItemStack stack) {
        return stack.getItem() instanceof ArtifactItem artifact && artifact.isArtifact(stack);
    }

    static Component label() {
        return Component.translatable("quality.hexwright.artifact");
    }

    static Component tooltipLine() {
        return tooltipLine(TextColor.fromLegacyFormat(COLOUR));
    }

    static Component tooltipLine(TextColor colour) {
        return Component.translatable("tooltip.hexwright.artifact.quality",
                label().copy().withStyle(style -> style.withColor(colour)))
            .withStyle(ChatFormatting.GRAY);
    }
}
