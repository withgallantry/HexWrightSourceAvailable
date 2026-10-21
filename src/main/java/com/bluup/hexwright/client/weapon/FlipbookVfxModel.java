package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public final class FlipbookVfxModel extends GeoModel<FlipbookVfx> {

    private final ResourceLocation geometry;
    private final ResourceLocation animations;
    private final ResourceLocation[] textures;

    FlipbookVfxModel(String rig, int frames) {
        this(rig, rig, frames);
    }

    FlipbookVfxModel(String rig, String sheets, int frames) {
        this.geometry = Hexwright.id("geo/fx/" + rig + ".geo.json");
        this.animations = Hexwright.id("animations/fx/" + rig + ".animation.json");
        this.textures = textures(sheets, frames);
    }

    static ResourceLocation[] textures(String rig, int frames) {
        ResourceLocation[] textures = new ResourceLocation[frames];
        for (int frame = 0; frame < frames; frame++) {
            textures[frame] = Hexwright.id("textures/fx/" + rig + "_" + frame + ".png");
        }
        return textures;
    }

    @Override
    public ResourceLocation getModelResource(FlipbookVfx animatable) {
        return this.geometry;
    }

    @Override
    public ResourceLocation getTextureResource(FlipbookVfx animatable) {
        return this.textures[Math.min(Math.max(animatable.frame, 0), this.textures.length - 1)];
    }

    @Override
    public ResourceLocation getAnimationResource(FlipbookVfx animatable) {
        return this.animations;
    }

    @Override
    public RenderType getRenderType(FlipbookVfx animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentEmissive(texture);
    }
}
