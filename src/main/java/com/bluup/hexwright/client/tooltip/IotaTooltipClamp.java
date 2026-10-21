package com.bluup.hexwright.client.tooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class IotaTooltipClamp {
    private static final String CONTAINS_KEY = "hexcasting.spelldata.onitem";
    private static final int MAX_WIDTH = 240;
    private static final int MAX_LINES = 3;
    private static final String ELLIPSIS = "...";

    private IotaTooltipClamp() {}

    public static void clamp(List<Component> lines) {
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (!(line.getContents() instanceof TranslatableContents contents)
                || !CONTAINS_KEY.equals(contents.getKey())
                || font.width(line) <= MAX_WIDTH) {
                continue;
            }
            List<Component> wrapped = wrap(font, line);
            lines.remove(i);
            lines.addAll(i, wrapped);
            i += wrapped.size() - 1;
        }
    }

    private static List<Component> wrap(Font font, Component line) {
        List<FormattedText> split = font.getSplitter().splitLines(line, MAX_WIDTH, Style.EMPTY);
        List<Component> out = new ArrayList<>(Math.min(split.size(), MAX_LINES));
        for (int i = 0; i < split.size() && i < MAX_LINES; i++) {
            out.add(toComponent(split.get(i)));
        }
        if (split.size() > MAX_LINES) {
            FormattedText last = split.get(MAX_LINES - 1);
            FormattedText trimmed = font.substrByWidth(last, MAX_WIDTH - font.width(ELLIPSIS));
            out.set(MAX_LINES - 1, toComponent(trimmed)
                .append(Component.literal(ELLIPSIS).withStyle(ChatFormatting.GRAY)));
        }
        return out;
    }

    private static MutableComponent toComponent(FormattedText text) {
        MutableComponent out = Component.empty();
        text.visit((style, chunk) -> {
            out.append(Component.literal(chunk).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }
}
