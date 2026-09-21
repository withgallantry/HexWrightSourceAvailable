package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.HexwrightDebug;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VaultContainment {

    private static final Map<UUID, Integer> CLAIMS = new HashMap<>();

    private static final Map<UUID, Integer> LAST_LOGGED = new HashMap<>();

    private static final int LOG_COOLDOWN_TICKS = 100;

    private VaultContainment() {
    }


    static void tick(MinecraftServer server) {
        ServerLevel vaultLevel = VaultDimension.level(server);
        if (vaultLevel == null || vaultLevel.players().isEmpty()) {
            CLAIMS.clear();
            LAST_LOGGED.clear();
            return;
        }
        List<ServerPlayer> present = new ArrayList<>(vaultLevel.players());
        Set<UUID> seen = new HashSet<>(present.size());
        for (ServerPlayer player : present) {
            seen.add(player.getUUID());
            if (!player.isSpectator()) {
                check(server, vaultLevel, player);
            }
        }
        CLAIMS.keySet().retainAll(seen);
        LAST_LOGGED.keySet().retainAll(seen);
    }

    private static void check(MinecraftServer server, ServerLevel vaultLevel, ServerPlayer player) {
        VaultRegistry registry = VaultRegistry.get(server);
        Integer claimed = CLAIMS.get(player.getUUID());
        if (claimed != null) {
            VaultRecord vault = registry.byId(claimed);
            if (vault != null && VaultRooms.habitableBounds(vault).contains(player.position())) {
                return;
            }
            rescue(server, vaultLevel, player, vault);
            return;
        }

        VaultRecord standing = registry.byRoom(player.position());
        VaultPortalSession session = standing == null ? null : VaultManager.sessionByVault(standing.id());
        if (standing != null && session != null
            && VaultAccess.mayEnter(standing, session, player.getUUID())) {
            CLAIMS.put(player.getUUID(), standing.id());
            return;
        }
        rescue(server, vaultLevel, player, null);
    }


    private static void rescue(MinecraftServer server, ServerLevel vaultLevel,
                               ServerPlayer player, @Nullable VaultRecord home) {
        Vec3 from = player.position();
        if (home != null && VaultManager.sessionByVault(home.id()) != null) {
            Vec3 arrival = VaultRooms.interiorArrival(home);
            player.setDeltaMovement(Vec3.ZERO);
            player.teleportTo(vaultLevel, arrival.x, arrival.y, arrival.z,
                player.getYRot(), player.getXRot());
            player.resetFallDistance();
            log(server, player, from, "returned to vault " + home.id());
            return;
        }
        CLAIMS.remove(player.getUUID());
        VaultManager.expel(player, home);
        log(server, player, from, "put outside the vault dimension");
    }

    private static void log(MinecraftServer server, ServerPlayer player, Vec3 from, String what) {
        int now = server.getTickCount();
        Integer last = LAST_LOGGED.get(player.getUUID());
        if (last != null && now - last < LOG_COOLDOWN_TICKS) {
            return;
        }
        LAST_LOGGED.put(player.getUUID(), now);
        HexwrightDebug.log(HexwrightDebug.VAULT, "[vault] containment: {} was at {}, {}, {} - {}",
            player.getGameProfile().getName(),
            Math.round(from.x), Math.round(from.y), Math.round(from.z), what);
    }


    static void grant(ServerPlayer player, VaultRecord record) {
        CLAIMS.put(player.getUUID(), record.id());
    }

    static void forget(UUID player) {
        CLAIMS.remove(player);
    }

    static @Nullable Integer claimOf(UUID player) {
        return CLAIMS.get(player);
    }
}
