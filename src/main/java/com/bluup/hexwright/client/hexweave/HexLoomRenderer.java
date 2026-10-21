package com.bluup.hexwright.client.hexweave;

import com.bluup.hexwright.server.hexweave.HexLoomBlock;
import com.bluup.hexwright.server.hexweave.HexLoomBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

@Environment(EnvType.CLIENT)
public final class HexLoomRenderer implements BlockEntityRenderer<HexLoomBlockEntity> {

    private static final float DEGREES_PER_TICK = 1.2f;
    private static final float SCALE = 0.6f;
    private static final double HOVER = HexLoomBlock.DOCK_TOP + 0.3;
    private static final double BOB = 0.04;
    private static final double RADIUS = 12 / 16.0 * 0.9 * SCALE / 2;
    static final AABB ORB_BOX = new AABB(0.5 - RADIUS, HOVER - RADIUS - BOB, 0.5 - RADIUS,
        0.5 + RADIUS, HOVER + RADIUS + BOB, 0.5 + RADIUS);

    public HexLoomRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HexLoomBlockEntity loom, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        ItemStack orb = loom.orb();
        Level level = loom.getLevel();
        if (orb.isEmpty() || level == null) {
            return;
        }
        float time = level.getGameTime() + partialTick;

        pose.pushPose();
        pose.translate(0.5, HOVER + Mth.sin(time * 0.05f) * BOB, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * DEGREES_PER_TICK));
        pose.scale(SCALE, SCALE, SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(orb, ItemDisplayContext.FIXED,
            LevelRenderer.getLightColor(level, loom.getBlockPos().above()), OverlayTexture.NO_OVERLAY, pose, buffers,
            level, (int) loom.getBlockPos().asLong());
        pose.popPose();
    }
}
