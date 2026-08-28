package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.Hexwright;
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

public final class VoidTearShaders implements SimpleSynchronousResourceReloadListener {
    private static final ResourceLocation ID = Hexwright.id("void_tear_shaders");
    private static final String SHADER_NAME = "hexwright_void_tear";

    @Nullable
    private static ShaderInstance shader;
    private static boolean failed;

    private VoidTearShaders() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new VoidTearShaders());
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
            Hexwright.LOGGER.info("Loaded void tear shader");
        } catch (IOException | RuntimeException e) {
            Hexwright.LOGGER.error("Failed to load void tear shader; rips will not render", e);
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
