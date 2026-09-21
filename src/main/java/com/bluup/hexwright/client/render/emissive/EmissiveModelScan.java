package com.bluup.hexwright.client.render.emissive;

import com.bluup.hexwright.HexwrightDebug;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record EmissiveModelScan(
    Map<ResourceLocation, ResourceLocation> baseByGlowId,
    Map<ResourceLocation, ResourceLocation> glowByBaseId,
    Map<ResourceLocation, Map<String, String>> texturesByGlowId,
    Map<ResourceLocation, GlowParams> brightnessSources,
    Map<ResourceLocation, PartTwin> partTwinByBase,
    Map<ResourceLocation, String> partTwinJson
) {
    public static final String GLOWMASK_SUFFIX = "_glowmask";

    public static final String EMPTY_MASK = "hexwright:item/empty_glowmask";

    private static final int MAX_PARENT_DEPTH = 8;

    private static final Pattern PARENT = Pattern.compile("\"parent\"\\s*:\\s*\"([^\"]+)\"");

    private static final String SOURCES_PATH = "emissive/item_sources.json";

    public static final String PARTS_SUFFIX = "_glowparts";

    public static final EmissiveModelScan EMPTY =
        new EmissiveModelScan(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    public boolean isEmpty() {
        return baseByGlowId.isEmpty() && brightnessSources.isEmpty();
    }

    public static EmissiveModelScan scan(ResourceManager resources) {
        Map<ResourceLocation, ResourceLocation> masks = findGlowmaskTextures(resources);
        Map<ResourceLocation, GlowParams> brightnessSources = findBrightnessSources(resources);
        Map<ResourceLocation, PartTwin> partTwinByBase = new HashMap<>();
        Map<ResourceLocation, String> partTwinJson = new HashMap<>();
        for (ResourceLocation modelId : brightnessSources.keySet()) {
            partTwinFor(resources, modelId, partTwinByBase, partTwinJson);
        }

        if (masks.isEmpty()) {
            return brightnessSources.isEmpty()
                ? EMPTY
                : new EmissiveModelScan(Map.of(), Map.of(), Map.of(), brightnessSources,
                    Map.copyOf(partTwinByBase), Map.copyOf(partTwinJson));
        }

        Set<String> needles = new HashSet<>();
        for (ResourceLocation base : masks.keySet()) {
            needles.add(base.toString());
            if (base.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
                needles.add(base.getPath());
            }
        }

        Map<ResourceLocation, ResourceLocation> baseByGlowId = new HashMap<>();
        Map<ResourceLocation, ResourceLocation> glowByBaseId = new HashMap<>();
        Map<ResourceLocation, Map<String, String>> texturesByGlowId = new HashMap<>();

        Map<ResourceLocation, Resource> models = new LinkedHashMap<>(
            resources.listResources("models/item", path -> path.getPath().endsWith(".json")));
        models.putAll(resources.listResources("models/block", path -> path.getPath().endsWith(".json")));

        Map<ResourceLocation, String> inherited = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> inheritedParent = new HashMap<>();

        for (Map.Entry<ResourceLocation, Resource> entry : models.entrySet()) {
            String text = read(entry.getValue());
            if (text == null) {
                continue;
            }
            ResourceLocation modelId = modelIdOf(entry.getKey());
            if (modelId == null || modelId.getPath().endsWith(GLOWMASK_SUFFIX)) {
                continue;
            }
            if (mentionsAny(text, needles)) {
                accept(resources, modelId, text, masks, baseByGlowId, glowByBaseId, texturesByGlowId);
                continue;
            }
            ResourceLocation parent = parentOf(text);
            if (parent != null) {
                inherited.put(modelId, text);
                inheritedParent.put(modelId, parent);
            }
        }

        for (int depth = 0; depth < MAX_PARENT_DEPTH; depth++) {
            boolean grew = false;
            for (Iterator<Map.Entry<ResourceLocation, String>> it = inherited.entrySet().iterator();
                 it.hasNext(); ) {
                Map.Entry<ResourceLocation, String> entry = it.next();
                if (!glowByBaseId.containsKey(inheritedParent.get(entry.getKey()))) {
                    continue;
                }
                it.remove();
                grew |= accept(resources, entry.getKey(), entry.getValue(), masks,
                    baseByGlowId, glowByBaseId, texturesByGlowId);
            }
            if (!grew) {
                break;
            }
        }

        brightnessSources.keySet().removeAll(glowByBaseId.keySet());

        if (!baseByGlowId.isEmpty() || !brightnessSources.isEmpty()) {
            HexwrightDebug.log(HexwrightDebug.RENDER,
                "Emissive models: {} carry a glowmask, {} glow by brightness, {} name their glowing parts",
                baseByGlowId.size(), brightnessSources.size(), partTwinByBase.size());
        }
        partTwinByBase.keySet().retainAll(brightnessSources.keySet());
        return new EmissiveModelScan(Map.copyOf(baseByGlowId), Map.copyOf(glowByBaseId),
            Map.copyOf(texturesByGlowId), Map.copyOf(brightnessSources),
            Map.copyOf(partTwinByBase), Map.copyOf(partTwinJson));
    }

    private static boolean accept(ResourceManager resources, ResourceLocation modelId, String text,
                                  Map<ResourceLocation, ResourceLocation> masks,
                                  Map<ResourceLocation, ResourceLocation> baseByGlowId,
                                  Map<ResourceLocation, ResourceLocation> glowByBaseId,
                                  Map<ResourceLocation, Map<String, String>> texturesByGlowId) {
        Map<String, String> masked = maskTextures(resolveTextures(resources, modelId, text), masks);
        if (masked == null) {
            return false;
        }
        ResourceLocation glowId =
            new ResourceLocation(modelId.getNamespace(), modelId.getPath() + GLOWMASK_SUFFIX);
        baseByGlowId.put(glowId, modelId);
        glowByBaseId.put(modelId, glowId);
        texturesByGlowId.put(glowId, Map.copyOf(masked));
        return true;
    }

    @Nullable
    private static ResourceLocation parentOf(String text) {
        Matcher matcher = PARENT.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new ResourceLocation(matcher.group(1));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void partTwinFor(ResourceManager resources, ResourceLocation modelId,
                                    Map<ResourceLocation, PartTwin> partTwinByBase,
                                    Map<ResourceLocation, String> partTwinJson) {
        JsonObject json = parseModel(resources, modelId);
        if (json == null || !json.has("elements") || !json.get("elements").isJsonArray()) {
            return;
        }
        JsonArray elements = json.getAsJsonArray("elements");

        boolean anyForced = false;
        boolean anyExcluded = false;
        for (JsonElement element : elements) {
            String tag = glowTagOf(element);
            anyForced |= "glow".equals(tag);
            anyExcluded |= "noglow".equals(tag);
        }
        if (!anyForced && !anyExcluded) {
            return;
        }

        JsonArray kept = new JsonArray();
        for (JsonElement element : elements) {
            String tag = glowTagOf(element);
            boolean keep = anyForced ? "glow".equals(tag) : !"noglow".equals(tag);
            if (keep) {
                kept.add(element);
            }
        }
        if (kept.isEmpty()) {
            return;
        }

        JsonObject twin = json.deepCopy();
        twin.add("elements", kept);
        twin.remove("overrides");

        ResourceLocation twinId =
            new ResourceLocation(modelId.getNamespace(), modelId.getPath() + PARTS_SUFFIX);
        partTwinByBase.put(modelId, new PartTwin(twinId, !anyForced));
        partTwinJson.put(twinId, twin.toString());
    }

    private static String glowTagOf(JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        if (!object.has("name") || !object.get("name").isJsonPrimitive()) {
            return null;
        }
        return glowTagOf(object.get("name").getAsString());
    }

    @Nullable
    public static String glowTagOf(@Nullable String elementName) {
        if (elementName == null) {
            return null;
        }
        String name = elementName.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (name.contains("noglow")) {
            return "noglow";
        }
        return name.contains("glow") ? "glow" : null;
    }

    private static Map<ResourceLocation, GlowParams> findBrightnessSources(ResourceManager resources) {
        Map<String, GlowParams> patterns = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resources.listResources(
            "emissive", path -> path.getPath().endsWith("item_sources.json")).entrySet()) {

            JsonObject json = parse(read(entry.getValue()));
            if (json == null || !json.has("sources") || !json.get("sources").isJsonArray()) {
                continue;
            }
            for (JsonElement element : json.getAsJsonArray("sources")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject source = element.getAsJsonObject();
                if (!source.has("model")) {
                    continue;
                }
                patterns.put(source.get("model").getAsString(), new GlowParams(
                    source.has("strength") ? source.get("strength").getAsFloat() : 1.0f,
                    source.has("threshold") ? source.get("threshold").getAsFloat() : GlowParams.INHERIT,
                    source.has("saturation") ? source.get("saturation").getAsFloat() : GlowParams.INHERIT));
            }
        }
        if (patterns.isEmpty()) {
            return new HashMap<>();
        }

        Map<ResourceLocation, GlowParams> sources = new HashMap<>();
        for (ResourceLocation path : resources.listResources(
            "models/item", p -> p.getPath().endsWith(".json")).keySet()) {

            ResourceLocation modelId = modelIdOf(path);
            if (modelId == null) {
                continue;
            }
            for (Map.Entry<String, GlowParams> pattern : patterns.entrySet()) {
                if (matches(pattern.getKey(), modelId.toString())) {
                    sources.put(modelId, pattern.getValue());
                }
            }
        }
        sources.values().removeIf(params -> params.strength() <= 0.0f);
        return sources;
    }

    private static boolean matches(String pattern, String value) {
        int star = pattern.indexOf('*');
        if (star < 0) {
            return pattern.equals(value);
        }
        String head = pattern.substring(0, star);
        String tail = pattern.substring(star + 1);
        return value.length() >= head.length() + tail.length()
            && value.startsWith(head) && value.endsWith(tail);
    }

    private static Map<ResourceLocation, ResourceLocation> findGlowmaskTextures(ResourceManager resources) {
        Map<ResourceLocation, ResourceLocation> masks = new HashMap<>();
        String tail = GLOWMASK_SUFFIX + ".png";
        for (ResourceLocation path : resources.listResources("textures", id -> id.getPath().endsWith(tail)).keySet()) {
            String full = path.getPath();
            String texture = full.substring("textures/".length(), full.length() - ".png".length());
            ResourceLocation mask = new ResourceLocation(path.getNamespace(), texture);
            if (mask.toString().equals(EMPTY_MASK)) {
                continue;
            }
            String base = texture.substring(0, texture.length() - GLOWMASK_SUFFIX.length());
            masks.put(new ResourceLocation(path.getNamespace(), base), mask);
        }
        return masks;
    }

    private static ResourceLocation modelIdOf(ResourceLocation resourcePath) {
        String path = resourcePath.getPath();
        if (!path.startsWith("models/") || !path.endsWith(".json")) {
            return null;
        }
        return new ResourceLocation(resourcePath.getNamespace(),
            path.substring("models/".length(), path.length() - ".json".length()));
    }

    private static Map<String, String> resolveTextures(
        ResourceManager resources, ResourceLocation modelId, String ownText) {

        List<JsonObject> chain = new ArrayList<>();
        JsonObject json = parse(ownText);
        Set<ResourceLocation> seen = new HashSet<>();
        seen.add(modelId);
        for (int depth = 0; json != null && depth < MAX_PARENT_DEPTH; depth++) {
            chain.add(json);
            if (!json.has("parent") || !json.get("parent").isJsonPrimitive()) {
                break;
            }
            ResourceLocation parent = new ResourceLocation(json.get("parent").getAsString());
            if (!seen.add(parent)) {
                break;
            }
            json = parseModel(resources, parent);
        }

        Map<String, String> textures = new LinkedHashMap<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            JsonObject entry = chain.get(i);
            if (!entry.has("textures") || !entry.get("textures").isJsonObject()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> texture : entry.getAsJsonObject("textures").entrySet()) {
                if (texture.getValue().isJsonPrimitive()) {
                    textures.put(texture.getKey(), texture.getValue().getAsString());
                }
            }
        }
        return textures;
    }

    private static Map<String, String> maskTextures(
        Map<String, String> textures, Map<ResourceLocation, ResourceLocation> masks) {

        Map<String, String> masked = new LinkedHashMap<>();
        boolean any = false;
        for (Map.Entry<String, String> entry : textures.entrySet()) {
            String value = entry.getValue();
            if (entry.getKey().equals("particle") || value.startsWith("#")) {
                masked.put(entry.getKey(), value);
                continue;
            }
            ResourceLocation mask = masks.get(new ResourceLocation(value));
            if (mask == null) {
                masked.put(entry.getKey(), EMPTY_MASK);
            } else {
                masked.put(entry.getKey(), mask.toString());
                any = true;
            }
        }
        return any ? masked : null;
    }

    private static boolean mentionsAny(String text, Set<String> needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject parseModel(ResourceManager resources, ResourceLocation modelId) {
        ResourceLocation path =
            new ResourceLocation(modelId.getNamespace(), "models/" + modelId.getPath() + ".json");
        Optional<Resource> resource = resources.getResource(path);
        return resource.map(value -> parse(read(value))).orElse(null);
    }

    private static JsonObject parse(String text) {
        if (text == null) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(text);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String read(Resource resource) {
        try (BufferedReader reader = resource.openAsReader()) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                text.append(buffer, 0, read);
            }
            return text.toString();
        } catch (IOException e) {
            return null;
        }
    }
}
