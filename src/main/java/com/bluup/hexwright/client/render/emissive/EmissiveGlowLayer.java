package com.bluup.hexwright.client.render.emissive;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class EmissiveGlowLayer extends RenderType {
    private static final String SHADER_NAME = "hexwright_item_glow";

    private static final TextureStateShard BLOCK_ATLAS_MIPPED =
        new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, true);

    private static final LayeringStateShard DECAL_OFFSET_LAYERING = new LayeringStateShard(
        "hexwright_decal_offset",
        () -> {
            RenderSystem.polygonOffset(0.0f, -10.0f);
            RenderSystem.enablePolygonOffset();
        },
        () -> {
            RenderSystem.polygonOffset(0.0f, 0.0f);
            RenderSystem.disablePolygonOffset();
        });

    @Nullable
    private static ShaderInstance shader;

    private static final RenderType LAYER = create(
        "hexwright_item_glow",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS,
        256,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(new ShaderStateShard(EmissiveGlowLayer::shader))
            .setTextureState(BLOCK_ATLAS_MIPPED)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setWriteMaskState(COLOR_WRITE)
            .setLayeringState(DECAL_OFFSET_LAYERING)
            .createCompositeState(false));

    private static final RenderType MASKED_LAYER = create(
        "hexwright_item_glowmask",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS,
        256,
        true,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
            .setTextureState(BLOCK_ATLAS_MIPPED)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setWriteMaskState(COLOR_WRITE)
            .setOverlayState(OVERLAY)
            .setLayeringState(DECAL_OFFSET_LAYERING)
            .createCompositeState(true));

    private EmissiveGlowLayer(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                              boolean affectsCrumbling, boolean sortOnUpload,
                              Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        throw new AssertionError("render type holder, never instantiated");
    }

    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context ->
            context.register(new ResourceLocation(SHADER_NAME), DefaultVertexFormat.NEW_ENTITY,
                loaded -> shader = loaded));
    }

    @Nullable
    public static RenderType layer() {
        return shader == null ? null : LAYER;
    }

    public static RenderType maskedLayer() {
        return MASKED_LAYER;
    }

    @Nullable
    private static ShaderInstance shader() {
        return shader;
    }

    public static void updateKnee(float knee) {
        if (shader != null && shader.getUniform("GlowKnee") != null) {
            shader.getUniform("GlowKnee").set(knee);
        }
    }
}
