package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.server.weapon.Stillness;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class StillnessVisualClient {

    private static final String SPEAR = "stillness_spear";
    private static final String IMPACT = "stillness_impact";
    private static final String CLIP = "idle";

    private static final int SPEAR_FRAMES = 4;
    private static final int IMPACT_FRAMES = 5;

    private static final int SPEAR_FRAME_TICKS = 2;
    private static final int IMPACT_FRAME_TICKS = 3;
    private static final int IMPACT_TICKS = 18;
    private static final int SPEAR_FADE_TICKS = 8;

    private static final double IMPACT_LIFT = 0.15D;

    private static final float SPEAR_BLOOM = 0.5f;
    private static final float IMPACT_BLOOM = 0.6f;

    private static final DustParticleOptions ZONE_DUST =
        new DustParticleOptions(new Vector3f(0x4D / 255.0f, 0x1A / 255.0f, 0xE8 / 255.0f), 1.0f);
    private static final int ZONE_RING_TICKS = 5;
    private static final double ZONE_MOTES_PER_BLOCK = 4.0D;
    private static final double ZONE_RING_LIFT = 0.1D;

    private static final FlipbookPieces PIECES = new FlipbookPieces("Stillness");
    private static final List<Zone> ZONES = new ArrayList<>();

    private StillnessVisualClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PIECES::tick);
        ClientTickEvents.END_CLIENT_TICK.register(StillnessVisualClient::tickZones);
        WorldRenderEvents.AFTER_ENTITIES.register(PIECES::render);
    }

    public static void handle(int kind, Vec3 at, float yaw) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 above = at.add(0.0D, Stillness.SPEAR_HEIGHT, 0.0D);
        switch (kind) {
            case Stillness.VFX_SPEAR_GATHER -> PIECES.add(FlipbookPieces.Piece.fixed(level,
                new FlipbookVfxModel(SPEAR, SPEAR_FRAMES), above, yaw, Stillness.FALL_TICKS, SPEAR_BLOOM,
                age -> Mth.clamp(age / SPEAR_FRAME_TICKS - 1, 0, SPEAR_FRAMES - 1), age -> CLIP));
            case Stillness.VFX_SPEAR_FALL -> PIECES.add(FlipbookPieces.Piece.flying(level,
                new FlipbookVfxModel(SPEAR, SPEAR_FRAMES), above, at, yaw,
                Stillness.LAND_TICKS - Stillness.FALL_TICKS, SPEAR_BLOOM, age -> SPEAR_FRAMES - 1, age -> CLIP));
            case Stillness.VFX_SPEAR_PLANTED -> PIECES.add(FlipbookPieces.Piece.fixed(level,
                new FlipbookVfxModel(SPEAR, SPEAR_FRAMES), at, yaw, Stillness.ZONE_TICKS, SPEAR_BLOOM,
                StillnessVisualClient::plantedSheet, age -> CLIP));
            case Stillness.VFX_IMPACT -> PIECES.add(FlipbookPieces.Piece.fixed(level,
                new FlipbookVfxModel(IMPACT, IMPACT_FRAMES), at.add(0.0D, IMPACT_LIFT, 0.0D), yaw, IMPACT_TICKS,
                IMPACT_BLOOM, age -> Math.min(age / IMPACT_FRAME_TICKS, IMPACT_FRAMES - 1), age -> CLIP));
            case Stillness.VFX_ZONE -> ZONES.add(new Zone(level, at));
            default -> {
            }
        }
    }

    private static int plantedSheet(int age) {
        int left = Stillness.ZONE_TICKS - age;
        return Mth.clamp(left * SPEAR_FRAMES / SPEAR_FADE_TICKS, 0, SPEAR_FRAMES - 1);
    }

    private static void tickZones(Minecraft mc) {
        if (ZONES.isEmpty()) {
            return;
        }
        Iterator<Zone> zones = ZONES.iterator();
        while (zones.hasNext()) {
            Zone zone = zones.next();
            if (zone.level != mc.level || zone.age >= Stillness.ZONE_TICKS) {
                zones.remove();
                continue;
            }
            if (zone.age % ZONE_RING_TICKS == 0) {
                ring(zone);
            }
            zone.age++;
        }
    }

    private static void ring(Zone zone) {
        int motes = (int) Math.ceil(Mth.TWO_PI * Stillness.RADIUS * ZONE_MOTES_PER_BLOCK);
        double y = zone.centre.y + ZONE_RING_LIFT;
        for (int i = 0; i < motes; i++) {
            double angle = Mth.TWO_PI * i / motes;
            zone.level.addParticle(ZONE_DUST, zone.centre.x + Math.cos(angle) * Stillness.RADIUS, y,
                zone.centre.z + Math.sin(angle) * Stillness.RADIUS, 0.0D, 0.0D, 0.0D);
        }
    }

    private static final class Zone {
        private final ClientLevel level;
        private final Vec3 centre;
        private int age;

        private Zone(ClientLevel level, Vec3 centre) {
            this.level = level;
            this.centre = centre;
        }
    }
}
