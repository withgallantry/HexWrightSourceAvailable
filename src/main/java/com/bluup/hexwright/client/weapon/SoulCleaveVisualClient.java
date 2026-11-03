package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.server.weapon.SoulCleave;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class SoulCleaveVisualClient {

    private static final String RIG = "soul_cleave";
    private static final int FRAMES = 5;
    private static final int LIFE_TICKS = 10;
    private static final float BLOOM = 0.55f;

    private static final FlipbookPieces PIECES = new FlipbookPieces("Soul Cleave");

    private SoulCleaveVisualClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PIECES::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(PIECES::render);
    }

    public static void handle(int kind, Vec3 at, float yaw) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        String clip = switch (kind) {
            case SoulCleave.VFX_SLASH_RIGHT -> "slash_right";
            case SoulCleave.VFX_SLASH_LEFT -> "slash_left";
            case SoulCleave.VFX_PIERCE -> "pierce";
            default -> null;
        };
        if (clip != null) {
            PIECES.add(FlipbookPieces.Piece.fixed(level, new FlipbookVfxModel(RIG, FRAMES), at, yaw, LIFE_TICKS,
                BLOOM, age -> Mth.clamp(age - 1, 0, FRAMES - 1), age -> clip));
        }
    }
}
