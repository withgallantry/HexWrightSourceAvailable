package com.bluup.hexwright.client.render.emissive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class BlockGlow {
    private static final Direction[] DIRECTIONS = Direction.values();

    private static final List<Draw> VISIBLE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final BlockPos.MutableBlockPos NEIGHBOUR = new BlockPos.MutableBlockPos();

    @Nullable
    private static ClientLevel indexedLevel;

    private BlockGlow() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(BlockGlow::render);
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> GlowBlockIndex.invalidate(chunk.getPos()));
    }

    public static void onBlockChanged(BlockPos pos) {
        GlowBlockIndex.invalidate(pos);
    }

    @Nullable
    static BakedModel glowTwinOf(@Nullable BakedModel model) {
        if (!(model instanceof EmissiveBakedModel emissive)
            || !emissive.hasTwin() || emissive.thresholded()) {
            return null;
        }
        return emissive.glowModel();
    }

    private static void render(WorldRenderContext context) {
        EmissiveBloomConfig config = EmissiveBloomConfigManager.get();
        ClientLevel level = context.world();
        MultiBufferSource consumers = context.consumers();
        RenderType layer = EmissiveGlowLayer.maskedLayer();
        if (!config.blockGlow || config.blockGlowStrength <= 0.0f
            || level == null || consumers == null) {
            return;
        }
        if (level != indexedLevel) {
            GlowBlockIndex.clear();
            indexedLevel = level;
        }
        if (!GlowBlockIndex.anyGlowingBlocks()) {
            return;
        }

        collect(level, context, config);
        if (VISIBLE.isEmpty()) {
            return;
        }

        PoseStack poses = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        float strength = config.blockGlowStrength;

        emitAll(consumers.getBuffer(layer), poses, camera, level, strength);
        if (consumers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(layer);
        }
        EmissiveBloom.capture(layer, consumer -> emitAll(consumer, poses, camera, level, strength));

        VISIBLE.clear();
    }

    private static void collect(ClientLevel level, WorldRenderContext context,
                                EmissiveBloomConfig config) {
        Vec3 camera = context.camera().getPosition();
        Frustum frustum = context.frustum();
        BlockModelShaper shaper = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper();

        int reach = config.blockGlowDistance;
        double reachSq = (double) reach * reach;
        int centreX = SectionPos.blockToSectionCoord(Mth.floor(camera.x));
        int centreY = SectionPos.blockToSectionCoord(Mth.floor(camera.y));
        int centreZ = SectionPos.blockToSectionCoord(Mth.floor(camera.z));
        int span = (reach >> 4) + 1;

        VISIBLE.clear();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -span; x <= span; x++) {
            for (int y = -span; y <= span; y++) {
                for (int z = -span; z <= span; z++) {
                    int sectionX = centreX + x;
                    int sectionY = centreY + y;
                    int sectionZ = centreZ + z;
                    if (frustum != null && !frustum.isVisible(sectionBox(sectionX, sectionY, sectionZ))) {
                        continue;
                    }
                    for (long packed : GlowBlockIndex.positions(level, sectionX, sectionY, sectionZ)) {
                        pos.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
                        if (pos.distToCenterSqr(camera) > reachSq) {
                            continue;
                        }
                        BlockState state = level.getBlockState(pos);
                        BakedModel model = shaper.getBlockModel(state);
                        BakedModel glow = glowTwinOf(model);
                        if (glow != null) {
                            VISIBLE.add(new Draw(pos.immutable(), state, glow, rotationOf(model)));
                        }
                    }
                }
            }
        }
    }

    @Nullable
    private static Transformation rotationOf(BakedModel model) {
        return model instanceof EmissiveBakedModel emissive ? emissive.modelRotation() : null;
    }

    private static AABB sectionBox(int sectionX, int sectionY, int sectionZ) {
        double x = SectionPos.sectionToBlockCoord(sectionX);
        double y = SectionPos.sectionToBlockCoord(sectionY);
        double z = SectionPos.sectionToBlockCoord(sectionZ);
        return new AABB(x, y, z, x + 16.0, y + 16.0, z + 16.0);
    }

    private static void emitAll(VertexConsumer consumer, PoseStack poses, Vec3 camera,
                                ClientLevel level, float strength) {
        for (Draw draw : VISIBLE) {
            BlockPos pos = draw.pos();
            poses.pushPose();
            poses.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            if (draw.rotation() != null) {
                poses.translate(0.5f, 0.5f, 0.5f);
                poses.mulPose(draw.rotation().getLeftRotation());
                poses.translate(-0.5f, -0.5f, -0.5f);
            }

            PoseStack.Pose pose = poses.last();
            long seed = draw.state().getSeed(pos);
            for (Direction direction : DIRECTIONS) {
                NEIGHBOUR.setWithOffset(pos, direction);
                if (!Block.shouldRenderFace(draw.state(), level, pos, direction, NEIGHBOUR)) {
                    continue;
                }
                RANDOM.setSeed(seed);
                emit(consumer, pose, draw.glow().getQuads(draw.state(), direction, RANDOM), strength);
            }
            RANDOM.setSeed(seed);
            emit(consumer, pose, draw.glow().getQuads(draw.state(), null, RANDOM), strength);
            poses.popPose();
        }
    }

    private static void emit(VertexConsumer consumer, PoseStack.Pose pose, List<BakedQuad> quads,
                             float strength) {
        for (BakedQuad quad : quads) {
            consumer.putBulkData(pose, quad, strength, strength, strength,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    private record Draw(BlockPos pos, BlockState state, BakedModel glow,
                        @Nullable Transformation rotation) {
    }
}
