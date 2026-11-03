package com.bluup.hexwright.server.journal;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.item.ArtifactItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Artifacts {

    private static final String CLASSPATH_DEFAULT = "/assets/hexwright/journal/artifacts.json";
    private static final String OVERRIDE_FILE_NAME = "hexwright-artifacts.json";

    private static final JournalFile<Content> FILE =
        new JournalFile<>(CLASSPATH_DEFAULT, OVERRIDE_FILE_NAME, Artifacts::parse, Artifacts::empty);

    private Artifacts() {
    }

    public record Content(List<Artifact> ordered, Map<String, Artifact> byId) {
    }

    public static Content get() {
        return FILE.get();
    }

    public static List<Artifact> all() {
        return get().ordered();
    }

    public static void validate() {
        for (Artifact artifact : all()) {
            if (!BuiltInRegistries.ITEM.containsKey(artifact.item())) {
                Hexwright.LOGGER.warn("Artifact '{}' names item '{}', which isn't registered - it can never be found",
                    artifact.id(), artifact.item());
                continue;
            }
            ItemStack stack = artifact.displayStack();
            if (stack.getItem() instanceof ArtifactItem && !ArtifactItem.is(stack)) {
                Hexwright.LOGGER.warn("Artifact '{}' draws a stack of '{}' that isn't an artifact - check its nbt; "
                    + "it can never be found", artifact.id(), artifact.item());
            }
        }
    }


    private static Content parse(JsonObject document) {
        if (document == null) {
            return empty();
        }

        List<Artifact> ordered = new ArrayList<>();
        Map<String, Artifact> byId = new LinkedHashMap<>();

        JsonArray array = GsonHelper.getAsJsonArray(document, "artifacts", new JsonArray());
        for (int i = 0; i < array.size(); i++) {
            Artifact artifact;
            try {
                artifact = Artifact.parse(GsonHelper.convertToJsonObject(array.get(i), "artifacts[" + i + "]"));
            } catch (Throwable t) {
                Hexwright.LOGGER.error("Skipping malformed artifact at index {}", i, t);
                continue;
            }
            if (byId.putIfAbsent(artifact.id(), artifact) != null) {
                Hexwright.LOGGER.warn("Duplicate artifact id '{}'; keeping the first", artifact.id());
                continue;
            }
            ordered.add(artifact);
        }

        return new Content(List.copyOf(ordered), Map.copyOf(byId));
    }

    private static Content empty() {
        return new Content(List.of(), Map.of());
    }
}
