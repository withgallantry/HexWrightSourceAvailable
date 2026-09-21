package com.bluup.hexwright.client.powerorb;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.powerorb.SpiritGolemEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SpiritGolemRenderer extends GeoEntityRenderer<SpiritGolemEntity> {

    private static final float SCALE = 0.8f;

    public SpiritGolemRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(Hexwright.id("spirit_golem")));
        withScale(SCALE);
        this.shadowRadius = 0.7f;
    }

    @Override
    public void render(SpiritGolemEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
    }

    @Override
    public RenderType getRenderType(SpiritGolemEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    protected float getDeathMaxRotation(SpiritGolemEntity animatable) {
        return 0.0f;
    }
}
