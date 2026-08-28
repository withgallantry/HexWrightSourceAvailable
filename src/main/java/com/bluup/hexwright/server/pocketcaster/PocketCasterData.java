package com.bluup.hexwright.server.pocketcaster;

import at.petrak.hexcasting.api.utils.NBTHelper;
import com.bluup.hexwright.server.item.HexwrightItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class PocketCasterData {

    public enum Quality {
        CRUDE(1),
        SOUND(2),
        FINE(3),
        EXQUISITE(4),
        MASTERWORK(5);

        private final int itemSlots;

        Quality(int itemSlots) {
            this.itemSlots = itemSlots;
        }

        public int itemSlots() {
            return itemSlots;
        }

        public String translationKey() {
            return "quality.hexwright." + name().toLowerCase(Locale.ROOT);
        }

        public ChatFormatting color() {
            return switch (this) {
                case CRUDE -> ChatFormatting.GRAY;
                case SOUND -> ChatFormatting.GREEN;
                case FINE -> ChatFormatting.AQUA;
                case EXQUISITE -> ChatFormatting.LIGHT_PURPLE;
                case MASTERWORK -> ChatFormatting.GOLD;
            };
        }

        public @Nullable String advancementTitleKey() {
            return this == CRUDE ? null : "advancements.hexwright.crafted_" + name().toLowerCase(Locale.ROOT) + ".title";
        }

        public @Nullable String advancementDescriptionKey() {
            return this == CRUDE ? null : "advancements.hexwright.crafted_" + name().toLowerCase(Locale.ROOT) + ".description";
        }

        public static Quality byName(String name) {
            for (Quality quality : values()) {
                if (quality.name().equalsIgnoreCase(name)) {
                    return quality;
                }
            }
            return SOUND;
        }
    }

    private static final String TAG_ROOT = "hexwright_pocket_caster";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_QUALITY = "Quality";

    private PocketCasterData() {
    }

    public static String formatWithBonus(long value, long bonus) {
        return bonus > 0 ? value + " (+" + bonus + ")" : String.valueOf(value);
    }

    public static Quality getQuality(ItemStack stack) {
        CompoundTag root = NBTHelper.getCompound(stack, TAG_ROOT);
        if (root == null || !root.contains(TAG_QUALITY)) {
            return Quality.SOUND;
        }
        return Quality.byName(root.getString(TAG_QUALITY));
    }

    public static int itemSlotCount(ItemStack stack) {
        return getQuality(stack).itemSlots();
    }

    public static int containerSize(ItemStack stack) {
        return 1 + itemSlotCount(stack);
    }

    public static NonNullList<ItemStack> loadItems(ItemStack stack, int size) {
        NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
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

    public static ItemStack create(Quality quality) {
        ItemStack stack = new ItemStack(HexwrightItems.POCKET_CASTER);
        CompoundTag root = NBTHelper.getOrCreateCompound(stack, TAG_ROOT);
        root.putString(TAG_QUALITY, quality.name());
        return stack;
    }
}
