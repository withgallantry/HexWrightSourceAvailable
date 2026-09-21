package com.bluup.hexwright.server.pentabox;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PentaboxStore extends SavedData {

    private static final String STORAGE_ID = "hexwright_pentaboxes";

    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_KEY = "Key";
    private static final String TAG_BOX = "Box";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_SEEN = "LastSeen";

    public static final class Entry {
        public ItemStack box;
        public String owner;
        public int deployedSlot;
        public long lastSeenTick;

        private Entry(ItemStack box, String owner, int deployedSlot, long lastSeenTick) {
            this.box = box;
            this.owner = owner;
            this.deployedSlot = deployedSlot;
            this.lastSeenTick = lastSeenTick;
        }
    }

    private final Map<String, Entry> parked = new HashMap<>();

    public static PentaboxStore get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(PentaboxStore::load, PentaboxStore::new, STORAGE_ID);
    }

    public Entry park(String key, ItemStack box, @Nullable String owner, int deployedSlot, long tick) {
        Entry existing = parked.get(key);
        if (existing != null) {
            existing.deployedSlot = deployedSlot;
            existing.lastSeenTick = tick;
            if (owner != null) {
                existing.owner = owner;
            }
            setDirty();
            return existing;
        }
        Entry entry = new Entry(box, owner, deployedSlot, tick);
        parked.put(key, entry);
        setDirty();
        return entry;
    }

    @Nullable
    public Entry peek(String key) {
        return parked.get(key);
    }

    public ItemStack take(String key) {
        Entry entry = parked.remove(key);
        if (entry == null) {
            return ItemStack.EMPTY;
        }
        setDirty();
        return entry.box;
    }

    public boolean touch(String key, @Nullable String carrier, long tick) {
        Entry entry = parked.get(key);
        if (entry == null) {
            return false;
        }
        entry.lastSeenTick = tick;
        if (entry.owner == null && carrier != null) {
            entry.owner = carrier;
            setDirty();
        }
        return true;
    }

    public List<String> staleKeys(String owner, long tick, long graceTicks) {
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, Entry> e : parked.entrySet()) {
            Entry entry = e.getValue();
            if (owner.equals(entry.owner) && tick - entry.lastSeenTick >= graceTicks) {
                stale.add(e.getKey());
            }
        }
        return stale;
    }

    public void refreshOwner(String owner, long tick) {
        for (Entry entry : parked.values()) {
            if (owner.equals(entry.owner)) {
                entry.lastSeenTick = tick;
            }
        }
    }

    public static PentaboxStore load(CompoundTag tag) {
        PentaboxStore store = new PentaboxStore();
        ListTag entries = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entryTag = entries.getCompound(i);
            String key = entryTag.getString(TAG_KEY);
            if (key.isEmpty()) {
                continue;
            }
            ItemStack box = ItemStack.of(entryTag.getCompound(TAG_BOX));
            if (box.isEmpty()) {
                continue;
            }
            store.parked.put(key, new Entry(
                box,
                entryTag.contains(TAG_OWNER, Tag.TAG_STRING) ? entryTag.getString(TAG_OWNER) : null,
                entryTag.getInt(TAG_SLOT),
                entryTag.getLong(TAG_SEEN)
            ));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Map.Entry<String, Entry> e : parked.entrySet()) {
            Entry entry = e.getValue();
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(TAG_KEY, e.getKey());
            entryTag.put(TAG_BOX, entry.box.save(new CompoundTag()));
            if (entry.owner != null) {
                entryTag.putString(TAG_OWNER, entry.owner);
            }
            entryTag.putInt(TAG_SLOT, entry.deployedSlot);
            entryTag.putLong(TAG_SEEN, entry.lastSeenTick);
            entries.add(entryTag);
        }
        tag.put(TAG_ENTRIES, entries);
        return tag;
    }
}
