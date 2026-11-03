package com.bluup.hexwright.client.weapon;

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

public final class BloodSmokeShaders implements SimpleSynchronousResourceReloadListener {
    private static final ResourceLocation ID = Hexwright.id("blood_smoke_shaders");
    private static final String SHADER_NAME = "hexwright_blood_smoke";

    @Nullable
    private static ShaderInstance shader;
    private static boolean failed;

    private BloodSmokeShaders() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new BloodSmokeShaders());
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
            HexwrightDebug.log(HexwrightDebug.RENDER, "Loaded blood smoke shader");
        } catch (IOException | RuntimeException e) {
            Hexwright.LOGGER.error("Failed to load the Blood Tree's smoke shader; its trees will have no smoke", e);
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
