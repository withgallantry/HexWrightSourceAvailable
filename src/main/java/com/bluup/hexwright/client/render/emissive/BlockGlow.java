package com.bluup.hexwright.client.render.emissive;

import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.client.render.SodiumCompat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class BlockGlow {
    private static final Direction[] DIRECTIONS = Direction.values();

    private static final List<GlowBlockIndex.Glow> VISIBLE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private static final MultiBufferSource.BufferSource MASKS =
        MultiBufferSource.immediate(new BufferBuilder(2048));

    private BlockGlow() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(BlockGlow::render);
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> GlowBlockIndex.onChunkLoaded(level, chunk.getPos()));
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> GlowBlockIndex.onChunkUnloaded(level, chunk.getPos()));
    }

    public static void onBlockChanged(ClientLevel level, BlockPos pos) {
        GlowBlockIndex.onBlockChanged(level, pos);
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
        RenderType layer = pickLayer(config);
        if (!config.blockGlow || config.blockGlowStrength <= 0.0f
            || level == null || consumers == null) {
            return;
        }
        if (config.blockGlowDisableWhenShaderPackActive && IrisCompat.isShaderPackActive()) {
            return;
        }
        if (IrisCompat.isRenderingShadowPass()) {
            return;
        }
        if (!GlowBlockIndex.anyGlowingBlocks()) {
            return;
        }

        BlockGlowTrace.nextFrame();
        collect(level, context, config);
        if (VISIBLE.isEmpty()) {
            return;
        }

        PoseStack poses = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        float strength = config.blockGlowStrength;
        float lift = config.blockGlowLift;
        boolean debugTint = config.blockGlowDebug;

        if (debugTint) {
            outlineAll(consumers.getBuffer(RenderType.lines()), poses, camera);
        }
        BlockGlowTrace.onSubmit(MASKS, true);
        emitAll(MASKS.getBuffer(layer), poses, camera, strength, lift, debugTint);
        MASKS.endBatch();
        EmissiveBloom.capture(layer, consumer -> emitAll(consumer, poses, camera, strength, lift, debugTint));

        BlockGlowTrace.endPass();
        VISIBLE.clear();
    }



    private static void collect(ClientLevel level, WorldRenderContext context,
                                EmissiveBloomConfig config) {
        Vec3 camera = context.camera().getPosition();
        Frustum frustum = context.frustum();
        double reachSq = (double) config.blockGlowDistance * config.blockGlowDistance;

        VISIBLE.clear();
        for (Long2ObjectMap.Entry<GlowBlockIndex.Glow[]> section
            : Long2ObjectMaps.fastIterable(GlowBlockIndex.litSections(level))) {
            AABB box = sectionBox(section.getLongKey());
            if (distanceSqToBox(camera, box) > reachSq
                || frustum != null && !frustum.isVisible(box)) {
                continue;
            }
            for (GlowBlockIndex.Glow glow : section.getValue()) {
                if (glow.pos().distToCenterSqr(camera) <= reachSq
                    && level.getBlockState(glow.pos()) == glow.state()) {
                    VISIBLE.add(glow);
                }
            }
        }
    }

    private static AABB sectionBox(long section) {
        double x = SectionPos.sectionToBlockCoord(SectionPos.x(section));
        double y = SectionPos.sectionToBlockCoord(SectionPos.y(section));
        double z = SectionPos.sectionToBlockCoord(SectionPos.z(section));
        return new AABB(x, y, z, x + 16.0, y + 16.0, z + 16.0);
    }

    private static double distanceSqToBox(Vec3 point, AABB box) {
        double dx = Math.max(Math.max(box.minX - point.x, point.x - box.maxX), 0.0);
        double dy = Math.max(Math.max(box.minY - point.y, point.y - box.maxY), 0.0);
        double dz = Math.max(Math.max(box.minZ - point.z, point.z - box.maxZ), 0.0);
        return dx * dx + dy * dy + dz * dz;
    }

    private static RenderType pickLayer(EmissiveBloomConfig config) {
        return config.blockGlowMipmap
            ? EmissiveGlowLayer.maskedLayer()
            : EmissiveGlowLayer.maskedLayerUnmipped();
    }

    private static void outlineAll(VertexConsumer consumer, PoseStack poses, Vec3 camera) {
        for (GlowBlockIndex.Glow draw : VISIBLE) {
            BlockPos pos = draw.pos();
            poses.pushPose();
            poses.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            LevelRenderer.renderLineBox(poses, consumer,
                0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0f, 1.0f, 1.0f, 1.0f);
            poses.popPose();
        }
    }

    private static void emitAll(VertexConsumer consumer, PoseStack poses, Vec3 camera,
                                float strength, float lift, boolean debugTint) {
        for (GlowBlockIndex.Glow draw : VISIBLE) {
            BlockPos pos = draw.pos();
            poses.pushPose();
            poses.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            if (draw.rotation() != null) {
                poses.translate(0.5f, 0.5f, 0.5f);
                poses.mulPose(draw.rotation().getLeftRotation());
                poses.translate(-0.5f, -0.5f, -0.5f);
            }

            long seed = draw.state().getSeed(pos);
            for (Direction direction : DIRECTIONS) {
                if (!draw.drawsFace(direction)) {
                    continue;
                }
                poses.pushPose();
                poses.translate(direction.getStepX() * lift,
                    direction.getStepY() * lift,
                    direction.getStepZ() * lift);
                RANDOM.setSeed(seed);
                emit(consumer, poses.last(), draw.glow().getQuads(draw.state(), direction, RANDOM), strength, debugTint);
                poses.popPose();
            }
            RANDOM.setSeed(seed);
            emit(consumer, poses.last(), draw.glow().getQuads(draw.state(), null, RANDOM), strength, debugTint);
            poses.popPose();
        }
    }

    private static void emit(VertexConsumer consumer, PoseStack.Pose pose, List<BakedQuad> quads,
                             float strength, boolean debugTint) {
        float red = strength;
        float green = debugTint ? 0.0f : strength;
        float blue = strength;
        for (BakedQuad quad : quads) {
            consumer.putBulkData(pose, quad, red, green, blue,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            SodiumCompat.markSpriteActive(quad.getSprite());
        }
    }
}
