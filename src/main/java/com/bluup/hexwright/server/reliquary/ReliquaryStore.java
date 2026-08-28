package com.bluup.hexwright.server.reliquary;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public class ReliquaryStore extends SavedData {

    public static final int SLOTS = 54;

    public static final int SLOT_CAP = 1_000_000_000;

    private static final String STORAGE_ID = "hexwright_reliquaries";

    private final Map<String, NonNullList<ItemStack>> inventories = new HashMap<>();
    private final Map<String, NonNullList<ItemStack>> hookDefaults = new HashMap<>();

    public static ReliquaryStore get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(ReliquaryStore::load, ReliquaryStore::new, STORAGE_ID);
    }

    public static String key(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public NonNullList<ItemStack> inventory(String key) {
        return inventories.computeIfAbsent(key, k -> NonNullList.withSize(SLOTS, ItemStack.EMPTY));
    }

    public boolean exists(String key) {
        return inventories.containsKey(key);
    }

    public NonNullList<ItemStack> remove(String key) {
        NonNullList<ItemStack> contents = inventories.remove(key);
        hookDefaults.remove(key);
        setDirty();
        return contents == null ? NonNullList.withSize(0, ItemStack.EMPTY) : contents;
    }

    private static final int HOOK_COUNT = SatchelItem.Hook.values().length;

    private static int hookIndex(SatchelItem.Hook hook) {
        return switch (hook) {
            case OPEN -> 0;
            case DEPOSIT -> 1;
            case WITHDRAW -> 2;
        };
    }

    private static String hookTag(int index) {
        return switch (index) {
            case 0 -> "Open";
            case 1 -> "Deposit";
            case 2 -> "Withdraw";
            default -> "";
        };
    }

    public ItemStack getHook(String key, SatchelItem.Hook hook) {
        NonNullList<ItemStack> hooks = hookDefaults.get(key);
        if (hooks == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stored = hooks.get(hookIndex(hook));
        return stored.isEmpty() ? ItemStack.EMPTY : stored.copy();
    }

    public void setHook(String key, SatchelItem.Hook hook, ItemStack focus) {
        NonNullList<ItemStack> hooks = hookDefaults.computeIfAbsent(key,
            ignored -> NonNullList.withSize(HOOK_COUNT, ItemStack.EMPTY));
        hooks.set(hookIndex(hook), focus.isEmpty() ? ItemStack.EMPTY : focus.copyWithCount(1));
        boolean any = false;
        for (ItemStack stack : hooks) {
            if (!stack.isEmpty()) {
                any = true;
                break;
            }
        }
        if (!any) {
            hookDefaults.remove(key);
        }
        setDirty();
    }

    public static final int NO_POSITION_PREFERENCE = -1;

    public ItemStack withdraw(String key, ItemStack sample, int count, int position) {
        NonNullList<ItemStack> items = inventory(key);
        if (position != NO_POSITION_PREFERENCE) {
            if (position < 0 || position >= items.size()) {
                com.bluup.hexwright.Hexwright.LOGGER.warn(
                    "Reliquary withdraw refused: position {} out of range for key {}", position, key);
                return ItemStack.EMPTY;
            }
            ItemStack stack = items.get(position);
            if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, sample)) {
                com.bluup.hexwright.Hexwright.LOGGER.warn(
                    "Reliquary withdraw refused at key {} position {}: slot has [{}], sample was [{}]",
                    key, position, stack, sample);
                return ItemStack.EMPTY;
            }
            int take = Math.min(Math.min(count, sample.getMaxStackSize()), stack.getCount());
            ItemStack gathered = stack.copyWithCount(take);
            stack.shrink(take);
            if (stack.isEmpty()) {
                items.set(position, ItemStack.EMPTY);
            }
            setDirty();
            return gathered;
        }
        int remaining = Math.min(count, sample.getMaxStackSize());
        ItemStack gathered = ItemStack.EMPTY;
        for (int i = 0; i < items.size() && remaining > 0; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, sample)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            if (gathered.isEmpty()) {
                gathered = stack.copyWithCount(take);
            } else {
                gathered.grow(take);
            }
            stack.shrink(take);
            if (stack.isEmpty()) {
                items.set(i, ItemStack.EMPTY);
            }
            remaining -= take;
        }
        if (!gathered.isEmpty()) {
            setDirty();
        }
        return gathered;
    }

    public ItemStack placeAt(String key, int slot, ItemStack incoming) {
        NonNullList<ItemStack> items = inventory(key);
        ItemStack existing = items.get(slot);
        if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, incoming)) {
            existing.grow(incoming.getCount());
            setDirty();
            return ItemStack.EMPTY;
        }
        items.set(slot, incoming.copy());
        setDirty();
        return existing;
    }

    public ItemStack deposit(String key, ItemStack stack) {
        NonNullList<ItemStack> items = inventory(key);
        ItemStack remaining = stack.copy();

        for (int i = 0; i < items.size() && !remaining.isEmpty(); i++) {
            ItemStack slot = items.get(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, remaining)) {
                int room = SLOT_CAP - slot.getCount();
                int move = Math.min(room, remaining.getCount());
                if (move > 0) {
                    slot.grow(move);
                    remaining.shrink(move);
                    setDirty();
                }
            }
        }
        for (int i = 0; i < items.size() && !remaining.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, remaining);
                remaining = ItemStack.EMPTY;
                setDirty();
            }
        }
        return remaining;
    }


    public static CompoundTag saveList(NonNullList<ItemStack> items) {
        ListTag list = new ListTag();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag slotTag = new CompoundTag();
            slotTag.putInt("Slot", i);
            ItemStack single = stack.copyWithCount(1);
            slotTag.put("Item", single.save(new CompoundTag()));
            slotTag.putInt("BigCount", stack.getCount());
            list.add(slotTag);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("BigItems", list);
        return tag;
    }

    public static void loadList(CompoundTag tag, NonNullList<ItemStack> into) {
        for (int i = 0; i < into.size(); i++) {
            into.set(i, ItemStack.EMPTY);
        }
        ListTag list = tag.getList("BigItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag slotTag = list.getCompound(i);
            int slot = slotTag.getInt("Slot");
            if (slot < 0 || slot >= into.size()) {
                continue;
            }
            ItemStack stack = ItemStack.of(slotTag.getCompound("Item"));
            if (!stack.isEmpty()) {
                stack.setCount(Math.max(1, slotTag.getInt("BigCount")));
                into.set(slot, stack);
            }
        }
    }

    private ReliquaryStore() {
    }

    private static ReliquaryStore load(CompoundTag tag) {
        ReliquaryStore store = new ReliquaryStore();
        ListTag entries = tag.getList("Reliquaries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            String key = entry.getString("Key");
            NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
            if (entry.contains("BigItems") || entry.getCompound("Items").contains("BigItems")) {
                loadList(entry.contains("BigItems") ? entry : entry.getCompound("Items"), items);
            } else {
                ContainerHelper.loadAllItems(entry.getCompound("Items"), items);
            }
            store.inventories.put(key, items);

            if (entry.contains("Hooks", Tag.TAG_COMPOUND)) {
                CompoundTag hooksTag = entry.getCompound("Hooks");
                NonNullList<ItemStack> hooks = NonNullList.withSize(HOOK_COUNT, ItemStack.EMPTY);
                boolean any = false;
                for (int hookIndex = 0; hookIndex < HOOK_COUNT; hookIndex++) {
                    String hookTag = hookTag(hookIndex);
                    if (!hookTag.isEmpty() && hooksTag.contains(hookTag, Tag.TAG_COMPOUND)) {
                        ItemStack focus = ItemStack.of(hooksTag.getCompound(hookTag));
                        if (!focus.isEmpty()) {
                            hooks.set(hookIndex, focus.copyWithCount(1));
                            any = true;
                        }
                    }
                }
                if (any) {
                    store.hookDefaults.put(key, hooks);
                }
            }
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Map.Entry<String, NonNullList<ItemStack>> entry : inventories.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            String key = entry.getKey();
            entryTag.putString("Key", key);
            entryTag.put("Items", saveList(entry.getValue()));
            NonNullList<ItemStack> hooks = hookDefaults.get(key);
            if (hooks != null) {
                CompoundTag hooksTag = new CompoundTag();
                for (int hookIndex = 0; hookIndex < HOOK_COUNT; hookIndex++) {
                    ItemStack focus = hooks.get(hookIndex);
                    if (!focus.isEmpty()) {
                        hooksTag.put(hookTag(hookIndex), focus.save(new CompoundTag()));
                    }
                }
                if (!hooksTag.isEmpty()) {
                    entryTag.put("Hooks", hooksTag);
                }
            }
            entries.add(entryTag);
        }
        tag.put("Reliquaries", entries);
        return tag;
    }

    public void touch() {
        setDirty();
    }
}
