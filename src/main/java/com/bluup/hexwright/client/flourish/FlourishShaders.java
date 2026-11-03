package com.bluup.hexwright.client.flourish;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public final class FlourishShaders implements SimpleSynchronousResourceReloadListener {
    private static final ResourceLocation ID = Hexwright.id("flourish_shaders");
    private static final String SHADER_NAME = "hexwright_flourish";

    @Nullable
    private static ShaderInstance shader;
    private static boolean failed;

    private FlourishShaders() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new FlourishShaders());
    }

    static @Nullable ShaderInstance shader() {
        return shader;
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        close();
        failed = false;
        load();
    }

    private static void load() {
        if (failed) {
            return;
        }
        try {
            shader = new ShaderInstance(Minecraft.getInstance().getResourceManager(),
                SHADER_NAME, DefaultVertexFormat.POSITION_TEX);
            HexwrightDebug.log(HexwrightDebug.RENDER, "Loaded flourish shader");
        } catch (IOException | RuntimeException e) {
            Hexwright.LOGGER.error("Failed to load flourish shader; flourishes fall back to plain particles", e);
            shader = null;
            failed = true;
        }
    }

    private static void close() {
        if (shader != null) {
            shader.close();
            shader = null;
        }
    }
}
