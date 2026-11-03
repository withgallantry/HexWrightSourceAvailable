package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.server.weapon.BloodTreeEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BloodTreeSky {

    private static final double FADE = 12.0D;

    private static final Vec3 SKY = new Vec3(0.09D, 0.01D, 0.025D);
    private static final Vec3 FOG = new Vec3(0.12D, 0.015D, 0.03D);
    private static final Vec3 CLOUDS = new Vec3(0.2D, 0.04D, 0.06D);
    private static final float NIGHT = 0.2F;

    private static float previous;
    private static float current;

    private BloodTreeSky() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(BloodTreeSky::tick);
    }

    private static void tick(Minecraft client) {
        previous = current;
        current = 0.0F;
        Entity camera = client.getCameraEntity();
        if (client.level == null || camera == null) {
            previous = 0.0F;
            return;
        }
        Vec3 eye = camera.getEyePosition();
        double reach = BloodTreeEntity.RADIUS + FADE;
        for (BloodTreeEntity tree : client.level.getEntitiesOfClass(BloodTreeEntity.class,
                new AABB(eye, eye).inflate(reach))) {
            float near = 1.0F - (float) Mth.clamp(tree.distanceOutside(eye) / FADE, 0.0D, 1.0D);
            current = Math.max(current, near * tree.presence());
        }
    }

    public static float darkness(float partialTick) {
        return Mth.lerp(partialTick, previous, current);
    }

    public static Vec3 sky(Vec3 colour, float darkness) {
        return colour.lerp(SKY, darkness);
    }

    public static Vec3 clouds(Vec3 colour, float darkness) {
        return colour.lerp(CLOUDS, darkness);
    }

    public static Vec3 fog(Vec3 colour, float darkness) {
        return colour.lerp(FOG, darkness);
    }

    public static float skyDarken(float skyDarken, float darkness) {
        return Math.min(skyDarken, Mth.lerp(darkness, skyDarken, NIGHT));
    }
}
