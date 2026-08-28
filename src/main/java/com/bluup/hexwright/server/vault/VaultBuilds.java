package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.Hexwright;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VaultBuilds {

    private static final Map<String, VaultBuild> BUILDS = new LinkedHashMap<>();

    static {
        register(new VaultCastleBuild());
        register(new VaultTowerBuild());
        register(new VaultKeepBuild());
        register(new VaultCottageBuild());
        register(new VaultStarterHouseBuild());
    }

    private VaultBuilds() {
    }

    private static void register(VaultBuild build) {
        BUILDS.put(build.id(), build);
    }

    public static List<String> ids() {
        return List.copyOf(BUILDS.keySet());
    }

    public static @Nullable VaultBuild byId(String id) {
        return BUILDS.get(id);
    }

    static int fallbackGroundsSize() {
        return BUILDS.values().iterator().next().groundsSize();
    }

    static VaultBuild roll(int vaultId) {
        List<String> ids = ids();
        int hash = Integer.hashCode(vaultId * 0x9E3779B9);
        return BUILDS.get(ids.get(Math.floorMod(hash, ids.size())));
    }

    public static VaultBuild of(VaultRecord record) {
        VaultBuild build = BUILDS.get(record.build());
        if (build == null) {
            VaultBuild fallback = BUILDS.values().iterator().next();
            Hexwright.LOGGER.warn("Vault {} names unknown build '{}'; falling back to '{}'",
                record.id(), record.build(), fallback.id());
            return fallback;
        }
        return build;
    }
}
