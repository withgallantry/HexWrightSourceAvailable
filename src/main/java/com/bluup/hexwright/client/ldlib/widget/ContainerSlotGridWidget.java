package com.bluup.hexwright.client.ldlib.widget;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import net.minecraft.world.Container;

@LDLRegister(name = "container_slot_grid", group = "widget.custom")
public class ContainerSlotGridWidget extends WidgetGroup {

    private static final int MARGIN = 5;

    @Configurable(name = "gui.hexwright.editor.name.rows")
    @NumberRange(range = {1, 32})
    private int rows = 3;
    @Configurable(name = "gui.hexwright.editor.name.cols")
    @NumberRange(range = {1, 32})
    private int cols = 9;
    @Configurable(name = "gui.hexwright.editor.name.slot_spacing")
    @NumberRange(range = {1, 64})
    private int slotSpacing = 18;
    @Configurable(name = "gui.hexwright.editor.name.start_index",
        tips = "gui.hexwright.editor.name.start_index.tips")
    @NumberRange(range = {0, 512})
    private int startIndex = 0;
    @Configurable(name = "gui.hexwright.editor.name.id_prefix",
        tips = "gui.hexwright.editor.name.id_prefix.tips")
    private String idPrefix = "slot_";
    @Configurable(name = "ldlib.gui.editor.name.slot_background")
    private IGuiTexture slotBackground = SlotWidget.ITEM_SLOT_TEXTURE.copy();
    @Configurable(name = "ldlib.gui.editor.name.allow_custom_background",
        tips = "ldlib.gui.editor.name.allow_custom_background.tips")
    private boolean allowCustomBackground = false;

    public ContainerSlotGridWidget() {
        super(0, 0, 172, 68);
        rebuildSlots();
    }

    @Override
    public void initTemplate() {
    }

    @Override
    public void initWidget() {
        super.initWidget();
        boolean inEditor = LDLib.isClient() && Editor.INSTANCE != null;
        for (var widget : widgets) {
            if (widget instanceof SlotWidget slotWidget) {
                slotWidget.setCanPutItems(!inEditor);
                slotWidget.setCanTakeItems(!inEditor);
            }
        }
    }

    public void bindContainer(Container container) {
        int index = startIndex;
        for (var widget : widgets) {
            if (widget instanceof SlotWidget slotWidget) {
                slotWidget.setContainerSlot(container, index++);
                slotWidget.setLocationInfo(false, false);
            }
        }
    }

    private void rebuildSlots() {
        clearAllWidgets();
        int safeRows = Math.max(1, rows);
        int safeCols = Math.max(1, cols);
        int safeSpacing = Math.max(1, slotSpacing);
        for (int row = 0; row < safeRows; row++) {
            for (int col = 0; col < safeCols; col++) {
                var slot = new SlotWidget();
                slot.initTemplate();
                slot.setSelfPosition(new Position(MARGIN + col * safeSpacing, MARGIN + row * safeSpacing));
                slot.setId(idPrefix + (col + row * safeCols));
                if (!allowCustomBackground) {
                    slot.setBackground(slotBackground);
                }
                addWidget(slot);
            }
        }
        int slotIconSize = 16;
        setSize(new Size(
            MARGIN * 2 + (safeCols - 1) * safeSpacing + slotIconSize,
            MARGIN * 2 + (safeRows - 1) * safeSpacing + slotIconSize
        ));
    }

    @ConfigSetter(field = "rows")
    public void setRows(int rows) {
        this.rows = Math.max(1, rows);
        rebuildSlots();
    }

    @ConfigSetter(field = "cols")
    public void setCols(int cols) {
        this.cols = Math.max(1, cols);
        rebuildSlots();
    }

    @ConfigSetter(field = "slotSpacing")
    public void setSlotSpacing(int slotSpacing) {
        this.slotSpacing = Math.max(1, slotSpacing);
        rebuildSlots();
    }

    @ConfigSetter(field = "idPrefix")
    public void setIdPrefix(String idPrefix) {
        this.idPrefix = idPrefix == null || idPrefix.isBlank() ? "slot_" : idPrefix;
        rebuildSlots();
    }

    @ConfigSetter(field = "slotBackground")
    public void setSlotBackground(IGuiTexture slotBackground) {
        this.slotBackground = slotBackground;
        if (allowCustomBackground) {
            return;
        }
        for (var widget : widgets) {
            if (widget instanceof SlotWidget slotWidget) {
                slotWidget.setBackground(slotBackground);
            }
        }
    }

    @ConfigSetter(field = "allowCustomBackground")
    public void setAllowCustomBackground(boolean allowCustomBackground) {
        this.allowCustomBackground = allowCustomBackground;
        if (!allowCustomBackground) {
            for (var widget : widgets) {
                if (widget instanceof SlotWidget slotWidget) {
                    slotWidget.setBackground(slotBackground);
                }
            }
        }
    }
}
