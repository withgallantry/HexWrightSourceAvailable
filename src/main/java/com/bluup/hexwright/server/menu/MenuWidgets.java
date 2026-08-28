package com.bluup.hexwright.server.menu;

import com.bluup.hexwright.Hexwright;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public final class MenuWidgets {

    private static final int TRANSPARENT = 0x00000000;

    private MenuWidgets() {
    }

    public static Map<String, List<Widget>> indexById(WidgetGroup root) {
        Map<String, List<Widget>> out = new HashMap<>();
        index(root, out);
        return out;
    }

    private static void index(Widget widget, Map<String, List<Widget>> out) {
        String id = widget.getId();
        if (id != null && !id.isEmpty()) {
            out.computeIfAbsent(id, key -> new ArrayList<>()).add(widget);
        }
        if (widget instanceof WidgetGroup group) {
            for (Widget child : new ArrayList<>(group.widgets)) {
                index(child, out);
            }
        }
    }

    public static @Nullable Widget firstById(Map<String, List<Widget>> widgetsById, String id) {
        List<Widget> matches = widgetsById.get(id);
        return matches == null || matches.isEmpty() ? null : matches.get(0);
    }

    public static IGuiTexture preserveBackground(Widget template) {
        IGuiTexture background = template.getBackgroundTexture();
        return background != null ? background : new ColorRectTexture(TRANSPARENT);
    }

    public static <W extends Widget> @Nullable W replaceInPlace(
        @Nullable Widget template,
        BiFunction<Integer, Integer, W> factory
    ) {
        if (template == null) {
            return null;
        }
        WidgetGroup parent = template.getParent();
        if (parent == null) {
            return null;
        }
        int insertIndex = parent.widgets.indexOf(template);
        IGuiTexture background = preserveBackground(template);
        String id = template.getId();
        parent.removeWidget(template);

        W replacement = factory.apply(template.getSelfPositionX(), template.getSelfPositionY());
        replacement.setId(id);
        replacement.setBackground(background);
        parent.addWidget(insertIndex < 0 ? parent.widgets.size() : insertIndex, replacement);
        return replacement;
    }

    public static <W extends Widget> @Nullable W replaceAllInPlace(
        Map<String, List<Widget>> widgetsById,
        String id,
        BiFunction<Integer, Integer, W> factory
    ) {
        List<Widget> matches = widgetsById.get(id);
        if (matches == null || matches.isEmpty()) {
            Hexwright.LOGGER.warn("UI template is missing slot widget '{}'", id);
            return null;
        }
        for (int i = 1; i < matches.size(); i++) {
            WidgetGroup duplicateParent = matches.get(i).getParent();
            if (duplicateParent != null) {
                duplicateParent.removeWidget(matches.get(i));
            }
        }
        return replaceInPlace(matches.get(0), factory);
    }

    public static void bindKeySlot(Widget template, Container container, int slot, List<Component> tooltips) {
        SlotWidget replacement = replaceInPlace(template, (x, y) -> singleItemSlot(container, slot, x, y));
        if (replacement != null) {
            replacement.setCanPutItems(true);
            replacement.setCanTakeItems(true);
            replacement.setHoverTooltips(tooltips);
        }
    }

    public static SlotWidget singleItemSlot(Container container, int slot, int x, int y) {
        return new SlotWidget(container, slot, x, y) {
            @Override
            public Slot createSlot(Container inventory, int index) {
                return new Slot(inventory, index, 0, 0) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return container.canPlaceItem(index, stack);
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                };
            }
        };
    }

    public static void bindContainerSlots(
        Map<String, List<Widget>> widgetsById,
        Container container,
        int count,
        SlotFactory factory
    ) {
        for (int index = 0; index < count; index++) {
            int slot = index;
            replaceAllInPlace(widgetsById, "slot_" + index, (x, y) -> factory.create(container, slot, x, y));
        }
    }

    public interface SlotFactory {
        Widget create(Container container, int index, int x, int y);
    }

    public static void bindPlayerInventory(Map<String, List<Widget>> widgetsById, Player entityPlayer) {
        for (int index = 0; index < 36; index++) {
            String id = "player_inv_" + index;
            Widget template = firstById(widgetsById, id);
            if (!(template instanceof SlotWidget)) {
                Hexwright.LOGGER.warn("container UI is missing player inventory slot widget '{}'", id);
                continue;
            }
            int slot = index;
            SlotWidget replacement = replaceInPlace(template,
                (x, y) -> new SlotWidget(entityPlayer.getInventory(), slot, x, y));
            if (replacement != null) {
                replacement.setCanPutItems(true);
                replacement.setCanTakeItems(true);
                replacement.setLocationInfo(true, slot < 9);
            }
        }
    }
}
