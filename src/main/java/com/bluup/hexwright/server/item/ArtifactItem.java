package com.bluup.hexwright.server.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public interface ArtifactItem {

    ChatFormatting COLOUR = ChatFormatting.RED;

    static Component label() {
        return Component.translatable("quality.hexwright.artifact");
    }

    static Component name(Component base) {
        return Component.translatable("item.hexwright.artifact.named", label(), base)
            .withStyle(COLOUR);
    }
}
