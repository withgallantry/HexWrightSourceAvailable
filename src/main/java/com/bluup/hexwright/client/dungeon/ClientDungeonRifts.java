package com.bluup.hexwright.client.dungeon;

import com.bluup.hexwright.client.portal.VoidTearRenderer;
import com.bluup.hexwright.server.dungeon.DungeonRift;
import com.bluup.hexwright.server.dungeon.DungeonRifts;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ClientDungeonRifts {

    private static final float FORMING = 0.26F;
    private static final float FORMING_FLICKER = 0.05F;
    private static final float WIDEST = 0.92F;
    private static final float NARROWEST = 0.5F;
    private static final float EXIT = 0.66F;
    private static final float EXIT_BREATH = 0.04F;

    private static final double OPEN_TICKS = 20.0;
    private static final double CLOSE_TICKS = 60.0;

    private static List<DungeonRift> rifts = List.of();
    private static ClientLevel boundLevel;

    private ClientDungeonRifts() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DungeonRifts.CHANNEL, (client, handler, buf, responseSender) -> {
            List<DungeonRift> received = buf.readList(DungeonRift::read);
            client.execute(() -> set(received));
        });
        VoidTearRenderer.addSource(ClientDungeonRifts::collect);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != boundLevel) {
                boundLevel = client.level;
                rifts = List.of();
            }
        });
    }

    private static void set(List<DungeonRift> received) {
        boundLevel = Minecraft.getInstance().level;
        rifts = List.copyOf(received);
    }

    private static void collect(Vec3 cameraPos, List<VoidTearRenderer.Rift> into) {
        ClientLevel level = boundLevel;
        if (level == null || rifts.isEmpty()) {
            return;
        }
        double time = level.getGameTime() + Minecraft.getInstance().getFrameTime();
        for (DungeonRift rift : rifts) {
            if (rift.center().distanceToSqr(cameraPos) > VoidTearRenderer.MAX_DRAW_DISTANCE_SQ) {
                continue;
            }
            float progress = progress(rift, time);
            if (progress > 0.0F) {
                into.add(new VoidTearRenderer.Rift(rift.center(), rift.u(), rift.v(), shaderSeed(rift.seed()), progress));
            }
        }
    }

    private static float progress(DungeonRift rift, double time) {
        return switch (rift.kind()) {
            case FORMING -> FORMING + FORMING_FLICKER * (float) Math.sin(time * 0.35);
            case EXIT -> EXIT + EXIT_BREATH * (float) Math.sin(time * 0.06);
            case ENTRANCE -> {
                double span = Math.max(1.0, rift.closesAt() - rift.opensAt());
                float held = Mth.lerp((float) Mth.clamp((rift.closesAt() - time) / span, 0.0, 1.0), NARROWEST, WIDEST);
                float opened = Mth.lerp((float) Mth.clamp((time - rift.opensAt()) / OPEN_TICKS, 0.0, 1.0), FORMING, held);
                yield opened * (float) Mth.clamp((rift.closesAt() - time) / CLOSE_TICKS, 0.0, 1.0);
            }
        };
    }

    private static float shaderSeed(long seed) {
        return (float) ((seed >>> 16 & 0xFFFFL) / 65535.0 * 64.0);
    }
}
