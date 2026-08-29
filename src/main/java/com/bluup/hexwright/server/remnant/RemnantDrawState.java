package com.bluup.hexwright.server.remnant;

import com.bluup.hexwright.common.remnant.RemnantSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RemnantDrawState extends SavedData {

    private static final String STORAGE_ID = "hexwright_remnant_draws";

    private final Map<UUID, Long> drawn = new HashMap<>();

    public static RemnantDrawState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(RemnantDrawState::load, RemnantDrawState::new, STORAGE_ID);
    }

    public boolean isDrawn(UUID snapshot) {
        return drawn.containsKey(snapshot);
    }

    public void markDrawn(RemnantSnapshot snapshot, long now) {
        drawn.put(snapshot.id(), snapshot.capturedAt());
        long horizon = now - (RemnantSnapshot.GRACE_TICKS + RemnantSnapshot.DECAY_TICKS);
        drawn.values().removeIf(capturedAt -> capturedAt < horizon);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        drawn.forEach((id, capturedAt) -> all.putLong(id.toString(), capturedAt));
        tag.put("Drawn", all);
        return tag;
    }

    public static RemnantDrawState load(CompoundTag tag) {
        RemnantDrawState state = new RemnantDrawState();
        CompoundTag all = tag.getCompound("Drawn");
        for (String key : all.getAllKeys()) {
            try {
                state.drawn.put(UUID.fromString(key), all.getLong(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return state;
    }
}
