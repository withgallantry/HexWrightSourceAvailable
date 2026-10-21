package com.bluup.hexwright.client.inspiration;

import com.bluup.hexwright.server.inspiration.InspirationOrbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class InspirationOrbRenderer extends EntityRenderer<InspirationOrbEntity> {

    private static final ResourceLocation SHEET = new ResourceLocation("textures/entity/experience_orb.png");
    private static final RenderType RENDER_TYPE = RenderType.itemEntityTranslucentCull(SHEET);

    private static final float TILE = 16.0f / 64.0f;

    private static final float SCALE = 0.3f;

    private static final float PULSE_TICKS = 24.0f;

    private static final int DIM = 0x0C6E9E;
    private static final int BRIGHT = 0x4CD6FF;

    private static final int ALPHA = 160;

    public InspirationOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.15f;
    }

    @Override
    public void render(InspirationOrbEntity orb, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.translate(0.0f, 0.1f, 0.0f);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
        poseStack.scale(SCALE, SCALE, SCALE);

        float pulse = (Mth.sin((orb.age() + partialTick) * Mth.TWO_PI / PULSE_TICKS) + 1.0f) * 0.5f;
        int red = channel(pulse, 16);
        int green = channel(pulse, 8);
        int blue = channel(pulse, 0);

        VertexConsumer buffer = buffers.getBuffer(RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();
        vertex(buffer, pose, -0.5f, -0.25f, 0.0f, TILE, red, green, blue, light);
        vertex(buffer, pose, 0.5f, -0.25f, TILE, TILE, red, green, blue, light);
        vertex(buffer, pose, 0.5f, 0.75f, TILE, 0.0f, red, green, blue, light);
        vertex(buffer, pose, -0.5f, 0.75f, 0.0f, 0.0f, red, green, blue, light);

        poseStack.popPose();
        super.render(orb, yaw, partialTick, poseStack, buffers, light);
    }

    private static int channel(float pulse, int shift) {
        return (int) Mth.lerp(pulse, (DIM >> shift) & 0xFF, (BRIGHT >> shift) & 0xFF);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float u, float v,
                               int red, int green, int blue, int light) {
        buffer.vertex(pose.pose(), x, y, 0.0f)
            .color(red, green, blue, ALPHA)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(pose.normal(), 0.0f, 1.0f, 0.0f)
            .endVertex();
    }

    @Override
    protected int getBlockLightLevel(InspirationOrbEntity orb, BlockPos pos) {
        return 15;
    }

    @Override
    public ResourceLocation getTextureLocation(InspirationOrbEntity orb) {
        return SHEET;
    }
}
