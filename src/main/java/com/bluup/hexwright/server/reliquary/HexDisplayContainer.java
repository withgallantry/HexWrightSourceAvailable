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

    public @Nullable ItemStack withdrawToCursor(int slot) {
        ItemStack clicked = shown.get(slot);
        if (clicked.isEmpty()) {
            return null;
        }
        WithdrawResult result = trigger.onWithdraw(slot, clicked.copy(), false);
        replaceContents(result.display());
        return result.gathered();
    }

    public @Nullable ItemStack depositAt(int slot, ItemStack offered) {
        ItemStack toCursor = evictForIncoming(slot);
        if (toCursor == null) {
            return null;
        }
        replaceContents(trigger.onDeposit(slot, offered));
        return toCursor;
    }

    private @Nullable ItemStack evictForIncoming(int slot) {
        ItemStack occupant = shown.get(slot);
        if (occupant.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack evicted = trigger.onEvict(slot, occupant.copy());
        return evicted.isEmpty() ? null : evicted;
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
        return shown.get(slot);
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
        if (stack.isEmpty()) {
            return;
        }
        depositAt(slot, stack.copy());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return true;
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
