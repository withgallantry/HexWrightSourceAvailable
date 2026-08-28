package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.server.weapon.SlashStyle;
import com.bluup.hexwright.server.weapon.SlashWaveEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class SlashWaveVisualClient {

    private static final float WIDTH = 3.4f;
    private static final float HEIGHT = WIDTH * 24.0f / 68.0f;

    private static final float TILT_DEGREES = 90.0f;

    private static final float FADE_IN_TICKS = 2.0f;
    private static final float FADE_OUT_TICKS = 3.0f;

    private static final Set<SlashWaveEntity> LIVE =
        Collections.newSetFromMap(new IdentityHashMap<>());

    private SlashWaveVisualClient() {
    }

    public static void register() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof SlashWaveEntity wave) {
                LIVE.add(wave);
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof SlashWaveEntity wave) {
                LIVE.remove(wave);
            }
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SlashWaveVisualClient::render);
    }

    private static void render(WorldRenderContext context) {
        if (LIVE.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            LIVE.clear();
            return;
        }
        if (!(context.consumers() instanceof MultiBufferSource.BufferSource buffers)) {
            return;
        }

        PoseStack pose = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        float partialTick = context.tickDelta();
        Map<SlashStyle, RenderType> drawn = new EnumMap<>(SlashStyle.class);

        for (SlashWaveEntity wave : LIVE) {
            if (wave.level() != level || wave.isRemoved()) {
                continue;
            }
            float alpha = alphaAt(wave, partialTick);
            if (alpha <= 0.0f) {
                continue;
            }

            RenderType type = drawn.computeIfAbsent(wave.style(),
                style -> RenderType.entityTranslucentEmissive(style.waveTexture()));
            VertexConsumer consumer = buffers.getBuffer(type);

            Vec3 at = wave.getPosition(partialTick);
            pose.pushPose();
            try {
                pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - wave.getViewYRot(partialTick)));
                pose.mulPose(Axis.XP.rotationDegrees(-wave.getViewXRot(partialTick) - TILT_DEGREES));
                quad(consumer, pose, alpha);
            } finally {
                pose.popPose();
            }
        }

        for (RenderType type : drawn.values()) {
            buffers.endBatch(type);
        }
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack, float alpha) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        float halfWidth = WIDTH / 2.0f;
        float halfHeight = HEIGHT / 2.0f;

        corner(consumer, pose, normal, -halfWidth, -halfHeight, 0.0f, 0.0f, alpha);
        corner(consumer, pose, normal, halfWidth, -halfHeight, 0.0f, 1.0f, alpha);
        corner(consumer, pose, normal, halfWidth, halfHeight, 1.0f, 1.0f, alpha);
        corner(consumer, pose, normal, -halfWidth, halfHeight, 1.0f, 0.0f, alpha);
    }

    private static void corner(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                               float x, float y, float u, float v, float alpha) {
        consumer.vertex(pose, x, y, 0.0f)
            .color(1.0f, 1.0f, 1.0f, alpha)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT)
            .normal(normal, 0.0f, 0.0f, 1.0f)
            .endVertex();
    }

    private static float alphaAt(SlashWaveEntity wave, float partialTick) {
        float age = wave.age() + partialTick;
        float remaining = SlashWaveEntity.LIFE_TICKS - age;
        return Mth.clamp(Math.min(age / FADE_IN_TICKS, remaining / FADE_OUT_TICKS), 0.0f, 1.0f);
    }
}
