package com.bluup.hexwright.server.journal;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

public record Investigation(
    String id,
    String title,
    String description,
    String icon,
    Detail detail,
    List<String> requires,
    CompletionCondition completion,
    List<String> unlocks
) {

    public record Detail(String title, String image, List<String> lines) {
    }

    public Component titleComponent() {
        return Component.translatable(title);
    }

    public Component descriptionComponent() {
        return Component.translatable(description);
    }

    public Component detailTitleComponent() {
        return Component.translatable(detail.title());
    }

    public List<String> detailLines() {
        List<String> escaped = new ArrayList<>(detail.lines().size());
        for (String line : detail.lines()) {
            escaped.add(line.replace("%", "%%"));
        }
        return escaped;
    }

    static Investigation parse(JsonObject json) {
        String id = GsonHelper.getAsString(json, "id");
        JsonObject detail = GsonHelper.getAsJsonObject(json, "detail");

        return new Investigation(
            id,
            GsonHelper.getAsString(json, "title"),
            GsonHelper.getAsString(json, "description", ""),
            GsonHelper.getAsString(json, "icon", ""),
            new Detail(
                GsonHelper.getAsString(detail, "title", GsonHelper.getAsString(json, "title")),
                GsonHelper.getAsString(detail, "image", ""),
                strings(GsonHelper.getAsJsonArray(detail, "lines", new JsonArray()))
            ),
            strings(GsonHelper.getAsJsonArray(json, "requires", new JsonArray())),
            CompletionCondition.parse(GsonHelper.getAsJsonObject(json, "completion")),
            strings(GsonHelper.getAsJsonArray(json, "unlocks", new JsonArray()))
        );
    }

    private static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            values.add(array.get(i).getAsString());
        }
        return List.copyOf(values);
    }
}
