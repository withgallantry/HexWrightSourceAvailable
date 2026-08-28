package com.bluup.hexwright.server.pentabox;

import at.petrak.hexcasting.api.utils.NBTHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.item.PentaboxItem;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;

import java.util.UUID;

public final class PentaboxData {
    public static final int GRID_COLUMNS = 5;
    public static final int GRID_ROWS = 5;
    public static final int SLOT_COUNT = GRID_COLUMNS * GRID_ROWS;

    private static final String TAG_ROOT = "hexwright_pentabox";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SELECTED = "SelectedIndex";
    private static final String TAG_BOX_UUID = "BoxUuid";
    private static final String TAG_QUALITY = "Quality";

    private static final String TAG_LINK_ROOT = "hexwright_pentabox_link";
    private static final String TAG_LINK_PENTABOX_STACK = "PentaboxStack";
    private static final String TAG_LINK_SLOT = "Slot";
    private static final String TAG_LINK_BOX_UUID = "BoxUuid";
    private static final String TAG_LINK_VERSION = "Version";

    public static NonNullList<ItemStack> loadItems(ItemStack stack) {
        NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        if (stack.isEmpty()) {
            return items;
        }
        CompoundTag root = NBTHelper.getCompound(stack, TAG_ROOT);
        if (root != null && NBTHelper.hasList(root, TAG_ITEMS, 10)) {
            ContainerHelper.loadAllItems(root, items);
        }
        return items;
    }

    public static void saveItems(ItemStack stack, NonNullList<ItemStack> items) {
        CompoundTag root = NBTHelper.getOrCreateCompound(stack, TAG_ROOT);
        ContainerHelper.saveAllItems(root, items);
    }

