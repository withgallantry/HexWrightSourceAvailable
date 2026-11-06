package com.bluup.hexwright.client.hexahedron;

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

public final class HexahedronShaders implements SimpleSynchronousResourceReloadListener {
    private static final ResourceLocation ID = Hexwright.id("hexahedron_shaders");

    @Nullable
    private static ShaderInstance cube;
    @Nullable
    private static ShaderInstance storm;
    @Nullable
    private static ShaderInstance clouds;
    @Nullable
    private static ShaderInstance overcast;
    @Nullable
    private static ShaderInstance mote;
    @Nullable
    private static ShaderInstance crystal;

    private HexahedronShaders() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new HexahedronShaders());
    }

    static boolean ready() {
        return cube != null && storm != null && clouds != null && overcast != null && mote != null && crystal != null;
    }

    static ShaderInstance cube() {
        return cube;
    }

    static ShaderInstance storm() {
        return storm;
    }

    static ShaderInstance clouds() {
        return clouds;
    }

    static ShaderInstance overcast() {
        return overcast;
    }

    static ShaderInstance mote() {
        return mote;
    }

    static ShaderInstance crystal() {
        return crystal;
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        close();
        try {
            cube = new ShaderInstance(resourceManager, "hexwright_hexahedron_cube", DefaultVertexFormat.POSITION);
            storm = new ShaderInstance(resourceManager, "hexwright_hexahedron_storm", DefaultVertexFormat.POSITION);
            clouds = new ShaderInstance(resourceManager, "hexwright_hexahedron_clouds", DefaultVertexFormat.POSITION);
            overcast = new ShaderInstance(resourceManager, "hexwright_hexahedron_overcast", DefaultVertexFormat.POSITION);
            mote = new ShaderInstance(resourceManager, "hexwright_hexahedron_mote", DefaultVertexFormat.POSITION_TEX_COLOR);
            crystal = new ShaderInstance(resourceManager, "hexwright_rift_crystal", DefaultVertexFormat.POSITION);
            HexwrightDebug.log(HexwrightDebug.RENDER, "Loaded Hexahedron shaders");
        } catch (IOException | RuntimeException e) {
            Hexwright.LOGGER.error("Failed to load the Hexahedron's shaders; it will not be drawn", e);
            close();
        }
    }

    private static void close() {
        if (cube != null) {
            cube.close();
            cube = null;
        }
        if (storm != null) {
            storm.close();
            storm = null;
        }
        if (clouds != null) {
            clouds.close();
            clouds = null;
        }
        if (overcast != null) {
            overcast.close();
            overcast = null;
        }
        if (mote != null) {
            mote.close();
            mote = null;
        }
        if (crystal != null) {
            crystal.close();
            crystal = null;
        }
    }
}
