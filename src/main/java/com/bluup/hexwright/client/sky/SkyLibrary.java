package com.bluup.hexwright.client.sky;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SkyLibrary implements SimpleSynchronousResourceReloadListener {
    private static final ResourceLocation ID = Hexwright.id("skies");
    private static final FileToIdConverter FILES = FileToIdConverter.json("skies");
    private static final int MAX_SUNS = 2;
    private static final float DEFAULT_TURN_MINUTES = 30.0F;

    private static Map<ResourceLocation, Sky> skies = Map.of();
    private static @Nullable ShaderInstance skyShader;
    private static @Nullable ShaderInstance backdropShader;

    private SkyLibrary() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SkyLibrary());
    }

    static @Nullable Sky get(ResourceLocation id) {
        return skies.get(id);
    }

    static @Nullable ShaderInstance skyShader() {
        return skyShader;
    }

    static @Nullable ShaderInstance backdropShader() {
        return backdropShader;
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        skyShader = reloadShader(resourceManager, skyShader, "hexwright_sky");
        backdropShader = reloadShader(resourceManager, backdropShader, "hexwright_sky_backdrop");

        Map<ResourceLocation, Sky> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : FILES.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation file = entry.getKey();
            try (Reader reader = entry.getValue().openAsReader()) {
                loaded.put(FILES.fileToId(file), Sky.parse(GsonHelper.parse(reader)));
            } catch (IOException | RuntimeException e) {
                Hexwright.LOGGER.error("Failed to load sky {}; wherever it hangs keeps the dimension's own", file, e);
            }
        }
        skies = loaded;
        HexwrightDebug.log(HexwrightDebug.RENDER, "Loaded {} skies", loaded.size());
    }

    private static @Nullable ShaderInstance reloadShader(ResourceManager resourceManager,
                                                         @Nullable ShaderInstance previous, String name) {
        if (previous != null) {
            previous.close();
        }
        try {
            return new ShaderInstance(resourceManager, name, DefaultVertexFormat.POSITION);
        } catch (IOException | RuntimeException e) {
            Hexwright.LOGGER.error("Failed to load {} shader; every sky falls back to the dimension's own", name, e);
            return null;
        }
    }

    enum Style {
        NEBULA,
        DAY,
        LATTICE,
        ORRERY,
        PRISM;

        static Style byName(String name) {
            for (Style style : values()) {
                if (style.name().equalsIgnoreCase(name)) {
                    return style;
                }
            }
            throw new JsonSyntaxException("Unknown sky style " + name);
        }
    }

    record Sky(Style style, Vector3f base, Vector3f primary, Vector3f secondary, Vector3f highlight, float coverage,
               float seed, float streaks, float turnMinutes, List<Vector4f> suns) {

        static Sky parse(JsonObject json) {
            JsonArray sunsJson = GsonHelper.getAsJsonArray(json, "suns", new JsonArray());
            if (sunsJson.size() > MAX_SUNS) {
                throw new JsonSyntaxException("At most " + MAX_SUNS + " suns, found " + sunsJson.size());
            }
            List<Vector4f> suns = new ArrayList<>();
            for (JsonElement element : sunsJson) {
                JsonObject sun = GsonHelper.convertToJsonObject(element, "sun");
                double yaw = Math.toRadians(GsonHelper.getAsFloat(sun, "yaw"));
                double pitch = Math.toRadians(GsonHelper.getAsFloat(sun, "pitch"));
                suns.add(new Vector4f((float) (Math.sin(yaw) * Math.cos(pitch)), (float) Math.sin(pitch),
                    (float) (-Math.cos(yaw) * Math.cos(pitch)), GsonHelper.getAsFloat(sun, "size")));
            }
            return new Sky(Style.byName(GsonHelper.getAsString(json, "style")),
                colour(json, "base"), colour(json, "primary"), colour(json, "secondary"), colour(json, "highlight"),
                GsonHelper.getAsFloat(json, "coverage"), GsonHelper.getAsFloat(json, "seed"),
                GsonHelper.getAsFloat(json, "streaks", 0.0F),
                GsonHelper.getAsFloat(json, "turn_minutes", DEFAULT_TURN_MINUTES), List.copyOf(suns));
        }

        private static Vector3f colour(JsonObject json, String key) {
            String hex = GsonHelper.getAsString(json, key);
            if (!hex.matches("#[0-9a-fA-F]{6}")) {
                throw new JsonSyntaxException(key + " must be a #rrggbb colour, was " + hex);
            }
            int rgb = Integer.parseInt(hex.substring(1), 16);
            return new Vector3f((rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F, (rgb & 255) / 255.0F);
        }

        void apply(ShaderInstance shader) {
            shader.safeGetUniform("Style").set(this.style.ordinal());
            shader.safeGetUniform("BaseColor").set(this.base);
            shader.safeGetUniform("PrimaryColor").set(this.primary);
            shader.safeGetUniform("SecondaryColor").set(this.secondary);
            shader.safeGetUniform("HighlightColor").set(this.highlight);
            shader.safeGetUniform("Coverage").set(this.coverage);
            shader.safeGetUniform("Seed").set(this.seed);
            shader.safeGetUniform("Streaks").set(this.streaks);
            for (int i = 0; i < MAX_SUNS; i++) {
                Vector4f sun = i < this.suns.size() ? this.suns.get(i) : new Vector4f(0.0F, 1.0F, 0.0F, 0.0F);
                shader.safeGetUniform("Sun" + i).set(sun);
            }
        }
    }
}
