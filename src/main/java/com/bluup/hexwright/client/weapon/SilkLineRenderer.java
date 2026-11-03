package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.server.weapon.SilkLineEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class SilkLineRenderer extends EntityRenderer<SilkLineEntity> {

    private static final ItemStack KNOT = new ItemStack(Items.COBWEB);
    private static final float KNOT_SCALE = 0.5F;

    private static final ResourceLocation SILK = new ResourceLocation("textures/misc/white.png");
    private static final double STRAND_HALF_WIDTH = 0.018D;

    private static final int SILK_RED = 236;
    private static final int SILK_GREEN = 234;
    private static final int SILK_BLUE = 244;
    private static final int SILK_ALPHA = 230;

    private final ItemRenderer itemRenderer;

    public SilkLineRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(SilkLineEntity silk, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.scale(KNOT_SCALE, KNOT_SCALE, KNOT_SCALE);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        this.itemRenderer.renderStatic(KNOT, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, poseStack,
            buffers, silk.level(), silk.getId());
        poseStack.popPose();

        if (silk.getOwner() instanceof Player archer) {
            Vec3 strand = handPosition(archer, partialTick).subtract(silk.getPosition(partialTick));
            if (strand.lengthSqr() > 1.0E-4D) {
                drawStrand(buffers.getBuffer(RenderType.entityTranslucent(SILK)), poseStack.last(), strand, light);
            }
        }
        super.render(silk, yaw, partialTick, poseStack, buffers, light);
    }

    private static void drawStrand(VertexConsumer buffer, PoseStack.Pose pose, Vec3 strand, int light) {
        Vec3 along = strand.normalize();
        Vec3 side = along.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-4D) {
            side = along.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize().scale(STRAND_HALF_WIDTH);
        Vec3 across = along.cross(side).normalize().scale(STRAND_HALF_WIDTH);
        ribbon(buffer, pose, strand, side, light);
        ribbon(buffer, pose, strand, across, light);
    }

    private static void ribbon(VertexConsumer buffer, PoseStack.Pose pose, Vec3 strand, Vec3 offset, int light) {
        Vector3f normal = strand.cross(offset).normalize().toVector3f();
        corner(buffer, pose, offset.scale(-1.0D), 0.0F, 0.0F, normal, light);
        corner(buffer, pose, offset, 1.0F, 0.0F, normal, light);
        corner(buffer, pose, strand.add(offset), 1.0F, 1.0F, normal, light);
        corner(buffer, pose, strand.subtract(offset), 0.0F, 1.0F, normal, light);
    }

    private static void corner(VertexConsumer buffer, PoseStack.Pose pose, Vec3 at, float u, float v,
                               Vector3f normal, int light) {
        buffer.vertex(pose.pose(), (float) at.x, (float) at.y, (float) at.z)
            .color(SILK_RED, SILK_GREEN, SILK_BLUE, SILK_ALPHA)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(pose.normal(), normal.x, normal.y, normal.z)
            .endVertex();
    }

    private Vec3 handPosition(Player archer, float partialTick) {
        int side = archer.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (this.entityRenderDispatcher.options.getCameraType().isFirstPerson() && archer == Minecraft.getInstance().player) {
            double fovScale = 960.0D / this.entityRenderDispatcher.options.fov().get();
            Vec3 offset = this.entityRenderDispatcher.camera.getNearPlane()
                .getPointOnPlane(side * 0.525F, -0.1F)
                .scale(fovScale);
            return archer.getEyePosition(partialTick).add(offset);
        }
        float bodyYaw = Mth.lerp(partialTick, archer.yBodyRotO, archer.yBodyRot) * Mth.DEG_TO_RAD;
        double sin = Mth.sin(bodyYaw);
        double cos = Mth.cos(bodyYaw);
        double across = side * 0.35D;
        return new Vec3(
            Mth.lerp(partialTick, archer.xo, archer.getX()) - cos * across - sin * 0.8D,
            Mth.lerp(partialTick, archer.yo, archer.getY()) + archer.getEyeHeight() - 0.45D
                + (archer.isCrouching() ? -0.1875D : 0.0D),
            Mth.lerp(partialTick, archer.zo, archer.getZ()) - sin * across + cos * 0.8D);
    }

    @Override
    public ResourceLocation getTextureLocation(SilkLineEntity silk) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
