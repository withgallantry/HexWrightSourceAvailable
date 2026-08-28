package com.bluup.hexwright.server.staff_assembly;

import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.utils.NBTHelper;
import at.petrak.hexcasting.common.items.storage.ItemScroll;
import at.petrak.hexcasting.common.lib.HexItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StaffGreatSpellData {
    public static final int MAX_LEARNED = 10;

    private static final String TAG_ROOT = "hexwright_amethyst_core";
    private static final String TAG_LEARNT = "great_spells";
    private static final String TAG_OP_ID = "op_id";
    private static final String TAG_PATTERN = "pattern";

    private StaffGreatSpellData() {
    }

    public enum LearnResult {
        ADDED,
        ALREADY_LEARNED,
        FULL,
        SCROLL_NOT_READY,
        INVALID_SCROLL
    }

    public record LearnedGreatSpell(String opId, CompoundTag pattern) {
        public ItemStack toDisplayScroll() {
            ItemStack scroll = new ItemStack(HexItems.SCROLL_LARGE);
            NBTHelper.putString(scroll, ItemScroll.TAG_OP_ID, opId);
            NBTHelper.putCompound(scroll, ItemScroll.TAG_PATTERN, pattern.copy());
            return scroll;
        }

        public PatternIota toPatternIota() {
            return new PatternIota(HexPattern.fromNBT(pattern));
        }

        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_OP_ID, opId);
            tag.put(TAG_PATTERN, pattern.copy());
            return tag;
        }
    }

    public static boolean isGreatSpellScroll(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemScroll)) {
            return false;
        }
        String opId = NBTHelper.getString(stack, ItemScroll.TAG_OP_ID);
        return opId != null && !opId.isBlank();
    }

    public static int count(ItemStack staff) {
        return getLearned(staff).size();
    }

    public static int maxLearned(ItemStack staff) {
        ItemStack coreItem = StaffAssemblyData.getCoreItem(staff);
        return StaffCoreData.amethystSlots(StaffCoreData.getQuality(coreItem));
    }

    public static List<LearnedGreatSpell> getLearned(ItemStack staff) {
        List<LearnedGreatSpell> raw = getLearnedRaw(staff);
        int cap = maxLearned(staff);
        return raw.size() <= cap ? raw : raw.subList(0, cap);
    }

    private static List<LearnedGreatSpell> getLearnedRaw(ItemStack staff) {
        CompoundTag root = NBTHelper.getCompound(staff, TAG_ROOT);
        if (root == null || !root.contains(TAG_LEARNT, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag list = root.getList(TAG_LEARNT, Tag.TAG_COMPOUND);
        List<LearnedGreatSpell> out = new ArrayList<>();
        for (Tag raw : list) {
            if (!(raw instanceof CompoundTag entry)) {
                continue;
            }
            String opId = entry.getString(TAG_OP_ID);
            if (opId == null || opId.isBlank()) {
                continue;
            }
            if (!entry.contains(TAG_PATTERN, Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag pattern = entry.getCompound(TAG_PATTERN).copy();
            try {
                HexPattern.fromNBT(pattern);
            } catch (RuntimeException ignored) {
                continue;
            }
            out.add(new LearnedGreatSpell(opId, pattern));
            if (out.size() >= MAX_LEARNED) {
                break;
            }
        }
        return out;
    }

    public static LearnResult learnFromScroll(ItemStack staff, ItemStack scroll) {
        if (!isGreatSpellScroll(scroll)) {
            return LearnResult.INVALID_SCROLL;
        }

        String rawOpId = NBTHelper.getString(scroll, ItemScroll.TAG_OP_ID);
        if (rawOpId == null || rawOpId.isBlank()) {
            return LearnResult.INVALID_SCROLL;
        }

        ResourceLocation opId = ResourceLocation.tryParse(rawOpId);
        if (opId == null) {
            return LearnResult.INVALID_SCROLL;
        }

        CompoundTag pattern = NBTHelper.getCompound(scroll, ItemScroll.TAG_PATTERN);
        if (pattern == null) {
            return LearnResult.SCROLL_NOT_READY;
        }
        pattern = pattern.copy();
        try {
            HexPattern.fromNBT(pattern);
        } catch (RuntimeException ignored) {
            return LearnResult.SCROLL_NOT_READY;
        }

        String normalizedOpId = opId.toString().toLowerCase(Locale.ROOT);
        List<LearnedGreatSpell> raw = new ArrayList<>(getLearnedRaw(staff));
        for (LearnedGreatSpell existing : raw) {
            if (existing.opId().equalsIgnoreCase(normalizedOpId)) {
                return LearnResult.ALREADY_LEARNED;
            }
        }

        if (raw.size() >= maxLearned(staff)) {
            return LearnResult.FULL;
        }

        raw.add(new LearnedGreatSpell(normalizedOpId, pattern));
        saveLearned(staff, raw);
        return LearnResult.ADDED;
    }

    public static LearnedGreatSpell forget(ItemStack staff, int index) {
        List<LearnedGreatSpell> raw = new ArrayList<>(getLearnedRaw(staff));
        int visible = Math.min(raw.size(), maxLearned(staff));
        if (index < 0 || index >= visible) {
            return null;
        }
        LearnedGreatSpell removed = raw.remove(index);
        saveLearned(staff, raw);
        return removed;
    }

    private static void saveLearned(ItemStack staff, List<LearnedGreatSpell> learned) {
        CompoundTag root = NBTHelper.getOrCreateCompound(staff, TAG_ROOT);
        if (learned.isEmpty()) {
            root.remove(TAG_LEARNT);
            return;
        }
        ListTag list = new ListTag();
        for (LearnedGreatSpell entry : learned) {
            list.add(entry.toTag());
        }
        root.put(TAG_LEARNT, list);
    }
}