package com.bluup.hexwright.server.journal;

import com.bluup.hexwright.Hexwright;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LoreEntries {

    private static final String CLASSPATH_DEFAULT = "/assets/hexwright/journal/lore.json";
    private static final String OVERRIDE_FILE_NAME = "hexwright-lore.json";

    private static final JournalFile<Content> FILE =
        new JournalFile<>(CLASSPATH_DEFAULT, OVERRIDE_FILE_NAME, LoreEntries::parse, LoreEntries::empty);

    private LoreEntries() {
    }

    public record Content(List<LoreEntry> ordered, Map<String, LoreEntry> byId, Map<String, InvestigationIcon> images) {
    }

    public static Content get() {
        return FILE.get();
    }

    public static List<LoreEntry> all() {
        return get().ordered();
    }

    public static List<LoreEntry> visible(Collection<String> completedInvestigations) {
        List<LoreEntry> shown = new ArrayList<>();
        for (LoreEntry entry : all()) {
            if (completedInvestigations.containsAll(entry.requires())) {
                shown.add(entry);
            }
        }
        return shown;
    }

    public static void validate() {
        Content content = get();
        Map<String, Investigation> investigations = Investigations.get().byId();
        for (LoreEntry entry : content.ordered()) {
            for (String required : entry.requires()) {
                if (!investigations.containsKey(required)) {
                    Hexwright.LOGGER.warn(
                        "Lore entry '{}' requires investigation '{}', which no investigation defines - "
                            + "it can never be shown", entry.id(), required);
                }
            }
            if (!entry.image().isEmpty() && !content.images().containsKey(entry.image())) {
                Hexwright.LOGGER.warn("Lore entry '{}' names image '{}', which the images table doesn't define",
                    entry.id(), entry.image());
            }
            if (entry.text().isBlank()) {
                Hexwright.LOGGER.warn("Lore entry '{}' has no text; its page will be a heading and nothing else",
                    entry.id());
            }
        }
    }


    private static Content parse(JsonObject document) {
        if (document == null) {
            return empty();
        }

        Map<String, InvestigationIcon> images = new LinkedHashMap<>();
        JsonObject table = GsonHelper.getAsJsonObject(document, "images", new JsonObject());
        for (String name : table.keySet()) {
            try {
                images.put(name, InvestigationIcon.parse(name, table.get(name)));
            } catch (Throwable t) {
                Hexwright.LOGGER.error("Skipping malformed lore image '{}'", name, t);
            }
        }

        List<LoreEntry> ordered = new ArrayList<>();
        Map<String, LoreEntry> byId = new LinkedHashMap<>();

        JsonArray array = GsonHelper.getAsJsonArray(document, "lore", new JsonArray());
        for (int i = 0; i < array.size(); i++) {
            LoreEntry entry;
            try {
                entry = LoreEntry.parse(GsonHelper.convertToJsonObject(array.get(i), "lore[" + i + "]"));
            } catch (Throwable t) {
                Hexwright.LOGGER.error("Skipping malformed lore entry at index {}", i, t);
                continue;
            }
            if (byId.putIfAbsent(entry.id(), entry) != null) {
                Hexwright.LOGGER.warn("Duplicate lore id '{}'; keeping the first", entry.id());
                continue;
            }
            ordered.add(entry);
        }

        return new Content(List.copyOf(ordered), Map.copyOf(byId), Map.copyOf(images));
    }

    private static Content empty() {
        return new Content(List.of(), Map.of(), Map.of());
    }
}
