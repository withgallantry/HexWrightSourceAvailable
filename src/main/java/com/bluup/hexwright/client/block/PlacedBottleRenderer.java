package com.bluup.hexwright.client.block;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.PlacedBottleBlock;
import com.bluup.hexwright.server.block.PlacedBottleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class PlacedBottleRenderer implements BlockEntityRenderer<PlacedBottleBlockEntity> {

    public PlacedBottleRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void register() {
        BlockEntityRenderers.register(HexwrightBlocks.PLACED_BOTTLE_BLOCK_ENTITY, PlacedBottleRenderer::new);
    }

    @Override
    public void render(PlacedBottleBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack bottle = blockEntity.getBottle();
        if (bottle.isEmpty() || blockEntity.getLevel() == null) {
            return;
        }

        float scale = PlacedBottleBlock.MODEL_SCALE;
        int rotation = blockEntity.getBlockState().getValue(PlacedBottleBlock.ROTATION);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5 * scale, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-rotation * 22.5f));
        poseStack.scale(scale, scale, scale);

        Minecraft.getInstance().getItemRenderer().renderStatic(
            bottle, ItemDisplayContext.NONE, packedLight, OverlayTexture.NO_OVERLAY,
            poseStack, bufferSource, blockEntity.getLevel(), (int) blockEntity.getBlockPos().asLong()
        );

        poseStack.popPose();
    }
}
