package com.bluup.hexwright.client.powerorb;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.powerorb.SpiritWardEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class SpiritWardRenderer extends EntityRenderer<SpiritWardEntity> {

    private static final ResourceLocation TEXTURE = Hexwright.id("textures/entity/spirit_golem.png");
    private static final float RING_U = 87.0f / 256.0f;
    private static final float RING_LINE = 38.1f / 43.5f;
    private static final float INNER_SCALE = 0.55f;
    private static final float TURN_DEGREES_PER_TICK = 0.6f;

    public SpiritWardRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(SpiritWardEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        float age = entity.tickCount + partialTick;
        float alpha = Mth.clamp(Math.min(age / SpiritWardEntity.FADE_IN,
            (SpiritWardEntity.DURATION - age) / SpiritWardEntity.FADE_OUT), 0.0f, 1.0f);
        if (alpha <= 0.0f) {
            return;
        }
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        float half = (float) SpiritWardEntity.RADIUS / RING_LINE;

        poseStack.pushPose();
        poseStack.translate(0.0, 0.06, 0.0);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * TURN_DEGREES_PER_TICK));
        ring(poseStack, buffer, half, alpha);
        poseStack.popPose();
        poseStack.translate(0.0, 0.01, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-age * TURN_DEGREES_PER_TICK * 1.5f));
        ring(poseStack, buffer, half * INNER_SCALE, alpha * 0.5f);
        poseStack.popPose();
    }

    private static void ring(PoseStack poseStack, VertexConsumer buffer, float half, float alpha) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        vertex(buffer, pose, normal, -half, -half, 0.0f, 0.0f, alpha);
        vertex(buffer, pose, normal, -half, half, 0.0f, RING_U, alpha);
        vertex(buffer, pose, normal, half, half, RING_U, RING_U, alpha);
        vertex(buffer, pose, normal, half, -half, RING_U, 0.0f, alpha);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Matrix3f normal, float x, float z,
                               float u, float v, float alpha) {
        buffer.vertex(pose, x, 0.0f, z)
            .color(1.0f, 1.0f, 1.0f, alpha)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT)
            .normal(normal, 0.0f, 1.0f, 0.0f)
            .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritWardEntity entity) {
        return TEXTURE;
    }
}
