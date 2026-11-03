package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.weapon.FrozenPursuit;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class FrostPursuitVisualClient {

    private static final int SNOW_TICKS = 26;
    private static final int CUT_TICKS = 14;

    private static final float FORWARD_SNOW_SCALE = 1.3f;

    private static final String[] CUT_CLIPS = {"lrd", "rld", "lr", "v", "v2"};

    private static final List<Piece> LIVE = new ArrayList<>();

    private static boolean warned;

    private FrostPursuitVisualClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FrostPursuitVisualClient::onClientTick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(FrostPursuitVisualClient::render);
    }

    public static void handle(int kind, Vec3 at, float yaw, int variant) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        switch (kind) {
            case FrozenPursuit.VFX_DASH -> {
                LIVE.add(new Piece(level, at, yaw, FrostVfxModel.SNOW, "forward", FORWARD_SNOW_SCALE, SNOW_TICKS));
                LIVE.add(new Piece(level, at, yaw, FrostVfxModel.SNOW, "outward_vertical", 1.0f, SNOW_TICKS));
            }
            case FrozenPursuit.VFX_CUT -> LIVE.add(new Piece(level, at, yaw, FrostVfxModel.CUT,
                CUT_CLIPS[Math.floorMod(variant, CUT_CLIPS.length)], 1.0f, CUT_TICKS));
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
        boolean drewCut = false;
        boolean drewSnow = false;

        for (Piece piece : LIVE) {
            if (piece.level != level) {
                continue;
            }
            pose.pushPose();
            try {
                pose.translate(piece.at.x - camera.x, piece.at.y - camera.y, piece.at.z - camera.z);
                pose.mulPose(Axis.YP.rotationDegrees(-piece.yaw));
                pose.scale(piece.scale, piece.scale, piece.scale);
                piece.renderer.render(pose, piece.vfx, buffers, null, null, LightTexture.FULL_BRIGHT);
                if (FrostVfxModel.CUT.equals(piece.rig)) {
                    drewCut = true;
                } else {
                    drewSnow = true;
                }
            } catch (Exception e) {
                if (!warned) {
                    warned = true;
                    Hexwright.LOGGER.error("Failed to draw a Frozen Pursuit effect", e);
                }
            } finally {
                pose.popPose();
            }
        }

        if (drewCut) {
            buffers.endBatch(RenderType.entityTranslucentEmissive(FrostVfxModel.texture(FrostVfxModel.CUT)));
        }
        if (drewSnow) {
            buffers.endBatch(RenderType.entityTranslucentEmissive(FrostVfxModel.texture(FrostVfxModel.SNOW)));
        }
    }

    private static final class Piece {
        private final ClientLevel level;
        private final Vec3 at;
        private final float yaw;
        private final String rig;
        private final float scale;
        private final int life;
        private final FrostVfx vfx;
        private final FrostVfxRenderer renderer;
        private int age;

        private Piece(ClientLevel level, Vec3 at, float yaw, String rig, String clip, float scale, int life) {
            this.level = level;
            this.at = at;
            this.yaw = yaw;
            this.rig = rig;
            this.scale = scale;
            this.life = life;
            this.vfx = new FrostVfx(clip);
            this.renderer = new FrostVfxRenderer(new FrostVfxModel(rig));
        }
    }
}
