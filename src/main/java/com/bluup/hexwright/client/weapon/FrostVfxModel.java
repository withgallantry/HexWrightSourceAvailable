package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public final class FrostVfxModel extends GeoModel<FrostVfx> {

    static final String CUT = "frost_cut";
    static final String SNOW = "frost_snow";

    private final ResourceLocation geometry;
    private final ResourceLocation animations;
    private final ResourceLocation texture;

    FrostVfxModel(String rig) {
        this.geometry = Hexwright.id("geo/fx/" + rig + ".geo.json");
        this.animations = Hexwright.id("animations/fx/" + rig + ".animation.json");
        this.texture = texture(rig);
    }

    static ResourceLocation texture(String rig) {
        return Hexwright.id("textures/fx/" + rig + ".png");
    }

    @Override
    public ResourceLocation getModelResource(FrostVfx animatable) {
        return this.geometry;
    }

    @Override
    public ResourceLocation getTextureResource(FrostVfx animatable) {
        return this.texture;
    }

    @Override
    public ResourceLocation getAnimationResource(FrostVfx animatable) {
        return this.animations;
    }

    @Override
    public RenderType getRenderType(FrostVfx animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentEmissive(texture);
    }
}
