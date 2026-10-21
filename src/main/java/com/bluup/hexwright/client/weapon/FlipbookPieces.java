package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

@Environment(EnvType.CLIENT)
final class FlipbookPieces {

    private final String ability;
    private final List<Piece> live = new ArrayList<>();
    private boolean warned;

    FlipbookPieces(String ability) {
        this.ability = ability;
    }

    void add(Piece piece) {
        this.live.add(piece);
    }

    void tick(Minecraft mc) {
        if (this.live.isEmpty()) {
            return;
        }
        ClientLevel level = mc.level;
        Iterator<Piece> pieces = this.live.iterator();
        while (pieces.hasNext()) {
            Piece piece = pieces.next();
            if (piece.level != level || ++piece.age >= piece.life) {
                pieces.remove();
            }
        }
    }

    void render(WorldRenderContext context) {
        if (this.live.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            this.live.clear();
            return;
        }
        PoseStack pose = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        float partialTick = context.tickDelta();

        for (Piece piece : this.live) {
            if (piece.level != level) {
                continue;
            }
            Vec3 at = piece.position(level, partialTick);
            if (at == null) {
                continue;
            }
            pose.pushPose();
            try {
                pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - piece.yaw(level, partialTick)));
                piece.vfx.frame = piece.frameOf.applyAsInt(piece.age);
                piece.vfx.play(piece.clipOf.apply(piece.age));
                piece.renderer.render(pose, piece.vfx, context.consumers(), null, null, LightTexture.FULL_BRIGHT);
                captureBloom(pose, piece, partialTick);
            } catch (Exception e) {
                if (!this.warned) {
                    this.warned = true;
                    Hexwright.LOGGER.error("Failed to draw a {} effect", this.ability, e);
                }
            } finally {
                pose.popPose();
            }
        }
    }

    private static void captureBloom(PoseStack pose, Piece piece, float partialTick) {
        if (piece.bloom <= 0.0f) {
            return;
        }
        GeoModel<FlipbookVfx> model = piece.renderer.getGeoModel();
        BakedGeoModel baked = model.getBakedModel(model.getModelResource(piece.vfx));
        RenderType layer = RenderType.entityTranslucentEmissive(model.getTextureResource(piece.vfx));
        SlashBloom.capture(layer, raw -> {
            VertexConsumer buffer = SlashBloom.dimmed(raw, piece.bloom);
            piece.renderer.reRender(baked, pose, type -> buffer, piece.vfx, layer, buffer, partialTick,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        });
    }

    static final class Piece {
        private final ClientLevel level;
        private final Vec3 at;
        @Nullable
        private final Vec3 to;
        private final float yaw;
        private final int wielderId;
        private final double height;
        private final boolean turns;
        private final int life;
        private final float bloom;
        private final IntUnaryOperator frameOf;
        private final IntFunction<String> clipOf;
        private final FlipbookVfx vfx;
        private final FlipbookVfxRenderer renderer;
        private int age;

        private Piece(ClientLevel level, FlipbookVfxModel model, Vec3 at, @Nullable Vec3 to, float yaw,
                      int wielderId, double height, boolean turns, int life, float bloom,
                      IntUnaryOperator frameOf, IntFunction<String> clipOf) {
            this.level = level;
            this.at = at;
            this.to = to;
            this.yaw = yaw;
            this.wielderId = wielderId;
            this.height = height;
            this.turns = turns;
            this.life = life;
            this.bloom = bloom;
            this.frameOf = frameOf;
            this.clipOf = clipOf;
            this.vfx = new FlipbookVfx(clipOf.apply(0));
            this.renderer = new FlipbookVfxRenderer(model);
        }

        static Piece fixed(ClientLevel level, FlipbookVfxModel model, Vec3 at, float yaw, int life, float bloom,
                           IntUnaryOperator frameOf, IntFunction<String> clipOf) {
            return new Piece(level, model, at, null, yaw, -1, 0.0D, false, life, bloom, frameOf, clipOf);
        }

        static Piece riding(ClientLevel level, FlipbookVfxModel model, int wielderId, double height, float yaw,
                            boolean turns, int life, float bloom, IntUnaryOperator frameOf,
                            IntFunction<String> clipOf) {
            return new Piece(level, model, Vec3.ZERO, null, yaw, wielderId, height, turns, life, bloom,
                frameOf, clipOf);
        }

        static Piece flying(ClientLevel level, FlipbookVfxModel model, Vec3 from, Vec3 to, float yaw, int life,
                            float bloom, IntUnaryOperator frameOf, IntFunction<String> clipOf) {
            return new Piece(level, model, from, to, yaw, -1, 0.0D, false, life, bloom, frameOf, clipOf);
        }

        @Nullable
        private Vec3 position(ClientLevel level, float partialTick) {
            if (this.wielderId >= 0) {
                Entity wielder = level.getEntity(this.wielderId);
                return wielder == null ? null : wielder.getPosition(partialTick).add(0.0D, this.height, 0.0D);
            }
            if (this.to != null) {
                double progress = Mth.clamp((this.age + partialTick) / this.life, 0.0D, 1.0D);
                return this.at.lerp(this.to, progress);
            }
            return this.at;
        }

        private float yaw(ClientLevel level, float partialTick) {
            if (this.turns && this.wielderId >= 0) {
                Entity wielder = level.getEntity(this.wielderId);
                if (wielder != null) {
                    return Mth.rotLerp(partialTick, wielder.yRotO, wielder.getYRot());
                }
            }
            return this.yaw;
        }
    }
}
