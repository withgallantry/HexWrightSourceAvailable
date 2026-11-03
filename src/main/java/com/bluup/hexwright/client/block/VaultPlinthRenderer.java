package com.bluup.hexwright.client.block;

import com.bluup.hexwright.server.block.VaultPlinthBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class VaultPlinthRenderer implements BlockEntityRenderer<VaultPlinthBlockEntity> {

    private static final float DEGREES_PER_TICK = 1.2f;
    private static final float SCALE = 0.6f;
    private static final double HOVER_HEIGHT = 1.15;

    public VaultPlinthRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void register() {
        BlockEntityRenderers.register(
            com.bluup.hexwright.server.block.HexwrightBlocks.VAULT_PLINTH_BLOCK_ENTITY,
            VaultPlinthRenderer::new
        );
    }

    @Override
    public void render(VaultPlinthBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack stack = blockEntity.getDisplayed();
        if (stack.isEmpty() || blockEntity.getLevel() == null) {
            return;
        }

        float time = blockEntity.getLevel().getGameTime() + partialTick;
        float bob = Mth.sin(time * 0.05f) * 0.06f;

        poseStack.pushPose();
        poseStack.translate(0.5, HOVER_HEIGHT + bob, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * DEGREES_PER_TICK));
        poseStack.scale(SCALE, SCALE, SCALE);

        Minecraft.getInstance().getItemRenderer().renderStatic(
            stack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
            poseStack, bufferSource, blockEntity.getLevel(), (int) blockEntity.getBlockPos().asLong()
        );

        poseStack.popPose();
    }
}
