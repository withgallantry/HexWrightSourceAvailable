package com.bluup.hexwright.client.staff_assembly;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.client.render.emissive.EmissiveModelScan;
import com.bluup.hexwright.common.staff_assembly.StaffPart;
import com.bluup.hexwright.common.staff_assembly.StaffPartCategory;
import com.bluup.hexwright.common.staff_assembly.StaffParts;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class StaffTipAnchors implements SimpleSynchronousResourceReloadListener {
    private static final ResourceLocation ID = Hexwright.id("staff_tip_anchors");

    private static final float MODEL_UNITS_PER_BLOCK = 16.0f;

    private static Map<String, Vector3f> anchors = Map.of();

    private StaffTipAnchors() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new StaffTipAnchors());
    }

    @Nullable
    public static Vector3f get(String modelId) {
        Vector3f anchor = anchors.get(modelId);
        return anchor == null ? null : new Vector3f(anchor).div(MODEL_UNITS_PER_BLOCK);
    }

    public static int count() {
        return anchors.size();
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        Map<String, Vector3f> loaded = new HashMap<>();

        for (StaffPart part : StaffParts.options(StaffPartCategory.MODEL)) {
            String modelId = part.id();
            ResourceLocation file = Hexwright.id("models/item/" + modelId + ".json");
            Optional<Resource> resource = resourceManager.getResource(file);
            if (resource.isEmpty()) {
                Hexwright.LOGGER.warn("Staff model {} has no model file at {}", modelId, file);
                continue;
            }

            try (Reader reader = new InputStreamReader(resource.get().open())) {
                Vector3f anchor = anchorOf(JsonParser.parseReader(reader), part.hasTag(StaffPart.TAG_BOOK));
                if (anchor == null) {
                    Hexwright.LOGGER.warn("Staff model {} declares no elements, so it has no tip", modelId);
                    continue;
                }
                loaded.put(modelId, anchor);
            } catch (Exception e) {
                Hexwright.LOGGER.error("Failed to read the tip anchor out of {}", file, e);
            }
        }

        anchors = Map.copyOf(loaded);
        HexwrightDebug.log(HexwrightDebug.CONTENT, "Resolved tip anchors for {} staff models", anchors.size());
    }

    @Nullable
    private static Vector3f anchorOf(JsonElement root, boolean book) {
        if (!root.isJsonObject()) {
            return null;
        }
        JsonElement elements = root.getAsJsonObject().get("elements");
        if (elements == null || !elements.isJsonArray()) {
            return null;
        }

        Vector3f glowTop = null;
        float glowTopY = Float.NEGATIVE_INFINITY;
        Vector3f anyTop = null;
        float anyTopY = Float.NEGATIVE_INFINITY;
        Bounds glowBounds = new Bounds();
        Bounds allBounds = new Bounds();

        for (JsonElement entry : elements.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject element = entry.getAsJsonObject();
            JsonArray from = element.getAsJsonArray("from");
            JsonArray to = element.getAsJsonArray("to");
            if (from == null || to == null || from.size() != 3 || to.size() != 3) {
                continue;
            }

            Vector3f centre = new Vector3f(
                midpoint(from, to, 0), midpoint(from, to, 1), midpoint(from, to, 2));
            float top = Math.max(from.get(1).getAsFloat(), to.get(1).getAsFloat());

            allBounds.add(from, to);
            if (top > anyTopY) {
                anyTopY = top;
                anyTop = centre;
            }

            String name = element.has("name") && element.get("name").isJsonPrimitive()
                ? element.get("name").getAsString()
                : null;
            if (!"glow".equals(EmissiveModelScan.glowTagOf(name))) {
                continue;
            }
            glowBounds.add(from, to);
            if (top > glowTopY) {
                glowTopY = top;
                glowTop = centre;
            }
        }

        if (book) {
            Vector3f runes = glowBounds.centre();
            return runes != null ? runes : allBounds.centre();
        }
        return glowTop != null ? glowTop : anyTop;
    }

    private static float midpoint(JsonArray from, JsonArray to, int axis) {
        return (from.get(axis).getAsFloat() + to.get(axis).getAsFloat()) / 2.0f;
    }

    private static final class Bounds {
        private final float[] lo = {
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
        private final float[] hi = {
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        private boolean any;

        private void add(JsonArray from, JsonArray to) {
            any = true;
            for (int axis = 0; axis < 3; axis++) {
                float a = from.get(axis).getAsFloat();
                float b = to.get(axis).getAsFloat();
                lo[axis] = Math.min(lo[axis], Math.min(a, b));
                hi[axis] = Math.max(hi[axis], Math.max(a, b));
            }
        }

        @Nullable
        private Vector3f centre() {
            if (!any) {
                return null;
            }
            return new Vector3f(
                (lo[0] + hi[0]) / 2.0f, (lo[1] + hi[1]) / 2.0f, (lo[2] + hi[2]) / 2.0f);
        }
    }
}
