package com.bluup.hexwright.server.pocketcaster;

import at.petrak.hexcasting.api.item.IotaHolderItem;
import com.bluup.hexwright.server.item.PocketCasterItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

public class PocketCasterContainer extends SimpleContainer {
    public static final int FOCUS_SLOT = 0;
    public static final int ITEM_FIRST = 1;

    private final ItemStack caster;

    public PocketCasterContainer(ItemStack caster) {
        super(PocketCasterData.containerSize(caster));
        this.caster = caster;
        NonNullList<ItemStack> items = PocketCasterData.loadItems(caster, getContainerSize());
        for (int i = 0; i < getContainerSize(); i++) {
            super.setItem(i, items.get(i));
        }
    }

    public int itemSlotCount() {
        return getContainerSize() - ITEM_FIRST;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == FOCUS_SLOT) {
            return stack.getItem() instanceof IotaHolderItem;
        }
        return !(stack.getItem() instanceof PocketCasterItem);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (caster.isEmpty()) {
            return;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < getContainerSize(); i++) {
            items.set(i, this.getItem(i));
        }
        PocketCasterData.saveItems(caster, items);
    }
}
