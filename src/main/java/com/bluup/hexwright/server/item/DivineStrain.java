package com.bluup.hexwright.server.item;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.effect.HexwrightEffects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DivineStrain {

    public static final int MAX_STRAIN_TICKS = 18000;

    public static final int RECOVERY_RATE = 3;

    public static final int MAX_CLEAR_TICKS = MAX_STRAIN_TICKS / RECOVERY_RATE;

    public static final int LEVEL_TICKS = 3600;

    private static final int SWEEP_INTERVAL_TICKS = 20;

    private static final Map<UUID, Integer> STRAIN = new HashMap<>();

    private DivineStrain() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DivineStrain::sweep);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            server.execute(() -> STRAIN.remove(handler.player.getUUID())));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STRAIN.clear());
    }

    public static int clearTicks(int strainTicks) {
        return (strainTicks + RECOVERY_RATE - 1) / RECOVERY_RATE;
    }

    public static int amplifier(int strainTicks) {
        return Math.min(MAX_STRAIN_TICKS / LEVEL_TICKS - 1, Math.max(0, strainTicks - 1) / LEVEL_TICKS);
    }

    private static void sweep(MinecraftServer server) {
        if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tick(player);
        }
    }

    private static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        MobEffectInstance shown = player.getEffect(HexwrightEffects.DIVINE_STRAIN);
        boolean straining = player.isAlive() && !player.isCreative() && !player.isSpectator()
            && DivinityItem.holdsOwn(player);
        Integer kept = STRAIN.get(id);
        if (kept == null && shown == null && !straining) {
            return;
        }

        int before = kept != null ? kept : shown != null ? shown.getDuration() * RECOVERY_RATE : 0;
        int strain = straining
            ? Math.min(MAX_STRAIN_TICKS, before + SWEEP_INTERVAL_TICKS)
            : Math.max(0, before - SWEEP_INTERVAL_TICKS * RECOVERY_RATE);

        if (strain == 0) {
            STRAIN.remove(id);
            if (shown != null) {
                player.removeEffect(HexwrightEffects.DIVINE_STRAIN);
            }
            return;
        }
        STRAIN.put(id, strain);

        int amplifier = amplifier(strain);
        if (amplifier != amplifier(before)) {
            HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] Divine Strain on {}: level {} ({} ticks)",
                player.getScoreboardName(), amplifier + 1, strain);
        }
        if (straining || shown == null || shown.getAmplifier() != amplifier) {
            player.forceAddEffect(new MobEffectInstance(HexwrightEffects.DIVINE_STRAIN, clearTicks(strain), amplifier,
                false, false, true), null);
        }
    }
}
