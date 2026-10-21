package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.boss.Bosses;
import com.bluup.hexwright.server.dust.DustManifestations;
import com.bluup.hexwright.server.effect.HexwrightEffects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Silence {

    public static final int SILENCE_TICKS = 500;

    private static final int SWEEP_INTERVAL_TICKS = 20;

    private static final Map<UUID, Long> SILENCED = new HashMap<>();

    private static final Map<UUID, Spurn> SPURNED = new HashMap<>();

    private record Spurn(UUID wielder, long until) {
    }

    private Silence() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Silence::sweep);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SILENCED.clear();
            SPURNED.clear();
        });
    }

    public static boolean strike(LivingEntity victim, ServerPlayer wielder) {
        long now = wielder.server.overworld().getGameTime();
        if (victim instanceof ServerPlayer player) {
            int ticks = PestiferousItem.afflictedTicks(player, SILENCE_TICKS);
            SILENCED.put(player.getUUID(), now + ticks);
            HexwrightEffects.restart(player, HexwrightEffects.SILENCED, ticks);
            DustManifestations.release(player);
            player.displayClientMessage(Component.translatable("message.hexwright.silenced",
                wielder.getDisplayName(), ticks / 20), true);
            HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] {} silenced by {}",
                player.getScoreboardName(), wielder.getScoreboardName());
            return true;
        } else if (victim instanceof Mob mob && !Bosses.is(mob)) {
            SPURNED.put(mob.getUUID(), new Spurn(wielder.getUUID(), now + SILENCE_TICKS));
            if (mob.getTarget() == wielder) {
                mob.setTarget(null);
            }
            Brain<?> brain = mob.getBrain();
            if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == wielder) {
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
            return true;
        }
        return false;
    }

    public static boolean isSilenced(@Nullable Entity entity) {
        if (entity == null || SILENCED.isEmpty() || entity.getServer() == null) {
            return false;
        }
        Long until = SILENCED.get(entity.getUUID());
        return until != null && until > entity.getServer().overworld().getGameTime();
    }

    public static boolean spurns(LivingEntity mob, LivingEntity target) {
        if (SPURNED.isEmpty() || mob.getServer() == null) {
            return false;
        }
        Spurn spurn = SPURNED.get(mob.getUUID());
        return spurn != null && spurn.wielder.equals(target.getUUID())
            && spurn.until > mob.getServer().overworld().getGameTime();
    }

    private static void sweep(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % SWEEP_INTERVAL_TICKS != 0L) {
            return;
        }
        SILENCED.values().removeIf(until -> until <= now);
        SPURNED.values().removeIf(spurn -> spurn.until <= now);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Long until = SILENCED.get(player.getUUID());
            HexwrightEffects.sync(player, HexwrightEffects.SILENCED, until != null,
                until == null ? 0 : (int) (until - now));
        }
    }
}
