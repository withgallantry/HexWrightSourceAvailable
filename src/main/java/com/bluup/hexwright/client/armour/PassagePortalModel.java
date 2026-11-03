package com.bluup.hexwright.client.armour;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public final class PassagePortalModel extends GeoModel<PassagePortalVfx> {

    static final ResourceLocation SWIRL = Hexwright.id("textures/fx/passage_portal.png");
    private static final ResourceLocation GEOMETRY = Hexwright.id("geo/fx/passage_portal.geo.json");
    private static final ResourceLocation ANIMATIONS = Hexwright.id("animations/fx/passage_portal.animation.json");

    @Override
    public ResourceLocation getModelResource(PassagePortalVfx animatable) {
        return GEOMETRY;
    }

    @Override
    public ResourceLocation getTextureResource(PassagePortalVfx animatable) {
        return SWIRL;
    }

    @Override
    public ResourceLocation getAnimationResource(PassagePortalVfx animatable) {
        return ANIMATIONS;
    }

    @Override
    public RenderType getRenderType(PassagePortalVfx animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentEmissive(texture);
    }
}
