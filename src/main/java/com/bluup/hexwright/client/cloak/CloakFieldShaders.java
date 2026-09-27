package com.bluup.hexwright.client.cloak;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public final class CloakFieldShaders implements SimpleSynchronousResourceReloadListener {
    private static final ResourceLocation ID = Hexwright.id("cloak_field_shaders");
    private static final String SHADER_NAME = "hexwright_cloak_field";

    @Nullable
    private static ShaderInstance shader;

    private CloakFieldShaders() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new CloakFieldShaders());
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
        if (shader != null) {
            shader.close();
            shader = null;
        }
        try {
            shader = new ShaderInstance(resourceManager, SHADER_NAME, DefaultVertexFormat.POSITION_TEX);
            HexwrightDebug.log(HexwrightDebug.RENDER, "Loaded cloak field shader");
        } catch (IOException | RuntimeException e) {
            Hexwright.LOGGER.error("Failed to load cloak field shader; cloak fields will not render", e);
        }
    }
}
