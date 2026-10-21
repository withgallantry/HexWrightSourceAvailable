package com.bluup.hexwright.server.research;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.menu.MenuWidgets;
import com.bluup.hexwright.server.menu.UiTemplates;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ResearchTableWindow {

    static final String UI_PROJECT = "research_table";

    private static final String TABLET_SLOT_ID = "tablet_slot";
    private static final String STAKE_ID = "stake";

    private static final int FRAME_RAIL = 10;
    private static final int MARGIN = 14;
    private static final int SLAB_W = 251;
    private static final int BOARD_H = 312;
    private static final int REDEAL_SIZE = 18;
    private static final int REDEAL_INSET = 4;
    private static final int GAUGE_X = MARGIN + SLAB_W + 6;
    private static final int GAUGE_W = 18;
    private static final int COLUMN_X = GAUGE_X + GAUGE_W + 6;
    private static final int COLUMN_W = 182;
    private static final int WIDTH = COLUMN_X + COLUMN_W + MARGIN;
    private static final int HEIGHT = MARGIN * 2 + BOARD_H;

    private static final int TITLE_Y = MARGIN;
    private static final int TITLE_H = 21;
    private static final int TRAY_Y = TITLE_Y + TITLE_H + 3;
    private static final int TRAY_H = 100;
    private static final int TABLET_Y = TRAY_Y + TRAY_H + 7;
    private static final int TABLET_H = 36;
    private static final int STAKE_Y = TABLET_Y + TABLET_H + 10;
    private static final int STAKE_H = 18;
    private static final int USE_W = 86;
    private static final int CAPTION_Y = STAKE_Y + STAKE_H + 3;
    private static final int INVENTORY_Y = HEIGHT - MARGIN - 82;
    private static final int BAR_H = 11;
    private static final int BAR_Y = INVENTORY_Y - 4 - BAR_H;

    private static final int TITLE_COLOUR = 0xFFABAAAB;

    private ResearchTableWindow() {
    }

    public static WidgetGroup build(ResearchTableBlockEntity table, Player player) {
        WidgetGroup root = layout();
        bind(root, table, player);
        return root;
    }

    private static WidgetGroup layout() {
        Supplier<WidgetGroup> template = UiTemplates.loadOptional(UI_PROJECT);
        if (template == null) {
            return defaultLayout();
        }
        WidgetGroup root = template.get();
        if (root == null || root.getWidgetsByType(ResearchSlabWidget.class).isEmpty()) {
            Hexwright.LOGGER.warn("{}.ui has no research_slab to play on; using the built-in Research Table layout", UI_PROJECT);
            return defaultLayout();
        }
        return root;
    }

    private static void bind(WidgetGroup root, ResearchTableBlockEntity table, Player player) {
        for (ResearchWidget widget : root.getWidgetsByType(ResearchWidget.class)) {
            widget.bind(table);
        }
        Map<String, List<Widget>> widgetsById = MenuWidgets.indexById(root);
        bindTabletSlot(MenuWidgets.firstById(widgetsById, TABLET_SLOT_ID), table);
        bindStake(MenuWidgets.firstById(widgetsById, STAKE_ID), table);
        MenuWidgets.bindPlayerInventory(widgetsById, player);
        root.addWidget(new Sync(table));
    }

    private static void bindTabletSlot(Widget template, ResearchTableBlockEntity table) {
        if (!(template instanceof SlotWidget)) {
            Hexwright.LOGGER.warn("{}.ui is missing the '{}' slot widget", UI_PROJECT, TABLET_SLOT_ID);
            return;
        }
        List<Component> authored = template.getTooltipTexts();
        SlotWidget slot = MenuWidgets.replaceInPlace(template, (x, y) -> new SlotWidget(table, ResearchTableBlockEntity.SLOT, x, y) {
            @Override
            public Slot createSlot(Container inventory, int index) {
                return new Slot(inventory, index, 0, 0) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return table.canPlaceItem(index, stack);
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return table.canTakeTablet();
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                };
            }

            @Override
            public boolean canTakeStack(Player player) {
                return super.canTakeStack(player) && table.canTakeTablet();
            }
        });
        if (slot != null) {
            slot.setCanPutItems(true);
            slot.setCanTakeItems(true);
            slot.setHoverTooltips(authored.isEmpty() ? List.of(
                Component.translatable("gui.hexwright.research.slot"),
                Component.translatable("gui.hexwright.research.slot.hint").withStyle(ChatFormatting.DARK_GRAY)) : authored);
        }
    }

    private static void bindStake(Widget template, ResearchTableBlockEntity table) {
        if (template == null || template.getParent() == null) {
            Hexwright.LOGGER.warn("{}.ui is missing the '{}' widget", UI_PROJECT, STAKE_ID);
            return;
        }
        WidgetGroup parent = template.getParent();
        int index = parent.widgets.indexOf(template);
        parent.removeWidget(template);
        parent.addWidget(index, new ResearchStakeWidget(table, template.getSelfPositionX(), template.getSelfPositionY(),
            template.getSizeWidth(), template.getSizeHeight(), MenuWidgets.preserveBackground(template)));
    }


    static WidgetGroup defaultLayout() {
        WidgetGroup root = new WidgetGroup(0, 0, WIDTH, HEIGHT);
        root.setBackground(new ResourceBorderTexture("ldlib:textures/menu/wooden-border.png", 180, 180,
            FRAME_RAIL, FRAME_RAIL));

        root.addWidget(place(new ResearchSlabWidget(), MARGIN, MARGIN, SLAB_W, BOARD_H));
        root.addWidget(place(new ResearchGaugeWidget(), GAUGE_X, MARGIN, GAUGE_W, BOARD_H));
        root.addWidget(new ImageWidget(COLUMN_X, TITLE_Y, COLUMN_W, TITLE_H, new GuiTextureGroup(
            new ResourceTexture("ldlib:textures/title.png"),
            new TextTexture("block.hexwright.wright_research_table", TITLE_COLOUR).setDropShadow(true))));
        root.addWidget(place(new ResearchTrayWidget(), COLUMN_X, TRAY_Y, COLUMN_W, TRAY_H));
        root.addWidget(place(new ResearchRedealWidget(), COLUMN_X + COLUMN_W - REDEAL_SIZE - REDEAL_INSET,
            TRAY_Y + REDEAL_INSET, REDEAL_SIZE, REDEAL_SIZE));

        WidgetGroup tabletFrame = new WidgetGroup(COLUMN_X, TABLET_Y, TABLET_H, TABLET_H);
        tabletFrame.setBackground(new ResourceTexture("ldlib:textures/menu/item_input.png"));
        tabletFrame.addWidget(placeholderSlot(9, 9, TABLET_SLOT_ID));
        root.addWidget(tabletFrame);
        root.addWidget(place(new ResearchReadoutWidget(), COLUMN_X + 42, TABLET_Y, COLUMN_W - 42, TABLET_H));

        WidgetGroup stake = new WidgetGroup(COLUMN_X, STAKE_Y, COLUMN_W - USE_W - 4, STAKE_H);
        stake.setId(STAKE_ID);
        stake.setBackground(new ResourceBorderTexture("ldlib:textures/potion_border.png", 195, 136, 0, 9));
        root.addWidget(stake);
        root.addWidget(place(new ResearchUseWidget(), COLUMN_X + COLUMN_W - USE_W, STAKE_Y, USE_W, STAKE_H));
        root.addWidget(place(new ResearchCancelWidget(), COLUMN_X, STAKE_Y, COLUMN_W, STAKE_H));
        root.addWidget(place(new ResearchStakeCaptionWidget(), COLUMN_X, CAPTION_Y, COLUMN_W, 9));

        root.addWidget(place(new InspirationBarWidget(), COLUMN_X, BAR_Y, COLUMN_W, BAR_H));

        WidgetGroup inventory = new WidgetGroup(COLUMN_X + (COLUMN_W - 168) / 2, INVENTORY_Y, 168, 82);
        inventory.setBackground(new ResourceTexture("ldlib:textures/menu/inv_main.png"));
        for (int i = 0; i < 36; i++) {
            int y = i < 9 ? 61 : 3 + (i / 9 - 1) * 18;
            inventory.addWidget(placeholderSlot(3 + (i % 9) * 18, y, "player_inv_" + i));
        }
        root.addWidget(inventory);
        return root;
    }

    private static Widget place(Widget widget, int x, int y, int width, int height) {
        widget.setSelfPosition(x, y);
        widget.setSize(width, height);
        return widget;
    }

    private static SlotWidget placeholderSlot(int x, int y, String id) {
        SlotWidget slot = new SlotWidget();
        slot.setSelfPosition(x, y);
        slot.setId(id);
        slot.setBackgroundTexture(IGuiTexture.EMPTY);
        return slot;
    }

    private static final class Sync extends Widget {

        private static final int UPDATE_SESSION = 1;
        private static final int CLOCK_SYNC_TICKS = 5;

        private final ResearchTableBlockEntity table;
        private int lastVersion = -1;
        private int sinceClockSync;

        Sync(ResearchTableBlockEntity table) {
            super(0, 0, 0, 0);
            this.table = table;
        }

        @Override
        public void detectAndSendChanges() {
            super.detectAndSendChanges();
            ResearchSession session = table.session();
            boolean moved = session.version() != lastVersion;
            boolean clock = session.isRunning() && ++sinceClockSync >= CLOCK_SYNC_TICKS;
            if (!moved && !clock) {
                return;
            }
            lastVersion = session.version();
            sinceClockSync = 0;
            writeUpdateInfo(UPDATE_SESSION, session::write);
        }

        @Override
        public void writeInitialData(FriendlyByteBuf buffer) {
            super.writeInitialData(buffer);
            table.session().write(buffer);
            lastVersion = table.session().version();
        }

        @Override
        @Environment(EnvType.CLIENT)
        public void readInitialData(FriendlyByteBuf buffer) {
            super.readInitialData(buffer);
            table.session().read(buffer);
        }

        @Override
        @Environment(EnvType.CLIENT)
        public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
            if (id == UPDATE_SESSION) {
                table.session().read(buffer);
            } else {
                super.readUpdateInfo(id, buffer);
            }
        }
    }
}
