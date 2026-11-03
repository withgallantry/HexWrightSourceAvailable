package com.bluup.hexwright.server.reliquary;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HexDisplayContainer implements Container {

    public static final int SLOTS = ReliquaryStore.SLOTS;

    public interface Trigger {
        List<ItemStack> onDeposit(int slot, ItemStack offered);

        WithdrawResult onWithdraw(int position, ItemStack clicked, boolean grantToInventory);

        ItemStack onEvict(int slot, ItemStack displayed);
    }

    public record WithdrawResult(@Nullable ItemStack gathered, List<ItemStack> display) {
    }

    private final Trigger trigger;
    private final NonNullList<ItemStack> shown = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public HexDisplayContainer(Trigger trigger) {
        this.trigger = trigger;
    }

    public void replaceContents(List<ItemStack> entries) {
        for (int i = 0; i < SLOTS; i++) {
            shown.set(i, i < entries.size() && !entries.get(i).isEmpty() ? entries.get(i).copy() : ItemStack.EMPTY);
        }
    }

    public void withdrawAt(int slot) {
        ItemStack clicked = shown.get(slot);
        if (clicked.isEmpty()) {
            return;
        }
        replaceContents(trigger.onWithdraw(slot, clicked.copy(), true).display());
    }

    public @Nullable ItemStack withdrawToCursor(int slot, int count) {
        ItemStack clicked = shown.get(slot);
        if (clicked.isEmpty()) {
            return null;
        }
        WithdrawResult result = trigger.onWithdraw(slot, clicked.copyWithCount(count), false);
        replaceContents(result.display());
        return result.gathered();
    }

    public @Nullable ItemStack clickWithCarried(int slot, ItemStack carried, int button) {
        ItemStack occupant = shown.get(slot);
        if (occupant.isEmpty() || ItemStack.isSameItemSameTags(occupant, carried)) {
            int placed = Math.min(button == 0 ? carried.getCount() : 1, getMaxStackSize() - occupant.getCount());
            if (placed <= 0) {
                return carried;
            }
            deposit(slot, carried.copyWithCount(placed));
            return placed == carried.getCount() ? ItemStack.EMPTY : carried.copyWithCount(carried.getCount() - placed);
        }
        if (occupant.getCount() > occupant.getMaxStackSize()) {
            return null;
        }
        ItemStack evicted = trigger.onEvict(slot, occupant.copy());
        if (evicted.isEmpty()) {
            return null;
        }
        deposit(slot, carried.copy());
        return evicted;
    }

    private void deposit(int slot, ItemStack offered) {
        replaceContents(trigger.onDeposit(slot, offered));
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : shown) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return shown.get(slot).copy();
    }

    @Override
    public int getMaxStackSize() {
        return ReliquaryStore.SLOT_CAP;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        withdrawAt(slot);
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, shown.get(slot).getCount());
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (stack.isEmpty() || !canPlaceItem(slot, stack)) {
            return;
        }
        int added = stack.getCount() - shown.get(slot).getCount();
        if (added > 0) {
            deposit(slot, stack.copyWithCount(added));
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        ItemStack occupant = shown.get(slot);
        return occupant.isEmpty() || ItemStack.isSameItemSameTags(occupant, stack);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < shown.size(); i++) {
            shown.set(i, ItemStack.EMPTY);
        }
    }
}
