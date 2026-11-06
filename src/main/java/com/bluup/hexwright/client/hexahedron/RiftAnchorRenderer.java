package com.bluup.hexwright.client.hexahedron;

import at.petrak.hexcasting.client.ClientTickCounter;
import com.bluup.hexwright.client.inversion.InversionShaders;
import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.bluup.hexwright.client.portal.RemoteLevelManager;
import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.mixin.BlendModeAccessor;
import com.bluup.hexwright.server.hexahedron.Hexahedron;
import com.bluup.hexwright.server.hexahedron.RiftAnchor;
import com.bluup.hexwright.server.hexahedron.RiftAnchors;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class RiftAnchorRenderer {

    private static final float BODY = 0.55f;
    private static final WardCrystal.Shape SHAPE =
        new WardCrystal.Shape(RiftAnchor.HALF_LENGTH, RiftAnchor.RADIUS, RiftAnchor.HALF_LENGTH * BODY);
    private static final float SPIN_PER_TICK = 0.006f;
    private static final float HANGING_TILT = 0.1f;
    private static final float STANDING_TILT = 0.26f;
    private static final float BURIED = 0.3f;
    private static final float COLUMN_WIDTH = 1.6f;
    private static final float RISE_HEIGHT = 12.0f;
    private static final int PULSE_TICKS = 30;
    private static final int FLASH_TICKS = 10;
    private static final float PULSE_RADIUS = 2.2f;
    private static final float TIME_WRAP_TICKS = 24000.0f;

    private record Pulse(UUID hexahedron, int anchor, long at) {
    }

    private record Pose(Vec3 centre, Matrix3f basis, float glow, float flare, float dissolve, float hold) {

        Vector3f axis() {
            return basis.getColumn(1, new Vector3f());
        }
    }

    private static final List<Pulse> PULSES = new ArrayList<>();
    private static final Map<UUID, long[]> LAST_MEDIA = new HashMap<>();
    @Nullable
    private static ClientLevel boundLevel;
    private static long ticks;

    private RiftAnchorRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> render(context, false));
        WorldRenderEvents.LAST.register(context -> render(context, true));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != boundLevel) {
                boundLevel = client.level;
                PULSES.clear();
                LAST_MEDIA.clear();
            }
            ticks++;
            PULSES.removeIf(pulse -> ticks - pulse.at() > PULSE_TICKS + FLASH_TICKS);
            if (client.level != null) {
                notice(client.level);
            }
        });
    }

    private static void notice(ClientLevel level) {
        for (ClientHexahedra.Shown shown : ClientHexahedra.shown(level, 0.0f)) {
            Hexahedron hexahedron = shown.hexahedron();
            long[] last = LAST_MEDIA.computeIfAbsent(hexahedron.id(),
                id -> hexahedron.anchors().stream().mapToLong(RiftAnchor::media).toArray());
            for (int i = 0; i < Math.min(last.length, hexahedron.anchors().size()); i++) {
                long media = hexahedron.anchors().get(i).media();
                if (media > last[i]) {
                    PULSES.add(new Pulse(hexahedron.id(), i, ticks));
                }
                last[i] = media;
            }
        }
    }

    private static void render(WorldRenderContext context, boolean light) {
        ClientLevel level = context.world();
        float partialTick = context.tickDelta();
        List<ClientHexahedra.Shown> shown = ClientHexahedra.shown(level, partialTick);
        if (shown.isEmpty() || !HexahedronShaders.ready() || !InversionShaders.ready()
            || IrisCompat.isRenderingShadowPass() || PortalViewRenderer.isRenderingView()
            || RemoteLevelManager.isRemotePassActive() || HexahedronRenderer.skyHidden(context.camera())) {
            return;
        }
        double gameTime = level.getGameTime() + partialTick;
        float time = (ClientTickCounter.getTotal() % TIME_WRAP_TICKS) / 20.0f;
        Vec3 eye = context.camera().getPosition();
        float reach = WardCrystal.reach();

        BlendMode previousBlend = BlendModeAccessor.hexwright$getLastApplied();
        BlendModeAccessor.hexwright$setLastApplied(null);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(!light);
        RenderSystem.disableCull();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.mulPoseMatrix(context.matrixStack().last().pose());
        RenderSystem.applyModelViewMatrix();

        ShaderInstance shader;
        if (light) {
            shader = InversionShaders.beam();
            shader.safeGetUniform("Time").set(time);
            shader.safeGetUniform("Light").set(1.0f);
        } else {
            shader = WardCrystal.shader(time, level.getSkyDarken(partialTick));
        }
        for (ClientHexahedra.Shown each : shown) {
            float appear = Mth.clamp((each.emergence() - 0.6f) / 0.4f, 0.0f, 1.0f);
            if (appear <= 0.0f) {
                continue;
            }
            Hexahedron hexahedron = each.hexahedron();
            for (int i = 0; i < hexahedron.anchors().size(); i++) {
                RiftAnchor anchor = hexahedron.anchors().get(i);
                Vec3 hung = hexahedron.anchorAt(i);
                Pose pose = pose(hexahedron, i, anchor, hung, gameTime, partialTick);
                if (pose == null) {
                    continue;
                }
                if (light) {
                    drawLight(shader, level, hexahedron, i, anchor, hung, pose, appear, eye, reach, partialTick);
                } else {
                    WardCrystal.draw(shader, pose.centre(), pose.basis(), SHAPE,
                        new WardCrystal.Look(anchor.fill(), pose.glow(), pose.flare(), pose.dissolve(), appear, i * 7.3f), eye);
                }
            }
        }

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        BlendModeAccessor.hexwright$setLastApplied(previousBlend);
    }

    private static @Nullable Pose pose(Hexahedron hexahedron, int index, RiftAnchor anchor, Vec3 hung,
                                       double gameTime, float partialTick) {
        float bob = 0.6f * Mth.sin((float) (gameTime / 20.0) * 0.7f + index * 1.9f);
        float flare = arrivalFlash(hexahedron.id(), index, partialTick);
        float glow = 0.3f + 0.6f * anchor.fill();
        if (!anchor.fallen()) {
            return new Pose(hung.add(0.0, bob, 0.0), basis(index, (float) gameTime * SPIN_PER_TICK, HANGING_TILT),
                glow, flare, 0.0f, 1.0f);
        }
        double since = gameTime - anchor.fellAt();
        double ground = anchor.groundY() == RiftAnchor.UNKNOWN ? hung.y - 200.0 : anchor.groundY();
        double standing = ground + RiftAnchor.HALF_LENGTH * (1.0f - 2.0f * BURIED) * Mth.cos(STANDING_TILT);
        float fellSpin = anchor.fellAt() * SPIN_PER_TICK;
        if (since < RiftAnchor.TEAR_TICKS) {
            float k = (float) (since / RiftAnchor.TEAR_TICKS);
            float jolt = 0.5f * k * k;
            Vec3 shaking = hung.add(jolt * Mth.sin((float) since * 2.3f), bob + jolt * Mth.sin((float) since * 3.1f),
                jolt * Mth.cos((float) since * 2.7f));
            return new Pose(shaking, basis(index, fellSpin + (float) since * SPIN_PER_TICK, HANGING_TILT),
                0.9f, 0.25f + 0.25f * Mth.sin((float) since * 1.7f), 0.0f, 1.0f - k);
        }
        double falling = since - RiftAnchor.TEAR_TICKS;
        float spun = fellSpin + (float) Math.min(since, RiftAnchor.TEAR_TICKS + RiftAnchor.FALL_TICKS) * SPIN_PER_TICK;
        if (falling < RiftAnchor.FALL_TICKS) {
            float f = (float) (falling / RiftAnchor.FALL_TICKS);
            float tilt = Mth.lerp(f, HANGING_TILT, STANDING_TILT) + 0.08f * (1.0f - f) * Mth.sin((float) falling * 0.5f);
            Vec3 at = new Vec3(hung.x, Mth.lerp(f * f, hung.y + bob, standing), hung.z);
            return new Pose(at, basis(index, spun, tilt), 0.9f, 0.0f, 0.0f, 0.0f);
        }
        double lying = falling - RiftAnchor.FALL_TICKS;
        if (lying >= RiftAnchor.DISSOLVE_TICKS) {
            return null;
        }
        float flash = (float) Math.max(0.0, 1.0 - lying / 8.0);
        return new Pose(new Vec3(hung.x, standing, hung.z), basis(index, spun, STANDING_TILT), 0.7f, flash,
            (float) (lying / RiftAnchor.DISSOLVE_TICKS), 0.0f);
    }

    private static Matrix3f basis(int index, float spin, float tilt) {
        Vector3f leanAxis = new Vector3f(Mth.cos(index * 1.7f), 0.0f, Mth.sin(index * 1.7f));
        return new Matrix3f().set(new Quaternionf().rotateAxis(tilt, leanAxis).rotateY(spin + index * 1.3f));
    }

    private static float arrivalFlash(UUID hexahedron, int index, float partialTick) {
        float flash = 0.0f;
        for (Pulse pulse : PULSES) {
            if (pulse.hexahedron().equals(hexahedron) && pulse.anchor() == index) {
                float since = ticks - pulse.at() + partialTick - PULSE_TICKS;
                if (since >= 0.0f) {
                    flash = Math.max(flash, 0.7f * (1.0f - since / FLASH_TICKS));
                }
            }
        }
        return flash;
    }


    private static void drawLight(ShaderInstance shader, ClientLevel level, Hexahedron hexahedron, int index,
                                  RiftAnchor anchor, Vec3 hung, Pose pose, float appear, Vec3 eye, float reach,
                                  float partialTick) {
        if (pose.hold() <= 0.0f) {
            return;
        }
        Vector3f axis = pose.axis().mul(RiftAnchor.HALF_LENGTH);
        Vec3 underside = pose.centre().subtract(axis.x, axis.y, axis.z);
        double ground = ground(level, anchor, hung);
        float fill = anchor.fill();
        float strength = appear * pose.hold() * (0.5f + 0.5f * fill);
        shader.safeGetUniform("Orb").set(0.0f);
        shader.safeGetUniform("Life").set(strength);
        strip(shader, new Vec3(underside.x, ground, underside.z), underside, COLUMN_WIDTH * (0.6f + 0.4f * fill), eye, reach);

        shader.safeGetUniform("Orb").set(1.0f);
        shader.safeGetUniform("Life").set(strength * 0.8f);
        pool(shader, new Vec3(underside.x, ground + 0.05, underside.z), (float) RiftAnchors.CATCH_RADIUS, eye, reach);
        for (Pulse pulse : PULSES) {
            float f = (ticks - pulse.at() + partialTick) / PULSE_TICKS;
            if (!pulse.hexahedron().equals(hexahedron.id()) || pulse.anchor() != index || f > 1.0f) {
                continue;
            }
            double y = Mth.lerp(Math.pow(f, 1.4), ground + RISE_HEIGHT, underside.y);
            shader.safeGetUniform("Life").set(appear * (1.0f - f * f * f * f));
            disc(shader, new Vec3(underside.x, y, underside.z), PULSE_RADIUS, eye, reach);
        }
    }

    private static double ground(ClientLevel level, RiftAnchor anchor, Vec3 hung) {
        if (anchor.groundY() != RiftAnchor.UNKNOWN) {
            return anchor.groundY();
        }
        int x = Mth.floor(hung.x);
        int z = Mth.floor(hung.z);
        return level.getChunkSource().hasChunk(x >> 4, z >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
            : level.getMinBuildHeight();
    }

    private static void strip(ShaderInstance shader, Vec3 from, Vec3 to, float width, Vec3 eye, float reach) {
        Vector3f a = new Vector3f((float) (from.x - eye.x), (float) (from.y - eye.y), (float) (from.z - eye.z));
        Vector3f b = new Vector3f((float) (to.x - eye.x), (float) (to.y - eye.y), (float) (to.z - eye.z));
        Vector3f along = b.sub(a, new Vector3f());
        float length = along.length();
        Vector3f side = along.cross(a.add(b, new Vector3f()).mul(0.5f), new Vector3f());
        if (length < 0.1f || side.lengthSquared() < 1.0e-6f) {
            return;
        }
        side.normalize(width);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        vertex(builder, a.sub(side, new Vector3f()), 0.0f, -1.0f, reach);
        vertex(builder, b.sub(side, new Vector3f()), length, -1.0f, reach);
        vertex(builder, b.add(side, new Vector3f()), length, 1.0f, reach);
        vertex(builder, a.add(side, new Vector3f()), 0.0f, 1.0f, reach);
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void disc(ShaderInstance shader, Vec3 centre, float radius, Vec3 eye, float reach) {
        Vector3f middle = new Vector3f((float) (centre.x - eye.x), (float) (centre.y - eye.y), (float) (centre.z - eye.z));
        Vector3f toEye = middle.negate(new Vector3f()).normalize();
        Vector3f right = toEye.cross(new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f());
        if (right.lengthSquared() < 1.0e-6f) {
            right.set(1.0f, 0.0f, 0.0f);
        }
        right.normalize(radius);
        Vector3f up = right.cross(toEye, new Vector3f()).normalize(radius);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (float[] corner : new float[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
            Vector3f at = new Vector3f(middle).add(right.mul(corner[0], new Vector3f())).add(up.mul(corner[1], new Vector3f()));
            vertex(builder, at, corner[0], corner[1], reach);
        }
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void pool(ShaderInstance shader, Vec3 centre, float radius, Vec3 eye, float reach) {
        Vector3f middle = new Vector3f((float) (centre.x - eye.x), (float) (centre.y - eye.y), (float) (centre.z - eye.z));
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (float[] corner : new float[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
            vertex(builder, new Vector3f(middle).add(corner[0] * radius, 0.0f, corner[1] * radius), corner[0], corner[1], reach);
        }
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(builder.end());
    }

    private static void vertex(BufferBuilder builder, Vector3f at, float u, float v, float reach) {
        float scale = WardCrystal.shrink(at.length(), reach);
        builder.vertex(at.x / scale, at.y / scale, at.z / scale).uv(u, v).endVertex();
    }
}
