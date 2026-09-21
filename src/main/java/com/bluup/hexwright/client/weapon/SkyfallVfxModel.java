package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public final class SkyfallVfxModel extends GeoModel<SkyfallVfx> {

    static final int SPARKLE_FRAMES = 9;

    private static final ResourceLocation GEOMETRY = Hexwright.id("geo/fx/piercing_skyfall.geo.json");
    private static final ResourceLocation ANIMATIONS = Hexwright.id("animations/fx/piercing_skyfall.animation.json");

    static final ResourceLocation[] TEXTURES = new ResourceLocation[SPARKLE_FRAMES];

    static {
        for (int frame = 0; frame < SPARKLE_FRAMES; frame++) {
            TEXTURES[frame] = Hexwright.id("textures/fx/piercing_skyfall_" + frame + ".png");
        }
    }

    @Override
    public ResourceLocation getModelResource(SkyfallVfx animatable) {
        return GEOMETRY;
    }

    @Override
    public ResourceLocation getTextureResource(SkyfallVfx animatable) {
        return TEXTURES[Math.floorMod(animatable.sparkleFrame, SPARKLE_FRAMES)];
    }

    @Override
    public ResourceLocation getAnimationResource(SkyfallVfx animatable) {
        return ANIMATIONS;
    }

    @Override
    public RenderType getRenderType(SkyfallVfx animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentEmissive(texture);
    }
}
