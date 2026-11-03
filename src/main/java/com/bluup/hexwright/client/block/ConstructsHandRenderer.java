package com.bluup.hexwright.client.block;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.render.PatternColors;
import at.petrak.hexcasting.client.render.WorldlyPatternRenderHelpers;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.constructshand.ConstructsHandBlock;
import com.bluup.hexwright.server.constructshand.ConstructsHandBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ConstructsHandRenderer implements BlockEntityRenderer<ConstructsHandBlockEntity> {

    private static final float PX = 1.0f / 16.0f;

    private static final float WRIST_Y = 10.0f;
    private static final float WRIST_Z = 8.0f;
    private static final float HAND_TILT = 45.0f;

    private static final float PALM_Z = 6.75f;
    private static final float PAD_EAST = 11.0f;
    private static final float PAD_TOP = 16.0f;
    private static final float PAD_SIZE = 6.0f;

    private static final float ITEM_Y = 19.7f;
    private static final float ITEM_Z = 7.2f;
    private static final float ITEM_SCALE = 0.45f;
    private static final float DEGREES_PER_TICK = 2.0f;

    public ConstructsHandRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void register() {
        BlockEntityRenderers.register(HexwrightBlocks.CONSTRUCTS_HAND_BLOCK_ENTITY, ConstructsHandRenderer::new);
    }

    @Override
    public void render(ConstructsHandBlockEntity hand, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (hand.getLevel() == null) {
            return;
        }
        Direction facing = hand.getBlockState().getValue(ConstructsHandBlock.FACING);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - facing.toYRot()));
        poseStack.translate(-0.5, 0.0, -0.5);

        HexPattern key = hand.getKey();
        if (key != null) {
            renderKey(hand, key, poseStack, bufferSource);
        }
        ItemStack held = hand.getHeld();
        if (!held.isEmpty()) {
            renderHeld(hand, held, partialTick, poseStack, bufferSource, packedLight);
        }

        poseStack.popPose();
    }

    private static void renderKey(ConstructsHandBlockEntity hand, HexPattern key, PoseStack poseStack,
                                  MultiBufferSource bufferSource) {
        poseStack.pushPose();
        poseStack.translate(8.0f * PX, WRIST_Y * PX, WRIST_Z * PX);
        poseStack.mulPose(Axis.XP.rotationDegrees(HAND_TILT));
        poseStack.translate(-8.0f * PX, -WRIST_Y * PX, -WRIST_Z * PX);
        poseStack.translate(PAD_EAST * PX, PAD_TOP * PX, PALM_Z * PX);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
        float scale = PAD_SIZE * PX;
        poseStack.scale(scale, scale, scale);
        WorldlyPatternRenderHelpers.renderPattern(key, WorldlyPatternRenderHelpers.WORLDLY_SETTINGS,
            PatternColors.SLATE_WOBBLY_PURPLE_COLOR, hand.getBlockPos().hashCode(), poseStack, bufferSource,
            null, -0.01f, LightTexture.FULL_BRIGHT, 1);
        poseStack.popPose();
    }

    private static void renderHeld(ConstructsHandBlockEntity hand, ItemStack held, float partialTick,
                                   PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float time = hand.getLevel().getGameTime() + partialTick;
        float bob = Mth.sin(time * 0.08f) * 0.5f;

        poseStack.pushPose();
        poseStack.translate(8.0f * PX, (ITEM_Y + bob) * PX, ITEM_Z * PX);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * DEGREES_PER_TICK));
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(
            held, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
            poseStack, bufferSource, hand.getLevel(), (int) hand.getBlockPos().asLong());
        poseStack.popPose();
    }
}
