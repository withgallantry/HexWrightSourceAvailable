package com.bluup.hexwright.server.pentabox;

import at.petrak.hexcasting.api.item.HexHolderItem;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.common.items.HexBaubleItem;
import at.petrak.hexcasting.common.items.ItemStaff;
import com.bluup.hexwright.server.item.PentaboxItem;
import com.bluup.hexwright.server.item.HexwrightItemTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class PentaboxFilter {

    public static boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof PentaboxItem) {
            return false;
        }
        return item instanceof ItemStaff
            || item instanceof IotaHolderItem
            || item instanceof HexHolderItem
            || item instanceof HexBaubleItem
            || isHexicalCharmedItem(stack)
            || stack.is(HexwrightItemTags.PENTABOX_STORABLE);
    }

    private static boolean isHexicalCharmedItem(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("charmed", 10);
    }

    private PentaboxFilter() {
    }
}
