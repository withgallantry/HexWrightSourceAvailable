package com.bluup.hexwright.client.cloak;

import com.bluup.hexwright.server.cloak.CloakField;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CloakFieldRipples {

    public static final int MAX_PER_FIELD = 4;
    private static final long LIFETIME_NANOS = 1_400_000_000L;

    public record Ripple(Vec3 point, long startNanos) {
    }

    private static final Map<UUID, ArrayDeque<Ripple>> RIPPLES = new HashMap<>();

    private CloakFieldRipples() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                RIPPLES.clear();
                return;
            }
            long now = System.nanoTime();
            RIPPLES.values().forEach(ripples -> ripples.removeIf(r -> now - r.startNanos() >= LIFETIME_NANOS));
            RIPPLES.values().removeIf(ArrayDeque::isEmpty);
        });
    }

    public static void add(UUID fieldId, Vec3 point) {
        ArrayDeque<Ripple> ripples = RIPPLES.computeIfAbsent(fieldId, id -> new ArrayDeque<>());
        if (ripples.size() >= MAX_PER_FIELD) {
            ripples.pollFirst();
        }
        ripples.addLast(new Ripple(point, System.nanoTime()));
    }

    public static float age(Ripple ripple, long now) {
        return (float) (now - ripple.startNanos()) / LIFETIME_NANOS;
    }

    public static Iterable<Ripple> of(CloakField field) {
        ArrayDeque<Ripple> ripples = RIPPLES.get(field.id());
        return ripples == null ? List.of() : ripples;
    }
}
