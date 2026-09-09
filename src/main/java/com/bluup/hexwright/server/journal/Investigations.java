package com.bluup.hexwright.server.journal;

import com.bluup.hexwright.Hexwright;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Investigations {

    private static final String CLASSPATH_DEFAULT = "/assets/hexwright/journal/investigations.json";
    private static final String OVERRIDE_FILE_NAME = "hexwright-investigations.json";

    private static final JournalFile<Content> FILE =
        new JournalFile<>(CLASSPATH_DEFAULT, OVERRIDE_FILE_NAME, Investigations::parse, Investigations::empty);

    private Investigations() {
    }

    public record Content(
        List<Investigation> ordered,
        Map<String, Investigation> byId,
        Map<String, InvestigationIcon> icons,
        Map<String, InvestigationIcon> images,
        String completedIcon,
        Map<String, CompletionCondition.Counted> countedByKey
    ) {
    }

    public static Content get() {
        return FILE.get();
    }

    public static List<Investigation> all() {
        return get().ordered();
    }

    public static List<Investigation> visible(Collection<String> completed) {
        List<Investigation> shown = new ArrayList<>();
        for (Investigation investigation : all()) {
            if (completed.containsAll(investigation.requires())) {
                shown.add(investigation);
            }
        }
        return shown;
    }

    public static void validate() {
        Content content = get();
        if (!content.completedIcon().isEmpty() && !content.icons().containsKey(content.completedIcon())) {
            Hexwright.LOGGER.warn("completed_icon names '{}', which the icons table doesn't define - "
                + "finished rows will keep their own icon", content.completedIcon());
        }
        for (Investigation investigation : content.ordered()) {
            for (String required : investigation.requires()) {
                if (!content.byId().containsKey(required)) {
                    Hexwright.LOGGER.warn(
                        "Investigation '{}' requires '{}', which no investigation defines - it can never be shown",
                        investigation.id(), required);
                }
            }
            if (!investigation.icon().isEmpty() && !content.icons().containsKey(investigation.icon())) {
                Hexwright.LOGGER.warn("Investigation '{}' names icon '{}', which the icons table doesn't define",
                    investigation.id(), investigation.icon());
            }
            String image = investigation.detail().image();
            if (!image.isEmpty() && !content.images().containsKey(image)) {
                Hexwright.LOGGER.warn("Investigation '{}' names image '{}', which the images table doesn't define",
                    investigation.id(), image);
            }
        }
    }


    private static Content parse(JsonObject document) {
        if (document == null) {
            return empty();
        }

        Map<String, InvestigationIcon> icons = parseArt(document, "icons");
        Map<String, InvestigationIcon> images = parseArt(document, "images");
        String completedIcon = GsonHelper.getAsString(document, "completed_icon", "");

        List<Investigation> ordered = new ArrayList<>();
        Map<String, Investigation> byId = new LinkedHashMap<>();
        Map<String, CompletionCondition.Counted> counted = new LinkedHashMap<>();

        JsonArray array = GsonHelper.getAsJsonArray(document, "investigations", new JsonArray());
        for (int i = 0; i < array.size(); i++) {
            Investigation investigation;
            try {
                investigation = Investigation.parse(GsonHelper.convertToJsonObject(array.get(i), "investigations[" + i + "]"));
            } catch (Throwable t) {
                Hexwright.LOGGER.error("Skipping malformed investigation at index {}", i, t);
                continue;
            }
            if (byId.putIfAbsent(investigation.id(), investigation) != null) {
                Hexwright.LOGGER.warn("Duplicate investigation id '{}'; keeping the first", investigation.id());
                continue;
            }
            ordered.add(investigation);

            List<CompletionCondition.Counted> found = new ArrayList<>();
            investigation.completion().collectCounted(found);
            for (CompletionCondition.Counted condition : found) {
                counted.putIfAbsent(condition.counterKey(), condition);
            }
        }

        return new Content(List.copyOf(ordered), Map.copyOf(byId), icons, images, completedIcon, Map.copyOf(counted));
    }

    private static Map<String, InvestigationIcon> parseArt(JsonObject document, String field) {
        Map<String, InvestigationIcon> parsed = new LinkedHashMap<>();
        JsonObject table = GsonHelper.getAsJsonObject(document, field, new JsonObject());
        for (String name : table.keySet()) {
            try {
                parsed.put(name, InvestigationIcon.parse(name, table.get(name)));
            } catch (Throwable t) {
                Hexwright.LOGGER.error("Skipping malformed journal {} entry '{}'", field, name, t);
            }
        }
        return Map.copyOf(parsed);
    }

    private static Content empty() {
        return new Content(List.of(), Map.of(), Map.of(), Map.of(), "", Map.of());
    }

    static Set<String> counterKeys() {
        return new LinkedHashSet<>(get().countedByKey().keySet());
    }
}
