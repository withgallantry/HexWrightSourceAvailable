package com.bluup.hexwright.client.inversion;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@Environment(EnvType.CLIENT)
public final class InversionShaders implements SimpleSynchronousResourceReloadListener {
    private static final ResourceLocation ID = Hexwright.id("inversion_shaders");

    @Nullable
    private static ShaderInstance sky;
    @Nullable
    private static ShaderInstance cube;
    @Nullable
    private static ShaderInstance beam;

    private InversionShaders() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new InversionShaders());
    }

    public static boolean ready() {
        return sky != null && cube != null && beam != null;
    }

    public static ShaderInstance sky() {
        return sky;
    }

    public static ShaderInstance cube() {
        return cube;
    }

    public static ShaderInstance beam() {
        return beam;
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        close();
        try {
            sky = new ShaderInstance(resourceManager, "hexwright_inversion_sky", DefaultVertexFormat.POSITION);
            cube = new ShaderInstance(resourceManager, "hexwright_arbiter_cube", DefaultVertexFormat.POSITION);
            beam = new ShaderInstance(resourceManager, "hexwright_void_beam", DefaultVertexFormat.POSITION_TEX);
            HexwrightDebug.log(HexwrightDebug.RENDER, "Loaded Inversion shaders");
        } catch (IOException | RuntimeException e) {
            Hexwright.LOGGER.error("Failed to load the Inversion's shaders; its sky and the Arbiter's cube will not be drawn", e);
            close();
        }
    }

    private static void close() {
        if (sky != null) {
            sky.close();
            sky = null;
        }
        if (cube != null) {
            cube.close();
            cube = null;
        }
        if (beam != null) {
            beam.close();
            beam = null;
        }
    }
}
