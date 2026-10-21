package com.bluup.hexwright.server.armour;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.menu.MenuWidgets;
import com.bluup.hexwright.server.menu.UiTemplates;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class ArmamentWorkbenchBlockEntity extends BlockEntity implements Container, IUIHolder.BlockEntityUI {

    private static final int SLOT = 0;

    private static final String UI_PROJECT = "armament_workbench";
    private static final String SLOT_ID = "armour_slot";
    private static final String STATUS_ID = "status";

    private static final IGuiTexture WORN_BACKDROP =
        new ResourceBorderTexture("ldlib:textures/menu/list-box-select.png", 180, 180, 4, 4);
    private static final IGuiTexture UNAVAILABLE_WASH = new ColorRectTexture(0xA0140E1C);

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    public ArmamentWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(HexwrightBlocks.ARMAMENT_WORKBENCH_BLOCK_ENTITY, pos, state);
    }

    public ItemStack piece() {
        return items.get(SLOT);
    }

    private @Nullable Restylable docked() {
        return restylable(items.get(SLOT));
    }

    private static @Nullable Restylable restylable(ItemStack stack) {
        return stack.getItem() instanceof Restylable piece && piece.ownLook() != null ? piece : null;
    }

    private boolean isWorn(ArmourTier look) {
        Restylable piece = docked();
        return piece != null && piece.look(items.get(SLOT)) == look;
    }

    private boolean isAvailable(ArmourTier look) {
        Restylable piece = docked();
        return piece != null && piece.canWear(look);
    }

    private void restyle(ArmourTier look) {
        Restylable piece = docked();
        if (piece == null || !piece.canWear(look) || isWorn(look) || level == null) {
            return;
        }
        piece.setLook(items.get(SLOT), look);
        setChanged();
        level.playSound(null, worldPosition, look.getEquipSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, items);
        items.clear();
    }


    @Override
    public ModularUI createUI(Player player) {
        Supplier<WidgetGroup> template = UiTemplates.load(UI_PROJECT);
        WidgetGroup root = template != null ? template.get() : fallbackLayout();
        bind(root, player);
        return new ModularUI(root, this, player);
    }

    private void bind(WidgetGroup root, Player player) {
        Map<String, List<Widget>> widgetsById = MenuWidgets.indexById(root);

        Widget slot = MenuWidgets.firstById(widgetsById, SLOT_ID);
        if (slot instanceof SlotWidget) {
            List<Component> authored = slot.getTooltipTexts();
            MenuWidgets.bindKeySlot(slot, this, SLOT, authored.isEmpty()
                ? List.of(Component.translatable("gui.hexwright.armament_workbench.slot"))
                : authored);
        } else {
            Hexwright.LOGGER.warn("{}.ui is missing the '{}' slot widget", UI_PROJECT, SLOT_ID);
        }

        for (ArmourTier look : ArmourTier.values()) {
            bindLook(widgetsById, look);
        }

        if (MenuWidgets.firstById(widgetsById, STATUS_ID) instanceof LabelWidget status) {
            status.setTextProvider(() -> docked() == null
                ? "gui.hexwright.armament_workbench.empty"
                : "gui.hexwright.armament_workbench.choose");
        }

        MenuWidgets.bindPlayerInventory(widgetsById, player);
    }

    private void bindLook(Map<String, List<Widget>> widgetsById, ArmourTier look) {
        String id = lookId(look);
        if (!(MenuWidgets.firstById(widgetsById, id) instanceof WidgetGroup group)) {
            Hexwright.LOGGER.warn("{}.ui is missing the '{}' group", UI_PROJECT, id);
            return;
        }
        int width = group.getSizeWidth();
        int height = group.getSizeHeight();

        group.addWidget(0, new ImageWidget(0, 0, width, height,
            () -> isWorn(look) ? WORN_BACKDROP : IGuiTexture.EMPTY));

        Widget icon = MenuWidgets.firstById(widgetsById, id + "_icon");
        if (icon != null && icon.getParent() != null) {
            WidgetGroup parent = icon.getParent();
            parent.addWidget(parent.widgets.indexOf(icon) + 1, new ImageWidget(
                icon.getSelfPositionX() + (icon.getSizeWidth() - 16) / 2,
                icon.getSelfPositionY() + (icon.getSizeHeight() - 16) / 2,
                16, 16, () -> preview(look)));
        }

        group.addWidget(new ImageWidget(0, 0, width, height,
            () -> isAvailable(look) ? IGuiTexture.EMPTY : UNAVAILABLE_WASH));
        group.addWidget(new MenuWidgets.SilentButtonWidget(0, 0, width, height, click -> {
            if (!click.isRemote) {
                restyle(look);
            }
        }).setHoverTooltips(lookTooltip(look)));
    }

    private IGuiTexture preview(ArmourTier look) {
        Restylable piece = docked();
        if (piece == null) {
            return IGuiTexture.EMPTY;
        }
        return new ItemStackTexture(piece.lookItem(look));
    }

    private static String lookId(ArmourTier look) {
        return "look_" + look.displayQuality().name().toLowerCase(Locale.ROOT);
    }

    private static List<Component> lookTooltip(ArmourTier look) {
        Component quality = Component.translatable(look.displayQuality().translationKey())
            .withStyle(look.displayQuality().color());
        return List.of(
            Component.translatable("gui.hexwright.armament_workbench.look", quality),
            Component.translatable("gui.hexwright.armament_workbench.look.requires", quality)
                .withStyle(ChatFormatting.GRAY));
    }

    private static WidgetGroup fallbackLayout() {
        WidgetGroup root = new WidgetGroup(0, 0, 198, 241);
        root.setBackground(border("ldlib:textures/main-border.png"));
        root.addWidget(new ImageWidget(5, 5, 188, 20, new GuiTextureGroup(
            new ResourceTexture("ldlib:textures/title.png"),
            new TextTexture("block.hexwright.armament_workbench", 0xFFABAAAB).setDropShadow(true))));

        WidgetGroup panel = new WidgetGroup(5, 28, 188, 208);
        panel.setBackground(border("ldlib:textures/internal-heavy.png"));
        root.addWidget(panel);

        LabelWidget status = new LabelWidget(6, 6, "");
        status.setId(STATUS_ID);
        panel.addWidget(status);

        WidgetGroup frame = new WidgetGroup(8, 49, 36, 36);
        frame.setBackground(new ResourceTexture("ldlib:textures/menu/item_input.png"));
        frame.addWidget(placeholderSlot(9, 9, SLOT_ID));
        panel.addWidget(frame);
        panel.addWidget(new ImageWidget(46, 58, 18, 18, new ResourceTexture("ldlib:textures/menu/arrow.png")));

        ArmourTier[] looks = ArmourTier.values();
        for (int i = 0; i < looks.length; i++) {
            WidgetGroup row = new WidgetGroup(66, 20 + i * 24, 116, 22);
            row.setId(lookId(looks[i]));
            row.setBackground(border("ldlib:textures/menu/list-box.png"));
            row.setHoverTexture(WORN_BACKDROP);
            ImageWidget icon = new ImageWidget(2, 2, 18, 18, new ResourceTexture("ldlib:textures/menu/inv_nomal.png"));
            icon.setId(lookId(looks[i]) + "_icon");
            row.addWidget(icon);
            row.addWidget(new LabelWidget(25, 7, "armour_tier.hexwright." + looks[i].id()));
            panel.addWidget(row);
        }

        WidgetGroup inventory = new WidgetGroup(10, 120, 168, 82);
        inventory.setBackground(new ResourceTexture("ldlib:textures/menu/inv_main.png"));
        for (int i = 0; i < 36; i++) {
            int y = i < 9 ? 61 : 3 + (i / 9 - 1) * 18;
            inventory.addWidget(placeholderSlot(3 + (i % 9) * 18, y, "player_inv_" + i));
        }
        panel.addWidget(inventory);
        return root;
    }

    private static IGuiTexture border(String location) {
        return new ResourceBorderTexture(location, 180, 180, 4, 4);
    }

    private static SlotWidget placeholderSlot(int x, int y, String id) {
        SlotWidget slot = new SlotWidget();
        slot.setSelfPosition(x, y);
        slot.setId(id);
        return slot;
    }


    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.get(SLOT).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return restylable(stack) != null;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items, true);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
