package com.bluup.hexwright.server.hexicon;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.utils.NBTHelper;
import com.bluup.hexwright.server.item.HexiconItem;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.reliquary.ChestCastEnv;
import com.bluup.hexwright.server.staff_assembly.StaffAssemblyData;
import com.bluup.hexwright.server.staff_assembly.StaffCoreData;
import com.bluup.hexwright.server.staff_assembly.StaffPowers;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class HexiconData {
    public static final int BARS = 8;
    public static final int SLOTS_PER_BAR = 9;
    public static final int TOTAL_SLOTS = BARS * SLOTS_PER_BAR;

    private static final String TAG_ROOT = "hexwright_hexicon";
    private static final String TAG_LIBRARY_ID = "library_uuid";
    private static final String TAG_SELECTED_BAR = "selected_bar";
    private static final String TAG_SELECTED_SLOT = "selected_slot";
    private static final String TAG_SELECTED_BOUND = "selected_bound";
    private static final String TAG_WRITTEN_COUNT = "written_count";
    private static final String TAG_REVISION = "revision";
    private static final String TAG_CHAPTERS = "chapters";
    private static final String TAG_CHAPTER_NAME = "name";
    private static final String TAG_CHAPTER_ICON = "icon";
    private static final String TAG_SLOT_DISPLAYS = "slot_displays";
    private static final String TAG_SLOT_DISPLAY_NAME = "name";
    private static final String TAG_SLOT_DISPLAY_ICON = "icon";
    private static final String TAG_SLOT_DISPLAY_BOUND = "bound";

    private static final String TAG_PAYLOAD_DISPLAY_NAME = "hexwright_display_name";
    private static final String TAG_PAYLOAD_ICON = "hexwright_display_icon";

    private HexiconData() {
    }

    private static @Nullable MinecraftServer server = null;

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);
    }

    public static @Nullable ServerLevel libraryLevel() {
        MinecraftServer running = server;
        if (running == null || !running.isSameThread()) {
            return null;
        }
        return running.overworld();
    }

    public static @Nullable Pair<InteractionHand, ItemStack> findHeldSpellbook(Player player) {
        if (player == null) {
            return null;
        }

        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (isHexiconStack(main)) {
            return Pair.of(InteractionHand.MAIN_HAND, main);
        }

        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (isHexiconStack(off)) {
            return Pair.of(InteractionHand.OFF_HAND, off);
        }

        return null;
    }

    public static boolean isHexiconStack(ItemStack stack) {
        if (stack.getItem() instanceof HexiconItem) {
            return true;
        }
        return stack.is(HexwrightItems.CONFIGURABLE_STAFF) && StaffPowers.hasHexiconCore(stack);
    }

    public static @Nullable UUID getLibraryId(ItemStack stack) {
        CompoundTag root = NBTHelper.getCompound(stack, TAG_ROOT);
        return NBTHelper.getUUID(root, TAG_LIBRARY_ID);
    }

    public static @Nullable UUID getOrCreateLibraryId(ItemStack stack, Level world) {
        UUID existing = getLibraryId(stack);
        if (existing != null) {
            return existing;
        }
        if (!(world instanceof ServerLevel serverLevel)) {
            return null;
        }

        UUID created = UUID.randomUUID();
        CompoundTag root = NBTHelper.getOrCreateCompound(stack, TAG_ROOT);
        NBTHelper.putUUID(root, TAG_LIBRARY_ID, created);
        HexiconSavedData.open(serverLevel).getOrCreateLibrary(created);
        return created;
    }

    public static int getAvailableBars(ItemStack stack) {
        if (stack.is(HexwrightItems.CONFIGURABLE_STAFF) && StaffPowers.hasHexiconCore(stack)) {
            ItemStack coreItem = StaffAssemblyData.getCoreItem(stack);
            int unlocked = StaffCoreData.scribeChapters(StaffCoreData.getQuality(coreItem));
            return Math.max(1, Math.min(BARS, unlocked));
        }
        return BARS;
    }

    public static int getSelectedBar(ItemStack stack) {
        CompoundTag root = NBTHelper.getCompound(stack, TAG_ROOT);
        return clampBar(stack, NBTHelper.getInt(root, TAG_SELECTED_BAR, 0));
    }

    public static int getSelectedSlot(ItemStack stack) {
        CompoundTag root = NBTHelper.getCompound(stack, TAG_ROOT);
        return clampSlot(NBTHelper.getInt(root, TAG_SELECTED_SLOT, 0));
    }

    public static void setSelectedBarAndSlot(ItemStack stack, int barIndex, int slotIndex) {
        CompoundTag root = NBTHelper.getOrCreateCompound(stack, TAG_ROOT);
        NBTHelper.putInt(root, TAG_SELECTED_BAR, clampBar(stack, barIndex));
        NBTHelper.putInt(root, TAG_SELECTED_SLOT, clampSlot(slotIndex));
    }

    public static int getAbsoluteSlotIndex(int barIndex, int slotIndex) {
        return clampBar(barIndex) * SLOTS_PER_BAR + clampSlot(slotIndex);
    }

    public static int getSelectedAbsoluteSlot(ItemStack stack) {
        return getAbsoluteSlotIndex(getSelectedBar(stack), getSelectedSlot(stack));
    }

    public static @Nullable UUID getSlotReference(ServerLevel world, UUID libraryId, int absoluteIndex) {
        return HexiconSavedData.open(world).getSlotReference(libraryId, absoluteIndex);
    }

    public static void setSlotReference(ServerLevel world, UUID libraryId, int absoluteIndex, @Nullable UUID payloadId) {
        HexiconSavedData.open(world).setSlotReference(libraryId, absoluteIndex, payloadId);
    }

    public static boolean isSlotEmpty(ServerLevel world, UUID libraryId, int absoluteIndex) {
        return getSlotReference(world, libraryId, absoluteIndex) == null;
    }

    public static @Nullable CompoundTag loadSpellPayload(ServerLevel world, UUID payloadId) {
        return HexiconSavedData.open(world).loadPayload(payloadId);
    }

    public static boolean writeSelectedSpell(ItemStack stack, @Nullable Iota payload) {
        ServerLevel serverLevel = libraryLevel();
        if (serverLevel == null) {
            return false;
        }

        if (ChestCastEnv.isScratch(stack)) {
            return false;
        }

        UUID libraryId = payload == null ? getLibraryId(stack) : getOrCreateLibraryId(stack, serverLevel);
        if (libraryId == null) {
            return payload == null;
        }

        int absolute = getSelectedAbsoluteSlot(stack);
        HexiconSavedData state = HexiconSavedData.open(serverLevel);
        UUID oldPayloadId = state.getSlotReference(libraryId, absolute);

        UUID newPayloadId = payload == null ? null : state.storePayload(IotaType.serialize(payload));
        state.setSlotReference(libraryId, absolute, newPayloadId);

        if (oldPayloadId != null && !oldPayloadId.equals(newPayloadId) && !state.isPayloadReferenced(oldPayloadId)) {
            state.removePayload(oldPayloadId);
        }

        setCachedSelectedBound(stack, payload != null);
        refreshCachedWrittenCount(serverLevel, stack);
        bumpRevision(stack);
        return true;
    }

    private static void bumpRevision(ItemStack stack) {
        CompoundTag root = NBTHelper.getOrCreateCompound(stack, TAG_ROOT);
        NBTHelper.putInt(root, TAG_REVISION, NBTHelper.getInt(root, TAG_REVISION, 0) + 1);
    }

    public static @Nullable CompoundTag readSelectedSpellTag(ItemStack stack) {
        ServerLevel serverLevel = libraryLevel();
        if (serverLevel == null) {
            return null;
        }

        UUID libraryId = getLibraryId(stack);
        if (libraryId == null) {
            setCachedSelectedBound(stack, false);
            return null;
        }

        UUID payloadId = getSlotReference(serverLevel, libraryId, getSelectedAbsoluteSlot(stack));
        if (payloadId == null) {
            setCachedSelectedBound(stack, false);
            return null;
        }

        CompoundTag payloadTag = loadSpellPayload(serverLevel, payloadId);
        if (payloadTag == null || payloadTag.isEmpty()) {
            setCachedSelectedBound(stack, false);
            return null;
        }

        setCachedSelectedBound(stack, true);
        return payloadTag;
    }


    public static void refreshCachedSelectedBound(ServerLevel world, ItemStack stack) {
        UUID libraryId = getLibraryId(stack);
        if (libraryId == null) {
            setCachedSelectedBound(stack, false);
            return;
        }
        boolean bound = !isSlotEmpty(world, libraryId, getSelectedAbsoluteSlot(stack));
        setCachedSelectedBound(stack, bound);
    }

    public static boolean getCachedSelectedBound(ItemStack stack) {
        CompoundTag root = NBTHelper.getCompound(stack, TAG_ROOT);
        return NBTHelper.getBoolean(root, TAG_SELECTED_BOUND, false);
    }

    public static void setCachedSelectedBound(ItemStack stack, boolean bound) {
        CompoundTag root = NBTHelper.getOrCreateCompound(stack, TAG_ROOT);
        NBTHelper.putBoolean(root, TAG_SELECTED_BOUND, bound);
    }

    public static int getCachedWrittenCount(ItemStack stack) {
        CompoundTag root = NBTHelper.getCompound(stack, TAG_ROOT);
        return NBTHelper.getInt(root, TAG_WRITTEN_COUNT, 0);
    }

    public static void refreshCachedWrittenCount(ServerLevel world, ItemStack stack) {
        UUID libraryId = getLibraryId(stack);
        int count = 0;
        if (libraryId != null) {
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                if (!isSlotEmpty(world, libraryId, i)) {
                    count++;
                }
            }
        }
        CompoundTag root = NBTHelper.getOrCreateCompound(stack, TAG_ROOT);
        NBTHelper.putInt(root, TAG_WRITTEN_COUNT, count);
    }

    public static String getChapterName(ItemStack stack, int barIndex) {
        int index = clampBar(barIndex);
        CompoundTag chapterTag = getChapterTag(stack, index, false);
        String fallback = "Chapter " + (index + 1);
        if (chapterTag == null) {
            return fallback;
        }
        String name = chapterTag.getString(TAG_CHAPTER_NAME);
        return name == null || name.isBlank() ? fallback : name;
    }

    public static void setChapterName(ItemStack stack, int barIndex, String name) {
        int index = clampBar(barIndex);
        CompoundTag chapterTag = getChapterTag(stack, index, true);
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            chapterTag.remove(TAG_CHAPTER_NAME);
            return;
        }
        chapterTag.putString(TAG_CHAPTER_NAME, trimmed);
    }

    public static @Nullable ResourceLocation getChapterIcon(ItemStack stack, int barIndex) {
        int index = clampBar(barIndex);
        CompoundTag chapterTag = getChapterTag(stack, index, false);
        if (chapterTag == null) {
            return null;
        }
        return parseResourceLocation(chapterTag.getString(TAG_CHAPTER_ICON));
    }

    public static void setChapterIcon(ItemStack stack, int barIndex, @Nullable ResourceLocation icon) {
        int index = clampBar(barIndex);
        CompoundTag chapterTag = getChapterTag(stack, index, true);
        if (icon == null) {
            chapterTag.remove(TAG_CHAPTER_ICON);
            return;
        }
        chapterTag.putString(TAG_CHAPTER_ICON, icon.toString());
    }

    public static SpellDisplay getCachedSpellDisplay(ItemStack stack, int barIndex, int slotIndex) {
        int clampedSlot = clampSlot(slotIndex);
        String fallback = "Spell " + (clampedSlot + 1);
        CompoundTag slotTag = getSlotDisplayTag(stack, getAbsoluteSlotIndex(barIndex, clampedSlot), false);
        if (slotTag == null) {
            return new SpellDisplay(fallback, null);
        }
        String name = slotTag.getString(TAG_SLOT_DISPLAY_NAME);
        if (name == null || name.isBlank()) {
            name = fallback;
        }
        ResourceLocation icon = parseResourceLocation(slotTag.getString(TAG_SLOT_DISPLAY_ICON));
        return new SpellDisplay(name, icon);
    }

    public static boolean getCachedSlotBound(ItemStack stack, int barIndex, int slotIndex) {
        CompoundTag slotTag = getSlotDisplayTag(stack, getAbsoluteSlotIndex(barIndex, slotIndex), false);
        if (slotTag == null) {
            return false;
        }
        return slotTag.getBoolean(TAG_SLOT_DISPLAY_BOUND);
    }

    public static void refreshCachedBarDisplay(ServerLevel world, ItemStack stack, int barIndex) {
        int clampedBar = clampBar(barIndex);
        UUID libraryId = getLibraryId(stack);
        if (libraryId == null) {
            for (int slot = 0; slot < SLOTS_PER_BAR; slot++) {
                clearCachedSpellDisplay(stack, clampedBar, slot);
            }
            return;
        }

        for (int slot = 0; slot < SLOTS_PER_BAR; slot++) {
            int absolute = getAbsoluteSlotIndex(clampedBar, slot);
            UUID payloadId = getSlotReference(world, libraryId, absolute);
            if (payloadId == null) {
                clearCachedSpellDisplay(stack, clampedBar, slot);
                continue;
            }

            SpellDisplay display = getSpellDisplay(world, payloadId, slot);
            setCachedSpellDisplay(stack, clampedBar, slot, display.name(), display.icon(), true);
        }
    }

    public static void setCachedSpellDisplay(ItemStack stack, int barIndex, int slotIndex, String name, @Nullable ResourceLocation icon, boolean bound) {
        int clampedBar = clampBar(barIndex);
        int clampedSlot = clampSlot(slotIndex);
        CompoundTag slotTag = getSlotDisplayTag(stack, getAbsoluteSlotIndex(clampedBar, clampedSlot), true);

        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            slotTag.remove(TAG_SLOT_DISPLAY_NAME);
        } else {
            slotTag.putString(TAG_SLOT_DISPLAY_NAME, trimmed);
        }

        if (icon == null) {
            slotTag.remove(TAG_SLOT_DISPLAY_ICON);
        } else {
            slotTag.putString(TAG_SLOT_DISPLAY_ICON, icon.toString());
        }

        slotTag.putBoolean(TAG_SLOT_DISPLAY_BOUND, bound);
    }

    public static void clearCachedSpellDisplay(ItemStack stack, int barIndex, int slotIndex) {
        CompoundTag slotTag = getSlotDisplayTag(stack, getAbsoluteSlotIndex(barIndex, slotIndex), true);
        slotTag.remove(TAG_SLOT_DISPLAY_NAME);
        slotTag.remove(TAG_SLOT_DISPLAY_ICON);
        slotTag.putBoolean(TAG_SLOT_DISPLAY_BOUND, false);
    }

    public static @Nullable UUID getSelectedPayloadId(ServerLevel world, ItemStack stack) {
        UUID libraryId = getLibraryId(stack);
        if (libraryId == null) {
            return null;
        }
        return getSlotReference(world, libraryId, getSelectedAbsoluteSlot(stack));
    }

    public static @Nullable UUID getPayloadId(ServerLevel world, UUID libraryId, int barIndex, int slotIndex) {
        if (libraryId == null) {
            return null;
        }
        return getSlotReference(world, libraryId, getAbsoluteSlotIndex(barIndex, slotIndex));
    }

    public static SpellDisplay getSpellDisplay(ServerLevel world, UUID payloadId, int slotIndex) {
        CompoundTag payloadTag = HexiconSavedData.open(world).loadPayload(payloadId);
        String fallback = "Spell " + (clampSlot(slotIndex) + 1);
        if (payloadTag == null) {
            return new SpellDisplay(fallback, null);
        }
        String name = payloadTag.getString(TAG_PAYLOAD_DISPLAY_NAME);
        if (name == null || name.isBlank()) {
            name = fallback;
        }
        ResourceLocation icon = parseResourceLocation(payloadTag.getString(TAG_PAYLOAD_ICON));
        return new SpellDisplay(name, icon);
    }

    public static void setSpellDisplay(ServerLevel world, UUID payloadId, String name, @Nullable ResourceLocation icon) {
        HexiconSavedData state = HexiconSavedData.open(world);
        CompoundTag payloadTag = state.loadPayload(payloadId);
        if (payloadTag == null) {
            return;
        }

        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            payloadTag.remove(TAG_PAYLOAD_DISPLAY_NAME);
        } else {
            payloadTag.putString(TAG_PAYLOAD_DISPLAY_NAME, trimmed);
        }

        if (icon == null) {
            payloadTag.remove(TAG_PAYLOAD_ICON);
        } else {
            payloadTag.putString(TAG_PAYLOAD_ICON, icon.toString());
        }

        state.setPayload(payloadId, payloadTag);
    }

    private static @Nullable CompoundTag getChapterTag(ItemStack stack, int barIndex, boolean create) {
        CompoundTag root = create ? NBTHelper.getOrCreateCompound(stack, TAG_ROOT) : NBTHelper.getCompound(stack, TAG_ROOT);
        if (root == null) {
            return null;
        }

        ListTag chapters;
        if (root.contains(TAG_CHAPTERS, Tag.TAG_LIST)) {
            chapters = root.getList(TAG_CHAPTERS, CompoundTag.TAG_COMPOUND);
        } else if (create) {
            chapters = new ListTag();
            root.put(TAG_CHAPTERS, chapters);
        } else {
            return null;
        }

        while (chapters.size() <= barIndex) {
            chapters.add(new CompoundTag());
        }

        return chapters.getCompound(barIndex);
    }

    private static @Nullable CompoundTag getSlotDisplayTag(ItemStack stack, int absoluteIndex, boolean create) {
        CompoundTag root = create ? NBTHelper.getOrCreateCompound(stack, TAG_ROOT) : NBTHelper.getCompound(stack, TAG_ROOT);
        if (root == null) {
            return null;
        }

        ListTag slotDisplays;
        if (root.contains(TAG_SLOT_DISPLAYS, Tag.TAG_LIST)) {
            slotDisplays = root.getList(TAG_SLOT_DISPLAYS, CompoundTag.TAG_COMPOUND);
        } else if (create) {
            slotDisplays = new ListTag();
            root.put(TAG_SLOT_DISPLAYS, slotDisplays);
        } else {
            return null;
        }

        while (slotDisplays.size() <= absoluteIndex) {
            slotDisplays.add(new CompoundTag());
        }

        return slotDisplays.getCompound(absoluteIndex);
    }

    private static @Nullable ResourceLocation parseResourceLocation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new ResourceLocation(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int clampBar(ItemStack stack, int barIndex) {
        return Math.max(0, Math.min(getAvailableBars(stack) - 1, barIndex));
    }

    private static int clampBar(int barIndex) {
        if (barIndex < 0) {
            return 0;
        }
        if (barIndex >= BARS) {
            return BARS - 1;
        }
        return barIndex;
    }

    private static int clampSlot(int slotIndex) {
        if (slotIndex < 0) {
            return 0;
        }
        if (slotIndex >= SLOTS_PER_BAR) {
            return SLOTS_PER_BAR - 1;
        }
        return slotIndex;
    }

    public record SpellDisplay(String name, @Nullable ResourceLocation icon) {
    }
}
