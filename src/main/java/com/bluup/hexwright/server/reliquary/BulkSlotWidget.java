package com.bluup.hexwright.server.reliquary;

import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

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
        };
    }

    @Override
    public boolean canMergeSlot(ItemStack stack) {
        if (container instanceof HexDisplayContainer) {
            return false;
        }
        return super.canMergeSlot(stack);
    }

    public @Nullable HexDisplayContainer getHexContainer() {
        return container instanceof HexDisplayContainer hexContainer ? hexContainer : null;
    }

    @Override
    public ItemStack slotClick(int dragType, ClickType clickTypeIn, Player player) {
        if (!(container instanceof HexDisplayContainer hexContainer)) {
            return null;
        }
        if (clickTypeIn != ClickType.PICKUP || gui == null) {
            return null;
        }
        var menu = gui.getModularUIContainer();
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            ItemStack gathered = hexContainer.withdrawToCursor(index);
            if (gathered != null && !gathered.isEmpty()) {
                menu.setCarried(gathered);
            }
            return ItemStack.EMPTY;
        }
        ItemStack evicted = hexContainer.depositAt(index, carried.copy());
        if (evicted != null) {
            menu.setCarried(evicted);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        ItemStack stack = container.getItem(index);
        if (stack.isEmpty() || stack.getCount() < 1000) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        String text = BulkCount.format(stack.getCount());
        var pos = getPosition();
        int right = pos.x + 17;
        int bottom = pos.y + 17;
        float scale = 0.7f;
        int textWidth = (int) (font.width(text) * scale);

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 300);
        graphics.fill(pos.x, bottom - 8, right, bottom, 0xC010131C);
        pose.translate(right - textWidth - 1, bottom - 7.5, 0);
        pose.scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, 0xFFFFFF, false);
        pose.popPose();
    }
}
