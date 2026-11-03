package com.bluup.hexwright.client.mob;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.mob.ConstructEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class ConstructRenderer<T extends ConstructEntity> extends GeoEntityRenderer<T> {

    public ConstructRenderer(EntityRendererProvider.Context context, String name, float shadowRadius) {
        this(context, new DefaultedEntityGeoModel<>(Hexwright.id(name)), shadowRadius);
    }

    public ConstructRenderer(EntityRendererProvider.Context context, GeoModel<T> model, float shadowRadius) {
        super(context, model);
        this.shadowRadius = shadowRadius;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected float getDeathMaxRotation(T animatable) {
        return 0.0f;
    }
}