    public static int getSelectedIndex(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }
        CompoundTag root = NBTHelper.getCompound(stack, TAG_ROOT);
        return root == null ? -1 : NBTHelper.getInt(root, TAG_SELECTED, -1);
    }

    public static void setSelectedIndex(ItemStack stack, int index) {
        CompoundTag root = NBTHelper.getOrCreateCompound(stack, TAG_ROOT);
        NBTHelper.putInt(root, TAG_SELECTED, index);
    }

    public static PocketCasterData.Quality getQuality(ItemStack stack) {
        if (stack.isEmpty()) {
            return PocketCasterData.Quality.MASTERWORK;
        }
        CompoundTag root = NBTHelper.getCompound(stack, TAG_ROOT);
        if (root == null || !root.contains(TAG_QUALITY)) {
            return PocketCasterData.Quality.MASTERWORK;
        }
        return PocketCasterData.Quality.byName(root.getString(TAG_QUALITY));
    }

    public static int usableRows(ItemStack stack) {
        return getQuality(stack).itemSlots();
    }

    public static int usableSlotCount(ItemStack stack) {
        return usableRows(stack) * GRID_COLUMNS;
    }

    public static ItemStack create(PocketCasterData.Quality quality) {
        ItemStack stack = new ItemStack(HexwrightItems.HARMONIZED_PENTABOX);
        CompoundTag root = NBTHelper.getOrCreateCompound(stack, TAG_ROOT);
        root.putString(TAG_QUALITY, quality.name());
        return stack;
    }

    public static String getOrCreateBoxUuid(ItemStack stack) {
        CompoundTag root = NBTHelper.getOrCreateCompound(stack, TAG_ROOT);
        String uuid = root.contains(TAG_BOX_UUID, 8) ? NBTHelper.getString(root, TAG_BOX_UUID) : "";
        if (uuid.isEmpty()) {
            uuid = UUID.randomUUID().toString();
            NBTHelper.putString(root, TAG_BOX_UUID, uuid);
        }
        return uuid;
    }

    public static ItemStack getSelectedStack(ItemStack stack) {
        int index = getSelectedIndex(stack);
        if (index < 0 || index >= usableSlotCount(stack)) {
            return ItemStack.EMPTY;
        }
        return loadItems(stack).get(index);
    }

    public static void setSlot(ItemStack stack, int index, ItemStack value) {
        NonNullList<ItemStack> items = loadItems(stack);
        items.set(index, value);
        saveItems(stack, items);
        if (value.isEmpty() && getSelectedIndex(stack) == index) {
            setSelectedIndex(stack, -1);
        }
    }

    public static boolean syncDeployedStack(ItemStack deployed) {
        if (deployed.isEmpty() || !isLinkedStack(deployed)) {
            return false;
        }
        ItemStack pentabox = getLinkedPentabox(deployed);
        if (!(pentabox.getItem() instanceof PentaboxItem)) {
            return false;
        }
        int slot = getLinkedSlot(deployed);
        if (slot < 0 || slot >= SLOT_COUNT) {
            return false;
        }

        NonNullList<ItemStack> items = loadItems(pentabox);
        if (items.get(slot).isEmpty()) {
            return false;
        }

        ItemStack stored = deployed.copy();
        clearLinkTag(stored);
        if (ItemStack.matches(items.get(slot), stored)) {
            return false;
        }

        items.set(slot, stored);
        saveItems(pentabox, items);
        setLinkedPentabox(deployed, pentabox);
        return true;
    }

    public static boolean isLinkedStack(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_LINK_ROOT, 10);
    }

    public static int getLinkedSlot(ItemStack stack) {
        CompoundTag link = getLinkTag(stack);
        if (link == null || !link.contains(TAG_LINK_SLOT, 3)) {
            return -1;
        }
        return link.getInt(TAG_LINK_SLOT);
    }

    public static ItemStack getLinkedPentabox(ItemStack stack) {
        CompoundTag link = getLinkTag(stack);
        if (link == null || !link.contains(TAG_LINK_PENTABOX_STACK, 10)) {
            return ItemStack.EMPTY;
        }
        ItemStack pentabox = ItemStack.of(link.getCompound(TAG_LINK_PENTABOX_STACK));
        return pentabox.getItem() instanceof PentaboxItem ? pentabox : ItemStack.EMPTY;
    }

    public static void setLinkedPentabox(ItemStack linkedStack, ItemStack pentabox) {
        CompoundTag tag = linkedStack.getOrCreateTag();
        CompoundTag link = tag.contains(TAG_LINK_ROOT, 10) ? tag.getCompound(TAG_LINK_ROOT) : new CompoundTag();
        CompoundTag pentaboxTag = new CompoundTag();
        pentabox.save(pentaboxTag);
        link.put(TAG_LINK_PENTABOX_STACK, pentaboxTag);
        tag.put(TAG_LINK_ROOT, link);
    }

    public static ItemStack resolvePentaboxStack(ItemStack stack) {
        if (stack.getItem() instanceof PentaboxItem) {
            return stack;
        }
        return getLinkedPentabox(stack);
    }

    public static boolean deploySelectedToHand(Player player, InteractionHand hand) {
        ItemStack pentabox = player.getItemInHand(hand);
        if (!(pentabox.getItem() instanceof PentaboxItem)) {
            return false;
        }

        int index = getSelectedIndex(pentabox);
        if (index < 0 || index >= SLOT_COUNT) {
            return false;
        }

        ItemStack inner = loadItems(pentabox).get(index);
        if (inner.isEmpty()) {
            return false;
        }

        ItemStack deployed = inner.copy();
        CompoundTag link = new CompoundTag();
        CompoundTag pentaboxTag = new CompoundTag();
        pentabox.save(pentaboxTag);
        link.put(TAG_LINK_PENTABOX_STACK, pentaboxTag);
        link.putInt(TAG_LINK_SLOT, index);
        link.putString(TAG_LINK_BOX_UUID, getOrCreateBoxUuid(pentabox));
        link.putInt(TAG_LINK_VERSION, 1);
        deployed.getOrCreateTag().put(TAG_LINK_ROOT, link);

        player.setItemInHand(hand, deployed);
        return true;
    }

    public static boolean selectFromContext(Player player, InteractionHand hand, int index) {
        ItemStack held = player.getItemInHand(hand);
        syncDeployedStack(held);
        ItemStack pentabox = resolvePentaboxStack(held);
        if (!(pentabox.getItem() instanceof PentaboxItem)) {
            return false;
        }

        if (index < 0 || index >= usableSlotCount(pentabox) || loadItems(pentabox).get(index).isEmpty()) {
            setSelectedIndex(pentabox, -1);
            player.setItemInHand(hand, pentabox);
            return true;
        }

        setSelectedIndex(pentabox, index);
        player.setItemInHand(hand, pentabox);
        return deploySelectedToHand(player, hand);
    }

    public static void clearLinkTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(TAG_LINK_ROOT);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    private static CompoundTag getLinkTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_LINK_ROOT, 10)) {
            return null;
        }
        return tag.getCompound(TAG_LINK_ROOT);
    }

    private PentaboxData() {
    }
}
