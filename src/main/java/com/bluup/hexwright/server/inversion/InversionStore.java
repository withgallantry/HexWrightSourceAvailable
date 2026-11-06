package com.bluup.hexwright.server.inversion;

import com.bluup.hexwright.inits.HexwrightNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class InversionStore extends SavedData {
    private static final String STORAGE_ID = "hexwright_inversions";

    private final Map<UUID, Inversion> inversions = new LinkedHashMap<>();
    private final Map<UUID, InversionOriginals> originals = new HashMap<>();
    private final Set<UUID> spent = new HashSet<>();

    private InversionStore() {
    }

    private static InversionStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            tag -> load(tag, level.holderLookup(Registries.BLOCK)), InversionStore::new, STORAGE_ID);
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncTo(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
            (player, origin, destination) -> syncTo(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> syncTo(newPlayer));
    }

    public static Collection<Inversion> in(ServerLevel level) {
        return List.copyOf(get(level).inversions.values());
    }

    public static @Nullable Inversion get(ServerLevel level, UUID id) {
        return get(level).inversions.get(id);
    }

    public static @Nullable Inversion covering(ServerLevel level, int x, int z) {
        for (Inversion inversion : get(level).inversions.values()) {
            if (inversion.covers(x, z)) {
                return inversion;
            }
        }
        return null;
    }

    public static boolean overlapsAny(ServerLevel level, int x, int z) {
        for (Inversion inversion : get(level).inversions.values()) {
            if (inversion.overlaps(x, z)) {
                return true;
            }
        }
        return false;
    }

    static void add(ServerLevel level, Inversion inversion) {
        InversionStore store = get(level);
        store.inversions.put(inversion.id(), inversion);
        store.originals.put(inversion.id(), new InversionOriginals(level.getMinBuildHeight()));
        if (inversion.source() != null) {
            store.spent.add(inversion.source());
        }
        store.setDirty();
        broadcast(level);
    }

    static void update(ServerLevel level, Inversion inversion) {
        InversionStore store = get(level);
        store.inversions.put(inversion.id(), inversion);
        store.setDirty();
        broadcast(level);
    }

    static void remove(ServerLevel level, UUID id) {
        InversionStore store = get(level);
        store.inversions.remove(id);
        store.originals.remove(id);
        store.setDirty();
        broadcast(level);
    }

    static InversionOriginals originals(ServerLevel level, UUID id) {
        return get(level).originals.computeIfAbsent(id, key -> new InversionOriginals(level.getMinBuildHeight()));
    }

    static void changed(ServerLevel level) {
        get(level).setDirty();
    }

    public static boolean spent(ServerLevel level, UUID source) {
        return get(level).spent.contains(source);
    }

    private static void syncTo(ServerPlayer player) {
        HexwrightNetworking.sendInversions(player, in(player.serverLevel()));
    }

    private static void broadcast(ServerLevel level) {
        Collection<Inversion> current = in(level);
        for (ServerPlayer player : PlayerLookup.world(level)) {
            HexwrightNetworking.sendInversions(player, current);
        }
    }

    private static InversionStore load(CompoundTag tag, HolderGetter<Block> blocks) {
        InversionStore store = new InversionStore();
        for (Tag entry : tag.getList("Inversions", Tag.TAG_COMPOUND)) {
            CompoundTag saved = (CompoundTag) entry;
            Inversion inversion = Inversion.load(saved);
            store.inversions.put(inversion.id(), inversion);
            if (saved.contains("Originals", Tag.TAG_COMPOUND)) {
                store.originals.put(inversion.id(), InversionOriginals.load(saved.getCompound("Originals"), blocks));
            }
        }
        for (Tag spent : tag.getList("Spent", Tag.TAG_INT_ARRAY)) {
            store.spent.add(NbtUtils.loadUUID(spent));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Inversion inversion : inversions.values()) {
            CompoundTag saved = inversion.save();
            InversionOriginals swallowed = originals.get(inversion.id());
            if (swallowed != null) {
                saved.put("Originals", swallowed.save());
            }
            entries.add(saved);
        }
        tag.put("Inversions", entries);
        ListTag spentTag = new ListTag();
        for (UUID source : spent) {
            spentTag.add(NbtUtils.createUUID(source));
        }
        tag.put("Spent", spentTag);
        return tag;
    }
}
