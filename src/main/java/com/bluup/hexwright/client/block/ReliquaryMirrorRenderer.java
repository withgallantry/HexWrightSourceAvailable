package com.bluup.hexwright.client.block;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.ReliquaryMirrorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class ReliquaryMirrorRenderer implements BlockEntityRenderer<ReliquaryMirrorBlockEntity> {
    private static final Material MATERIAL =
        new Material(Sheets.CHEST_SHEET, Hexwright.id("entity/chest/hexbound"));

    private static final ReliquaryMirrorBlockEntity ITEM_RENDER_ENTITY = new ReliquaryMirrorBlockEntity(
        BlockPos.ZERO,
        HexwrightBlocks.RELIQUARY_MIRROR_BLOCK.defaultBlockState()
    );

    private final ModelPart bottom;
    private final ModelPart lid;
    private final ModelPart lock;

    public ReliquaryMirrorRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(ModelLayers.CHEST);
        this.bottom = root.getChild("bottom");
        this.lid = root.getChild("lid");
        this.lock = root.getChild("lock");
    }

    public static void register() {
        BlockEntityRenderers.register(HexwrightBlocks.RELIQUARY_MIRROR_BLOCK_ENTITY, ReliquaryMirrorRenderer::new);
        BuiltinItemRendererRegistry.INSTANCE.register(
            HexwrightBlocks.RELIQUARY_MIRROR_ITEM,
            (stack, poseStack, bufferSource, light, overlay) -> Minecraft.getInstance()
                .getBlockEntityRenderDispatcher()
                .renderItem(ITEM_RENDER_ENTITY, poseStack, bufferSource, light, overlay)
        );
    }

    @Override
    public void render(
        ReliquaryMirrorBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        BlockState state = blockEntity.getBlockState();
        Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
            ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
            : Direction.NORTH;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-0.5, -0.5, -0.5);

        float openness = 1.0f - blockEntity.getOpenNess(partialTick);
        openness = 1.0f - openness * openness * openness;
        float angle = -(openness * ((float) Math.PI / 2.0f));
        lid.xRot = angle;
        lock.xRot = angle;

        VertexConsumer buffer = MATERIAL.buffer(bufferSource, RenderType::entityCutout);
        lid.render(poseStack, buffer, packedLight, packedOverlay);
        lock.render(poseStack, buffer, packedLight, packedOverlay);
        bottom.render(poseStack, buffer, packedLight, packedOverlay);

        poseStack.popPose();
    }
}
