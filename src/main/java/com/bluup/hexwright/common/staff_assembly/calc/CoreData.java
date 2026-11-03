package com.bluup.hexwright.common.staff_assembly.calc;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public record CoreData(String id, String powerId, double attunementCost) {

    private static final int DESCRIPTION_WRAP_WIDTH = 45;

    public Component displayName() {
        return Component.translatable("item.hexwright." + id);
    }

    public List<Component> description() {
        return wrap(Component.translatable("core_desc.hexwright." + id).getString(), DESCRIPTION_WRAP_WIDTH);
    }

    private static List<Component> wrap(String text, int maxWidth) {
        List<Component> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > maxWidth) {
                lines.add(Component.literal(line.toString()));
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(Component.literal(line.toString()));
        }
        return lines;
    }
}
