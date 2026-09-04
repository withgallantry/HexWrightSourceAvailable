package com.bluup.hexwright.client.ldlib.widget;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;

@LDLRegister(name = "staff_item_display", group = "widget.basic")
public class StaffItemDisplayWidget extends Widget implements IConfigurableWidget {
    private Supplier<ItemStack> stackSupplier;

    @Configurable(name = "ldlib.gui.editor.name.item")
    private String itemId = "minecraft:stick";

    @Configurable(name = "ldlib.gui.editor.name.show_tooltip")
    private final boolean showTooltip;

    public StaffItemDisplayWidget() {
        this(0, 0, 32, 32, null, false, "minecraft:stick");
    }

    public StaffItemDisplayWidget(int x, int y, int width, int height, Supplier<ItemStack> stackSupplier) {
        this(x, y, width, height, stackSupplier, false, "minecraft:stick");
    }

    public StaffItemDisplayWidget(int x, int y, int width, int height, Supplier<ItemStack> stackSupplier, boolean showTooltip) {
        this(x, y, width, height, stackSupplier, showTooltip, "minecraft:stick");
    }

    public StaffItemDisplayWidget(int x, int y, int width, int height, Supplier<ItemStack> stackSupplier, boolean showTooltip, String itemId) {
        super(x, y, width, height);
        this.stackSupplier = stackSupplier;
        this.showTooltip = showTooltip;
        this.itemId = itemId == null || itemId.isBlank() ? "minecraft:stick" : itemId;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        ItemStack stack = stackSupplier != null ? stackSupplier.get() : previewStack();
        if (stack.isEmpty()) {
            return;
        }

        int drawSize = Math.min(this.getSize().width, this.getSize().height);
        int drawX = this.getPosition().x + (this.getSize().width - drawSize) / 2;
        int drawY = this.getPosition().y + (this.getSize().height - drawSize) / 2;
        renderScaledItem(graphics, stack, drawX, drawY, drawSize);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (!showTooltip || !this.isMouseOverElement(mouseX, mouseY)) {
            return;
        }

        ItemStack stack = stackSupplier != null ? stackSupplier.get() : previewStack();
        if (!stack.isEmpty()) {
            graphics.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
        }
    }

    public void setItemId(String itemId) {
        this.itemId = itemId == null || itemId.isBlank() ? "minecraft:stick" : itemId;
    }

    public void setStackSupplier(Supplier<ItemStack> stackSupplier) {
        this.stackSupplier = stackSupplier;
    }

    private ItemStack previewStack() {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return new ItemStack(Items.STICK);
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            return new ItemStack(Items.STICK);
        }
        return new ItemStack(item);
    }

    @Environment(EnvType.CLIENT)
    public static void renderScaledItem(GuiGraphics graphics, ItemStack stack, int x, int y, int iconSize) {
        if (stack.isEmpty() || iconSize <= 0) {
            return;
        }
        float scale = iconSize / 16f;
        graphics.pose().pushPose();
        graphics.pose().translate((float) x, (float) y, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }
}