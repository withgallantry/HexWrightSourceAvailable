package com.bluup.hexwright.client.wardingbox;

import at.petrak.hexcasting.api.pigment.FrozenPigment;
import com.bluup.hexwright.client.accessory.WornSpectacles;
import com.bluup.hexwright.server.block.WardingBoxBlockEntity;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WardingBoxVisualClient {

    private static final Set<WardingBoxBlockEntity> TRACKED =
        Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final double MAX_RENDER_DISTANCE_SQ = 96.0 * 96.0;

    private WardingBoxVisualClient() {
    }

    public static void track(WardingBoxBlockEntity box) {
        TRACKED.add(box);
    }

    public static void untrack(WardingBoxBlockEntity box) {
        TRACKED.remove(box);
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(WardingBoxVisualClient::render);
    }

    private static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || TRACKED.isEmpty()) {
            return;
        }
        if (!WornSpectacles.worn()) {
            return;
        }
        MultiBufferSource consumers = context.consumers();
        if (consumers == null) {
            return;
        }

        Vec3 camera = context.camera().getPosition();
        PoseStack poseStack = context.matrixStack();
        VertexConsumer lines = consumers.getBuffer(RenderType.lines());
        float time = (mc.level.getGameTime() % 200000L) + context.tickDelta();

        poseStack.pushPose();
        try {
            poseStack.translate(-camera.x, -camera.y, -camera.z);

            for (WardingBoxBlockEntity box : TRACKED) {
                if (box.isRemoved()
                    || box.getLevel() != mc.level
                    || mc.level.getBlockEntity(box.getBlockPos()) != box) {
                    TRACKED.remove(box);
                    continue;
                }
                Vec3 center = Vec3.atCenterOf(box.getBlockPos());
                if (center.distanceToSqr(camera) > MAX_RENDER_DISTANCE_SQ) {
                    continue;
                }

                AABB area = box.wardedArea();
                float r;
                float g;
                float b;
                float a;
                if (box.isArmed()) {
                    FrozenPigment pigment = box.getPigment();
                    int color;
                    try {
                        color = pigment.getColorProvider().getColor(time, center);
                    } catch (RuntimeException e) {
                        color = 0xFFFFFF;
                    }
                    r = ((color >> 16) & 0xFF) / 255f;
                    g = ((color >> 8) & 0xFF) / 255f;
                    b = (color & 0xFF) / 255f;
                    a = 0.9f;
                } else {
                    r = 0.55f;
                    g = 0.58f;
                    b = 0.62f;
                    a = 0.35f;
                }

                LevelRenderer.renderLineBox(
                    poseStack, lines,
                    area.minX, area.minY, area.minZ,
                    area.maxX, area.maxY, area.maxZ,
                    r, g, b, a
                );
            }
        } finally {
            poseStack.popPose();
        }
    }
}
