package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.server.weapon.MeteorOfDoom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class MeteorOfDoomVisualClient {

    private static final String METEOR = "meteor_of_doom";
    private static final String CROSS = "meteor_of_doom_cross";
    private static final String BURST = "meteor_explosion";

    private static final int FLAME_FRAMES = 6;
    private static final int BURST_FRAMES = 8;

    private static final double CHARGE_HEIGHT = 1.5D;

    private static final int CROSS_TICKS = 97;
    private static final int BURST_TICKS = 10;

    private static final float METEOR_BLOOM = 0.5f;
    private static final float CROSS_BLOOM = 0.3f;
    private static final float BURST_BLOOM = 0.6f;

    private static final FlipbookPieces PIECES = new FlipbookPieces("Meteor of Doom");

    private MeteorOfDoomVisualClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PIECES::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(PIECES::render);
    }

    public static void handle(int kind, Vec3 at, float yaw, int wielderId, Vec3 to, int ticks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        switch (kind) {
            case MeteorOfDoom.VFX_CHARGE -> PIECES.add(FlipbookPieces.Piece.riding(level, flames(METEOR), wielderId,
                CHARGE_HEIGHT, yaw, true, ticks, METEOR_BLOOM, MeteorOfDoomVisualClient::flame, age -> "charge"));
            case MeteorOfDoom.VFX_FLIGHT -> PIECES.add(FlipbookPieces.Piece.flying(level, flames(METEOR), at, to, yaw,
                Math.max(1, ticks), METEOR_BLOOM, MeteorOfDoomVisualClient::flame, age -> "flight"));
            case MeteorOfDoom.VFX_CROSS -> PIECES.add(FlipbookPieces.Piece.fixed(level, flames(CROSS), at, yaw,
                CROSS_TICKS, CROSS_BLOOM, MeteorOfDoomVisualClient::flame, age -> "impact"));
            case MeteorOfDoom.VFX_BURST -> {
                String clip = ticks == 0 ? "burst" : "burst_alt";
                PIECES.add(FlipbookPieces.Piece.fixed(level, new FlipbookVfxModel(BURST, BURST_FRAMES), at, yaw,
                    BURST_TICKS, BURST_BLOOM, age -> Math.min(age, BURST_FRAMES - 1), age -> clip));
            }
            default -> {
            }
        }
    }

    private static FlipbookVfxModel flames(String rig) {
        return new FlipbookVfxModel(rig, METEOR, FLAME_FRAMES);
    }

    private static int flame(int age) {
        return age % FLAME_FRAMES;
    }
}
