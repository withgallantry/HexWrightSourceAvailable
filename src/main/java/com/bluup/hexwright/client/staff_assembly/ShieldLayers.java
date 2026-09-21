package com.bluup.hexwright.client.staff_assembly;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class ShieldLayers extends RenderType {

    private static final ResourceLocation SHIELD_WHITE =
        new ResourceLocation("hexwright", "textures/effects/shield_white.png");

    private static final RenderType SHIELD = create(
        "hexwright_shield",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS,
        2048,
        true,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(new TextureStateShard(SHIELD_WHITE, false, false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setLightmapState(LIGHTMAP)
            .setOverlayState(OVERLAY)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false));

    private static final RenderType BANDS = create(
        "hexwright_shield_bands",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS,
        512,
        true,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(new TextureStateShard(SHIELD_WHITE, false, false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setLightmapState(LIGHTMAP)
            .setOverlayState(OVERLAY)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false));

    public static RenderType shield() {
        return SHIELD;
    }

    public static RenderType bands() {
        return BANDS;
    }

    private ShieldLayers(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                         boolean affectsCrumbling, boolean sortOnUpload,
                         Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        throw new AssertionError("render type holder, never instantiated");
    }
}
