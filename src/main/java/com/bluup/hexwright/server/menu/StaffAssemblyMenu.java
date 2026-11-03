package com.bluup.hexwright.server.menu;

import com.bluup.hexwright.common.staff_assembly.StaffPart;
import com.bluup.hexwright.common.staff_assembly.StaffPartCategory;
import com.bluup.hexwright.common.staff_assembly.StaffParts;
import com.bluup.hexwright.common.staff_assembly.calc.StaffCalculationResult;
import com.bluup.hexwright.common.staff_assembly.calc.StaffCalculator;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.item.ConfigurableStaffItem;
import com.bluup.hexwright.server.staff_assembly.StaffAssemblyData;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class StaffAssemblyMenu extends AbstractContainerMenu {

    public static final int CONTAINER_SIZE  = 20;
    public static final int STAFF_SLOT      = 0;
    public static final int CORE_SLOT       = 1;
    public static final int BINDING_FIRST   = 2;
    public static final int BINDING_LAST    = 7;
    public static final int FOCUS_FIRST     = 8;
    public static final int FOCUS_LAST      = 13;
    public static final int CATALYST_FIRST  = 14;
    public static final int CATALYST_LAST   = 19;

    public static final int CRAFT_BUTTON_BASE = 1000;

    private static final int PLAYER_INV_START = CATALYST_LAST + 1;
    private static final int HOTBAR_START     = PLAYER_INV_START + 27;
    private static final int SLOT_COUNT       = HOTBAR_START + 9;

    public static final int OUTER_PADDING = 4;
    public static final int SECTION_EDGE_PADDING = 4;

    public static final int IMAGE_WIDTH  = 303;
    public static final int IMAGE_HEIGHT = 252;

    public static final int LEFT_PANEL_X     = 7 + OUTER_PADDING;
    public static final int LEFT_PANEL_WIDTH = 162;
    public static final int LEFT_PANEL_TOP_PADDING = 6 + OUTER_PADDING;

    public static final int STAFF_SLOT_X     = LEFT_PANEL_X + (LEFT_PANEL_WIDTH - 18) / 2;
    public static final int STAFF_SLOT_Y     = 10 + LEFT_PANEL_TOP_PADDING;

    public static final int CORE_SLOT_X      = LEFT_PANEL_X + SECTION_EDGE_PADDING;
    public static final int CORE_SLOT_Y      = 44 + LEFT_PANEL_TOP_PADDING;

    public static final int SECTIONS_SLOTS_Y = 82 + LEFT_PANEL_TOP_PADDING;
    public static final int BINDING_X        = LEFT_PANEL_X + SECTION_EDGE_PADDING;
    public static final int FOCUS_X          = BINDING_X + (63 - SECTION_EDGE_PADDING);
    public static final int CATALYST_X       = FOCUS_X + (63 - SECTION_EDGE_PADDING);


    public static final int SECTION_WIDTH       = 36;
    public static final int CAPACITY_BAR_Y       = SECTIONS_SLOTS_Y + 3 * 18 + 2;
    public static final int BAR_HEIGHT           = 8;
    public static final int CAPACITY_LABEL_Y     = CAPACITY_BAR_Y + BAR_HEIGHT + 1;

    public static final int PANEL_GAP          = 10;
    public static final int RIGHT_PANEL_WIDTH  = 116;
    public static final int RIGHT_PANEL_X      = LEFT_PANEL_X + LEFT_PANEL_WIDTH + PANEL_GAP;
    public static final int RIGHT_PANEL_Y      = 8 + OUTER_PADDING;
    public static final int RIGHT_PANEL_HEIGHT = IMAGE_HEIGHT - RIGHT_PANEL_Y - (6 + OUTER_PADDING);

    public static final int PLAYER_INV_LEFT  = LEFT_PANEL_X;
    public static final int PLAYER_INV_TOP   = 156 + LEFT_PANEL_TOP_PADDING;
    public static final int HOTBAR_TOP       = PLAYER_INV_TOP + 3 * 18 + 4;

    private final Container container;
    private final ContainerLevelAccess access;

    private boolean coreLocked;

    public StaffAssemblyMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SIZE), ContainerLevelAccess.NULL);
    }

    public StaffAssemblyMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access) {
        super(HexwrightMenus.STAFF_ASSEMBLY_MENU, containerId);
        checkContainerSize(container, CONTAINER_SIZE);
        this.container = container;
        this.access = access;
        container.startOpen(playerInventory.player);


        this.addSlot(new StaffSlot(container, STAFF_SLOT, STAFF_SLOT_X, STAFF_SLOT_Y));

        this.addSlot(new CoreSlot(container, CORE_SLOT, CORE_SLOT_X, CORE_SLOT_Y));


        addPartGrid(container, BINDING_FIRST, BINDING_X);
        addPartGrid(container, FOCUS_FIRST, FOCUS_X);
        addPartGrid(container, CATALYST_FIRST, CATALYST_X);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_INV_LEFT + col * 18, PLAYER_INV_TOP + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, PLAYER_INV_LEFT + col * 18, HOTBAR_TOP));
        }

        syncCoreSlot();
    }

    private void addPartGrid(Container container, int firstIndex, int gridX) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                this.addSlot(new PartSlot(container, firstIndex + row * 2 + col,
                        gridX + col * 18, SECTIONS_SLOTS_Y + row * 18));
            }
        }
    }

    public ItemStack staff() {
        return this.slots.get(STAFF_SLOT).getItem();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= CRAFT_BUTTON_BASE) {
            return craft(player, id - CRAFT_BUTTON_BASE);
        }

        List<StaffPart> models = StaffParts.options(StaffPartCategory.MODEL);
        if (id < 0 || id >= models.size()) {
            return false;
        }

        ItemStack staff = this.slots.get(STAFF_SLOT).getItem();
        if (!(staff.getItem() instanceof ConfigurableStaffItem)) {
            return false;
        }

        StaffPart model = models.get(id);
        StaffCalculationResult result = computeResult();
        if (!StaffParts.isUnlocked(model, result.qualityRating())) {
            return false;
        }

        StaffAssemblyData.setPart(staff, StaffPartCategory.MODEL, model.id());
        this.slots.get(STAFF_SLOT).setChanged();
        this.broadcastChanges();
        return true;
    }

    private boolean craft(Player player, int modelSelection) {
        ItemStack staff = this.slots.get(STAFF_SLOT).getItem();
        if (!(staff.getItem() instanceof ConfigurableStaffItem)) {
            return false;
        }

        StaffCalculationResult live = computeResult();
        StaffCalculationResult result = StaffCalculator.mergeWithPersisted(live, StaffAssemblyData.getPersistedStats(staff));
        if (!StaffCalculator.isCraftable(result)) {
            return false;
        }

        if (modelSelection > 0) {
            List<StaffPart> models = StaffParts.options(StaffPartCategory.MODEL);
            int index = modelSelection - 1;
            if (index < 0 || index >= models.size()) {
                return false;
            }
            StaffPart model = models.get(index);
            if (!StaffParts.isUnlocked(model, result.qualityRating())) {
                return false;
            }
            StaffAssemblyData.setPart(staff, StaffPartCategory.MODEL, model.id());
        }

        StaffAssemblyData.setStats(staff, result);

        StaffAssemblyData.setCoreItem(staff, this.slots.get(CORE_SLOT).getItem());

        this.slots.get(CORE_SLOT).set(ItemStack.EMPTY);
        for (int i = BINDING_FIRST; i <= CATALYST_LAST; i++) {
            this.slots.get(i).set(ItemStack.EMPTY);
        }

        ItemStack toGive = staff.copy();
        this.slots.get(STAFF_SLOT).set(ItemStack.EMPTY);
        if (!player.getInventory().add(toGive) || !toGive.isEmpty()) {
            player.drop(toGive, false);
        }

        this.broadcastChanges();
        return true;
    }

    public StaffCalculationResult computeResult() {
        ItemStack core = this.slots.get(CORE_SLOT).getItem();
        List<ItemStack> wrap = slotRange(BINDING_FIRST, BINDING_LAST);
        List<ItemStack> focus = slotRange(FOCUS_FIRST, FOCUS_LAST);
        List<ItemStack> catalyst = slotRange(CATALYST_FIRST, CATALYST_LAST);
        return StaffCalculator.calculate(core, wrap, focus, catalyst);
    }

    private List<ItemStack> slotRange(int firstIndex, int lastIndex) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = firstIndex; i <= lastIndex; i++) {
            ItemStack stack = this.slots.get(i).getItem();
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        if (!slot.mayPickup(player)) return ItemStack.EMPTY;

        ItemStack original = slot.getItem();
        ItemStack moved = original.copy();

        if (index < PLAYER_INV_START) {
            if (!this.moveItemStackTo(original, PLAYER_INV_START, SLOT_COUNT, true)) {
                return ItemStack.EMPTY;
            }
        } else if (original.getItem() instanceof ConfigurableStaffItem) {
            if (!this.moveItemStackTo(original, STAFF_SLOT, STAFF_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < HOTBAR_START) {
            if (!this.moveItemStackTo(original, CORE_SLOT, PLAYER_INV_START, false)
                    && !this.moveItemStackTo(original, HOTBAR_START, SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.moveItemStackTo(original, CORE_SLOT, PLAYER_INV_START, false)
                    && !this.moveItemStackTo(original, PLAYER_INV_START, HOTBAR_START, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (original.getCount() == moved.getCount()) return ItemStack.EMPTY;

        slot.onTake(player, original);
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, HexwrightBlocks.STAFF_ASSEMBLY_BLOCK);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    @Override
    public void broadcastChanges() {
        syncCoreSlot();
        super.broadcastChanges();
    }

    private void syncCoreSlot() {
        ItemStack staff = this.slots.get(STAFF_SLOT).getItem();
        if (StaffAssemblyData.hasStats(staff)) {
            ItemStack persistedCore = StaffAssemblyData.getCoreItem(staff);
            ItemStack currentCore = this.slots.get(CORE_SLOT).getItem();
            if (!ItemStack.isSameItemSameTags(currentCore, persistedCore) || currentCore.getCount() != persistedCore.getCount()) {
                this.slots.get(CORE_SLOT).set(persistedCore);
            }
            this.coreLocked = true;
        } else if (this.coreLocked) {
            this.slots.get(CORE_SLOT).set(ItemStack.EMPTY);
            this.coreLocked = false;
        }
    }

    private static final class StaffSlot extends Slot {
        StaffSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof ConfigurableStaffItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private final class CoreSlot extends Slot {
        CoreSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !StaffAssemblyMenu.this.coreLocked;
        }

        @Override
        public boolean mayPickup(Player player) {
            return !StaffAssemblyMenu.this.coreLocked;
        }
    }

    private static final class PartSlot extends Slot {
        PartSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
