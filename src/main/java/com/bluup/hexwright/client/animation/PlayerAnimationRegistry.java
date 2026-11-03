package com.bluup.hexwright.client.animation;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlayerAnimationRegistry implements SimpleSynchronousResourceReloadListener {
    private static final ResourceLocation ID = Hexwright.id("player_animations");
    private static final String DIRECTORY = "player_animations";

    private static Map<String, KeyframeAnimation> animations = Map.of();

    private PlayerAnimationRegistry() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new PlayerAnimationRegistry());
    }

    public static KeyframeAnimation get(String name) {
        return animations.get(name);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        Map<String, KeyframeAnimation> loaded = new HashMap<>();

        for (Map.Entry<ResourceLocation, Resource> entry
                : resourceManager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = entry.getKey();
            try (var stream = entry.getValue().open()) {
                List<KeyframeAnimation> parsed = AnimationSerializing.deserializeAnimation(stream);
                if (parsed.isEmpty()) {
                    continue;
                }
                String path = file.getPath();
                String id = path.substring(DIRECTORY.length() + 1, path.length() - ".json".length());
                loaded.put(id, parsed.get(0));
            } catch (Exception e) {
                Hexwright.LOGGER.error("Failed to load player animation {}", file, e);
            }
        }

        animations = loaded;
        HexwrightDebug.log(HexwrightDebug.CONTENT, "Loaded {} player animations.", loaded.size());
    }
}
