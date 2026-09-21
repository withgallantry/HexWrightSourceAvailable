package com.bluup.hexwright.server.pentabox;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.menu.UiTemplates;
import com.bluup.hexwright.server.item.PentaboxItem;
import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public final class PentaboxUIFactory extends UIFactory<PentaboxUIFactory.Holder> {
    public static final PentaboxUIFactory INSTANCE = new PentaboxUIFactory();
    private static final ColorRectTexture LOCKED_SLOT_OVERLAY = new ColorRectTexture(0xB2101010);
    private static final ColorRectTexture DEPLOYED_SLOT_OVERLAY = new ColorRectTexture(0x59101010);
    private static Method SLOT_WIDGET_UPDATE_SLOT;

    private PentaboxUIFactory() {
        super(Hexwright.id("pentabox_ui"));
    }

    public boolean openForHand(ServerPlayer player, InteractionHand hand) {
        return openUI(new Holder(player, hand), player);
    }

    @Override
    protected ModularUI createUITemplate(Holder holder, Player entityPlayer) {
        Supplier<WidgetGroup> template = UiTemplates.load("storage_box");
        if (template == null) {
            return null;
        }
        WidgetGroup root = template.get();
        if (root == null) {
            return null;
        }

        PentaboxContainer pentaboxContainer;
        ItemStack pentaboxStack;
        int deployedSlot = -1;
        ItemStack held = entityPlayer.getItemInHand(holder.hand);
        MinecraftServer server = entityPlayer.getServer();
        if (held.getItem() instanceof PentaboxItem) {
            pentaboxStack = held;
            pentaboxContainer = new PentaboxContainer(held);
        } else if (PentaboxData.isLinkedStack(held)) {
            if (server == null) {
                ItemStack display = PentaboxData.getLinkedDisplay(held);
                if (!(display.getItem() instanceof PentaboxItem)) {
                    return null;
                }
                pentaboxStack = display;
                deployedSlot = PentaboxData.getLinkedSlot(held);
                pentaboxContainer = new PentaboxContainer(display);
            } else {
                PentaboxData.syncDeployedStack(server, held);
                ItemStack parked = PentaboxData.peekBox(server, held);
                if (!(parked.getItem() instanceof PentaboxItem)) {
                    return null;
                }
                pentaboxStack = parked;
                deployedSlot = PentaboxData.getLinkedSlot(held);
                pentaboxContainer = new PentaboxContainer(parked, items -> {
                    PentaboxData.saveItems(parked, items);
                    PentaboxStore.get(server).setDirty();
                    PentaboxData.setLinkedDisplay(held, parked);
                });
            }
        } else {
            return null;
        }

        bindPlayerInventorySlots(root, entityPlayer);
        bindPentaboxSlots(root, pentaboxContainer, pentaboxStack, deployedSlot);

        return new ModularUI(root, holder, entityPlayer);
    }

    @Override
    @Environment(EnvType.CLIENT)
    protected Holder readHolderFromSyncData(FriendlyByteBuf syncData) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        return new Holder(player, syncData.readEnum(InteractionHand.class));
    }

    @Override
    protected void writeHolderToSyncData(FriendlyByteBuf syncData, Holder holder) {
        syncData.writeEnum(holder.hand);
    }

    private static void bindPlayerInventorySlots(WidgetGroup root, Player player) {
        for (int i = 0; i < 36; i++) {
            Widget widget = root.getFirstWidgetById("^player_inv_" + i + "$");
            if (widget instanceof SlotWidget slotWidget) {
                slotWidget.setContainerSlot(player.getInventory(), i);
                slotWidget.setLocationInfo(true, i < 9);
            }
        }
    }

    private static void bindPentaboxSlots(WidgetGroup root, PentaboxContainer container, ItemStack pentabox, int deployedSlot) {
        List<SlotWidget> pentaboxSlots = new ArrayList<>();
        collectPentaboxSlotWidgets(root, pentaboxSlots, false);
        pentaboxSlots.sort(Comparator.comparingInt(SlotWidget::getPositionY).thenComparingInt(SlotWidget::getPositionX));

        int gridSlots = Math.min(PentaboxData.SLOT_COUNT, pentaboxSlots.size());
        int usable = PentaboxData.usableSlotCount(pentabox);
        int count = Math.min(usable, gridSlots);
        for (int i = 0; i < count; i++) {
            SlotWidget slotWidget = pentaboxSlots.get(i);
            boolean deployed = i == deployedSlot;
            setFilteredContainerSlot(slotWidget, container, i, deployed);
            slotWidget.setLocationInfo(false, false);
            if (deployed) {
                slotWidget.setDrawHoverOverlay(false);
                slotWidget.setOverlay(DEPLOYED_SLOT_OVERLAY);
            }
        }
        for (int i = count; i < gridSlots; i++) {
            markSlotLocked(pentaboxSlots.get(i));
        }
    }

    private static void markSlotLocked(SlotWidget slotWidget) {
        slotWidget.setActive(false);
        slotWidget.setDrawHoverOverlay(false);
        slotWidget.setOverlay(LOCKED_SLOT_OVERLAY);
    }

    private static void setFilteredContainerSlot(SlotWidget slotWidget, Container container, int slotIndex, boolean deployed) {
        Slot filtered = new Slot(container, slotIndex, 0, 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !deployed && PentaboxFilter.accepts(stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return !deployed;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return 1;
            }
        };

        try {
            if (SLOT_WIDGET_UPDATE_SLOT == null) {
                SLOT_WIDGET_UPDATE_SLOT = SlotWidget.class.getDeclaredMethod("updateSlot", Slot.class);
                SLOT_WIDGET_UPDATE_SLOT.setAccessible(true);
            }
            SLOT_WIDGET_UPDATE_SLOT.invoke(slotWidget, filtered);
        } catch (Throwable t) {
            Hexwright.LOGGER.error("Failed to bind filtered pentabox slot widget; falling back to default slot binding", t);
            slotWidget.setContainerSlot(container, slotIndex);
        }
    }

    private static void collectPentaboxSlotWidgets(Widget widget, List<SlotWidget> out, boolean inPlayerInventoryWidget) {
        boolean childInPlayerInv = inPlayerInventoryWidget || "player_inventory".equals(widget.getId());
        if (widget instanceof SlotWidget slotWidget && !childInPlayerInv) {
            out.add(slotWidget);
        }
        if (widget instanceof WidgetGroup group) {
            for (Widget child : group.widgets) {
                collectPentaboxSlotWidgets(child, out, childInPlayerInv);
            }
        }
    }

    public static final class Holder implements IUIHolder {
        private final Player player;
        private final InteractionHand hand;

        private Holder(Player player, InteractionHand hand) {
            this.player = player;
            this.hand = hand;
        }

        @Override
        public ModularUI createUI(Player entityPlayer) {
            return null;
        }

        @Override
        public boolean isInvalid() {
            ItemStack current = player.getItemInHand(hand);
            if (current.getItem() instanceof PentaboxItem) {
                return false;
            }
            return !PentaboxData.isLinkedStack(current);
        }

        @Override
        public boolean isRemote() {
            return player.level().isClientSide;
        }

        @Override
        public void markAsDirty() {
        }
    }
}
