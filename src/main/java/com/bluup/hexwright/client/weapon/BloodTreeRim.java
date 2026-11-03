package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.weapon.BloodTreeEntity;
import com.bluup.hexwright.server.weapon.Hailpiercer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class BloodTreeRim {

    private static final ResourceLocation TEXTURE = Hexwright.id("textures/effects/blood_tree_rim.png");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

    private static final int SEGMENTS = 96;
    private static final int TILES_ROUND = 36;
    private static final float HEIGHT = 1.5F;
    private static final float SINK = 0.25F;
    private static final float CLIMB = 0.015F;
    private static final float ALPHA = 0.55F;
    private static final float FLARE = 0.6F;

    private static final double SEEN_FROM = 128.0D;
    private static final int RESURVEY_TICKS = 40;

    private static final Map<BloodTreeEntity, float[]> RIMS = new IdentityHashMap<>();

    private BloodTreeRim() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(BloodTreeRim::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(BloodTreeRim::render);
    }

    private static void tick(Minecraft client) {
        Entity camera = client.getCameraEntity();
        if (client.level == null || camera == null) {
            RIMS.clear();
            return;
        }
        List<BloodTreeEntity> trees = client.level.getEntitiesOfClass(BloodTreeEntity.class,
            new AABB(camera.position(), camera.position()).inflate(SEEN_FROM));
        RIMS.keySet().retainAll(trees);
        for (BloodTreeEntity tree : trees) {
            if (!RIMS.containsKey(tree) || tree.age() % RESURVEY_TICKS == 0) {
                RIMS.put(tree, survey(client.level, tree));
            }
        }
    }

    private static float[] survey(Level level, BloodTreeEntity tree) {
        float[] ground = new float[SEGMENTS];
        for (int n = 0; n < SEGMENTS; n++) {
            float angle = Mth.TWO_PI * n / SEGMENTS;
            Vec3 post = tree.position().add(Mth.cos(angle) * BloodTreeEntity.RADIUS, 0.0D,
                Mth.sin(angle) * BloodTreeEntity.RADIUS);
            ground[n] = (float) Hailpiercer.onSurface(level, post).y;
        }
        return ground;
    }

    private static void render(WorldRenderContext context) {
        if (RIMS.isEmpty() || !(context.consumers() instanceof MultiBufferSource.BufferSource buffers)) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(TYPE);
        PoseStack.Pose pose = context.matrixStack().last();
        Vec3 camera = context.camera().getPosition();
        float partialTick = context.tickDelta();
        for (Map.Entry<BloodTreeEntity, float[]> rim : RIMS.entrySet()) {
            BloodTreeEntity tree = rim.getKey();
            float presence = tree.presence();
            if (tree.level() == context.world() && presence > 0.0F) {
                draw(consumer, pose, camera, tree, rim.getValue(), presence, partialTick);
            }
        }
        buffers.endBatch(TYPE);
    }

    private static void draw(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, BloodTreeEntity tree,
                             float[] ground, float presence, float partialTick) {
        float height = HEIGHT * presence;
        float alpha = ALPHA * presence * (1.0F + FLARE * tree.heartbeat(partialTick));
        float top = (tree.age() + partialTick) * CLIMB;
        float bottom = top + height * TILES_ROUND / (Mth.TWO_PI * (float) BloodTreeEntity.RADIUS);
        float x = (float) (tree.getX() - camera.x);
        float z = (float) (tree.getZ() - camera.z);
        for (int n = 0; n < SEGMENTS; n++) {
            int next = (n + 1) % SEGMENTS;
            float from = Mth.TWO_PI * n / SEGMENTS;
            float to = Mth.TWO_PI * (n + 1) / SEGMENTS;
            float x0 = x + Mth.cos(from) * (float) BloodTreeEntity.RADIUS;
            float z0 = z + Mth.sin(from) * (float) BloodTreeEntity.RADIUS;
            float x1 = x + Mth.cos(to) * (float) BloodTreeEntity.RADIUS;
            float z1 = z + Mth.sin(to) * (float) BloodTreeEntity.RADIUS;
            float y0 = (float) (ground[n] - camera.y) - SINK;
            float y1 = (float) (ground[next] - camera.y) - SINK;
            float u0 = (float) n * TILES_ROUND / SEGMENTS;
            float u1 = (float) (n + 1) * TILES_ROUND / SEGMENTS;
            vertex(consumer, pose, x0, y0, z0, u0, bottom, alpha);
            vertex(consumer, pose, x0, y0 + height, z0, u0, top, 0.0F);
            vertex(consumer, pose, x1, y1 + height, z1, u1, top, 0.0F);
            vertex(consumer, pose, x1, y1, z1, u1, bottom, alpha);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u,
                               float v, float alpha) {
        Matrix4f position = pose.pose();
        Matrix3f normal = pose.normal();
        consumer.vertex(position, x, y, z).color(1.0F, 1.0F, 1.0F, alpha).uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(normal, 0.0F, 1.0F, 0.0F)
            .endVertex();
    }
}
