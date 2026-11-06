package com.bluup.hexwright.server.hexahedron;

import com.bluup.hexwright.inits.HexwrightNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HexahedronStore extends SavedData {
    private static final String STORAGE_ID = "hexwright_hexahedra";

    private final Map<UUID, Hexahedron> hexahedra = new LinkedHashMap<>();

    private HexahedronStore() {
    }

    private static HexahedronStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(HexahedronStore::load, HexahedronStore::new, STORAGE_ID);
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncTo(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
            (player, origin, destination) -> syncTo(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> syncTo(newPlayer));
    }

    public static Collection<Hexahedron> in(ServerLevel level) {
        return List.copyOf(get(level).hexahedra.values());
    }

    public static @Nullable Hexahedron covering(ServerLevel level, Vec3 point) {
        for (Hexahedron hexahedron : get(level).hexahedra.values()) {
            if (hexahedron.covers(point) && !hexahedron.hasBurst(level.getGameTime())) {
                return hexahedron;
            }
        }
        return null;
    }

    public static boolean isEmpty(ServerLevel level) {
        return get(level).hexahedra.isEmpty();
    }

    public static Hexahedron breakThrough(ServerLevel level, double x, double z, int radius, boolean opensInversion) {
        Hexahedron hexahedron = Hexahedron.opening(UUID.randomUUID(), x, z,
            level.getMaxBuildHeight() + Hexahedron.RIFT_ABOVE_BUILD_LIMIT, radius, level.getGameTime(), opensInversion);
        add(level, hexahedron);
        return hexahedron;
    }

    public static void add(ServerLevel level, Hexahedron hexahedron) {
        HexahedronStore store = get(level);
        store.hexahedra.put(hexahedron.id(), hexahedron);
        store.setDirty();
        broadcast(level);
    }

    public static void update(ServerLevel level, Hexahedron hexahedron) {
        HexahedronStore store = get(level);
        if (store.hexahedra.replace(hexahedron.id(), hexahedron) == null) {
            return;
        }
        store.setDirty();
        broadcast(level);
    }

    public static boolean remove(ServerLevel level, UUID id) {
        HexahedronStore store = get(level);
        if (store.hexahedra.remove(id) == null) {
            return false;
        }
        store.setDirty();
        broadcast(level);
        return true;
    }

    public static int clear(ServerLevel level) {
        HexahedronStore store = get(level);
        int removed = store.hexahedra.size();
        if (removed > 0) {
            store.hexahedra.clear();
            store.setDirty();
            broadcast(level);
        }
        return removed;
    }

    private static void syncTo(ServerPlayer player) {
        HexwrightNetworking.sendHexahedra(player, in(player.serverLevel()));
    }

    private static void broadcast(ServerLevel level) {
        Collection<Hexahedron> current = in(level);
        for (ServerPlayer player : PlayerLookup.world(level)) {
            HexwrightNetworking.sendHexahedra(player, current);
        }
    }

    private static HexahedronStore load(CompoundTag tag) {
        HexahedronStore store = new HexahedronStore();
        ListTag entries = tag.getList("Hexahedra", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            Hexahedron hexahedron = Hexahedron.load(entries.getCompound(i));
            store.hexahedra.put(hexahedron.id(), hexahedron);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Hexahedron hexahedron : hexahedra.values()) {
            entries.add(hexahedron.save());
        }
        tag.put("Hexahedra", entries);
        return tag;
    }
}
