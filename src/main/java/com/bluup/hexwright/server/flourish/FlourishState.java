package com.bluup.hexwright.server.flourish;

import com.bluup.hexwright.common.flourish.Flourish;
import com.bluup.hexwright.server.worn.WornItemState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FlourishState extends WornItemState {

    private static final String STORAGE_ID = "hexwright_flourishes";

    private final Map<UUID, Set<Flourish>> found = new HashMap<>();

    public static FlourishState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(FlourishState::load, FlourishState::new, STORAGE_ID);
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return FlourishItem.isWearable(stack);
    }

    public Set<Flourish> found(UUID player) {
        return found.getOrDefault(player, Collections.emptySet());
    }

    public void find(UUID player, Flourish flourish) {
        if (found.computeIfAbsent(player, uuid -> EnumSet.noneOf(Flourish.class)).add(flourish)) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        saveWorn(tag);
        CompoundTag allFound = new CompoundTag();
        for (Map.Entry<UUID, Set<Flourish>> entry : found.entrySet()) {
            ListTag ids = new ListTag();
            for (Flourish flourish : entry.getValue()) {
                ids.add(StringTag.valueOf(flourish.id()));
            }
            allFound.put(entry.getKey().toString(), ids);
        }
        tag.put("Found", allFound);
        return tag;
    }

    public static FlourishState load(CompoundTag tag) {
        FlourishState state = new FlourishState();
        state.loadWorn(tag);
        CompoundTag allFound = tag.getCompound("Found");
        for (String key : allFound.getAllKeys()) {
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Set<Flourish> flourishes = EnumSet.noneOf(Flourish.class);
            for (Tag id : allFound.getList(key, Tag.TAG_STRING)) {
                Flourish flourish = Flourish.byId(id.getAsString());
                if (flourish != null) {
                    flourishes.add(flourish);
                }
            }
            state.found.put(player, flourishes);
        }
        return state;
    }
}
