package com.bluup.hexwright.client.bindstone;

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

public final class BindstoneDissolveShaders implements SimpleSynchronousResourceReloadListener {

    private static final ResourceLocation ID = Hexwright.id("bindstone_dissolve_shaders");
    private static final String SHADER_NAME = "hexwright_bindstone_dissolve";

    @Nullable
    private static ShaderInstance shader;

    private BindstoneDissolveShaders() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new BindstoneDissolveShaders());
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
            shader = new ShaderInstance(resourceManager, SHADER_NAME, DefaultVertexFormat.NEW_ENTITY);
            HexwrightDebug.log(HexwrightDebug.RENDER, "Loaded bindstone dissolve shader");
        } catch (IOException | RuntimeException e) {
            Hexwright.LOGGER.error("Failed to load the bindstone dissolve shader; claimed pillars will "
                + "disappear without coming apart", e);
        }
    }
}
