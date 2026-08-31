package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.item.ArtifactItem;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class WeaponTooltips {

    public static final int MAX_LINE_CHARS = 50;

    private WeaponTooltips() {
    }

    public static Component graded(ItemStack stack, Component name,
                                   @Nullable PocketCasterData.Quality quality) {
        if (stack.getItem() instanceof ArtifactItem) {
            return name.copy().withStyle(ArtifactItem.COLOUR);
        }
        return quality == null ? name : name.copy().withStyle(quality.color());
    }

    public static void add(List<Component> tooltip, Component line, ChatFormatting style) {
        for (String wrapped : wrap(line.getString())) {
            tooltip.add(Component.literal(wrapped).withStyle(style));
        }
    }

    public static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= MAX_LINE_CHARS) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }
}
