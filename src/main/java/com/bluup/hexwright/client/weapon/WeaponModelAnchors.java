package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class WeaponModelAnchors implements SimpleSynchronousResourceReloadListener {
    private static final ResourceLocation ID = Hexwright.id("weapon_model_anchors");

    static final float MODEL_UNITS_PER_BLOCK = 16.0f;

    static final String OFFSET_FILE = "hexwright-trail-offsets.json";

    private static Map<Item, Anchor> anchors = Map.of();

    private WeaponModelAnchors() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new WeaponModelAnchors());
    }

    @Nullable
    public static Vector3f get(Item item) {
        Anchor anchor = anchors.get(item);
        if (anchor == null) {
            return null;
        }
        return new Vector3f(anchor.centre).add(offsetOf(item)).div(MODEL_UNITS_PER_BLOCK);
    }

    @Nullable
    static Vector3f centreOf(Item item) {
        Anchor anchor = anchors.get(item);
        return anchor == null ? null : new Vector3f(anchor.centre);
    }

    static Vector3f offsetOf(Item item) {
        Vector3f live = WeaponTrailTuner.offsetOf(item);
        if (live != null) {
            return live;
        }
        Anchor anchor = anchors.get(item);
        return anchor == null ? new Vector3f() : new Vector3f(anchor.offset);
    }

    static Iterable<Item> tunable() {
        return anchors.keySet();
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        Map<Item, Anchor> loaded = new HashMap<>();
        JsonObject saved = readSavedOffsets();

        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof AnimatedWeapon weapon)) {
                continue;
            }
            String part = weapon.trailAnchorPart();
            if (part == null) {
                continue;
            }

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            ResourceLocation modelId = new ResourceLocation(
                itemId.getNamespace(), "models/item/" + itemId.getPath() + ".json");
            Optional<Resource> resource = resourceManager.getResource(modelId);
            if (resource.isEmpty()) {
                Hexwright.LOGGER.warn("Weapon {} wants a trail on part '{}' but {} is missing",
                    itemId, part, modelId);
                continue;
            }

            try (Reader reader = new InputStreamReader(resource.get().open())) {
                Vector3f centre = boxCentre(JsonParser.parseReader(reader), part);
                if (centre == null) {
                    Hexwright.LOGGER.warn("Weapon model {} has no element named '{}'", modelId, part);
                    continue;
                }

                Vector3f fromFile = savedOffset(saved, itemId);
                Vector3f offset = fromFile != null ? fromFile : new Vector3f(weapon.trailAnchorOffset());
                if (fromFile != null) {
                    HexwrightDebug.log(HexwrightDebug.CONTENT, "Trail offset for {} taken from {}: {}",
                        itemId, OFFSET_FILE, offset);
                }
                loaded.put(item, new Anchor(centre, offset));
            } catch (Exception e) {
                Hexwright.LOGGER.error("Failed to read trail anchor '{}' from {}", part, modelId, e);
            }
        }

        anchors = Map.copyOf(loaded);
    }

    static Path saveOffset(Item item, Vector3f offset) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(OFFSET_FILE);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);

        JsonObject root = readSavedOffsets();
        JsonArray values = new JsonArray();
        values.add(offset.x);
        values.add(offset.y);
        values.add(offset.z);
        root.add(itemId.toString(), values);

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, root.toString());
        } catch (Exception e) {
            Hexwright.LOGGER.error("Could not write trail offsets to {}", path, e);
        }

        Anchor anchor = anchors.get(item);
        if (anchor != null) {
            anchor.offset.set(offset);
        }
        return path;
    }

    @Nullable
    private static Vector3f boxCentre(JsonElement root, String part) {
        if (!root.isJsonObject()) {
            return null;
        }
        JsonElement elements = root.getAsJsonObject().get("elements");
        if (elements == null || !elements.isJsonArray()) {
            return null;
        }

        for (JsonElement entry : elements.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject element = entry.getAsJsonObject();
            JsonElement name = element.get("name");
            if (name == null || !name.isJsonPrimitive() || !part.equals(name.getAsString())) {
                continue;
            }

            JsonArray from = element.getAsJsonArray("from");
            JsonArray to = element.getAsJsonArray("to");
            if (from == null || to == null || from.size() != 3 || to.size() != 3) {
                return null;
            }
            return new Vector3f(midpoint(from, to, 0), midpoint(from, to, 1), midpoint(from, to, 2));
        }
        return null;
    }

    private static float midpoint(JsonArray from, JsonArray to, int axis) {
        return (from.get(axis).getAsFloat() + to.get(axis).getAsFloat()) / 2.0f;
    }

    private static JsonObject readSavedOffsets() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(OFFSET_FILE);
        if (!Files.isRegularFile(path)) {
            return new JsonObject();
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            Hexwright.LOGGER.warn("Could not read trail offsets from {}", path, e);
            return new JsonObject();
        }
    }

    @Nullable
    private static Vector3f savedOffset(JsonObject saved, ResourceLocation itemId) {
        JsonElement entry = saved.get(itemId.toString());
        if (entry == null || !entry.isJsonArray()) {
            return null;
        }
        JsonArray values = entry.getAsJsonArray();
        if (values.size() != 3) {
            Hexwright.LOGGER.warn("Trail offset for {} needs three numbers, got {}", itemId, values);
            return null;
        }
        return new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(),
            values.get(2).getAsFloat());
    }

    private static final class Anchor {
        private final Vector3f centre;

        private final Vector3f offset;

        private Anchor(Vector3f centre, Vector3f offset) {
            this.centre = centre;
            this.offset = offset;
        }
    }
}
