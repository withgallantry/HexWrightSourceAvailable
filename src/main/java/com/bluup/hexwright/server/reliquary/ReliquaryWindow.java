package com.bluup.hexwright.server.reliquary;

import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.common.lib.HexAttributes;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.menu.MenuWidgets;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ram.talia.moreiotas.api.casting.iota.ItemStackIota;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ReliquaryWindow {

    public static final int GRID_LEFT = 8;
    public static final int GRID_TOP = 54;

    private static final int TRANSPARENT_COLOR = 0x00000000;

    private ReliquaryWindow() {
    }

    public static List<ItemStack> runOpen(ServerPlayer player, InteractionHand hand, ItemStack openFocus,
                                           ChestCastEnv.HeldSlot heldSlot) {
        if (openFocus.isEmpty()) {
            return List.of();
        }
        List<Iota> result = SatchelCasting.fire(player, hand, openFocus, List.of(new EntityIota(player)), heldSlot);
        if (result.isEmpty()) {
            return List.of();
        }
        Iota top = result.get(result.size() - 1);
        if (!(top instanceof ListIota list)) {
            return List.of();
        }
        List<ItemStack> view = new ArrayList<>();
        for (Iota entry : list.getList()) {
            if (entry instanceof ItemStackIota stackIota) {
                view.add(stackIota.getItemStack());
            }
        }
        return view;
    }

    public static void fireDeposit(ServerPlayer player, InteractionHand hand, ItemStack depositFocus,
                                    ChestCastEnv.HeldSlot heldSlot, ItemEntity offering, int slot) {
        if (depositFocus.isEmpty()) {
            return;
        }
        SatchelCasting.fire(player, hand, depositFocus,
            List.of(new EntityIota(offering), new DoubleIota(slot)), heldSlot);
    }

    public static ItemStack fireWithdraw(ServerPlayer player, InteractionHand hand, ItemStack withdrawFocus,
                                          ChestCastEnv.HeldSlot heldSlot, ItemStack clicked, int position) {
        if (withdrawFocus.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<Iota> result = SatchelCasting.fire(player, hand, withdrawFocus,
            List.of(ItemStackIota.createFiltered(clicked), new DoubleIota(position)), heldSlot);
        if (result.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return claimEntity(player, result.get(result.size() - 1));
    }

    private static ItemStack claimEntity(ServerPlayer player, Iota top) {
        if (!(top instanceof EntityIota entityIota) || !(entityIota.getEntity() instanceof ItemEntity delivered)) {
            if (!(top instanceof NullIota)) {
                Hexwright.LOGGER.warn(
                    "Reliquary withdraw refused for {}: the Withdraw hook ended on [{}] instead of an item entity"
                        + " - only a real item entity can be withdrawn, see ReliquaryWindow#claimEntity",
                    player.getGameProfile().getName(), top);
            }
            return ItemStack.EMPTY;
        }
        double ambit = player.getAttributes().hasAttribute(HexAttributes.AMBIT_RADIUS)
            ? player.getAttributeValue(HexAttributes.AMBIT_RADIUS)
            : PlayerBasedCastEnv.DEFAULT_AMBIT_RADIUS;
        if (!delivered.isAlive() || delivered.level() != player.level()
            || delivered.distanceToSqr(player) > ambit * ambit) {
            Hexwright.LOGGER.warn("Reliquary withdraw refused for {}: the delivered item entity is gone or out of ambit",
                player.getGameProfile().getName());
            return ItemStack.EMPTY;
        }
        ItemStack payload = delivered.getItem().copy();
        if (payload.isEmpty()) {
            return ItemStack.EMPTY;
        }
        delivered.setItem(ItemStack.EMPTY);
        delivered.discard();
        return payload;
    }

    public static void grantWithdrawal(ServerPlayer player, ItemStack gathered, boolean grantToInventory) {
        if (gathered.isEmpty() || !grantToInventory) {
            return;
        }
        grantOrDrop(player, gathered);
    }

    private static void grantOrDrop(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
            dropped.setPickUpDelay(10);
            player.level().addFreshEntity(dropped);
        }
    }

    public static Container hoardView(boolean clientSide, List<ItemStack> openResult,
                                      @Nullable HexDisplayContainer.Trigger trigger) {
        if (clientSide || trigger == null) {
            return new SimpleContainer(HexDisplayContainer.SLOTS);
        }
        HexDisplayContainer display = new HexDisplayContainer(trigger);
        display.replaceContents(openResult);
        return display;
    }

    public static void bindHoardGrid(Map<String, List<Widget>> widgetsById, Container view) {
        MenuWidgets.bindContainerSlots(widgetsById, view, HexDisplayContainer.SLOTS,
            (container, index, x, y) -> new BulkSlotWidget(container, index, x, y, true, null));
    }

    public static void bindHoardGrid(ModularUI ui, Container view) {
        for (int index = 0; index < HexDisplayContainer.SLOTS; index++) {
            ui.widget(new BulkSlotWidget(view, index,
                GRID_LEFT + (index % 9) * 18 - 1, GRID_TOP + (index / 9) * 18 - 1, true, null)
                .setBackground(new ColorRectTexture(TRANSPARENT_COLOR)));
        }
    }
}
