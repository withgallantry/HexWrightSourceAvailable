package com.bluup.hexwright.server.cloak;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class CloakFieldStore extends SavedData {
    private static final String STORAGE_ID = "hexwright_cloak_fields";

    private final Map<UUID, CloakField> fields = new LinkedHashMap<>();

    private static Supplier<Collection<CloakField>> clientFields = List::of;

    private CloakFieldStore() {
    }

    private static CloakFieldStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CloakFieldStore::load, CloakFieldStore::new, STORAGE_ID);
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncTo(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
            (player, origin, destination) -> syncTo(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> syncTo(newPlayer));
    }

    public static Collection<CloakField> fieldsIn(ServerLevel level) {
        return List.copyOf(get(level).fields.values());
    }

    public static Collection<CloakField> fieldsIn(Level level) {
        return level instanceof ServerLevel server ? fieldsIn(server) : clientFields.get();
    }

    public static void setClientFields(Supplier<Collection<CloakField>> supplier) {
        clientFields = supplier;
    }

    public static boolean contains(ServerLevel level, UUID id) {
        return get(level).fields.containsKey(id);
    }

    public static void add(ServerLevel level, CloakField field) {
        CloakFieldStore store = get(level);
        store.fields.put(field.id(), field);
        CloakFieldShell.raise(level, field);
        store.setDirty();
        broadcast(level);
    }

    public static boolean remove(ServerLevel level, UUID id) {
        CloakFieldStore store = get(level);
        CloakField removed = store.fields.remove(id);
        if (removed == null) {
            return false;
        }
        CloakFieldShell.lower(level, removed);
        store.setDirty();
        broadcast(level);
        return true;
    }

    public static int clear(ServerLevel level) {
        CloakFieldStore store = get(level);
        int removed = store.fields.size();
        if (removed > 0) {
            store.fields.values().forEach(field -> CloakFieldShell.lower(level, field));
            store.fields.clear();
            store.setDirty();
            broadcast(level);
        }
        return removed;
    }

    private static void syncTo(ServerPlayer player) {
        HexwrightNetworking.sendCloakFields(player, fieldsIn(player.serverLevel()));
    }

    private static void broadcast(ServerLevel level) {
        Collection<CloakField> current = fieldsIn(level);
        for (ServerPlayer player : PlayerLookup.world(level)) {
            HexwrightNetworking.sendCloakFields(player, current);
        }
    }

    private static CloakFieldStore load(CompoundTag tag) {
        CloakFieldStore store = new CloakFieldStore();
        ListTag entries = tag.getList("Fields", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CloakField field = CloakField.load(entries.getCompound(i));
            store.fields.put(field.id(), field);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (CloakField field : fields.values()) {
            entries.add(field.save());
        }
        tag.put("Fields", entries);
        return tag;
    }
}
