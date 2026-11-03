package com.bluup.hexwright.server.reliquary;

import com.bluup.hexwright.client.reliquary.BulkCountRenderer;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

public class BulkSlotWidget extends SlotWidget {

    private final Container container;
    private final int index;
    private final boolean allowPlace;
    @Nullable
    private final IntPredicate canTake;

    public BulkSlotWidget(Container container, int index, int x, int y,
                          boolean allowPlace, @Nullable IntPredicate canTake) {
        super(container, index, x, y);
        this.container = container;
        this.index = index;
        this.allowPlace = allowPlace;
        this.canTake = canTake;
    }

    @Override
    public Slot createSlot(Container inventory, int slotIndex) {
        return new Slot(inventory, slotIndex, 0, 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return allowPlace && inventory.canPlaceItem(slotIndex, stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                if (inventory instanceof HexDisplayContainer) {
                    return false;
                }
                return canTake == null || canTake.test(slotIndex);
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return getMaxStackSize();
            }
        };
    }

    @Override
    public boolean canMergeSlot(ItemStack stack) {
        if (container instanceof HexDisplayContainer || isOversize(container.getItem(index))) {
            return false;
        }
        return super.canMergeSlot(stack);
    }

    @Override
    public ItemStack slotClick(int dragType, ClickType clickTypeIn, Player player) {
        if (!(container instanceof HexDisplayContainer hexContainer)) {
            return clickOversize(dragType, clickTypeIn, player);
        }
        if (clickTypeIn != ClickType.PICKUP || gui == null) {
            return null;
        }
        var menu = gui.getModularUIContainer();
        ItemStack carried = menu.getCarried();
        ItemStack cursor = carried.isEmpty()
            ? hexContainer.withdrawToCursor(index, pickupCount(container.getItem(index), dragType))
            : hexContainer.clickWithCarried(index, carried, dragType);
        if (cursor != null) {
            menu.setCarried(cursor);
        }
        return ItemStack.EMPTY;
    }

    private static int pickupCount(ItemStack stack, int button) {
        int whole = Math.min(stack.getCount(), stack.getMaxStackSize());
        return button == 0 ? whole : (whole + 1) / 2;
    }

    private @Nullable ItemStack clickOversize(int button, ClickType type, Player player) {
        ItemStack stack = container.getItem(index);
        if (!isOversize(stack) || gui == null || !getHandler().mayPickup(player)) {
            return null;
        }
        int vanillaStack = stack.getMaxStackSize();
        var menu = gui.getModularUIContainer();
        switch (type) {
            case PICKUP -> {
                ItemStack carried = menu.getCarried();
                if (carried.isEmpty()) {
                    menu.setCarried(container.removeItem(index, pickupCount(stack, button)));
                    return ItemStack.EMPTY;
                }
                return ItemStack.isSameItemSameTags(carried, stack) ? null : ItemStack.EMPTY;
            }
            case SWAP -> {
                Inventory inventory = player.getInventory();
                if (inventory.getItem(button).isEmpty()) {
                    inventory.setItem(button, container.removeItem(index, vanillaStack));
                }
                return ItemStack.EMPTY;
            }
            case QUICK_MOVE -> {
                ItemStack probe = stack.copyWithCount(vanillaStack);
                menu.attemptMergeStack(probe, true, true);
                int fits = vanillaStack - probe.getCount();
                if (fits > 0) {
                    ItemStack moving = container.removeItem(index, fits);
                    menu.attemptMergeStack(moving, true, false);
                    if (!moving.isEmpty()) {
                        player.drop(moving, false, false);
                    }
                }
                return ItemStack.EMPTY;
            }
            default -> {
                return null;
            }
        }
    }

    private static boolean isOversize(ItemStack stack) {
        return !stack.isEmpty() && stack.getCount() > stack.getMaxStackSize();
    }

    @Override
    public ItemStack getRealStack(ItemStack itemStack) {
        ItemStack real = super.getRealStack(itemStack);
        return BulkCount.isBulk(real.getCount()) ? real.copyWithCount(1) : real;
    }

    @Override
    public List<Component> getFullTooltipTexts() {
        List<Component> tooltip = super.getFullTooltipTexts();
        int count = container.getItem(index).getCount();
        if (!BulkCount.isBulk(count)) {
            return tooltip;
        }
        List<Component> withCount = new ArrayList<>(tooltip);
        withCount.add(BulkCount.exactLine(count));
        return withCount;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        int count = container.getItem(index).getCount();
        if (BulkCount.isBulk(count)) {
            var pos = getPosition();
            BulkCountRenderer.drawCount(graphics, Minecraft.getInstance().font, count, pos.x + 1, pos.y + 1);
        }
    }
}
