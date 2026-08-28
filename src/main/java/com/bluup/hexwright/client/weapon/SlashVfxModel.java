package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.weapon.SlashStyle;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class SlashVfxModel extends GeoModel<SlashVfx> {

    private static final ResourceLocation GEOMETRY = Hexwright.id("geo/fx/slash.geo.json");
    private static final ResourceLocation ANIMATIONS = Hexwright.id("animations/fx/slash.animation.json");

    private final ResourceLocation texture;

    SlashVfxModel(SlashStyle style) {
        this.texture = style.crescentTexture();
    }

    @Override
    public ResourceLocation getModelResource(SlashVfx animatable) {
        return GEOMETRY;
    }

    @Override
    public ResourceLocation getTextureResource(SlashVfx animatable) {
        return this.texture;
    }

    @Override
    public ResourceLocation getAnimationResource(SlashVfx animatable) {
        return ANIMATIONS;
    }

    @Override
    public RenderType getRenderType(SlashVfx animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentEmissive(texture);
    }
}
