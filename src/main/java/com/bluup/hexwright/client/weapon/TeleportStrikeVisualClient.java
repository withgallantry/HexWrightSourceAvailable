package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.weapon.TeleportStrike;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class TeleportStrikeVisualClient {

    private static final int TELEPORT_TICKS = 11;
    private static final int EXPLOSION_TICKS = 17;

    private static final int EXPLOSION_HOLD_TICKS = 2;

    private static final String TELEPORT_RIG = "thunder_teleport";
    private static final String EXPLOSION_RIG = "thunder_explosion";
    private static final int FRAMES = 10;

    private static final String CLIP = "strike";

    private static final ResourceLocation[] TELEPORT_TEXTURES = FlipbookVfxModel.textures(TELEPORT_RIG, FRAMES);
    private static final ResourceLocation[] EXPLOSION_TEXTURES = FlipbookVfxModel.textures(EXPLOSION_RIG, FRAMES);

    private static final List<Piece> LIVE = new ArrayList<>();

    private static boolean warned;

    private TeleportStrikeVisualClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(TeleportStrikeVisualClient::onClientTick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(TeleportStrikeVisualClient::render);
    }

    public static void handle(int kind, Vec3 at, float yaw) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        switch (kind) {
            case TeleportStrike.VFX_TELEPORT -> LIVE.add(new Piece(level, at, yaw, TELEPORT_RIG,
                TELEPORT_TICKS, 0));
            case TeleportStrike.VFX_EXPLOSION -> LIVE.add(new Piece(level, at, yaw, EXPLOSION_RIG,
                EXPLOSION_TICKS, EXPLOSION_HOLD_TICKS));
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
        boolean drewTeleport = false;
        boolean drewExplosion = false;

        for (Piece piece : LIVE) {
            if (piece.level != level) {
                continue;
            }
            pose.pushPose();
            try {
                pose.translate(piece.at.x - camera.x, piece.at.y - camera.y, piece.at.z - camera.z);
                pose.mulPose(Axis.YP.rotationDegrees(-piece.yaw));
                piece.vfx.frame = piece.age - piece.hold;
                piece.renderer.render(pose, piece.vfx, buffers, null, null, LightTexture.FULL_BRIGHT);
                if (TELEPORT_RIG.equals(piece.rig)) {
                    drewTeleport = true;
                } else {
                    drewExplosion = true;
                }
            } catch (Exception e) {
                if (!warned) {
                    warned = true;
                    Hexwright.LOGGER.error("Failed to draw a Teleport Strike effect", e);
                }
            } finally {
                pose.popPose();
            }
        }

        if (drewTeleport) {
            for (ResourceLocation texture : TELEPORT_TEXTURES) {
                buffers.endBatch(RenderType.entityTranslucentEmissive(texture));
            }
        }
        if (drewExplosion) {
            for (ResourceLocation texture : EXPLOSION_TEXTURES) {
                buffers.endBatch(RenderType.entityTranslucentEmissive(texture));
            }
        }
    }

    private static final class Piece {
        private final ClientLevel level;
        private final Vec3 at;
        private final float yaw;
        private final String rig;
        private final int life;
        private final int hold;
        private final FlipbookVfx vfx = new FlipbookVfx(CLIP);
        private final FlipbookVfxRenderer renderer;
        private int age;

        private Piece(ClientLevel level, Vec3 at, float yaw, String rig, int life, int hold) {
            this.level = level;
            this.at = at;
            this.yaw = yaw;
            this.rig = rig;
            this.life = life;
            this.hold = hold;
            this.renderer = new FlipbookVfxRenderer(new FlipbookVfxModel(rig, FRAMES));
        }
    }
}
