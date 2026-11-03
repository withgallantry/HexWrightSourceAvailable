package com.bluup.hexwright.client.tooltip;

import at.petrak.hexcasting.common.lib.HexAttributes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.List;

public final class MediaConsumptionTooltip {
    private static final String RAISE_KEY = "attribute.modifier.plus.";
    private static final String LOWER_KEY = "attribute.modifier.take.";

    private MediaConsumptionTooltip() {}

    public static void recolour(List<Component> lines) {
        String attributeKey = HexAttributes.MEDIA_CONSUMPTION_MODIFIER.getDescriptionId();
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (!(line.getContents() instanceof TranslatableContents contents)
                || contents.getArgs().length < 2
                || !(contents.getArgs()[1] instanceof Component attribute)
                || !(attribute.getContents() instanceof TranslatableContents name)
                || !attributeKey.equals(name.getKey())) {
                continue;
            }
            if (contents.getKey().startsWith(LOWER_KEY)) {
                lines.set(i, line.copy().withStyle(ChatFormatting.GREEN));
            } else if (contents.getKey().startsWith(RAISE_KEY)) {
                lines.set(i, line.copy().withStyle(ChatFormatting.RED));
            }
        }
    }
}
