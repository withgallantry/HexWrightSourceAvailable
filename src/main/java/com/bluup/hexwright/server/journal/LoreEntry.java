package com.bluup.hexwright.server.journal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

public record LoreEntry(
    String id,
    String title,
    String type,
    String cap,
    String image,
    String text,
    List<String> requires
) {

    public Component titleComponent() {
        return Component.translatable(title);
    }

    public Component typeComponent() {
        return Component.translatable(type);
    }

    public Component capComponent() {
        if (!cap.isEmpty()) {
            return Component.translatable(cap);
        }
        String rendered = titleComponent().getString().trim();
        return rendered.isEmpty()
            ? Component.empty()
            : Component.literal(rendered.substring(0, rendered.offsetByCodePoints(0, 1)).toUpperCase());
    }

    public List<String> bodyLines() {
        String rendered = Component.translatable(text).getString();
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : rendered.split("\r?\n", -1)) {
            paragraphs.add(paragraph.isBlank() ? " " : paragraph.replace("%", "%%"));
        }
        return paragraphs;
    }

    static LoreEntry parse(JsonObject json) {
        return new LoreEntry(
            GsonHelper.getAsString(json, "id"),
            GsonHelper.getAsString(json, "title"),
            GsonHelper.getAsString(json, "type", ""),
            GsonHelper.getAsString(json, "cap", ""),
            GsonHelper.getAsString(json, "image", ""),
            body(json),
            strings(GsonHelper.getAsJsonArray(json, "requires", new JsonArray()))
        );
    }

    private static String body(JsonObject json) {
        JsonElement value = json.get("text");
        if (value == null || value.isJsonNull()) {
            return "";
        }
        if (!value.isJsonArray()) {
            return GsonHelper.convertToString(value, "text");
        }
        return String.join("\n", strings(value.getAsJsonArray()));
    }

    private static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            values.add(array.get(i).getAsString());
        }
        return List.copyOf(values);
    }
}
