package com.bluup.hexwright.client.weapon;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class PestilentSlashVisualClient {

    private static final String RIG = "pestilent_slash";
    private static final String CLIP = "idle";
    private static final int FRAMES = 6;

    private static final int FRAME_TICKS = 2;
    private static final int LIFE_TICKS = FRAMES * FRAME_TICKS;

    private static final float BLOOM = 0.6f;

    private static final FlipbookPieces PIECES = new FlipbookPieces("Pestilent Slash");

    private PestilentSlashVisualClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PIECES::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(PIECES::render);
    }

    public static void handle(Vec3 at, float yaw) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        PIECES.add(FlipbookPieces.Piece.fixed(level, new FlipbookVfxModel(RIG, FRAMES), at, yaw, LIFE_TICKS, BLOOM,
            age -> Math.min(age / FRAME_TICKS, FRAMES - 1), age -> CLIP));
    }
}
