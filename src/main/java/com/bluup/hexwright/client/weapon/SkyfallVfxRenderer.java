package com.bluup.hexwright.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

@Environment(EnvType.CLIENT)
public final class SkyfallVfxRenderer extends GeoObjectRenderer<SkyfallVfx> {

    SkyfallVfxRenderer(SkyfallVfxModel model) {
        super(model);
    }

    @Override
    public void preRender(PoseStack poseStack, SkyfallVfx animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
            packedLight, packedOverlay, red, green, blue, alpha);
        poseStack.translate(-0.5f, -0.51f, -0.5f);
    }
}
