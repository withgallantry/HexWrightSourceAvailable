package com.bluup.hexwright.client.powerorb;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.powerorb.SanctuaryHandsEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SanctuaryHandsRenderer extends GeoEntityRenderer<SanctuaryHandsEntity> {

    public SanctuaryHandsRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(Hexwright.id("sanctuary_hands")));
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(SanctuaryHandsEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
    }

    @Override
    public RenderType getRenderType(SanctuaryHandsEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    protected void applyRotations(SanctuaryHandsEntity animatable, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTick) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f
            - Mth.rotLerp(partialTick, animatable.yRotO, animatable.getYRot())));
    }
}
