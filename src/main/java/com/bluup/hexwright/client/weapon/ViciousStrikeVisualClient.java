package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.weapon.ViciousStrike;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class ViciousStrikeVisualClient {

    private static final double CHARGE_HEIGHT = 2.4D;

    private static final List<Piece> LIVE = new ArrayList<>();

    private static boolean warned;

    private ViciousStrikeVisualClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ViciousStrikeVisualClient::onClientTick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ViciousStrikeVisualClient::render);
    }

    public static void handle(int kind, Vec3 at, float yaw, int wielderId) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        switch (kind) {
            case ViciousStrike.VFX_CHARGE -> LIVE.add(new Piece(level, Rig.CHARGE, at, yaw, wielderId));
            case ViciousStrike.VFX_RUPTURE -> LIVE.add(new Piece(level, Rig.RUPTURE, at, yaw, -1));
            case ViciousStrike.VFX_SLASH -> LIVE.add(new Piece(level, Rig.SLASH, at, yaw, -1));
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
            if (piece.level != level || ++piece.age >= piece.rig.life) {
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
        float partialTick = context.tickDelta();
        EnumSet<Rig> drawn = EnumSet.noneOf(Rig.class);

        for (Piece piece : LIVE) {
            if (piece.level != level) {
                continue;
            }
            Vec3 at = piece.at;
            float yaw = piece.yaw;
            if (piece.wielderId >= 0) {
                Entity wielder = level.getEntity(piece.wielderId);
                if (wielder == null) {
                    continue;
                }
                at = wielder.getPosition(partialTick).add(0.0D, CHARGE_HEIGHT, 0.0D);
                yaw = Mth.rotLerp(partialTick, wielder.yRotO, wielder.getYRot());
            }
            pose.pushPose();
            try {
                pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));
                piece.vfx.frame = piece.age - piece.rig.hold;
                piece.renderer.render(pose, piece.vfx, buffers, null, null, LightTexture.FULL_BRIGHT);
                drawn.add(piece.rig);
            } catch (Exception e) {
                if (!warned) {
                    warned = true;
                    Hexwright.LOGGER.error("Failed to draw a Vicious Strike effect", e);
                }
            } finally {
                pose.popPose();
            }
        }

        for (Rig rig : drawn) {
            for (ResourceLocation texture : rig.textures) {
                buffers.endBatch(RenderType.entityTranslucentEmissive(texture));
            }
        }
    }

    private enum Rig {
        CHARGE("vicious_strike_charge", 1, "charge", ViciousStrike.CHARGE_TICKS, 0),
        RUPTURE("vicious_strike", 13, "rupture", 40, 0),
        SLASH("vicious_strike_slash", 8, "slash_up", 10, 1);

        private final String id;
        private final int frames;
        private final String clip;
        private final int life;
        private final int hold;
        private final ResourceLocation[] textures;

        Rig(String id, int frames, String clip, int life, int hold) {
            this.id = id;
            this.frames = frames;
            this.clip = clip;
            this.life = life;
            this.hold = hold;
            this.textures = FlipbookVfxModel.textures(id, frames);
        }
    }

    private static final class Piece {
        private final ClientLevel level;
        private final Rig rig;
        private final Vec3 at;
        private final float yaw;
        private final int wielderId;
        private final FlipbookVfx vfx;
        private final FlipbookVfxRenderer renderer;
        private int age;

        private Piece(ClientLevel level, Rig rig, Vec3 at, float yaw, int wielderId) {
            this.level = level;
            this.rig = rig;
            this.at = at;
            this.yaw = yaw;
            this.wielderId = wielderId;
            this.vfx = new FlipbookVfx(rig.clip);
            this.renderer = new FlipbookVfxRenderer(new FlipbookVfxModel(rig.id, rig.frames));
        }
    }
}
