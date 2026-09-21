package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.weapon.SlashStyle;
import com.bluup.hexwright.server.weapon.SlashWaveEntity;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SlashWaveVisualClient {

    private static final float WIDTH = 3.4f;
    private static final float HEIGHT = WIDTH * 24.0f / 68.0f;

    private static final float TILT_DEGREES = 90.0f;

    private static final float FADE_IN_TICKS = 1.0f;
    private static final float FADE_OUT_TICKS = 3.0f;

    private static final Set<SlashWaveEntity> LIVE =
        Collections.newSetFromMap(new IdentityHashMap<>());

    private static final Set<SlashWaveEntity> TRACED =
        Collections.newSetFromMap(new IdentityHashMap<>());

    private static final Set<SlashWaveEntity> TRACED_BLOOM =
        Collections.newSetFromMap(new IdentityHashMap<>());

    private static final Map<SlashStyle, List<SlashWaveEntity>> BY_STYLE =
        new EnumMap<>(SlashStyle.class);

    static {
        for (SlashStyle style : SlashStyle.values()) {
            BY_STYLE.put(style, new ArrayList<>());
        }
    }

    private SlashWaveVisualClient() {
    }

    public static void register() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof SlashWaveEntity wave) {
                LIVE.add(wave);
                HexwrightDebug.log(HexwrightDebug.WEAPON,
                    "[weapon] wave #{} arrived: yaw {} pitch {} at {}",
                    wave.getId(), wave.getYRot(), wave.getXRot(), wave.position());
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof SlashWaveEntity wave) {
                LIVE.remove(wave);
                TRACED.remove(wave);
                TRACED_BLOOM.remove(wave);
                HexwrightDebug.log(HexwrightDebug.WEAPON,
                    "[weapon] wave #{} ended client-side at {} after {} ticks, yaw {} pitch {}",
                    wave.getId(), wave.position(), wave.age(), wave.getYRot(), wave.getXRot());
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(SlashWaveVisualClient::captureBloom);
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

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
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

                pose.pushPose();
                try {
                    trace(pose, wave, camera, partialTick, context, "screen", TRACED);
                    orient(pose, wave, camera, partialTick);
                    quad(consumer, pose, alpha);
                } finally {
                    pose.popPose();
                }
            }

            for (RenderType type : drawn.values()) {
                buffers.endBatch(type);
            }
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void captureBloom(WorldRenderContext context) {
        if (LIVE.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        PoseStack pose = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        float partialTick = context.tickDelta();

        for (List<SlashWaveEntity> group : BY_STYLE.values()) {
            group.clear();
        }
        for (SlashWaveEntity wave : LIVE) {
            if (wave.level() == level && !wave.isRemoved() && alphaAt(wave, partialTick) > 0.0f) {
                BY_STYLE.get(wave.style()).add(wave);
            }
        }

        for (Map.Entry<SlashStyle, List<SlashWaveEntity>> group : BY_STYLE.entrySet()) {
            List<SlashWaveEntity> waves = group.getValue();
            float strength = SlashBloom.waveStrength(group.getKey());
            if (waves.isEmpty() || strength <= 0.0f) {
                continue;
            }
            RenderType type = RenderType.entityTranslucentEmissive(group.getKey().waveTexture());
            SlashBloom.capture(type, raw -> {
                VertexConsumer consumer = SlashBloom.dimmed(raw, strength);
                for (SlashWaveEntity wave : waves) {
                    pose.pushPose();
                    try {
                        trace(pose, wave, camera, partialTick, context, "bloom", TRACED_BLOOM);
                        orient(pose, wave, camera, partialTick);
                        quad(consumer, pose, alphaAt(wave, partialTick));
                    } finally {
                        pose.popPose();
                    }
                }
            });
        }
    }

    private static void trace(PoseStack poseStack, SlashWaveEntity wave, Vec3 camera,
                              float partialTick, WorldRenderContext context, String pass,
                              Set<SlashWaveEntity> once) {
        if (!HexwrightDebug.on(HexwrightDebug.WEAPON)) {
            return;
        }
        Vec3 at = wave.getPosition(partialTick);
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && once == TRACED) {
            level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
        }
        if (!once.add(wave)) {
            return;
        }
        Matrix4f world = new Matrix4f(poseStack.last().pose());
        Matrix4f global = new Matrix4f(RenderSystem.getModelViewMatrix());
        HexwrightDebug.log(HexwrightDebug.WEAPON,
            "[weapon] wave #{} first draw in {}: at {} camera {} (yaw {} pitch {});"
                + " pose +Z {}; modelView +Z {}",
            wave.getId(), pass, at, camera, context.camera().getYRot(),
            context.camera().getXRot(),
            world.transformDirection(new Vector3f(0.0f, 0.0f, 1.0f)),
            global.transformDirection(new Vector3f(0.0f, 0.0f, 1.0f)));
    }

    private static void orient(PoseStack poseStack, SlashWaveEntity wave, Vec3 camera,
                               float partialTick) {
        Vec3 at = wave.getPosition(partialTick);
        poseStack.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - wave.getViewYRot(partialTick)));
        poseStack.mulPose(Axis.XP.rotationDegrees(-wave.getViewXRot(partialTick) - TILT_DEGREES));
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
