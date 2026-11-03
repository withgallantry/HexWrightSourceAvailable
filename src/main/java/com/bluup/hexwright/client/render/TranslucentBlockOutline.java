package com.bluup.hexwright.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class TranslucentBlockOutline {
    private static final float ALPHA = 0.4F;

    private static final MultiBufferSource.BufferSource LINES =
        MultiBufferSource.immediate(new BufferBuilder(256));

    @Nullable
    private static Deferred pending;

    private TranslucentBlockOutline() {
    }

    public static void register() {
        WorldRenderEvents.BLOCK_OUTLINE.register(TranslucentBlockOutline::onOutline);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(TranslucentBlockOutline::render);
        WorldRenderEvents.LAST.register(TranslucentBlockOutline::render);
    }

    private static boolean onOutline(WorldRenderContext world, WorldRenderContext.BlockOutlineContext outline) {
        if (!defersOutline(outline.blockState())) {
            return true;
        }
        pending = new Deferred(
            outline.blockPos(), outline.blockState(), outline.entity(),
            outline.cameraX(), outline.cameraY(), outline.cameraZ());
        return false;
    }

    private static void render(WorldRenderContext context) {
        Deferred deferred = pending;
        pending = null;
        if (deferred == null || context.world() == null) {
            return;
        }

        BlockGetter level = context.world();
        VoxelShape shape = deferred.state.getShape(level, deferred.pos, CollisionContext.of(deferred.entity));
        if (shape.isEmpty()) {
            return;
        }

        renderShape(
            context.matrixStack(), LINES.getBuffer(RenderType.lines()), shape,
            deferred.pos.getX() - deferred.cameraX,
            deferred.pos.getY() - deferred.cameraY,
            deferred.pos.getZ() - deferred.cameraZ);
        LINES.endBatch(RenderType.lines());
    }

    private static void renderShape(PoseStack poseStack, VertexConsumer consumer, VoxelShape shape,
                                    double x, double y, double z) {
        PoseStack.Pose pose = poseStack.last();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1);
            float dy = (float) (y2 - y1);
            float dz = (float) (z2 - z1);
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= length;
            dy /= length;
            dz /= length;
            consumer.vertex(pose.pose(), (float) (x1 + x), (float) (y1 + y), (float) (z1 + z))
                .color(0.0F, 0.0F, 0.0F, ALPHA).normal(pose.normal(), dx, dy, dz).endVertex();
            consumer.vertex(pose.pose(), (float) (x2 + x), (float) (y2 + y), (float) (z2 + z))
                .color(0.0F, 0.0F, 0.0F, ALPHA).normal(pose.normal(), dx, dy, dz).endVertex();
        });
    }

    private static boolean defersOutline(BlockState state) {
        if (ItemBlockRenderTypes.getChunkRenderType(state) != RenderType.translucent()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return com.bluup.hexwright.Hexwright.MOD_ID.equals(id.getNamespace());
    }

    private record Deferred(BlockPos pos, BlockState state, Entity entity,
                            double cameraX, double cameraY, double cameraZ) {
    }
}
