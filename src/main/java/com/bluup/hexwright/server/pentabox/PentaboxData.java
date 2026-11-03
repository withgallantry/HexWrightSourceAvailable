package com.bluup.hexwright.server.pentabox;

import at.petrak.hexcasting.api.utils.NBTHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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
    private static final String TAG_DISPLAY_ONLY = "DisplayOnly";

    private static final String TAG_LINK_ROOT = "hexwright_pentabox_link";
    private static final String TAG_LINK_PENTABOX_STACK = "PentaboxStack";
    private static final String TAG_LINK_DISPLAY = "Display";
    private static final String TAG_LINK_SLOT = "Slot";
    private static final String TAG_LINK_BOX_UUID = "BoxUuid";
    private static final String TAG_LINK_VERSION = "Version";
    private static final int LINK_VERSION = 2;

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
        NonNullList<ItemStack> sanitized = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        for (int i = 0; i < Math.min(SLOT_COUNT, items.size()); i++) {
            ItemStack stored = items.get(i);
            if (isLinkedStack(stored)) {
                stored = stored.copy();
                clearLinkTag(stored);
            }
            sanitized.set(i, stored);
        }
        CompoundTag root = NBTHelper.getOrCreateCompound(stack, TAG_ROOT);
        ContainerHelper.saveAllItems(root, sanitized);
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

    private static String reassignBoxUuid(ItemStack stack) {
        String uuid = UUID.randomUUID().toString();
        NBTHelper.putString(NBTHelper.getOrCreateCompound(stack, TAG_ROOT), TAG_BOX_UUID, uuid);
        return uuid;
    }

    private static void retireBoxUuid(ItemStack pentabox) {
        reassignBoxUuid(pentabox);
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

    public static boolean isLinkedStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
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

    public static String getLinkedBoxUuid(ItemStack stack) {
        CompoundTag link = getLinkTag(stack);
        return link == null ? "" : link.getString(TAG_LINK_BOX_UUID);
    }

    public static ItemStack getLinkedDisplay(ItemStack stack) {
        CompoundTag link = getLinkTag(stack);
        if (link == null) {
            return ItemStack.EMPTY;
        }
        CompoundTag snapshot;
        if (link.contains(TAG_LINK_DISPLAY, 10)) {
            snapshot = link.getCompound(TAG_LINK_DISPLAY);
        } else if (link.contains(TAG_LINK_PENTABOX_STACK, 10)) {
            snapshot = link.getCompound(TAG_LINK_PENTABOX_STACK);
        } else {
            return ItemStack.EMPTY;
        }
        ItemStack pentabox = ItemStack.of(snapshot);
        return pentabox.getItem() instanceof PentaboxItem ? pentabox : ItemStack.EMPTY;
    }

    public static void setLinkedDisplay(ItemStack projection, ItemStack pentabox) {
        CompoundTag tag = projection.getOrCreateTag();
        CompoundTag link = tag.contains(TAG_LINK_ROOT, 10) ? tag.getCompound(TAG_LINK_ROOT) : new CompoundTag();
        ItemStack snapshot = pentabox.copy();
        NBTHelper.getOrCreateCompound(snapshot, TAG_ROOT).putBoolean(TAG_DISPLAY_ONLY, true);
        link.put(TAG_LINK_DISPLAY, snapshot.save(new CompoundTag()));
        link.remove(TAG_LINK_PENTABOX_STACK);
        tag.put(TAG_LINK_ROOT, link);
    }

    public static boolean isDisplayOnly(ItemStack pentabox) {
        CompoundTag root = NBTHelper.getCompound(pentabox, TAG_ROOT);
        return root != null && root.getBoolean(TAG_DISPLAY_ONLY);
    }

    public static ItemStack resolvePentaboxStack(ItemStack stack) {
        if (stack.getItem() instanceof PentaboxItem) {
            return stack;
        }
        return getLinkedDisplay(stack);
    }

    public static ItemStack peekBox(MinecraftServer server, ItemStack projection) {
        PentaboxStore.Entry entry = entryFor(server, projection);
        return entry == null ? ItemStack.EMPTY : entry.box;
    }

    public static boolean discardStalePointer(MinecraftServer server, ItemStack stack) {
        if (server == null || !isLinkedStack(stack)) {
            return false;
        }
        if (entryFor(server, stack) != null) {
            return false;
        }
        clearLinkTag(stack);
        return true;
    }

    @Nullable
    private static PentaboxStore.Entry entryFor(MinecraftServer server, ItemStack projection) {
        if (server == null || !isLinkedStack(projection)) {
            return null;
        }
        migrateLegacyLink(server, projection);
        String key = getLinkedBoxUuid(projection);
        return key.isEmpty() ? null : PentaboxStore.get(server).peek(key);
    }

    private static void migrateLegacyLink(MinecraftServer server, ItemStack projection) {
        CompoundTag link = getLinkTag(projection);
        if (link == null || !link.contains(TAG_LINK_PENTABOX_STACK, 10)) {
            return;
        }
        ItemStack inline = ItemStack.of(link.getCompound(TAG_LINK_PENTABOX_STACK));
        if (!(inline.getItem() instanceof PentaboxItem)) {
            link.remove(TAG_LINK_PENTABOX_STACK);
            return;
        }
        String key = getOrCreateBoxUuid(inline);
        int slot = getLinkedSlot(projection);
        PentaboxStore store = PentaboxStore.get(server);
        if (store.peek(key) == null && slot >= 0 && slot < SLOT_COUNT) {
            setSlot(inline, slot, ItemStack.EMPTY);
        }
        store.park(key, inline, null, slot, gameTime(server));
        link.putString(TAG_LINK_BOX_UUID, key);
        link.putInt(TAG_LINK_VERSION, LINK_VERSION);
        setLinkedDisplay(projection, inline);
    }

    private static long gameTime(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static boolean normalizeStacks(Player player, ItemStack pentabox) {
        if (!(pentabox.getItem() instanceof PentaboxItem)) {
            return false;
        }
        NonNullList<ItemStack> items = loadItems(pentabox);
        boolean changed = false;
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stored = items.get(i);
            if (stored.getCount() <= 1) {
                continue;
            }
            ItemStack surplus = stored.split(stored.getCount() - 1);
            changed = true;
            if (!player.getInventory().add(surplus)) {
                player.drop(surplus, false);
            }
        }
        if (changed) {
            saveItems(pentabox, items);
        }
        return changed;
    }

    public static boolean deploySelectedToHand(Player player, InteractionHand hand) {
        ItemStack pentabox = player.getItemInHand(hand);
        if (!(pentabox.getItem() instanceof PentaboxItem) || isDisplayOnly(pentabox)) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        normalizeStacks(player, pentabox);

        int index = getSelectedIndex(pentabox);
        if (index < 0 || index >= SLOT_COUNT) {
            return false;
        }

        NonNullList<ItemStack> items = loadItems(pentabox);
        ItemStack drawn = items.get(index);
        if (drawn.isEmpty()) {
            return false;
        }
        items.set(index, ItemStack.EMPTY);
        saveItems(pentabox, items);

        PentaboxStore store = PentaboxStore.get(server);
        String key = getOrCreateBoxUuid(pentabox);
        if (store.peek(key) != null) {
            key = reassignBoxUuid(pentabox);
        }
        store.park(key, pentabox, player.getUUID().toString(), index, gameTime(server));

        CompoundTag link = new CompoundTag();
        link.putString(TAG_LINK_BOX_UUID, key);
        link.putInt(TAG_LINK_SLOT, index);
        link.putInt(TAG_LINK_VERSION, LINK_VERSION);
        drawn.getOrCreateTag().put(TAG_LINK_ROOT, link);
        setLinkedDisplay(drawn, pentabox);

        player.setItemInHand(hand, drawn);
        return true;
    }

    public static boolean selectFromContext(Player player, InteractionHand hand, int index) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);

        ItemStack pentabox;
        if (held.getItem() instanceof PentaboxItem) {
            if (isDisplayOnly(held)) {
                return false;
            }
            pentabox = held;
        } else if (isLinkedStack(held)) {
            pentabox = stowProjection(player, held);
            if (pentabox.isEmpty()) {
                return false;
            }
        } else {
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

    public static boolean withdrawProjection(Player player, InteractionHand hand) {
        ItemStack box = detachProjection(player.getServer(), player.getItemInHand(hand));
        if (box.isEmpty()) {
            return false;
        }
        player.setItemInHand(hand, box);
        return true;
    }

    public static ItemStack stowProjection(Player player, ItemStack projection) {
        int slot = getLinkedSlot(projection);
        ItemStack pentabox = detachProjection(player.getServer(), projection);
        if (pentabox.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack item = projection.copy();
        projection.setCount(0);

        NonNullList<ItemStack> items = loadItems(pentabox);
        int home = slot >= 0 && slot < usableSlotCount(pentabox) && items.get(slot).isEmpty()
            ? slot
            : firstFreeSlot(pentabox, items);
        if (home < 0) {
            player.drop(item, false);
            return pentabox;
        }
        items.set(home, item);
        saveItems(pentabox, items);
        return pentabox;
    }

    private static int firstFreeSlot(ItemStack pentabox, NonNullList<ItemStack> items) {
        int usable = usableSlotCount(pentabox);
        for (int i = 0; i < usable; i++) {
            if (items.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static ItemStack detachProjection(MinecraftServer server, ItemStack projection) {
        if (server == null || !isLinkedStack(projection)) {
            return ItemStack.EMPTY;
        }
        migrateLegacyLink(server, projection);
        String key = getLinkedBoxUuid(projection);
        clearLinkTag(projection);
        return key.isEmpty() ? ItemStack.EMPTY : unpark(server, key);
    }

    public static ItemStack reclaimOrphan(MinecraftServer server, String key) {
        return unpark(server, key);
    }

    private static ItemStack unpark(MinecraftServer server, String key) {
        ItemStack pentabox = PentaboxStore.get(server).take(key);
        if (!(pentabox.getItem() instanceof PentaboxItem)) {
            return ItemStack.EMPTY;
        }
        retireBoxUuid(pentabox);
        setSelectedIndex(pentabox, -1);
        return pentabox;
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
