package com.bluup.hexwright.client.armour;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

@Environment(EnvType.CLIENT)
public final class PassagePortalRenderer extends GeoObjectRenderer<PassagePortalVfx> {

    PassagePortalRenderer(PassagePortalModel model) {
        super(model);
    }

    @Override
    public void preRender(PoseStack poseStack, PassagePortalVfx animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
            packedLight, packedOverlay, red, green, blue, alpha);
        poseStack.translate(-0.5f, -0.51f, -0.5f);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, PassagePortalVfx animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if (bone.getParent() == null) {
            if (bone.getName().startsWith("portal")) {
                renderType = RenderType.entityTranslucentEmissive(PassagePortalModel.SWIRL);
                packedLight = LightTexture.FULL_BRIGHT;
            } else {
                if (!animatable.showStandIn) {
                    return;
                }
                renderType = RenderType.entityTranslucent(animatable.skin);
                packedLight = animatable.light;
            }
            buffer = bufferSource.getBuffer(renderType);
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
            isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
