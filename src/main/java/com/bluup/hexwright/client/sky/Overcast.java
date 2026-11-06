package com.bluup.hexwright.client.sky;

import com.bluup.hexwright.client.hexahedron.ClientHexahedra;
import com.bluup.hexwright.client.inversion.ClientInversions;
import com.bluup.hexwright.server.hexahedron.Hexahedron;
import com.bluup.hexwright.server.inversion.Inversion;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class Overcast {

    private static final double FADE = 600.0;
    private static final float GREYING = 0.85f;
    private static final float CLOUD_GREYING = 0.7f;
    private static final float DIMMING = 0.2f;
    private static final float VEIL = 0.7f;
    private static final float GLOOM_VEIL = 1.0f;
    private static final float GLOOMING = 0.92f;
    private static final float GLOOM_DARK = 0.14f;
    private static final float GLOOM_DIMMING = 0.55f;
    private static final double GLOOM_FRONT = 80.0;
    private static final Vec3 GREY = new Vec3(0.9, 0.93, 1.0);
    private static final Vec3 GLOOM = new Vec3(0.85, 0.78, 1.0);

    private static float greyBefore;
    private static float grey;
    private static float gloomBefore;
    private static float gloom;

    private Overcast() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Overcast::tick);
    }

    private static void tick(Minecraft client) {
        greyBefore = grey;
        gloomBefore = gloom;
        grey = 0.0f;
        gloom = 0.0f;
        Entity camera = client.getCameraEntity();
        ClientLevel level = client.level;
        if (level == null || camera == null) {
            greyBefore = 0.0f;
            gloomBefore = 0.0f;
            return;
        }
        Vec3 eye = camera.getEyePosition();
        double now = level.getGameTime();
        for (ClientHexahedra.Shown shown : ClientHexahedra.shown(level, 0.0f)) {
            Hexahedron hexahedron = shown.hexahedron();
            float near = near(Math.hypot(eye.x - hexahedron.x(), eye.z - hexahedron.z()) - hexahedron.radius());
            grey = Math.max(grey, near * shown.emergence() * (1.0f - hexahedron.cleared(now)));
        }
        for (Inversion inversion : ClientInversions.in(level)) {
            float near = near(Math.hypot(eye.x - inversion.x(), eye.z - inversion.z()) - Inversion.HALF);
            gloom = Math.max(gloom, near * (float) Mth.clamp(inversion.front(now) / GLOOM_FRONT, 0.0, 1.0));
        }
    }

    private static float near(double outside) {
        return 1.0f - (float) Mth.clamp(outside / FADE, 0.0, 1.0);
    }

    public static boolean any(float partialTick) {
        return grey(partialTick) > 0.0f || gloom(partialTick) > 0.0f;
    }

    private static float grey(float partialTick) {
        return Mth.lerp(partialTick, greyBefore, grey);
    }

    private static float gloom(float partialTick) {
        return Mth.lerp(partialTick, gloomBefore, gloom);
    }

    public static Vec3 sky(Vec3 colour, float partialTick) {
        return shade(colour, partialTick, GREYING);
    }

    public static Vec3 fog(Vec3 colour, float partialTick) {
        return shade(colour, partialTick, GREYING);
    }

    public static Vec3 clouds(Vec3 colour, float partialTick) {
        return shade(colour, partialTick, CLOUD_GREYING);
    }

    public static float skyDarken(float skyDarken, float partialTick) {
        return skyDarken * (1.0f - DIMMING * grey(partialTick)) * (1.0f - GLOOM_DIMMING * gloom(partialTick));
    }

    public static float veil(float partialTick) {
        return Math.max(grey(partialTick) * VEIL, gloom(partialTick) * GLOOM_VEIL);
    }

    private static Vec3 shade(Vec3 colour, float partialTick, float greying) {
        double brightness = colour.x * 0.3 + colour.y * 0.59 + colour.z * 0.11;
        Vec3 greyed = colour.lerp(GREY.scale(brightness), grey(partialTick) * greying);
        return greyed.lerp(GLOOM.scale(brightness * GLOOM_DARK), gloom(partialTick) * GLOOMING);
    }
}
