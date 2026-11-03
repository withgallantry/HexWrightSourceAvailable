package com.bluup.hexwright.client.block;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.PlacedBottleBlock;
import com.bluup.hexwright.server.fluid.LiquefactriumBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class LiquefactriumRenderer implements BlockEntityRenderer<LiquefactriumBlockEntity> {

    public LiquefactriumRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void register() {
        BlockEntityRenderers.register(HexwrightBlocks.LIQUEFACTRIUM_BLOCK_ENTITY, LiquefactriumRenderer::new);
    }

    @Override
    public void render(LiquefactriumBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack bottle = blockEntity.getBottle();
        if (bottle.isEmpty() || blockEntity.getLevel() == null) {
            return;
        }

        float scale = PlacedBottleBlock.MODEL_SCALE;
        int light = LevelRenderer.getLightColor(blockEntity.getLevel(), blockEntity.getBlockPos().above());

        poseStack.pushPose();
        poseStack.translate(0.5, 1.0 + 0.5 * scale, 0.5);
        poseStack.scale(scale, scale, scale);

        Minecraft.getInstance().getItemRenderer().renderStatic(
            bottle, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
            poseStack, bufferSource, blockEntity.getLevel(), (int) blockEntity.getBlockPos().asLong()
        );

        poseStack.popPose();
    }
}
