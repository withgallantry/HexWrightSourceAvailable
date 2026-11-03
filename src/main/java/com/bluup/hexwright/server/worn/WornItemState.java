package com.bluup.hexwright.server.worn;

import com.bluup.hexwright.Hexwright;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class WornItemState extends SavedData {

    private final Map<UUID, ItemStack> worn = new HashMap<>();

    public abstract boolean accepts(ItemStack stack);

    public boolean wearing(UUID player) {
        return worn.containsKey(player);
    }

    public ItemStack worn(UUID player) {
        return worn.getOrDefault(player, ItemStack.EMPTY);
    }

    public ItemStack wornItem(UUID player) {
        ItemStack stack = worn.get(player);
        return stack != null ? stack.copy() : ItemStack.EMPTY;
    }

    public void wear(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            worn.remove(player.getUUID());
        } else {
            worn.put(player.getUUID(), stack.copyWithCount(1));
        }
        setDirty();
    }

    protected void saveWorn(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, ItemStack> entry : worn.entrySet()) {
            all.put(entry.getKey().toString(), entry.getValue().save(new CompoundTag()));
        }
        tag.put("Players", all);
    }

    protected void loadWorn(CompoundTag tag) {
        CompoundTag all = tag.getCompound("Players");
        for (String key : all.getAllKeys()) {
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ItemStack stack = ItemStack.of(all.getCompound(key));
            if (!accepts(stack)) {
                Hexwright.LOGGER.warn("Dropping worn {} of player {}: not wearable there in this version",
                    all.getCompound(key).getString("id"), key);
                continue;
            }
            worn.put(player, stack);
        }
    }
}
