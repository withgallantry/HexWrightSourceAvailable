package com.bluup.hexwright.client.mob;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.mob.FracturedConstructEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class FracturedConstructModel extends GeoModel<FracturedConstructEntity> {

    private static final ResourceLocation TEXTURE = Hexwright.id("textures/entity/servitor_construct.png");
    private static final ResourceLocation[] MODELS = new ResourceLocation[FracturedConstructEntity.PIECES];
    private static final ResourceLocation[] ANIMATIONS = new ResourceLocation[FracturedConstructEntity.PIECES];

    static {
        for (int piece = 1; piece <= FracturedConstructEntity.PIECES; piece++) {
            MODELS[piece - 1] = Hexwright.id("geo/entity/fractured_construct_" + piece + ".geo.json");
            ANIMATIONS[piece - 1] = Hexwright.id("animations/entity/fractured_construct_" + piece + ".animation.json");
        }
    }

    @Override
    public ResourceLocation getModelResource(FracturedConstructEntity animatable) {
        return MODELS[animatable.piece() - 1];
    }

    @Override
    public ResourceLocation getTextureResource(FracturedConstructEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(FracturedConstructEntity animatable) {
        return ANIMATIONS[animatable.piece() - 1];
    }
}
