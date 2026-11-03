package com.bluup.hexwright.client.sky;

import com.bluup.hexwright.server.sky.CellSkies;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientSkies {

    private static final float DISTANCE = 100.0F;
    private static final float[] CUBE = cube();
    private static final long EPOCH = Util.getMillis();

    private static Map<ResourceKey<Level>, List<CellSkies.Region>> regions = Map.of();

    private ClientSkies() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CellSkies.CHANNEL, (client, handler, buf, responseSender) -> {
            ResourceKey<Level> dimension = CellSkies.readDimension(buf);
            List<CellSkies.Region> received = buf.readList(CellSkies.Region::read);
            client.execute(() -> {
                Map<ResourceKey<Level>, List<CellSkies.Region>> updated = new HashMap<>(regions);
                updated.put(dimension, List.copyOf(received));
                regions = Map.copyOf(updated);
            });
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> regions = Map.of());
    }

    public static boolean covers(@Nullable ClientLevel level, double x, double z) {
        return skyAt(level, x, z) != null && SkyLibrary.skyShader() != null && SkyLibrary.backdropShader() != null;
    }

    private static @Nullable SkyLibrary.Sky skyAt(@Nullable ClientLevel level, double x, double z) {
        if (level == null) {
            return null;
        }
        for (CellSkies.Region region : regions.getOrDefault(level.dimension(), List.of())) {
            if (region.covers(x, z)) {
                return SkyLibrary.get(region.sky());
            }
        }
        return null;
    }

    public static boolean render(PoseStack poseStack, @Nullable ClientLevel level, double x, double z) {
        SkyLibrary.Sky sky = skyAt(level, x, z);
        ShaderInstance skyShader = SkyLibrary.skyShader();
        ShaderInstance backdropShader = SkyLibrary.backdropShader();
        if (sky == null || skyShader == null || backdropShader == null) {
            return false;
        }
        long now = Util.getMillis();
        float time = (now - EPOCH) / 1000.0F;
        Matrix4f pose = new Matrix4f(poseStack.last().pose());
        long turnMillis = (long) (sky.turnMinutes() * 60_000.0F);
        if (turnMillis > 0) {
            pose.rotateY((float) (now % turnMillis) / turnMillis * (float) (Math.PI * 2.0));
        }

        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderTarget atlas = SkyAtlas.update(backdropShader, sky, time);
        skyShader.setSampler("AtlasSampler", atlas);
        skyShader.safeGetUniform("SkyPose").set(pose);
        skyShader.safeGetUniform("FaceSize").set((float) SkyAtlas.FACE_SIZE);
        skyShader.safeGetUniform("Time").set(time);
        sky.apply(skyShader);
        RenderSystem.setShader(() -> skyShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (int i = 0; i < CUBE.length; i += 3) {
            builder.vertex(CUBE[i], CUBE[i + 1], CUBE[i + 2]).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        return true;
    }

    private static float[] cube() {
        float[] corners = new float[6 * 4 * 3];
        int i = 0;
        for (int axis = 0; axis < 3; axis++) {
            for (int side = -1; side <= 1; side += 2) {
                for (int[] corner : new int[][] {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
                    corners[i + axis] = side * DISTANCE;
                    corners[i + (axis + 1) % 3] = corner[0] * DISTANCE;
                    corners[i + (axis + 2) % 3] = corner[1] * DISTANCE;
                    i += 3;
                }
            }
        }
        return corners;
    }
}
