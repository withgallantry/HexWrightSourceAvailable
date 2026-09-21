package com.bluup.hexwright.server.pentabox;

import net.minecraft.core.NonNullList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public class PentaboxContainer extends SimpleContainer {
    private final ItemStack pentabox;
    private final Consumer<NonNullList<ItemStack>> saveSink;
    private boolean initializing = true;

    public PentaboxContainer(ItemStack pentabox) {
        this(pentabox, items -> PentaboxData.saveItems(pentabox, items));
    }

    public PentaboxContainer(ItemStack pentabox, Consumer<NonNullList<ItemStack>> saveSink) {
        super(PentaboxData.SLOT_COUNT);
        this.pentabox = pentabox;
        this.saveSink = saveSink;
        NonNullList<ItemStack> items = PentaboxData.loadItems(pentabox);
        for (int i = 0; i < PentaboxData.SLOT_COUNT; i++) {
            super.setItem(i, items.get(i));
        }
        this.initializing = false;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return PentaboxFilter.accepts(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (initializing || pentabox.isEmpty()) {
            return;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(PentaboxData.SLOT_COUNT, ItemStack.EMPTY);
        for (int i = 0; i < PentaboxData.SLOT_COUNT; i++) {
            items.set(i, this.getItem(i));
        }
        saveSink.accept(items);
    }
}
