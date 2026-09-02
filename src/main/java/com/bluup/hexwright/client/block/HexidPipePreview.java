package com.bluup.hexwright.client.block;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.fluid.HexidPipeBlock;
import com.bluup.hexwright.server.fluid.PipeJoint;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class HexidPipePreview {

    private static final float ALPHA = 0.55F;
    private static final float TINT_R = 1.0F;
    private static final float TINT_G = 0.86F;
    private static final float TINT_B = 0.62F;

    private static final float OUTLINE_ALPHA = 0.35F;

    private static final float NEIGHBOUR_ALPHA = 0.4F;
    private static final float NEIGHBOUR_SWELL = 1.08F;

    private static final float HUB_X = 0.5F;
    private static final float HUB_Y = 1F / 16F;
    private static final float HUB_Z = 0.5F;

    private HexidPipePreview() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(HexidPipePreview::render);
    }

    private static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null || mc.options.hideGui) {
            return;
        }
        if (!(context.consumers() instanceof MultiBufferSource.BufferSource buffers)) {
            return;
        }

        Placement placement = predict(mc, level, player);
        if (placement == null) {
            return;
        }

        PoseStack pose = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        RenderType ghostType = RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS);
        BlockRenderDispatcher blocks = mc.getBlockRenderer();

        ghost(blocks, buffers, ghostType, pose, camera,
            placement.pos, placement.state, ALPHA, 1.0F);
        for (Direction side : Direction.values()) {
            BlockState reacting = reactionTo(level, placement, side);
            if (reacting != null) {
                ghost(blocks, buffers, ghostType, pose, camera, placement.pos.relative(side),
                    reacting, NEIGHBOUR_ALPHA, NEIGHBOUR_SWELL);
            }
        }
        buffers.endBatch(ghostType);

        outline(pose, buffers, camera, placement);
    }

    @Nullable
    private static BlockState reactionTo(ClientLevel level, Placement placement, Direction side) {
        BlockPos pos = placement.pos.relative(side);
        BlockState neighbour = level.getBlockState(pos);
        if (!neighbour.is(HexwrightBlocks.HEXID_PIPE_BLOCK)) {
            return null;
        }
        PipeJoint joint = HexidPipeBlock.jointWith(placement.state, side.getOpposite());
        BlockState changed =
            neighbour.setValue(HexidPipeBlock.JOINTS.get(side.getOpposite()), joint);
        return changed == neighbour ? null : changed;
    }

    private static void ghost(BlockRenderDispatcher blocks, MultiBufferSource.BufferSource buffers,
                              RenderType type, PoseStack pose, Vec3 camera, BlockPos pos,
                              BlockState state, float alpha, float swell) {
        pose.pushPose();
        try {
            pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            if (swell != 1.0F) {
                pose.translate(HUB_X, HUB_Y, HUB_Z);
                pose.scale(swell, swell, swell);
                pose.translate(-HUB_X, -HUB_Y, -HUB_Z);
            }
            MultiBufferSource ghosted = ignored -> new Ghost(buffers.getBuffer(type), alpha);
            blocks.renderSingleBlock(state, pose, ghosted,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } finally {
            pose.popPose();
        }
    }

    private static void outline(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
                                Placement placement) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(pose, lines,
                placement.pos.getX(), placement.pos.getY(), placement.pos.getZ(),
                placement.pos.getX() + 1.0, placement.pos.getY() + 1.0, placement.pos.getZ() + 1.0,
                TINT_R, TINT_G, TINT_B, OUTLINE_ALPHA);
        } finally {
            pose.popPose();
        }
        buffers.endBatch(RenderType.lines());
    }

    private record Placement(BlockPos pos, BlockState state) {
    }

    @Nullable
    private static Placement predict(Minecraft mc, ClientLevel level, Player player) {
        InteractionHand hand = holdingPipe(player);
        if (hand == null) {
            return null;
        }
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPlaceContext context =
            new BlockPlaceContext(level, player, hand, player.getItemInHand(hand), block);
        if (!context.canPlace()) {
            return null;
        }

        BlockPos pos = context.getClickedPos();
        BlockState state = HexwrightBlocks.HEXID_PIPE_BLOCK.getStateForPlacement(context);
        if (state == null || !state.canSurvive(level, pos)) {
            return null;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (!shape.isEmpty() && !level.isUnobstructed(state, pos, CollisionContext.of(player))) {
            return null;
        }
        return new Placement(pos, state);
    }

    @Nullable
    private static InteractionHand holdingPipe(Player player) {
        if (player.getMainHandItem().is(HexwrightBlocks.HEXID_PIPE_ITEM)) {
            return InteractionHand.MAIN_HAND;
        }
        ItemStack offhand = player.getOffhandItem();
        if (player.getMainHandItem().isEmpty() && offhand.is(HexwrightBlocks.HEXID_PIPE_ITEM)) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private record Ghost(VertexConsumer inner, float alpha) implements VertexConsumer {

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return inner.vertex(x, y, z);
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return inner.color(
                (int) (red * TINT_R),
                (int) (green * TINT_G),
                (int) (blue * TINT_B),
                (int) (alpha * this.alpha));
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return inner.uv(u, v);
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return inner.overlayCoords(u, v);
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return inner.uv2(u, v);
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return inner.normal(x, y, z);
        }

        @Override
        public void endVertex() {
            inner.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            inner.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            inner.unsetDefaultColor();
        }
    }
}
