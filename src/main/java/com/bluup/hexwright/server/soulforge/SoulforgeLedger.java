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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SoulforgeLedger extends SavedData {

    private static final String STORAGE_ID = "hexwright_soulforged_weapons";

    private final Map<UUID, Bond> bonds = new HashMap<>();

    private final Set<UUID> spent = new HashSet<>();

    private final Map<UUID, String> blacklist = new HashMap<>();

    static SoulforgeLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(SoulforgeLedger::load, SoulforgeLedger::new, STORAGE_ID);
    }

    private Bond bond(UUID owner) {
        return this.bonds.computeIfAbsent(owner, Bond::new);
    }

    @Nullable
    Bond ownedBy(UUID owner) {
        Bond bond = this.bonds.get(owner);
        return bond != null && bond.item != null ? bond : null;
    }

    List<Bond> bonded() {
        List<Bond> bonded = new ArrayList<>();
        for (Bond bond : this.bonds.values()) {
            if (bond.item != null) {
                bonded.add(bond);
            }
        }
        return bonded;
    }

    Bond bind(ServerPlayer owner, ItemStack stack) {
        Bond bond = bond(owner.getUUID());
        UUID instance = SoulBond.instance(stack);
        bond.ownerName = owner.getGameProfile().getName();
        bond.item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        bond.instance = instance != null ? instance : UUID.randomUUID();
        bond.doomAt = SoulBond.doomAt(stack);
        bond.dismissed = false;
        bond.generation++;
        SoulBond.stamp(stack, bond.owner, bond.ownerName, bond.generation, bond.instance);
        bond.weapon = save(stack);
        setDirty();
        return bond;
    }

    void restamp(Bond bond, ItemStack stack) {
        bond.generation++;
        SoulBond.stamp(stack, bond.owner, bond.ownerName, bond.generation, bond.instance);
        bond.weapon = save(stack);
        setDirty();
    }

    void remember(Bond bond, ItemStack stack) {
        CompoundTag saved = save(stack);
        if (!saved.equals(bond.weapon)) {
            bond.weapon = saved;
            setDirty();
        }
    }

    void stow(Bond bond, @Nullable ItemStack stack) {
        if (stack != null) {
            bond.weapon = save(stack);
        }
        bond.dismissed = true;
        bond.generation++;
        setDirty();
    }

    ItemStack draw(Bond bond) {
        bond.generation++;
        bond.dismissed = false;
        ItemStack stack = ItemStack.of(bond.weapon);
        if (stack.isEmpty()) {
            stack = new ItemStack(bond.item());
        }
        SoulBond.stamp(stack, bond.owner, bond.ownerName, bond.generation, bond.instance);
        bond.weapon = save(stack);
        setDirty();
        return stack;
    }

    void end(Bond bond) {
        if (bond.instance != null) {
            this.spent.add(bond.instance);
        }
        bond.item = null;
        bond.instance = null;
        bond.weapon = new CompoundTag();
        bond.dismissed = false;
        bond.doomAt = 0L;
        bond.generation++;
        setDirty();
    }

    boolean isSpent(@Nullable UUID instance) {
        return instance != null && this.spent.contains(instance);
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
        for (Bond bond : this.bonds.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", bond.owner);
            entry.putString("Name", bond.ownerName);
            entry.putInt("Generation", bond.generation);
            if (bond.item != null && bond.instance != null) {
                entry.putString("Item", bond.item.toString());
                entry.putUUID("Instance", bond.instance);
                entry.put("Weapon", bond.weapon);
                entry.putBoolean("Dismissed", bond.dismissed);
                entry.putLong("DoomAt", bond.doomAt);
            }
            list.add(entry);
        }
        tag.put("Bonds", list);
        ListTag spentList = new ListTag();
        for (UUID instance : this.spent) {
            CompoundTag spentEntry = new CompoundTag();
            spentEntry.putUUID("Instance", instance);
            spentList.add(spentEntry);
        }
        tag.put("Spent", spentList);
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
        if (tag.contains("Bonds", Tag.TAG_LIST)) {
            for (Tag element : tag.getList("Bonds", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) element;
                Bond bond = ledger.bond(entry.getUUID("Owner"));
                bond.ownerName = entry.getString("Name");
                bond.generation = entry.getInt("Generation");
                ResourceLocation item = ResourceLocation.tryParse(entry.getString("Item"));
                if (item != null && BuiltInRegistries.ITEM.containsKey(item) && entry.hasUUID("Instance")) {
                    bond.item = item;
                    bond.instance = entry.getUUID("Instance");
                    bond.weapon = entry.getCompound("Weapon");
                    bond.dismissed = entry.getBoolean("Dismissed");
                    bond.doomAt = entry.getLong("DoomAt");
                }
            }
        } else {
            ledger.migrateWorldUnique(tag);
        }
        for (Tag element : tag.getList("Spent", Tag.TAG_COMPOUND)) {
            ledger.spent.add(((CompoundTag) element).getUUID("Instance"));
        }
        for (Tag element : tag.getList("Blacklist", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            ledger.blacklist.put(entry.getUUID("Player"), entry.getString("Name"));
        }
        return ledger;
    }

    private void migrateWorldUnique(CompoundTag tag) {
        for (Tag element : tag.getList("Weapons", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            ResourceLocation item = ResourceLocation.tryParse(entry.getString("Item"));
            if (item == null || !BuiltInRegistries.ITEM.containsKey(item)
                || !"BOUND".equals(entry.getString("State")) || !entry.hasUUID("Owner")) {
                continue;
            }
            Bond bond = bond(entry.getUUID("Owner"));
            bond.ownerName = entry.getString("Name");
            bond.item = item;
            bond.instance = UUID.randomUUID();
            bond.generation = entry.getInt("Generation");
            bond.weapon = entry.getCompound("Weapon");
            bond.dismissed = entry.getBoolean("Dismissed");
        }
        setDirty();
    }

    static final class Bond {
        final UUID owner;
        String ownerName = "";
        @Nullable
        ResourceLocation item;
        @Nullable
        UUID instance;
        int generation;
        CompoundTag weapon = new CompoundTag();
        boolean dismissed;
        long doomAt;
        long carriedTick = Long.MIN_VALUE;

        private Bond(UUID owner) {
            this.owner = owner;
        }

        Item item() {
            return BuiltInRegistries.ITEM.get(this.item);
        }

        boolean isCurrent(ItemStack stack) {
            return this.item != null && this.owner.equals(SoulBond.owner(stack))
                && SoulBond.generation(stack) == this.generation
                && this.item.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
    }
}
