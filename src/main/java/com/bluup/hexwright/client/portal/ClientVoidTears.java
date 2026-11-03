package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.server.portal.VoidTear;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ClientVoidTears {

    private static final double NANOS_PER_TICK = 50_000_000.0;

    public record Entry(VoidTear tear, long receivedNanos) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private static ClientLevel boundLevel;

    private ClientVoidTears() {
    }

    public static void register() {
        VoidTearRenderer.addSource(ClientVoidTears::collect);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != boundLevel) {
                boundLevel = client.level;
                ENTRIES.clear();
            }
        });
    }

    private static void collect(Vec3 cameraPos, List<VoidTearRenderer.Rift> into) {
        for (Entry entry : entries()) {
            VoidTear tear = entry.tear();
            if (tear.center().distanceToSqr(cameraPos) > VoidTearRenderer.MAX_DRAW_DISTANCE_SQ) {
                continue;
            }
            float progress = openProgress(entry);
            if (progress <= 0.0f) {
                continue;
            }
            into.add(new VoidTearRenderer.Rift(
                tear.center(), tear.u(), tear.v(), seedOf(tear), progress));
        }
    }

    private static float seedOf(VoidTear tear) {
        return (float) ((tear.seed() >>> 16 & 0xFFFFL) / 65535.0 * 64.0);
    }

    public static void add(VoidTear tear) {
        if (!tear.isValid()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level != boundLevel) {
            boundLevel = level;
            ENTRIES.clear();
        }
        ENTRIES.add(new Entry(tear, System.nanoTime()));
    }

    public static List<Entry> entries() {
        if (!ENTRIES.isEmpty()) {
            long now = System.nanoTime();
            ENTRIES.removeIf(entry -> elapsedTicks(entry, now) >= VoidTear.LIFETIME_TICKS);
        }
        return ENTRIES;
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public static float openProgress(Entry entry) {
        double elapsed = elapsedTicks(entry, System.nanoTime());
        if (elapsed <= 0.0) {
            return 0.0f;
        }
        if (elapsed < VoidTear.OPEN_TICKS) {
            return (float) Mth.clamp(elapsed / VoidTear.OPEN_TICKS, 0.0, 1.0);
        }
        double sealStart = VoidTear.LIFETIME_TICKS - VoidTear.SEAL_TICKS;
        if (elapsed >= sealStart) {
            return (float) Mth.clamp((VoidTear.LIFETIME_TICKS - elapsed) / VoidTear.SEAL_TICKS, 0.0, 1.0);
        }
        return 1.0f;
    }

    private static double elapsedTicks(Entry entry, long now) {
        return (now - entry.receivedNanos()) / NANOS_PER_TICK;
    }
}
