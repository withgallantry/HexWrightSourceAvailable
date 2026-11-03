package com.bluup.hexwright.server.malediction.workbench;

import com.bluup.hexwright.server.flourish.FlourishItem;
import com.bluup.hexwright.server.malediction.MaledictionBlankItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

final class MaledictionFlourishSlot implements Container {

    private final MaledictionWorkbenchBlockEntity workbench;

    MaledictionFlourishSlot(MaledictionWorkbenchBlockEntity workbench) {
        this.workbench = workbench;
    }

    private @Nullable ItemStack malediction() {
        ItemStack held = workbench.held();
        return MaledictionBlankItem.isFused(held) ? held : null;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return getItem(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        ItemStack malediction = malediction();
        return malediction == null ? ItemStack.EMPTY : MaledictionBlankItem.flourish(malediction);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return amount > 0 ? removeItemNoUpdate(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack flourish = getItem(slot);
        if (!flourish.isEmpty()) {
            setItem(slot, ItemStack.EMPTY);
        }
        return flourish;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        ItemStack malediction = malediction();
        if (malediction != null) {
            MaledictionBlankItem.setFlourish(malediction, stack);
            workbench.setChanged();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return malediction() != null && FlourishItem.isWearable(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {
        workbench.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return workbench.stillValid(player);
    }

    @Override
    public void clearContent() {
        setItem(0, ItemStack.EMPTY);
    }
}
