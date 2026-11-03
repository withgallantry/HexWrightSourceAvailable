package com.bluup.hexwright.server.remnant;

import com.bluup.hexwright.common.remnant.RemnantType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RemnantBuffState extends SavedData {

    private static final String STORAGE_ID = "hexwright_remnant_buffs";

    public static final class Active {
        private final Map<RemnantType, Long> expiries = new EnumMap<>(RemnantType.class);

        public void set(RemnantType type, long expiresAt) {
            expiries.put(type, expiresAt);
        }

        public void clear(RemnantType type) {
            expiries.remove(type);
        }

        public boolean has(RemnantType type, long now) {
            Long expiry = expiries.get(type);
            return expiry != null && expiry > now;
        }

        public Map<RemnantType, Long> all() {
            return expiries;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            expiries.forEach((type, expiry) -> tag.putLong(type.name(), expiry));
            return tag;
        }

        static Active load(CompoundTag tag) {
            Active active = new Active();
            for (String key : tag.getAllKeys()) {
                RemnantType type = RemnantType.byName(key);
                if (type != null) {
                    active.expiries.put(type, tag.getLong(key));
                }
            }
            return active;
        }
    }

    private final Map<UUID, Active> players = new HashMap<>();

    public static RemnantBuffState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(RemnantBuffState::load, RemnantBuffState::new, STORAGE_ID);
    }

    public Active forPlayer(UUID player) {
        return players.computeIfAbsent(player, id -> new Active());
    }

    public Map<UUID, Active> everyone() {
        return players;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, Active> entry : players.entrySet()) {
            all.put(entry.getKey().toString(), entry.getValue().save());
        }
        tag.put("Players", all);
        return tag;
    }

    public static RemnantBuffState load(CompoundTag tag) {
        RemnantBuffState state = new RemnantBuffState();
        CompoundTag all = tag.getCompound("Players");
        for (String key : all.getAllKeys()) {
            try {
                state.players.put(UUID.fromString(key), Active.load(all.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return state;
    }
}
