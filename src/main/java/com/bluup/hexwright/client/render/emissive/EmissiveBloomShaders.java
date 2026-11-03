package com.bluup.hexwright.client.render.emissive;

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
import java.util.ArrayList;
import java.util.List;

final class EmissiveBloomShaders implements SimpleSynchronousResourceReloadListener {
    private static final ResourceLocation ID = Hexwright.id("emissive_bloom_shaders");
    private static final String DOWNSAMPLE_NAME = "hexwright_bloom_downsample";
    private static final String BLUR_NAME = "hexwright_bloom_blur";
    private static final String COMPOSITE_NAME = "hexwright_bloom_composite";
    private static final String VIEW_NAME = "hexwright_bloom_view";

    @Nullable
    private static ShaderInstance downsample;
    @Nullable
    private static ShaderInstance blur;
    @Nullable
    private static ShaderInstance composite;
    @Nullable
    private static ShaderInstance view;
    private static boolean failed;

    private EmissiveBloomShaders() {
    }

    static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new EmissiveBloomShaders());
    }

    static boolean shadersReady() {
        return downsample != null && blur != null && composite != null && view != null;
    }

    @Nullable
    static ShaderInstance downsample() {
        return downsample;
    }

    @Nullable
    static ShaderInstance blur() {
        return blur;
    }

    @Nullable
    static ShaderInstance composite() {
        return composite;
    }

    @Nullable
    static ShaderInstance view() {
        return view;
    }

    static void retry() {
        failed = false;
        load();
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

        List<ShaderInstance> built = new ArrayList<>(3);
        try {
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            ShaderInstance newDownsample = build(resources, DOWNSAMPLE_NAME, built);
            ShaderInstance newBlur = build(resources, BLUR_NAME, built);
            ShaderInstance newComposite = build(resources, COMPOSITE_NAME, built);
            ShaderInstance newView = build(resources, VIEW_NAME, built);

            downsample = newDownsample;
            blur = newBlur;
            composite = newComposite;
            view = newView;
            HexwrightDebug.log(HexwrightDebug.RENDER, "Loaded emissive bloom shaders");
        } catch (IOException | RuntimeException e) {
            Hexwright.LOGGER.error("Failed to load emissive bloom shaders; bloom stays off until a retry", e);
            built.forEach(ShaderInstance::close);
            downsample = null;
            blur = null;
            composite = null;
            view = null;
            failed = true;
        }
    }

    private static ShaderInstance build(ResourceManager resources, String name, List<ShaderInstance> built)
        throws IOException {
        ShaderInstance shader = new ShaderInstance(resources, name, DefaultVertexFormat.POSITION_TEX);
        built.add(shader);
        return shader;
    }

    static void close() {
        if (downsample != null) {
            downsample.close();
            downsample = null;
        }
        if (blur != null) {
            blur.close();
            blur = null;
        }
        if (composite != null) {
            composite.close();
            composite = null;
        }
        if (view != null) {
            view.close();
            view = null;
        }
    }
}
