package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.weapon.RisenThrallEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class RisenThrallRenderer extends GeoEntityRenderer<RisenThrallEntity> {

    private static final float SCALE = 0.7F;

    private static final ResourceLocation TEXTURE = Hexwright.id("textures/entity/risen_thrall.png");

    public RisenThrallRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<RisenThrallEntity>(Hexwright.id("tower_skeleton_minion")) {
            @Override
            public ResourceLocation getTextureResource(RisenThrallEntity thrall) {
                return TEXTURE;
            }
        });
        this.withScale(SCALE);
        this.shadowRadius = 0.4f;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
