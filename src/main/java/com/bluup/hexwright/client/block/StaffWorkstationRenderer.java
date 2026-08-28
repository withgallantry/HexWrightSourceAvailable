package com.bluup.hexwright.client.block;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.StaffAssemblyBlockEntity;
import com.bluup.hexwright.server.menu.StaffAssemblyMenu;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class StaffWorkstationRenderer implements BlockEntityRenderer<StaffAssemblyBlockEntity> {
    private static final float REST_X = 8.0f / 16.0f;
    private static final float REST_Y = 15.8f / 16.0f;
    private static final float REST_Z = 8.0f / 16.0f;
    private static final float SCALE = 0.5f;

    public StaffWorkstationRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static void register() {
        BlockEntityRenderers.register(HexwrightBlocks.STAFF_ASSEMBLY_BLOCK_ENTITY, StaffWorkstationRenderer::new);
    }

    @Override
    public void render(
        StaffAssemblyBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        if (blockEntity.getLevel() == null) {
            return;
        }

        ItemStack staff = blockEntity.getItem(StaffAssemblyMenu.STAFF_SLOT);
        if (staff.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(REST_X, REST_Y, REST_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0f));
        poseStack.scale(SCALE, SCALE, SCALE);

        Minecraft.getInstance().getItemRenderer().renderStatic(
            staff,
            ItemDisplayContext.NONE,
            packedLight,
            packedOverlay,
            poseStack,
            bufferSource,
            blockEntity.getLevel(),
            (int) blockEntity.getBlockPos().asLong()
        );

        poseStack.popPose();
    }
}
