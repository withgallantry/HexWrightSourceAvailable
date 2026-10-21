package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.weapon.Hailpiercer;
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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class HailpiercerVisualClient {

    private static final String INHALE_RIG = "hailpiercer_inhale";
    private static final String SPIKE_RIG = "hailpiercer";

    private static final int INHALE_TICKS = 28;
    private static final int SPIKE_TICKS = 42;

    private static final float INHALE_BLOOM = 0.8f;
    private static final float SPIKE_BLOOM = 0.35f;

    private static final List<Piece> LIVE = new ArrayList<>();

    private static boolean warned;

    private HailpiercerVisualClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HailpiercerVisualClient::onClientTick);
        WorldRenderEvents.AFTER_ENTITIES.register(HailpiercerVisualClient::render);
    }

    public static void handle(int kind, Vec3 at, float yaw) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        switch (kind) {
            case Hailpiercer.VFX_INHALE -> LIVE.add(new Piece(level, at, yaw, INHALE_RIG, "inhale",
                INHALE_TICKS, INHALE_BLOOM));
            case Hailpiercer.VFX_SPIKE_NEAR -> LIVE.add(new Piece(level, at, yaw, SPIKE_RIG, "ice_spike_o",
                SPIKE_TICKS, SPIKE_BLOOM));
            case Hailpiercer.VFX_SPIKE_SIDE -> LIVE.add(new Piece(level, at, yaw, SPIKE_RIG, "ice_spike",
                SPIKE_TICKS, SPIKE_BLOOM));
            case Hailpiercer.VFX_SPIKE_FAR -> LIVE.add(new Piece(level, at, yaw, SPIKE_RIG, "ice_spike2",
                SPIKE_TICKS, SPIKE_BLOOM));
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

        PoseStack pose = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        float partialTick = context.tickDelta();

        for (Piece piece : LIVE) {
            if (piece.level != level) {
                continue;
            }
            pose.pushPose();
            try {
                pose.translate(piece.at.x - camera.x, piece.at.y - camera.y, piece.at.z - camera.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - piece.yaw));
                piece.renderer.render(pose, piece.vfx, context.consumers(), null, null, LightTexture.FULL_BRIGHT);
                captureBloom(pose, piece, partialTick);
            } catch (Exception e) {
                if (!warned) {
                    warned = true;
                    Hexwright.LOGGER.error("Failed to draw a Hailpiercer effect", e);
                }
            } finally {
                pose.popPose();
            }
        }
    }

    private static void captureBloom(PoseStack pose, Piece piece, float partialTick) {
        GeoModel<FrostVfx> model = piece.renderer.getGeoModel();
        BakedGeoModel baked = model.getBakedModel(model.getModelResource(piece.vfx));
        RenderType layer = RenderType.entityTranslucentEmissive(FrostVfxModel.texture(piece.rig));
        SlashBloom.capture(layer, raw -> {
            VertexConsumer buffer = SlashBloom.dimmed(raw, piece.bloom);
            piece.renderer.reRender(baked, pose, type -> buffer, piece.vfx, layer, buffer, partialTick,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        });
    }

    private static final class Piece {
        private final ClientLevel level;
        private final Vec3 at;
        private final float yaw;
        private final String rig;
        private final int life;
        private final float bloom;
        private final FrostVfx vfx;
        private final FrostVfxRenderer renderer;
        private int age;

        private Piece(ClientLevel level, Vec3 at, float yaw, String rig, String clip, int life, float bloom) {
            this.level = level;
            this.at = at;
            this.yaw = yaw;
            this.rig = rig;
            this.life = life;
            this.bloom = bloom;
            this.vfx = new FrostVfx(clip);
            this.renderer = new FrostVfxRenderer(new FrostVfxModel(rig));
        }
    }
}
