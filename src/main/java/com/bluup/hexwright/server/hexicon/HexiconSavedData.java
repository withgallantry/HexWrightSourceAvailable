package com.bluup.hexwright.server.hexicon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HexiconSavedData extends SavedData {
    public static final String DATA_ID = "hexwright_hexicon_data_v1";

    private static final String TAG_LIBRARIES = "libraries";
    private static final String TAG_PAYLOADS = "payloads";
    private static final String TAG_SLOTS = "slots";
    private static final String TAG_PAYLOAD_ID = "payload_id";

    private final Map<UUID, UUID[]> libraries = new HashMap<>();
    private final Map<UUID, CompoundTag> payloads = new HashMap<>();

    public HexiconSavedData() {
    }

    private HexiconSavedData(CompoundTag tag) {
        CompoundTag libsTag = tag.getCompound(TAG_LIBRARIES);
        for (String key : libsTag.getAllKeys()) {
            try {
                UUID libraryId = UUID.fromString(key);
                this.libraries.put(libraryId, decodeSlots(libsTag.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }

        CompoundTag payloadsTag = tag.getCompound(TAG_PAYLOADS);
        for (String key : payloadsTag.getAllKeys()) {
            try {
                UUID payloadId = UUID.fromString(key);
                this.payloads.put(payloadId, payloadsTag.getCompound(key).copy());
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public static HexiconSavedData open(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            HexiconSavedData::new,
            HexiconSavedData::new,
            DATA_ID
        );
    }

    public UUID[] getOrCreateLibrary(UUID libraryId) {
        UUID[] slots = this.libraries.computeIfAbsent(libraryId, ignored -> new UUID[HexiconData.TOTAL_SLOTS]);
        setDirty();
        return slots;
    }

    public UUID getSlotReference(UUID libraryId, int absoluteIndex) {
        UUID[] slots = this.libraries.get(libraryId);
        if (slots == null || absoluteIndex < 0 || absoluteIndex >= slots.length) {
            return null;
        }
        return slots[absoluteIndex];
    }

    public void setSlotReference(UUID libraryId, int absoluteIndex, UUID payloadId) {
        if (absoluteIndex < 0 || absoluteIndex >= HexiconData.TOTAL_SLOTS) {
            return;
        }
        UUID[] slots = getOrCreateLibrary(libraryId);
        slots[absoluteIndex] = payloadId;
        setDirty();
    }

    public UUID storePayload(CompoundTag payloadTag) {
        UUID payloadId = UUID.randomUUID();
        this.payloads.put(payloadId, payloadTag.copy());
        setDirty();
        return payloadId;
    }

    public CompoundTag loadPayload(UUID payloadId) {
        CompoundTag payload = this.payloads.get(payloadId);
        return payload == null ? null : payload.copy();
    }

    public void setPayload(UUID payloadId, CompoundTag payloadTag) {
        if (!this.payloads.containsKey(payloadId)) {
            return;
        }
        this.payloads.put(payloadId, payloadTag.copy());
        setDirty();
    }

    public void removePayload(UUID payloadId) {
        if (this.payloads.remove(payloadId) != null) {
            setDirty();
        }
    }

    public boolean isPayloadReferenced(UUID payloadId) {
        for (UUID[] refs : this.libraries.values()) {
            for (UUID ref : refs) {
                if (payloadId.equals(ref)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag libsTag = new CompoundTag();
        for (Map.Entry<UUID, UUID[]> entry : this.libraries.entrySet()) {
            libsTag.put(entry.getKey().toString(), encodeSlots(entry.getValue()));
        }
        tag.put(TAG_LIBRARIES, libsTag);

        CompoundTag payloadsTag = new CompoundTag();
        for (Map.Entry<UUID, CompoundTag> entry : this.payloads.entrySet()) {
            payloadsTag.put(entry.getKey().toString(), entry.getValue().copy());
        }
        tag.put(TAG_PAYLOADS, payloadsTag);
        return tag;
    }

    private static CompoundTag encodeSlots(UUID[] slots) {
        CompoundTag out = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < HexiconData.TOTAL_SLOTS; i++) {
            CompoundTag slotTag = new CompoundTag();
            UUID payloadId = i < slots.length ? slots[i] : null;
            if (payloadId != null) {
                slotTag.putUUID(TAG_PAYLOAD_ID, payloadId);
            }
            list.add(slotTag);
        }
        out.put(TAG_SLOTS, list);
        return out;
    }

    private static UUID[] decodeSlots(CompoundTag tag) {
        UUID[] slots = new UUID[HexiconData.TOTAL_SLOTS];
        ListTag list = tag.getList(TAG_SLOTS, CompoundTag.TAG_COMPOUND);
        int max = Math.min(list.size(), HexiconData.TOTAL_SLOTS);
        for (int i = 0; i < max; i++) {
            CompoundTag slotTag = list.getCompound(i);
            if (slotTag.hasUUID(TAG_PAYLOAD_ID)) {
                slots[i] = slotTag.getUUID(TAG_PAYLOAD_ID);
            }
        }
        return slots;
    }
}
