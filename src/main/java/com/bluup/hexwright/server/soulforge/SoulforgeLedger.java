package com.bluup.hexwright.server.soulforge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SoulforgeLedger extends SavedData {

    private static final String STORAGE_ID = "hexwright_soulforged_weapons";

    enum State {
        POOL,
        LOOSE,
        BOUND
    }

    private final Map<ResourceLocation, Record> records = new HashMap<>();

    private final Map<UUID, ResourceLocation> lapsed = new HashMap<>();

    private final Map<UUID, String> blacklist = new HashMap<>();

    static SoulforgeLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(SoulforgeLedger::load, SoulforgeLedger::new, STORAGE_ID);
    }

    Record record(Item item) {
        return this.records.computeIfAbsent(BuiltInRegistries.ITEM.getKey(item), Record::new);
    }

    @Nullable
    Record ownedBy(UUID owner) {
        for (Record record : this.records.values()) {
            if (record.state == State.BOUND && owner.equals(record.owner)) {
                return record;
            }
        }
        return null;
    }

    Collection<Record> records() {
        return this.records.values();
    }

    void release(Record record, ItemStack stack) {
        record.state = State.LOOSE;
        record.owner = null;
        record.ownerName = "";
        record.absentMillis = 0L;
        record.generation++;
        SoulBond.stamp(stack, null, "", record.generation);
        setDirty();
    }

    void bind(Record record, ServerPlayer owner, ItemStack stack) {
        record.state = State.BOUND;
        record.owner = owner.getUUID();
        record.ownerName = owner.getGameProfile().getName();
        record.dismissed = false;
        record.absentMillis = 0L;
        record.generation++;
        SoulBond.stamp(stack, record.owner, record.ownerName, record.generation);
        record.weapon = save(stack);
        this.lapsed.remove(record.owner);
        setDirty();
    }

    void restamp(Record record, ItemStack stack) {
        record.generation++;
        SoulBond.stamp(stack, record.owner, record.ownerName, record.generation);
        if (record.state == State.BOUND) {
            record.weapon = save(stack);
        }
        setDirty();
    }

    void remember(Record record, ItemStack stack) {
        CompoundTag saved = save(stack);
        if (!saved.equals(record.weapon)) {
            record.weapon = saved;
            setDirty();
        }
    }

    void stow(Record record, @Nullable ItemStack stack) {
        if (stack != null) {
            record.weapon = save(stack);
        }
        record.dismissed = true;
        record.generation++;
        setDirty();
    }

    ItemStack draw(Record record) {
        record.generation++;
        record.dismissed = false;
        ItemStack stack = ItemStack.of(record.weapon);
        if (stack.isEmpty()) {
            stack = new ItemStack(record.item());
        }
        SoulBond.stamp(stack, record.owner, record.ownerName, record.generation);
        record.weapon = save(stack);
        setDirty();
        return stack;
    }

    void returnToPool(Record record) {
        record.state = State.POOL;
        record.owner = null;
        record.ownerName = "";
        record.weapon = new CompoundTag();
        record.dismissed = false;
        record.absentMillis = 0L;
        record.generation++;
        setDirty();
    }

    void age(Record record, long step) {
        long absent = step < 0L ? 0L : record.absentMillis + step;
        if (absent != record.absentMillis) {
            record.absentMillis = absent;
            setDirty();
        }
    }

    void noteLapsed(UUID owner, Record record) {
        this.lapsed.put(owner, record.item);
        setDirty();
    }

    @Nullable
    Item takeLapsed(UUID owner) {
        ResourceLocation item = this.lapsed.remove(owner);
        if (item == null) {
            return null;
        }
        setDirty();
        return BuiltInRegistries.ITEM.get(item);
    }

    boolean isBlacklisted(UUID player) {
        return this.blacklist.containsKey(player);
    }

    boolean addToBlacklist(UUID player, String name) {
        if (this.blacklist.putIfAbsent(player, name) != null) {
            return false;
        }
        setDirty();
        return true;
    }

    boolean removeFromBlacklist(UUID player) {
        if (this.blacklist.remove(player) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    Collection<String> blacklistedNames() {
        return this.blacklist.values();
    }

    private static CompoundTag save(ItemStack stack) {
        return stack.copyWithCount(1).save(new CompoundTag());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Record record : this.records.values()) {
            if (record.generation == 0) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString("Item", record.item.toString());
            entry.putString("State", record.state.name());
            entry.putInt("Generation", record.generation);
            if (record.owner != null) {
                entry.putUUID("Owner", record.owner);
                entry.putString("Name", record.ownerName);
            }
            entry.put("Weapon", record.weapon);
            entry.putBoolean("Dismissed", record.dismissed);
            entry.putLong("AbsentMillis", record.absentMillis);
            list.add(entry);
        }
        tag.put("Weapons", list);
        ListTag lapsedList = new ListTag();
        for (Map.Entry<UUID, ResourceLocation> entry : this.lapsed.entrySet()) {
            CompoundTag lapsedEntry = new CompoundTag();
            lapsedEntry.putUUID("Owner", entry.getKey());
            lapsedEntry.putString("Item", entry.getValue().toString());
            lapsedList.add(lapsedEntry);
        }
        tag.put("Lapsed", lapsedList);
        ListTag blacklistTag = new ListTag();
        for (Map.Entry<UUID, String> entry : this.blacklist.entrySet()) {
            CompoundTag blacklistEntry = new CompoundTag();
            blacklistEntry.putUUID("Player", entry.getKey());
            blacklistEntry.putString("Name", entry.getValue());
            blacklistTag.add(blacklistEntry);
        }
        tag.put("Blacklist", blacklistTag);
        return tag;
    }

    private static SoulforgeLedger load(CompoundTag tag) {
        SoulforgeLedger ledger = new SoulforgeLedger();
        for (Tag element : tag.getList("Weapons", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            ResourceLocation item = ResourceLocation.tryParse(entry.getString("Item"));
            if (item == null || !BuiltInRegistries.ITEM.containsKey(item)) {
                continue;
            }
            Record record = new Record(item);
            record.state = State.valueOf(entry.getString("State"));
            record.generation = entry.getInt("Generation");
            if (entry.hasUUID("Owner")) {
                record.owner = entry.getUUID("Owner");
                record.ownerName = entry.getString("Name");
            }
            record.weapon = entry.getCompound("Weapon");
            record.dismissed = entry.getBoolean("Dismissed");
            record.absentMillis = entry.getLong("AbsentMillis");
            ledger.records.put(item, record);
        }
        for (Tag element : tag.getList("Lapsed", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            ResourceLocation item = ResourceLocation.tryParse(entry.getString("Item"));
            if (item != null && BuiltInRegistries.ITEM.containsKey(item)) {
                ledger.lapsed.put(entry.getUUID("Owner"), item);
            }
        }
        for (Tag element : tag.getList("Blacklist", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            ledger.blacklist.put(entry.getUUID("Player"), entry.getString("Name"));
        }
        return ledger;
    }

    static final class Record {
        final ResourceLocation item;
        State state = State.POOL;
        int generation;
        @Nullable
        UUID owner;
        String ownerName = "";
        CompoundTag weapon = new CompoundTag();
        boolean dismissed;
        long absentMillis;
        long carriedTick = Long.MIN_VALUE;
        @Nullable
        UUID lastHolder;

        private Record(ResourceLocation item) {
            this.item = item;
        }

        Item item() {
            return BuiltInRegistries.ITEM.get(this.item);
        }

        boolean isCurrent(ItemStack stack) {
            return this.state != State.POOL && SoulBond.generation(stack) == this.generation;
        }
    }
}
