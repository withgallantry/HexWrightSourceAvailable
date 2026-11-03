package com.bluup.hexwright.server.reliquary;

import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.menu.MenuWidgets;
import com.bluup.hexwright.server.menu.UiTemplates;
import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class SatchelUIFactory extends UIFactory<SatchelUIFactory.Holder> {
    public static final SatchelUIFactory INSTANCE = new SatchelUIFactory();

    private static final IGuiTexture HELD_SLOT_GUIDE_TEXTURE = new ResourceTexture("ldlib:textures/menu/leather_secondary_hand.png");
    private static final IGuiTexture HELD_SLOT_FILLED_TEXTURE = new ResourceTexture("ldlib:textures/menu/leather_secondary_input.png");

    private static final String[] HOOK_IDS = {"list", "in", "out"};

    private SatchelUIFactory() {
        super(Hexwright.id("satchel_ui"));
    }

    public boolean openForHand(ServerPlayer player, InteractionHand hand) {
        return openUI(new Holder(player, hand), player);
    }

    @Override
    protected ModularUI createUITemplate(Holder holder, Player entityPlayer) {
        ItemStack satchel = entityPlayer.getItemInHand(holder.hand);
        if (!(satchel.getItem() instanceof SatchelItem)) {
            return null;
        }

        Supplier<WidgetGroup> template = UiTemplates.load("container_satchel");
        WidgetGroup root = template == null ? null : template.get();
        if (root == null) {
            Hexwright.LOGGER.error("Failed to load container_satchel UI project");
            return null;
        }

        bindSatchelWidgets(root, holder, entityPlayer, satchel);
        return new ModularUI(root, holder, entityPlayer);
    }

    private static void bindSatchelWidgets(WidgetGroup root, Holder holder, Player entityPlayer, ItemStack satchel) {
        Map<String, List<Widget>> widgetsById = MenuWidgets.indexById(root);

        SimpleContainer fittings = new SimpleContainer(
            SatchelItem.getHeld(satchel),
            SatchelItem.getFocus(satchel, SatchelItem.Hook.OPEN),
            SatchelItem.getFocus(satchel, SatchelItem.Hook.DEPOSIT),
            SatchelItem.getFocus(satchel, SatchelItem.Hook.WITHDRAW)
        ) {
            @Override
            public void setChanged() {
                super.setChanged();
                ItemStack held = resolveSatchelStack(entityPlayer, holder.hand);
                if (!(held.getItem() instanceof SatchelItem)) {
                    return;
                }
                SatchelItem.setHeld(held, getItem(0));
                SatchelItem.setFocus(held, SatchelItem.Hook.OPEN, getItem(1));
                SatchelItem.setFocus(held, SatchelItem.Hook.DEPOSIT, getItem(2));
                SatchelItem.setFocus(held, SatchelItem.Hook.WITHDRAW, getItem(3));
                entityPlayer.getInventory().setChanged();
            }
        };

        bindFittingSlot(widgetsById, "held", fittings, 0, true, "");
        SatchelItem.Hook[] hooks = SatchelItem.Hook.values();
        for (int i = 0; i < hooks.length; i++) {
            bindFittingSlot(widgetsById, HOOK_IDS[i], fittings, 1 + i, false, hooks[i].translationKey() + ".tooltip");
        }

        boolean clientSide = entityPlayer.level().isClientSide;
        List<ItemStack> openResult = List.of();
        HexDisplayContainer.Trigger trigger = null;
        if (!clientSide && entityPlayer instanceof ServerPlayer serverPlayer) {
            ChestCastEnv.HeldSlot heldSlot = new ChestCastEnv.HeldSlot() {
                @Override
                public ItemStack get() {
                    return SatchelItem.getHeld(serverPlayer.getItemInHand(holder.hand));
                }

                @Override
                public void set(ItemStack stack) {
                    SatchelItem.setHeld(serverPlayer.getItemInHand(holder.hand), stack);
                }
            };
            String key = SatchelItem.storeKey(satchel);
            ItemStack openFocus = effectiveHookFocus(serverPlayer, satchel, key, SatchelItem.Hook.OPEN);
            openResult = ReliquaryWindow.runOpen(serverPlayer, holder.hand, openFocus, heldSlot);
            trigger = new HexDisplayContainer.Trigger() {
                @Override
                public HexDisplayContainer.WithdrawResult onWithdraw(int position, ItemStack clicked, boolean grantToInventory) {
                    ItemStack satchelNow = serverPlayer.getItemInHand(holder.hand);
                    String keyNow = SatchelItem.storeKey(satchelNow);
                    ItemStack withdrawFocus = effectiveHookFocus(serverPlayer, satchelNow, keyNow, SatchelItem.Hook.WITHDRAW);
                    ItemStack gathered = ReliquaryWindow.fireWithdraw(serverPlayer, holder.hand, withdrawFocus, heldSlot, clicked, position);
                    boolean withdrew = !gathered.isEmpty();
                    if (withdrew) {
                        ReliquaryWindow.grantWithdrawal(serverPlayer, gathered, grantToInventory);
                    }
                    ItemStack refreshedOpenFocus = effectiveHookFocus(serverPlayer, satchelNow, keyNow, SatchelItem.Hook.OPEN);
                    List<ItemStack> display = ReliquaryWindow.runOpen(serverPlayer, holder.hand, refreshedOpenFocus, heldSlot);
                    return new HexDisplayContainer.WithdrawResult(withdrew ? gathered : null, display);
                }

                @Override
                public ItemStack onEvict(int slot, ItemStack displayed) {
                    ItemStack satchelNow = serverPlayer.getItemInHand(holder.hand);
                    String keyNow = SatchelItem.storeKey(satchelNow);
                    ItemStack withdrawFocus = effectiveHookFocus(serverPlayer, satchelNow, keyNow, SatchelItem.Hook.WITHDRAW);
                    return ReliquaryWindow.fireWithdraw(serverPlayer, holder.hand, withdrawFocus, heldSlot, displayed, slot);
                }

                @Override
                public List<ItemStack> onDeposit(int slot, ItemStack offered) {
                    ItemStack satchelNow = serverPlayer.getItemInHand(holder.hand);
                    String keyNow = SatchelItem.storeKey(satchelNow);
                    ItemEntity entity = new ItemEntity(serverPlayer.level(),
                        serverPlayer.getX(), serverPlayer.getY() + 0.5, serverPlayer.getZ(), offered);
                    entity.setPickUpDelay(10);
                    serverPlayer.level().addFreshEntity(entity);
                    ItemStack depositFocus = effectiveHookFocus(serverPlayer, satchelNow, keyNow, SatchelItem.Hook.DEPOSIT);
                    ReliquaryWindow.fireDeposit(serverPlayer, holder.hand, depositFocus, heldSlot, entity, slot);
                    ItemStack refreshedOpenFocus = effectiveHookFocus(serverPlayer, satchelNow, keyNow, SatchelItem.Hook.OPEN);
                    return ReliquaryWindow.runOpen(serverPlayer, holder.hand, refreshedOpenFocus, heldSlot);
                }
            };
        }
        ReliquaryWindow.bindHoardGrid(widgetsById, ReliquaryWindow.hoardView(clientSide, openResult, trigger));
        MenuWidgets.bindPlayerInventory(widgetsById, entityPlayer);
    }

    private static void bindFittingSlot(Map<String, List<Widget>> widgetsById, String id, SimpleContainer fittings, int index, boolean anyItem, String tooltipKey) {
        Widget templateWidget = MenuWidgets.firstById(widgetsById, id);
        if (!(templateWidget instanceof SlotWidget template)) {
            Hexwright.LOGGER.warn("container_satchel.ui is missing control slot widget '{}'", id);
            return;
        }
        WidgetGroup parent = template.getParent();
        if (parent == null) {
            Hexwright.LOGGER.warn("container_satchel.ui control slot widget '{}' has no parent group", id);
            return;
        }
        List<Component> authored = template.getTooltipTexts();
        WidgetGroup guideHost = "held".equals(id) ? parent : null;
        FittingSlotWidget replacement = MenuWidgets.replaceInPlace(template,
            (x, y) -> new FittingSlotWidget(fittings, index, x, y, anyItem, guideHost));
        if (replacement == null) {
            return;
        }
        replacement.setHoverTooltips(authored.isEmpty() ? List.of(Component.translatable(tooltipKey)) : authored);
    }

    public static ItemStack effectiveHookFocus(ServerPlayer player, ItemStack satchel,
                                                @Nullable String key, SatchelItem.Hook hook) {
        ItemStack override = SatchelItem.getFocus(satchel, hook);
        if (!override.isEmpty()) {
            return override;
        }
        if (key == null) {
            return ItemStack.EMPTY;
        }
        return ReliquaryStore.get(player.getServer()).getHook(key, hook);
    }

    private static ItemStack resolveSatchelStack(Player player, InteractionHand preferredHand) {
        ItemStack preferred = player.getItemInHand(preferredHand);
        if (preferred.getItem() instanceof SatchelItem) {
            return preferred;
        }
        InteractionHand other = preferredHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack fallback = player.getItemInHand(other);
        if (fallback.getItem() instanceof SatchelItem) {
            return fallback;
        }
        return preferred;
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


    private static final class FittingSlotWidget extends SlotWidget {
        private final boolean anyItem;
        @Nullable
        private final WidgetGroup guideHost;
        private boolean guideShown = true;

        private FittingSlotWidget(Container container, int index, int x, int y, boolean anyItem, @Nullable WidgetGroup guideHost) {
            super(container, index, x, y);
            this.anyItem = anyItem;
            this.guideHost = guideHost;
        }

        @Override
        @Environment(EnvType.CLIENT)
        public void updateScreen() {
            super.updateScreen();
            if (guideHost == null) {
                return;
            }
            boolean empty = getItem().isEmpty();
            if (empty != guideShown) {
                guideShown = empty;
                guideHost.setBackground(guideShown ? HELD_SLOT_GUIDE_TEXTURE : HELD_SLOT_FILLED_TEXTURE);
            }
        }

        @Override
        public Slot createSlot(Container inventory, int index) {
            return new Slot(inventory, index, 0, 0) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (anyItem) {
                        return true;
                    }
                    return IXplatAbstractions.INSTANCE.findDataHolder(stack) != null;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
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
            return !(player.getItemInHand(hand).getItem() instanceof SatchelItem);
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
