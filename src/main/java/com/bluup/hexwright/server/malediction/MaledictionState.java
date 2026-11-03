package com.bluup.hexwright.server.malediction;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.common.malediction.MaledictionForm;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MaledictionState extends SavedData {

    public static final int SLOTS = 3;

    private static final String STORAGE_ID = "hexwright_maledictions";

    private final Map<UUID, Caster> casters = new HashMap<>();

    private static final class Caster {
        final Set<MaledictionForm> forms = EnumSet.noneOf(MaledictionForm.class);
        final Set<MaledictionFinish> finishes = EnumSet.noneOf(MaledictionFinish.class);
        final ItemStack[] slots = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        final Map<MaledictionForm, Long> fullAt = new EnumMap<>(MaledictionForm.class);
    }

    public static MaledictionState get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(MaledictionState::load, MaledictionState::new, STORAGE_ID);
    }

    private Caster caster(UUID player) {
        return casters.computeIfAbsent(player, uuid -> new Caster());
    }


    public Set<MaledictionForm> forms(UUID player) {
        Caster caster = casters.get(player);
        return caster == null ? Collections.emptySet() : Collections.unmodifiableSet(caster.forms);
    }

    public Set<MaledictionFinish> finishes(UUID player) {
        Caster caster = casters.get(player);
        return caster == null ? Collections.emptySet() : Collections.unmodifiableSet(caster.finishes);
    }

    public boolean learn(UUID player, MaledictionForm form) {
        boolean learned = caster(player).forms.add(form);
        if (learned) {
            setDirty();
        }
        return learned;
    }

    public boolean learn(UUID player, MaledictionFinish finish) {
        boolean learned = caster(player).finishes.add(finish);
        if (learned) {
            setDirty();
        }
        return learned;
    }


    public ItemStack slot(UUID player, int slot) {
        Caster caster = casters.get(player);
        return caster == null ? ItemStack.EMPTY : caster.slots[slot];
    }

    public ItemStack swap(UUID player, int slot, ItemStack incoming) {
        Caster caster = caster(player);
        ItemStack outgoing = caster.slots[slot];
        caster.slots[slot] = incoming.isEmpty() ? ItemStack.EMPTY : incoming.copyWithCount(1);
        setDirty();
        return outgoing;
    }


    public int charges(UUID player, MaledictionForm form, long now) {
        Caster caster = casters.get(player);
        long fullAt = caster == null ? 0L : caster.fullAt.getOrDefault(form, 0L);
        long missingTicks = Math.max(0L, fullAt - now);
        int missing = (int) ((missingTicks + form.rechargeTicks() - 1) / form.rechargeTicks());
        return Math.max(0, form.charges() - missing);
    }

    public boolean spend(UUID player, MaledictionForm form, long now) {
        if (charges(player, form, now) < 1) {
            return false;
        }
        Caster caster = caster(player);
        caster.fullAt.put(form, Math.max(caster.fullAt.getOrDefault(form, 0L), now) + form.rechargeTicks());
        setDirty();
        return true;
    }

    public void refill(UUID player) {
        Caster caster = casters.get(player);
        if (caster != null && !caster.fullAt.isEmpty()) {
            caster.fullAt.clear();
            setDirty();
        }
    }


    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<UUID, Caster> entry : casters.entrySet()) {
            Caster caster = entry.getValue();
            CompoundTag own = new CompoundTag();
            ListTag forms = new ListTag();
            for (MaledictionForm form : caster.forms) {
                forms.add(StringTag.valueOf(form.id()));
            }
            own.put("Forms", forms);
            ListTag finishes = new ListTag();
            for (MaledictionFinish finish : caster.finishes) {
                finishes.add(StringTag.valueOf(finish.id()));
            }
            own.put("Finishes", finishes);
            ListTag slots = new ListTag();
            for (ItemStack stack : caster.slots) {
                slots.add(stack.save(new CompoundTag()));
            }
            own.put("Slots", slots);
            CompoundTag fullAt = new CompoundTag();
            for (Map.Entry<MaledictionForm, Long> charge : caster.fullAt.entrySet()) {
                fullAt.putLong(charge.getKey().id(), charge.getValue());
            }
            own.put("FullAt", fullAt);
            all.put(entry.getKey().toString(), own);
        }
        tag.put("Players", all);
        return tag;
    }

    public static MaledictionState load(CompoundTag tag) {
        MaledictionState state = new MaledictionState();
        CompoundTag all = tag.getCompound("Players");
        for (String key : all.getAllKeys()) {
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag own = all.getCompound(key);
            Caster caster = new Caster();
            for (Tag id : own.getList("Forms", Tag.TAG_STRING)) {
                MaledictionForm form = MaledictionForm.byId(id.getAsString());
                if (form != null) {
                    caster.forms.add(form);
                }
            }
            for (Tag id : own.getList("Finishes", Tag.TAG_STRING)) {
                MaledictionFinish finish = MaledictionFinish.byId(id.getAsString());
                if (finish != null) {
                    caster.finishes.add(finish);
                }
            }
            ListTag slots = own.getList("Slots", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(SLOTS, slots.size()); i++) {
                ItemStack stack = ItemStack.of(slots.getCompound(i));
                if (!stack.isEmpty() && MaledictionBlankItem.form(stack) == null) {
                    Hexwright.LOGGER.warn("Dropping internalized Malediction {} of player {}: not one this version knows",
                        slots.getCompound(i), key);
                    continue;
                }
                caster.slots[i] = stack;
            }
            CompoundTag fullAt = own.getCompound("FullAt");
            for (String formId : fullAt.getAllKeys()) {
                MaledictionForm form = MaledictionForm.byId(formId);
                if (form != null) {
                    caster.fullAt.put(form, fullAt.getLong(formId));
                }
            }
            state.casters.put(player, caster);
        }
        return state;
    }
}
