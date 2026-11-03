package com.bluup.hexwright.server.pocketcaster;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.menu.UiTemplates;
import com.bluup.hexwright.server.item.PocketCasterItem;
import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class PocketCasterUIFactory extends UIFactory<PocketCasterUIFactory.Holder> {
    public static final PocketCasterUIFactory INSTANCE = new PocketCasterUIFactory();

    private static final ColorRectTexture LOCKED_SLOT_OVERLAY = new ColorRectTexture(0xB2101010);
    private static final int TRANSPARENT_COLOR = 0x00000000;
    private static final int GRID_SLOTS = PocketCasterData.Quality.MASTERWORK.itemSlots();

    private PocketCasterUIFactory() {
        super(Hexwright.id("pocket_caster_ui"));
    }

    public boolean openForHand(ServerPlayer player, InteractionHand hand) {
        return openUI(new Holder(player, hand), player);
    }

    @Override
    protected ModularUI createUITemplate(Holder holder, Player entityPlayer) {
        ItemStack caster = entityPlayer.getItemInHand(holder.hand);
        if (!(caster.getItem() instanceof PocketCasterItem)) {
            return null;
        }

        Supplier<WidgetGroup> template = UiTemplates.load("pocket_caster");
        if (template == null) {
            return null;
        }
        WidgetGroup root = template.get();
        if (root == null) {
            return null;
        }

        PocketCasterContainer container = new PocketCasterContainer(caster);
        InteractionHand hand = holder.hand;

        bindFocusSlot(root, container);
        bindReagentSlots(root, container);
        bindCastButton(root, hand, container, entityPlayer);

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

    private static void bindFocusSlot(WidgetGroup root, PocketCasterContainer container) {
        replaceSlotWidget(root, "focus", (x, y) -> {
            FilteredSlotWidget slot = new FilteredSlotWidget(container, PocketCasterContainer.FOCUS_SLOT, x, y);
            slot.setLocationInfo(false, false);
            slot.setHoverTooltips(Component.translatable("gui.hexwright.pocket_caster.focus.tooltip"));
            return slot;
        });
    }

    private static void bindReagentSlots(WidgetGroup root, PocketCasterContainer container) {
        int usable = Math.min(container.itemSlotCount(), GRID_SLOTS);
        for (int i = 0; i < GRID_SLOTS; i++) {
            int reagentIndex = i;
            replaceSlotWidget(root, "slot_" + i, (x, y) -> {
                if (reagentIndex < usable) {
                    FilteredSlotWidget slot = new FilteredSlotWidget(container, PocketCasterContainer.ITEM_FIRST + reagentIndex, x, y);
                    slot.setLocationInfo(false, false);
                    return slot;
                }
                return lockedSlot(x, y);
            });
        }
    }

    private static SlotWidget lockedSlot(int x, int y) {
        SlotWidget locked = new SlotWidget();
        locked.initTemplate();
        locked.setSelfPosition(x, y);
        locked.setActive(false);
        locked.setDrawHoverOverlay(false);
        locked.setOverlay(LOCKED_SLOT_OVERLAY);
        return locked;
    }

    private static void replaceSlotWidget(WidgetGroup root, String id, BiFunction<Integer, Integer, SlotWidget> factory) {
        List<Widget> matches = root.getWidgetsById("^" + id + "$");
        if (matches.isEmpty()) {
            Hexwright.LOGGER.warn("pocket_caster.ui is missing slot widget '{}'", id);
            return;
        }
        Widget first = matches.get(0);
        WidgetGroup parent = first.getParent();
        if (parent == null) {
            return;
        }
        int insertIndex = Math.max(parent.widgets.indexOf(first), 0);
        int x = first.getSelfPositionX();
        int y = first.getSelfPositionY();
        IGuiTexture background = first instanceof SlotWidget templateSlot ? templateSlot.getBackgroundTexture() : null;
        for (Widget match : matches) {
            WidgetGroup matchParent = match.getParent();
            if (matchParent != null) {
                matchParent.removeWidget(match);
            }
        }
        SlotWidget replacement = factory.apply(x, y);
        replacement.setId(id);
        replacement.setBackgroundTexture(background != null ? background : new ColorRectTexture(TRANSPARENT_COLOR));
        parent.addWidget(insertIndex, replacement);
    }

    private static void bindCastButton(WidgetGroup root, InteractionHand hand, PocketCasterContainer container, Player entityPlayer) {
        List<Widget> matches = root.getWidgetsById("^cast$");
        if (matches.isEmpty()) {
            Hexwright.LOGGER.warn("pocket_caster.ui is missing the 'cast' button group");
            return;
        }
        for (Widget match : matches) {
            if (!(match instanceof WidgetGroup castGroup)) {
                continue;
            }
            ButtonWidget clickCatcher = new ButtonWidget(
                0, 0, castGroup.getSizeWidth(), castGroup.getSizeHeight(),
                clickData -> {
                    if (entityPlayer.level().isClientSide || !(entityPlayer instanceof ServerPlayer serverPlayer)) {
                        return;
                    }
                    PocketCasterCasting.cast(serverPlayer, hand, container);
                }
            );
            castGroup.addWidget(clickCatcher);
        }
    }

    private static final class FilteredSlotWidget extends SlotWidget {
        private FilteredSlotWidget(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public Slot createSlot(Container inventory, int index) {
            return new Slot(inventory, index, 0, 0) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return inventory.canPlaceItem(index, stack);
                }
            };
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
            return !(player.getItemInHand(hand).getItem() instanceof PocketCasterItem);
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
