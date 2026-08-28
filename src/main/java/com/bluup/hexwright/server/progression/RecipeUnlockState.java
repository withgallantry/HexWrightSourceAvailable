package com.bluup.hexwright.server.progression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RecipeUnlockState extends SavedData {

    private static final String STORAGE_ID = "hexwright_recipe_unlocks";

    private final Map<UUID, Set<String>> players = new HashMap<>();

    public static RecipeUnlockState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(RecipeUnlockState::load, RecipeUnlockState::new, STORAGE_ID);
    }

    public Set<String> unlocked(UUID player) {
        return players.computeIfAbsent(player, id -> new HashSet<>());
    }

    public boolean unlock(UUID player, String recipeNameKey) {
        if (!unlocked(player).add(recipeNameKey)) {
            return false;
        }
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, Set<String>> entry : players.entrySet()) {
            ListTag recipes = new ListTag();
            for (String recipe : entry.getValue()) {
                recipes.add(StringTag.valueOf(recipe));
            }
            all.put(entry.getKey().toString(), recipes);
        }
        tag.put("Players", all);
        return tag;
    }

    public static RecipeUnlockState load(CompoundTag tag) {
        RecipeUnlockState state = new RecipeUnlockState();
        CompoundTag all = tag.getCompound("Players");
        for (String key : all.getAllKeys()) {
            try {
                UUID id = UUID.fromString(key);
                Set<String> recipes = state.unlocked(id);
                ListTag list = all.getList(key, Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    recipes.add(list.getString(i));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return state;
    }
}
