package com.bluup.hexwright.server.flourish;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.flourish.Flourish;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlourishState extends SavedData {

    private static final String STORAGE_ID = "hexwright_flourishes";

    private final Map<UUID, ItemStack> worn = new HashMap<>();

    public static FlourishState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(FlourishState::load, FlourishState::new, STORAGE_ID);
    }

    public @Nullable Flourish flourish(UUID player) {
        ItemStack stack = worn.get(player);
        return stack != null ? FlourishItem.of(stack) : null;
    }

    public ItemStack wornItem(UUID player) {
        ItemStack stack = worn.get(player);
        return stack != null ? stack.copy() : ItemStack.EMPTY;
    }

    public void wear(UUID player, ItemStack stack) {
        if (stack.isEmpty()) {
            worn.remove(player);
        } else {
            worn.put(player, stack.copyWithCount(1));
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, ItemStack> entry : worn.entrySet()) {
            all.put(entry.getKey().toString(), entry.getValue().save(new CompoundTag()));
        }
        tag.put("Players", all);
        return tag;
    }

    public static FlourishState load(CompoundTag tag) {
        FlourishState state = new FlourishState();
        CompoundTag all = tag.getCompound("Players");
        for (String key : all.getAllKeys()) {
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ItemStack stack = ItemStack.of(all.getCompound(key));
            if (FlourishItem.of(stack) == null) {
                Hexwright.LOGGER.warn("Dropping worn flourish {} of player {}: not a flourish in this version",
                    all.getCompound(key).getString("id"), key);
                continue;
            }
            state.worn.put(player, stack);
        }
        return state;
    }
}
