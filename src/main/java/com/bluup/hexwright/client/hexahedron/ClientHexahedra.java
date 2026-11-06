package com.bluup.hexwright.client.hexahedron;

import com.bluup.hexwright.server.hexahedron.Hexahedron;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ClientHexahedra {

    private static final int WITHDRAW_TICKS = 400;
    private static final int PULSE_MIN_TICKS = 100;
    private static final int PULSE_MAX_TICKS = 240;
    private static final float FLARE_TICKS = 12.0f;

    public record Shown(Hexahedron hexahedron, float emergence, float through, float flare) {
    }

    private record Withdrawing(Hexahedron hexahedron, float from, long since) {
    }

    private static final class Pulses {
        long last = Long.MIN_VALUE / 2;
        long next;
    }

    private static final RandomSource RANDOM = RandomSource.create();

    private static List<Hexahedron> standing = List.of();
    private static final List<Withdrawing> withdrawing = new ArrayList<>();
    private static final Map<UUID, Pulses> pulses = new HashMap<>();
    private static ClientLevel boundLevel;
    private static boolean synced;
    private static long ticks;

    private ClientHexahedra() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != boundLevel) {
                boundLevel = client.level;
                standing = List.of();
                withdrawing.clear();
                pulses.clear();
                synced = false;
            }
            ticks++;
            withdrawing.removeIf(gone -> ticks - gone.since() >= WITHDRAW_TICKS);
            if (!client.isPaused() && client.level != null) {
                long now = client.level.getGameTime();
                standing.stream().filter(hexahedron -> !hexahedron.hasBurst(now)).forEach(ClientHexahedra::pulse);
            }
        });
    }

    public static void set(List<Hexahedron> received) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == boundLevel && synced) {
            Set<UUID> kept = received.stream().map(Hexahedron::id).collect(Collectors.toSet());
            for (Hexahedron gone : standing) {
                if (!kept.contains(gone.id()) && !gone.hasBurst(level.getGameTime())) {
                    withdrawing.add(new Withdrawing(gone, gone.through(level.getGameTime()), ticks));
                }
            }
        } else {
            withdrawing.clear();
        }
        boundLevel = level;
        synced = true;
        standing = List.copyOf(received);
        pulses.keySet().retainAll(standing.stream().map(Hexahedron::id).collect(Collectors.toSet()));
    }

    public static List<Shown> shown(ClientLevel level, float partialTick) {
        if (standing.isEmpty() && withdrawing.isEmpty()) {
            return List.of();
        }
        double gameTime = level.getGameTime() + partialTick;
        List<Shown> shown = new ArrayList<>(standing.size() + withdrawing.size());
        for (Hexahedron hexahedron : standing) {
            if (hexahedron.cleared(gameTime) >= 1.0f) {
                continue;
            }
            shown.add(new Shown(hexahedron, hexahedron.emergence(gameTime), hexahedron.through(gameTime),
                flare(hexahedron, partialTick)));
        }
        for (Withdrawing gone : withdrawing) {
            float left = 1.0f - Mth.clamp((ticks - gone.since() + partialTick) / WITHDRAW_TICKS, 0.0f, 1.0f);
            float through = gone.from() * left * left;
            shown.add(new Shown(gone.hexahedron(), Math.min(through, 1.0f), through, 0.0f));
        }
        return shown;
    }

    private static void pulse(Hexahedron hexahedron) {
        Pulses due = pulses.computeIfAbsent(hexahedron.id(), id -> {
            Pulses first = new Pulses();
            first.next = ticks + Mth.nextInt(RANDOM, PULSE_MIN_TICKS, PULSE_MAX_TICKS);
            return first;
        });
        if (ticks < due.next) {
            return;
        }
        due.last = ticks;
        due.next = ticks + Mth.nextInt(RANDOM, PULSE_MIN_TICKS, PULSE_MAX_TICKS);
        HexahedronHum.pulse(hexahedron);
    }

    private static float flare(Hexahedron hexahedron, float partialTick) {
        Pulses due = pulses.get(hexahedron.id());
        return due == null ? 0.0f : (float) Math.exp(-(ticks - due.last + partialTick) / FLARE_TICKS);
    }
}
