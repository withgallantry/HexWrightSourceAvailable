package com.bluup.hexwright.server.remnant;

import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.common.remnant.RemnantDecay;
import com.bluup.hexwright.common.remnant.RemnantSnapshot;
import com.bluup.hexwright.common.remnant.RemnantType;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RemnantDrawState extends SavedData {

    private static final String STORAGE_ID = "hexwright_remnant_draws";

    private final Map<UUID, Long> drawn = new HashMap<>();

    private record Draught(Remnant remnant, long issuedAt) {
    }

    private final Map<UUID, Draught> live = new HashMap<>();

    private static @Nullable MinecraftServer server = null;

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);
    }

    public static RemnantDrawState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(RemnantDrawState::load, RemnantDrawState::new, STORAGE_ID);
    }

    public static @Nullable RemnantDrawState get() {
        MinecraftServer running = server;
        if (running == null || !running.isSameThread()) {
            return null;
        }
        return get(running);
    }

    public static long gameTime() {
        MinecraftServer running = server;
        return running == null ? 0L : running.overworld().getGameTime();
    }

    public UUID issue(Remnant remnant, long now) {
        live.values().removeIf(row -> RemnantDecay.potency(row.issuedAt(), now) <= 0.0);
        UUID draught = UUID.randomUUID();
        live.put(draught, new Draught(remnant, now));
        setDirty();
        return draught;
    }

    public @Nullable Remnant peek(UUID draught, long now) {
        Draught row = live.get(draught);
        if (row == null) {
            return null;
        }
        double potency = RemnantDecay.potency(row.issuedAt(), now);
        Remnant faded = row.remnant().withDrams(row.remnant().drams() * potency);
        return faded.isEmpty() ? null : faded;
    }

    public @Nullable Remnant redeem(UUID draught, long now) {
        Remnant worth = peek(draught, now);
        if (live.remove(draught) != null) {
            setDirty();
        }
        return worth;
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

        ListTag inFlight = new ListTag();
        live.forEach((id, row) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.putString("Type", row.remnant().type().name());
            entry.putDouble("Drams", row.remnant().drams());
            entry.putLong("At", row.issuedAt());
            inFlight.add(entry);
        });
        tag.put("Live", inFlight);
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
        ListTag inFlight = tag.getList("Live", Tag.TAG_COMPOUND);
        for (int i = 0; i < inFlight.size(); i++) {
            CompoundTag entry = inFlight.getCompound(i);
            RemnantType type = RemnantType.byName(entry.getString("Type"));
            if (type != null && entry.hasUUID("Id")) {
                state.live.put(entry.getUUID("Id"),
                    new Draught(new Remnant(type, entry.getDouble("Drams")), entry.getLong("At")));
            }
        }
        return state;
    }
}
