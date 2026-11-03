package com.bluup.hexwright.client.region;

import at.petrak.hexcasting.api.pigment.ColorProvider;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.region.Region;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;

public final class RegionProjectionClient {

    private static final long LIFETIME_MS = 10_000L;
    private static final long FADE_IN_MS = 250L;
    private static final long FADE_OUT_MS = 1_500L;
    private static final int MAX_PROJECTIONS = 16;
    private static final float ALPHA = 0.9F;
    private static final float THROUGH_ALPHA = 0.25F;

    private static final List<Projection> LIVE = new ArrayList<>();

    private RegionProjectionClient() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(RegionProjectionClient::render);
    }

    public static void project(@Nullable CompoundTag regionTag, @Nullable CompoundTag pigmentTag) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || regionTag == null || pigmentTag == null) {
            return;
        }
        FrozenPigment pigment = FrozenPigment.fromNBT(pigmentTag);
        Region region;
        try {
            region = Region.load(regionTag);
        } catch (IllegalArgumentException malformed) {
            Hexwright.LOGGER.warn("Ignoring a malformed Region Projection", malformed);
            return;
        }
        if (region.isEmpty()) {
            return;
        }

        CompletableFuture<RegionOutline> outline = null;
        for (int i = 0; i < LIVE.size(); i++) {
            Projection shown = LIVE.get(i);
            if (shown.level == level && shown.region.equals(region)) {
                outline = shown.outline;
                LIVE.remove(i);
                break;
            }
        }
        if (outline == null) {
            outline = CompletableFuture.supplyAsync(() -> trace(region), Util.backgroundExecutor())
                .exceptionally(failure -> {
                    Hexwright.LOGGER.error("Region Projection could not trace its Region", failure);
                    return null;
                });
        }
        while (LIVE.size() >= MAX_PROJECTIONS) {
            LIVE.remove(0);
        }
        LIVE.add(new Projection(level, region, pigment, outline, Util.getMillis()));
    }

    private static RegionOutline trace(Region region) {
        long started = System.nanoTime();
        RegionOutline outline = RegionOutline.trace(region);
        HexwrightDebug.log(HexwrightDebug.RENDER, "[region] projection traced {} lines in {} ms",
            outline.lineCount(), (System.nanoTime() - started) / 1_000_000L);
        return outline;
    }

    private static void render(WorldRenderContext context) {
        if (LIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        LIVE.removeIf(projection -> now - projection.shownAt >= LIFETIME_MS);
        if (mc.level == null || LIVE.isEmpty()
            || !(context.consumers() instanceof MultiBufferSource.BufferSource buffers)) {
            return;
        }

        Frustum frustum = context.frustum();
        List<Projection> visible = new ArrayList<>(LIVE.size());
        for (Projection projection : LIVE) {
            RegionOutline outline = projection.outline.getNow(null);
            if (projection.level == mc.level && outline != null
                && (frustum == null || frustum.isVisible(outline.box()))) {
                visible.add(projection);
            }
        }
        if (visible.isEmpty()) {
            return;
        }

        Vec3 camera = context.camera().getPosition();
        PoseStack.Pose pose = context.matrixStack().last();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            VertexConsumer through = buffers.getBuffer(Layer.THROUGH_TERRAIN);
            for (Projection projection : visible) {
                projection.draw(through, pose, camera, THROUGH_ALPHA * projection.fade(now));
            }
            buffers.endBatch(Layer.THROUGH_TERRAIN);

            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            for (Projection projection : visible) {
                projection.draw(lines, pose, camera, ALPHA * projection.fade(now));
            }
            buffers.endBatch(RenderType.lines());
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static final class Projection {

        final ClientLevel level;
        final Region region;
        final FrozenPigment pigment;
        final CompletableFuture<RegionOutline> outline;
        final long shownAt;
        private int[] colors;

        Projection(ClientLevel level, Region region, FrozenPigment pigment, CompletableFuture<RegionOutline> outline,
                   long shownAt) {
            this.level = level;
            this.region = region;
            this.pigment = pigment;
            this.outline = outline;
            this.shownAt = shownAt;
        }

        float fade(long now) {
            long age = now - shownAt;
            float in = age / (float) FADE_IN_MS;
            float out = (LIFETIME_MS - age) / (float) FADE_OUT_MS;
            return Math.max(0.0F, Math.min(1.0F, Math.min(in, out)));
        }

        void draw(VertexConsumer lines, PoseStack.Pose pose, Vec3 camera, float alpha) {
            RegionOutline traced = outline.getNow(null);
            if (traced == null) {
                return;
            }
            int[] segments = traced.segments();
            int[] colors = colors(traced);
            double offsetX = traced.originX() - camera.x;
            double offsetY = traced.originY() - camera.y;
            double offsetZ = traced.originZ() - camera.z;
            for (int i = 0; i < segments.length; i += 6) {
                int fromX = segments[i];
                int fromY = segments[i + 1];
                int fromZ = segments[i + 2];
                int toX = segments[i + 3];
                int toY = segments[i + 4];
                int toZ = segments[i + 5];
                float normalX = toX != fromX ? 1.0F : 0.0F;
                float normalY = toY != fromY ? 1.0F : 0.0F;
                float normalZ = toZ != fromZ ? 1.0F : 0.0F;
                vertex(lines, pose, offsetX + fromX, offsetY + fromY, offsetZ + fromZ,
                    colors[i / 3], alpha, normalX, normalY, normalZ);
                vertex(lines, pose, offsetX + toX, offsetY + toY, offsetZ + toZ,
                    colors[i / 3 + 1], alpha, normalX, normalY, normalZ);
            }
        }

        private int[] colors(RegionOutline traced) {
            if (colors == null) {
                int[] segments = traced.segments();
                colors = new int[segments.length / 3];
                ColorProvider provider = pigment.getColorProvider();
                float time = level.getGameTime();
                for (int end = 0; end < colors.length; end++) {
                    Vec3 at = new Vec3(traced.originX() + segments[end * 3],
                        traced.originY() + segments[end * 3 + 1], traced.originZ() + segments[end * 3 + 2]);
                    int color;
                    try {
                        color = provider.getColor(time, at);
                    } catch (RuntimeException e) {
                        color = 0xFFFFFF;
                    }
                    colors[end] = color;
                }
            }
            return colors;
        }

        private static void vertex(VertexConsumer lines, PoseStack.Pose pose, double x, double y, double z,
                                   int color, float alpha, float normalX, float normalY, float normalZ) {
            lines.vertex(pose.pose(), (float) x, (float) y, (float) z)
                .color(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, alpha)
                .normal(pose.normal(), normalX, normalY, normalZ)
                .endVertex();
        }
    }

    private static final class Layer extends RenderType {

        static final RenderType THROUGH_TERRAIN = create(
            "hexwright_region_projection_through",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_LINES_SHADER)
                .setLineState(new LineStateShard(OptionalDouble.empty()))
                .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setOutputState(ITEM_ENTITY_TARGET)
                .setWriteMaskState(COLOR_WRITE)
                .setDepthTestState(NO_DEPTH_TEST)
                .setCullState(NO_CULL)
                .createCompositeState(false));

        private Layer(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                      boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
            throw new AssertionError("render type holder, never instantiated");
        }
    }
}
