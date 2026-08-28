package com.bluup.hexwright.client.staff_assembly;

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

public final class ShieldContactShaders implements SimpleSynchronousResourceReloadListener {
    private static final ResourceLocation ID = Hexwright.id("shield_contact_shaders");
    private static final String SHADER_NAME = "hexwright_shield_contact";

    @Nullable
    private static ShaderInstance shader;
    private static boolean failed;

    private ShieldContactShaders() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new ShieldContactShaders());
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
                SHADER_NAME, DefaultVertexFormat.POSITION_COLOR_TEX);
            Hexwright.LOGGER.info("Loaded shield contact shader");
        } catch (IOException | RuntimeException e) {
            Hexwright.LOGGER.error("Failed to load shield contact shader; "
                + "the area cast shield will draw without its contact glow", e);
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
