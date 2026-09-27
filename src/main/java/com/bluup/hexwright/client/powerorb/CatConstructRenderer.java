package com.bluup.hexwright.client.powerorb;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.powerorb.CatConstructEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CatConstructRenderer extends GeoEntityRenderer<CatConstructEntity> {

    public CatConstructRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(Hexwright.id("cat_construct")));
        this.shadowRadius = 0.5f;
    }

    @Override
    public void render(CatConstructEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
        PowerOrbBloom.capture(this, entity, poseStack, partialTick, PowerOrbBloom.Spirit.CAT);
    }
}
