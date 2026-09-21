package com.bluup.hexwright.client.render.emissive;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EmissiveBloomConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "hexwright-emissive-bloom.json";

    private static final float FRAMEBUFFER_SCALE_MIN = 0.0625f;
    private static final float FRAMEBUFFER_SCALE_MAX = 1.0f;
    private static final float CORE_STRENGTH_MIN = 0.0f;
    private static final float CORE_STRENGTH_MAX = 4.0f;
    private static final float GLOW_THRESHOLD_MIN = 0.0f;
    private static final float GLOW_THRESHOLD_MAX = 1.0f;
    private static final float GLOW_SATURATION_MIN = 0.0f;
    private static final float GLOW_SATURATION_MAX = 1.0f;
    private static final float GLOW_KNEE_MIN = 0.0f;
    private static final float GLOW_KNEE_MAX = 1.0f;
    private static final float INTENSITY_MIN = 0.0f;
    private static final float INTENSITY_MAX = 4.0f;
    private static final int DEBUG_MODE_MIN = 0;
    private static final int DEBUG_MODE_MAX = 3;
    private static final float BLUR_RADIUS_MIN = 0.0f;
    private static final float BLUR_RADIUS_MAX = 8.0f;
    private static final int BLOCK_GLOW_DISTANCE_MIN = 0;
    private static final int BLOCK_GLOW_DISTANCE_MAX = 128;
    private static final float BLOCK_GLOW_LIFT_MIN = 0.0f;
    private static final float BLOCK_GLOW_LIFT_MAX = 0.25f;
    private static final float BLOCK_GLOW_DEPTH_BIAS_MIN = -1024.0f;
    private static final float BLOCK_GLOW_DEPTH_BIAS_MAX = 0.0f;
    private static final float BLOCK_GLOW_DEPTH_SLOPE_MIN = -16.0f;
    private static final float BLOCK_GLOW_DEPTH_SLOPE_MAX = 0.0f;
    private static final float BLOCK_GLOW_STRENGTH_MIN = 0.0f;
    private static final float BLOCK_GLOW_STRENGTH_MAX = 1.0f;

    private static final EmissiveBloomConfig CONFIG = new EmissiveBloomConfig();

    private EmissiveBloomConfigManager() {
    }

    public static EmissiveBloomConfig get() {
        return CONFIG;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static void reload() {
        Path path = configPath();
        if (!Files.exists(path)) {
            write(path, new EmissiveBloomConfig(), "Created default bloom config at {}");
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            EmissiveBloomConfig loaded = GSON.fromJson(reader, EmissiveBloomConfig.class);
            if (loaded == null) {
                throw new JsonSyntaxException("empty or null config document");
            }
            applyClamped(loaded);
            HexwrightDebug.log(HexwrightDebug.RENDER, "Loaded bloom config from {}", path);
        } catch (IOException | JsonSyntaxException e) {
            Hexwright.LOGGER.error("Failed to read bloom config at {}, keeping previous values", path, e);
        }
    }

    public static void save() {
        write(configPath(), CONFIG, null);
    }

    private static void write(Path path, EmissiveBloomConfig config, @Nullable String successMessage) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            if (successMessage != null) {
                HexwrightDebug.log(HexwrightDebug.RENDER, successMessage, path);
            }
        } catch (IOException e) {
            Hexwright.LOGGER.error("Failed to write bloom config to {}", path, e);
        }
    }

    private static void applyClamped(EmissiveBloomConfig loaded) {
        CONFIG.enabled = loaded.enabled;
        CONFIG.framebufferScale = clampFramebufferScale(loaded.framebufferScale);
        CONFIG.intensity = clampIntensity(loaded.intensity);
        CONFIG.coreStrength = clampCoreStrength(loaded.coreStrength);
        CONFIG.glowThreshold = clampGlowThreshold(loaded.glowThreshold);
        CONFIG.glowKnee = clampGlowKnee(loaded.glowKnee);
        CONFIG.glowSaturation = clampGlowSaturation(loaded.glowSaturation);
        CONFIG.blurRadius = clampBlurRadius(loaded.blurRadius);
        CONFIG.blockGlow = loaded.blockGlow;
        CONFIG.blockGlowDisableWhenShaderPackActive = loaded.blockGlowDisableWhenShaderPackActive;
        CONFIG.blockGlowDistance = clampBlockGlowDistance(loaded.blockGlowDistance);
        CONFIG.blockGlowStrength = clampBlockGlowStrength(loaded.blockGlowStrength);
        CONFIG.blockGlowLift = clampBlockGlowLift(loaded.blockGlowLift);
        CONFIG.blockGlowDepthSlope = clampBlockGlowDepthSlope(loaded.blockGlowDepthSlope);
        CONFIG.blockGlowDepthBias = clampBlockGlowDepthBias(loaded.blockGlowDepthBias);
        CONFIG.blockGlowMipmap = loaded.blockGlowMipmap;
        CONFIG.blockGlowDebug = loaded.blockGlowDebug;
        CONFIG.disableWhenShaderPackActive = loaded.disableWhenShaderPackActive;
        CONFIG.debugMode = clampDebugMode(loaded.debugMode);
    }


    static float clampFramebufferScale(float value) {
        return Mth.clamp(value, FRAMEBUFFER_SCALE_MIN, FRAMEBUFFER_SCALE_MAX);
    }

    static float clampGlowThreshold(float value) {
        return Mth.clamp(value, GLOW_THRESHOLD_MIN, GLOW_THRESHOLD_MAX);
    }

    static float clampGlowSaturation(float value) {
        return Mth.clamp(value, GLOW_SATURATION_MIN, GLOW_SATURATION_MAX);
    }

    static float clampGlowKnee(float value) {
        return Mth.clamp(value, GLOW_KNEE_MIN, GLOW_KNEE_MAX);
    }

    static float clampCoreStrength(float value) {
        return Mth.clamp(value, CORE_STRENGTH_MIN, CORE_STRENGTH_MAX);
    }

    static float clampIntensity(float value) {
        return Mth.clamp(value, INTENSITY_MIN, INTENSITY_MAX);
    }

    static float clampBlurRadius(float value) {
        return Mth.clamp(value, BLUR_RADIUS_MIN, BLUR_RADIUS_MAX);
    }

    static int clampBlockGlowDistance(int value) {
        return Mth.clamp(value, BLOCK_GLOW_DISTANCE_MIN, BLOCK_GLOW_DISTANCE_MAX);
    }

    static float clampBlockGlowLift(float value) {
        return Mth.clamp(value, BLOCK_GLOW_LIFT_MIN, BLOCK_GLOW_LIFT_MAX);
    }

    static float clampBlockGlowDepthBias(float value) {
        return Mth.clamp(value, BLOCK_GLOW_DEPTH_BIAS_MIN, BLOCK_GLOW_DEPTH_BIAS_MAX);
    }

    static float clampBlockGlowDepthSlope(float value) {
        return Mth.clamp(value, BLOCK_GLOW_DEPTH_SLOPE_MIN, BLOCK_GLOW_DEPTH_SLOPE_MAX);
    }

    static float clampBlockGlowStrength(float value) {
        return Mth.clamp(value, BLOCK_GLOW_STRENGTH_MIN, BLOCK_GLOW_STRENGTH_MAX);
    }

    static int clampDebugMode(int value) {
        return Mth.clamp(value, DEBUG_MODE_MIN, DEBUG_MODE_MAX);
    }
}
