package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.weapon.PiercingSkyfall;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class SkyfallVisualClient {

    private static final int LOOSE_TICKS = 22;
    private static final int RAIN_TICKS = 30;

    private static final int HIT_FRAMES = 7;

    private static final float HIT_SIZE = 2.2f;

    private static final ResourceLocation HIT_TEXTURE = Hexwright.id("textures/fx/skyfall_hit.png");

    private static final List<Piece> LIVE = new ArrayList<>();

    private static boolean warned;

    private SkyfallVisualClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SkyfallVisualClient::onClientTick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SkyfallVisualClient::render);
    }

    public static void handle(int kind, Vec3 at, float yaw, int variant) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        switch (kind) {
            case PiercingSkyfall.VFX_LOOSE -> LIVE.add(new Piece(level, at, yaw, "loose", LOOSE_TICKS));
            case PiercingSkyfall.VFX_RAIN -> LIVE.add(new Piece(level, at, yaw,
                "rain_" + (Math.floorMod(variant, PiercingSkyfall.RAIN_VARIANTS) + 1), RAIN_TICKS));
            case PiercingSkyfall.VFX_HIT -> LIVE.add(new Piece(level, at, yaw, null, HIT_FRAMES));
            default -> {
            }
        }
    }

    private static void onClientTick(Minecraft mc) {
        if (LIVE.isEmpty()) {
            return;
        }
        ClientLevel level = mc.level;
        Iterator<Piece> pieces = LIVE.iterator();
        while (pieces.hasNext()) {
            Piece piece = pieces.next();
            if (piece.level != level || ++piece.age >= piece.life) {
                pieces.remove();
            }
        }
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
        boolean drewRig = false;
        boolean drewHit = false;

        for (Piece piece : LIVE) {
            if (piece.level != level) {
                continue;
            }
            pose.pushPose();
            try {
                pose.translate(piece.at.x - camera.x, piece.at.y - camera.y, piece.at.z - camera.z);
                if (piece.vfx != null) {
                    pose.mulPose(Axis.YP.rotationDegrees(180.0f - piece.yaw));
                    piece.vfx.sparkleFrame = piece.age;
                    piece.renderer.render(pose, piece.vfx, buffers, null, null, LightTexture.FULL_BRIGHT);
                    drewRig = true;
                } else {
                    pose.mulPose(context.camera().rotation());
                    hitQuad(buffers.getBuffer(RenderType.entityTranslucentEmissive(HIT_TEXTURE)), pose,
                        Math.min(piece.age, HIT_FRAMES - 1));
                    drewHit = true;
                }
            } catch (Exception e) {
                if (!warned) {
                    warned = true;
                    Hexwright.LOGGER.error("Failed to draw a Piercing Skyfall effect", e);
                }
            } finally {
                pose.popPose();
            }
        }

        if (drewRig) {
            for (ResourceLocation texture : SkyfallVfxModel.TEXTURES) {
                buffers.endBatch(RenderType.entityTranslucentEmissive(texture));
            }
        }
        if (drewHit) {
            buffers.endBatch(RenderType.entityTranslucentEmissive(HIT_TEXTURE));
        }
    }

    private static void hitQuad(VertexConsumer consumer, PoseStack poseStack, int frame) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        float half = HIT_SIZE / 2.0f;
        float u0 = (float) frame / HIT_FRAMES;
        float u1 = (float) (frame + 1) / HIT_FRAMES;
        corner(consumer, pose, normal, -half, -half, u1, 1.0f);
        corner(consumer, pose, normal, half, -half, u0, 1.0f);
        corner(consumer, pose, normal, half, half, u0, 0.0f);
        corner(consumer, pose, normal, -half, half, u1, 0.0f);
    }

    private static void corner(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                               float x, float y, float u, float v) {
        consumer.vertex(pose, x, y, 0.0f)
            .color(1.0f, 1.0f, 1.0f, 1.0f)
            .uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT)
            .normal(normal, 0.0f, 0.0f, 1.0f)
            .endVertex();
    }

    private static final class Piece {
        private final ClientLevel level;
        private final Vec3 at;
        private final float yaw;
        private final int life;
        private final SkyfallVfx vfx;
        private final SkyfallVfxRenderer renderer;
        private int age;

        private Piece(ClientLevel level, Vec3 at, float yaw, String clip, int life) {
            this.level = level;
            this.at = at;
            this.yaw = yaw;
            this.life = life;
            this.vfx = clip == null ? null : new SkyfallVfx(clip);
            this.renderer = clip == null ? null : new SkyfallVfxRenderer(new SkyfallVfxModel());
        }
    }
}
