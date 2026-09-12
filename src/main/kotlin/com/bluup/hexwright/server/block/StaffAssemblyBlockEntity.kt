package com.bluup.hexwright.server.block

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.server.menu.UiTemplates
import com.bluup.hexwright.inits.HexwrightNetworking
import com.bluup.hexwright.client.ldlib.widget.StaffItemDisplayWidget
import com.bluup.hexwright.common.staff_assembly.StaffPart
import com.bluup.hexwright.common.staff_assembly.StaffPartCategory
import com.bluup.hexwright.common.staff_assembly.StaffParts
import com.bluup.hexwright.common.staff_assembly.calc.EfficiencyRating
import com.bluup.hexwright.common.staff_assembly.calc.StaffCalculationResult
import com.bluup.hexwright.common.staff_assembly.calc.StaffCalculator
import com.bluup.hexwright.server.item.ConfigurableStaffItem
import com.bluup.hexwright.server.staff_assembly.StaffAssemblyData
import com.bluup.hexwright.server.staff_assembly.StaffCoreItem
import com.bluup.hexwright.server.menu.StaffAssemblyMenu
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder
import com.lowdragmc.lowdraglib.gui.modular.ModularUI
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget
import com.lowdragmc.lowdraglib.gui.widget.DialogWidget
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup
import com.lowdragmc.lowdraglib.gui.widget.Widget
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.Containers
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.roundToInt
import java.util.Locale

class StaffAssemblyBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.STAFF_ASSEMBLY_BLOCK_ENTITY, pos, state), Container, MenuProvider, IUIHolder.BlockEntityUI {

    private companion object {
        private const val TEXT_COLOR = 0xC8D7FF.toInt()
        private const val TEXT_DIM_COLOR = 0xFF8895AA.toInt()
        private const val COLOR_BAD = 0xFFFF5555.toInt()
        private const val BAR_BG_COLOR = 0xFF0E1014.toInt()
        private const val BAR_ATTUNE_COLOR = 0xFFCC6633.toInt()
        private const val BAR_ATTUNE_OVERLOAD_COLOR = 0xFFFF5555.toInt()
        private const val BAR_EFFICIENCY_COLOR = 0xFF55CC55.toInt()
        private const val BAR_WRAP_COMPONENT_COLOR = 0xFF78448B.toInt()
        private const val BAR_FOCUS_COMPONENT_COLOR = 0xFF425B88.toInt()
        private const val BAR_CATALYST_COMPONENT_COLOR = 0xFF538352.toInt()
        private const val COLOR_SELECTED_BORDER = 0xFFE8C547.toInt()
        private const val COLOR_DIM_OVERLAY = 0xA0000000.toInt()
        private const val COLOR_TEXT_DISABLED = 0xFF7A7A7A.toInt()

        private const val RESERVE_VALUE_X = 48
        private const val RESERVE_VALUE_WIDTH = 48
        private const val RESERVE_VALUE_WRAP_WIDTH = 64

        private val BUTTON_DISABLED_TEXTURE = ResourceBorderTexture("ldlib:textures/button-disabled.png", 180, 180, 4, 4)

        private val BUTTON_DEFAULT_TEXTURE = ResourceBorderTexture("ldlib:textures/button-defaultl.png", 180, 180, 4, 4)
        private val BUTTON_HOVER_TEXTURE = ResourceBorderTexture("ldlib:textures/button-hovered.png", 180, 180, 4, 4)

        private val MODEL_FILTER_TABS: List<Pair<String, EfficiencyRating?>> = listOf(
            "all_staffs" to null,
            "masterwork" to EfficiencyRating.MASTERWORK,
            "exquisite" to EfficiencyRating.EXQUISITE,
            "fine" to EfficiencyRating.FINE,
            "sound" to EfficiencyRating.SOUND
        )

        private const val MODEL_GRID_COLUMNS = 4
        private const val MODEL_CELL_WIDTH = 76
        private const val MODEL_CELL_HEIGHT = 92
        private const val MODEL_CELL_GAP = 0
        private const val MODEL_NAME_SCALE = 0.65f

        private const val TAB_ACTIVE_COLOR = -1
        private const val TAB_INACTIVE_COLOR = -6447972

        private const val CORE_DESC_CHAR_LIMIT = 60
    }

    private val items: NonNullList<ItemStack> = NonNullList.withSize(StaffAssemblyMenu.CONTAINER_SIZE, ItemStack.EMPTY)
    private var coreLocked: Boolean = false
    private var syncingCore = false
    private var pendingModelId: String? = null
    private var pendingModelDirty = false
    private var syncedStaffForModel = ItemStack.EMPTY

    override fun getContainerSize(): Int = StaffAssemblyMenu.CONTAINER_SIZE

    override fun isEmpty(): Boolean = items.all { it.isEmpty }

    override fun getItem(slot: Int): ItemStack = items[slot]

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        if (slot == StaffAssemblyMenu.CORE_SLOT && this.coreLocked) {
            return ItemStack.EMPTY
        }
        val result = ContainerHelper.removeItem(items, slot, amount)
        if (!result.isEmpty) setChanged()
        return result
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack {
        if (slot == StaffAssemblyMenu.CORE_SLOT && this.coreLocked) {
            return ItemStack.EMPTY
        }
        return ContainerHelper.takeItem(items, slot)
    }

    override fun setItem(slot: Int, stack: ItemStack) {
        if (slot == StaffAssemblyMenu.CORE_SLOT && this.coreLocked) {
            return
        }
        items[slot] = stack
        val slotLimit = slotLimit(slot)
        if (stack.count > slotLimit) stack.count = slotLimit
        setChanged()
    }

    override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)

    override fun clearContent() {
        for (i in items.indices) items[i] = ItemStack.EMPTY
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        ContainerHelper.loadAllItems(tag, items)
        clampComponentStacksToOne()
        syncCoreSlot()
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        ContainerHelper.saveAllItems(tag, items)
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun setChanged() {
        if (!syncingCore) {
            syncingCore = true
            syncCoreSlot()
            syncingCore = false
        }
        super.setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    override fun getDisplayName(): Component = Component.translatable("block.hexwright.staff_assembly")

    override fun createMenu(containerId: Int, inventory: Inventory, player: Player): AbstractContainerMenu =
        StaffAssemblyMenu(containerId, inventory, this, ContainerLevelAccess.create(requireNotNull(level), worldPosition))


    override fun createUI(entityPlayer: Player): ModularUI {
        val template = UiTemplates.load("staff_creation_screen")
        if (template == null) {
            Hexwright.LOGGER.error("Failed to load staff_creation_screen UI; falling back to minimal staff assembly UI")
            return createFallbackUI(entityPlayer)
        }

        val root = template.get() ?: run {
            Hexwright.LOGGER.error("staff_creation_screen UI template produced no widget tree; falling back to minimal staff assembly UI")
            return createFallbackUI(entityPlayer)
        }

        val ui = ModularUI(root, this, entityPlayer)
        bindCreationScreenWidgets(root, ui, entityPlayer)
        return ui
    }

    private fun createFallbackUI(player: Player): ModularUI {
        val ui = ModularUI(StaffAssemblyMenu.IMAGE_WIDTH, StaffAssemblyMenu.IMAGE_HEIGHT, this, player)

        ui.widget(StaffSlotWidget(StaffAssemblyMenu.STAFF_SLOT, 20, 20))
        ui.widget(CoreSlotWidget(StaffAssemblyMenu.CORE_SLOT, 60, 20))

        for (i in 0 until 6) {
            ui.widget(PartSlotWidget(StaffAssemblyMenu.BINDING_FIRST + i, 20 + i * 18, 50))
        }
        for (i in 0 until 6) {
            ui.widget(PartSlotWidget(StaffAssemblyMenu.FOCUS_FIRST + i, 20 + i * 18, 70))
        }
        for (i in 0 until 6) {
            ui.widget(PartSlotWidget(StaffAssemblyMenu.CATALYST_FIRST + i, 20 + i * 18, 90))
        }

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val slotIndex = col + row * 9 + 9
                ui.widget(SlotWidget(player.inventory, slotIndex, 19 + col * 18, 130 + row * 18))
            }
        }
        for (col in 0 until 9) {
            ui.widget(SlotWidget(player.inventory, col, 19 + col * 18, 188))
        }

        return ui
    }

    private fun bindCreationScreenWidgets(root: WidgetGroup, ui: ModularUI, player: Player) {
        spliceNamedSlot(root, "staff_slot") { x, y -> StaffSlotWidget(StaffAssemblyMenu.STAFF_SLOT, x, y) }
        spliceNamedSlot(root, "core_slot") { x, y -> CoreSlotWidget(StaffAssemblyMenu.CORE_SLOT, x, y) }

        bindPartGrid(root, "wrap_inv", StaffAssemblyMenu.BINDING_FIRST)
        bindPartGrid(root, "focus_inv", StaffAssemblyMenu.FOCUS_FIRST)
        bindPartGrid(root, "catalyst_inv", StaffAssemblyMenu.CATALYST_FIRST)

        addStatusBar(root, "wrap_status",
            { mergedResultForUi().wrap().statValue() }, { StaffCalculator.WRAP_CONFIG.usableValueCap() },
            { formatNumber(mergedResultForUi().wrap().statValue()) + "/" + formatNumber(StaffCalculator.WRAP_CONFIG.usableValueCap()) },
            BAR_WRAP_COMPONENT_COLOR)
        addStatusBar(root, "focus_status",
            { mergedResultForUi().focus().statValue() }, { StaffCalculator.FOCUS_CONFIG.usableValueCap() },
            { formatNumber(mergedResultForUi().focus().statValue()) + "/" + formatNumber(StaffCalculator.FOCUS_CONFIG.usableValueCap()) },
            BAR_FOCUS_COMPONENT_COLOR)
        addStatusBar(root, "catalyst_status",
            { mergedResultForUi().catalyst().statValue() }, { StaffCalculator.CATALYST_CONFIG.usableValueCap() },
            { formatNumber(mergedResultForUi().catalyst().statValue()) + "/" + formatNumber(StaffCalculator.CATALYST_CONFIG.usableValueCap()) },
            BAR_CATALYST_COMPONENT_COLOR)

        addStatusBar(root, "attenuation",
            { mergedResultForUi().totalAttunement() }, { StaffCalculator.MAX_ATTUNEMENT },
            { attunementValueText() },
            BAR_ATTUNE_COLOR, BAR_ATTUNE_OVERLOAD_COLOR,
            Component.translatable("gui.hexwright.staff_assembly.attunement.tooltip"))
        addStatusBar(root, "efficiency",
            { efficiencyRatio() }, { 1.0 },
            { efficiencyValueText() },
            BAR_EFFICIENCY_COLOR,
            tooltip = Component.translatable("gui.hexwright.staff_assembly.efficiency.tooltip"))

        val staffPreview = (root.getFirstWidgetById("^staff_preview$") as? StaffItemDisplayWidget)
            ?.also { it.setStackSupplier { previewItem() } }
        val qualityLabel = (root.getFirstWidgetById("^quality_label$") as? LabelWidget)?.also { it.setClientSideWidget() }
        val qualityPercent = (root.getFirstWidgetById("^quality_percent$") as? TextTextureWidget)?.also { it.setClientSideWidget() }
        val gridSizeValue = (root.getFirstWidgetById("^grid_size_value$") as? TextTextureWidget)?.also { it.setClientSideWidget() }
        val ambitValue = (root.getFirstWidgetById("^ambit_value$") as? TextTextureWidget)?.also { it.setClientSideWidget() }
        val reserveValue = (root.getFirstWidgetById("^reserve_value$") as? TextTextureWidget)?.also {
            it.setClientSideWidget()
            it.setSelfPosition(RESERVE_VALUE_X, it.selfPositionY)
            it.setSize(RESERVE_VALUE_WIDTH, it.size.height)
            it.textTexture.setWidth(RESERVE_VALUE_WRAP_WIDTH)
        }
        val coreDesc = (root.getFirstWidgetById("^core_desc$") as? LabelWidget)?.also { it.setClientSideWidget() }

        val setCraftEnabled = makeGroupClickable(root, "craft") {
            if (!player.level().isClientSide) {
                craft(player)
            }
        }
        val setChangeModelEnabled = makeGroupClickable(root, "change_model") {
            openModelSelectDialog(ui)
        }

        root.addWidget(
            StaffAssemblySyncWidget(staffPreview, qualityLabel, qualityPercent, gridSizeValue, ambitValue, reserveValue, coreDesc, setCraftEnabled, setChangeModelEnabled)
        )
    }

    private fun spliceNamedSlot(root: WidgetGroup, id: String, factory: (Int, Int) -> SlotWidget) {
        val original = root.getFirstWidgetById("^$id$") as? SlotWidget
        if (original == null) {
            Hexwright.LOGGER.warn("staff_creation_screen is missing slot widget '$id'")
            return
        }
        val parent = original.parent
        if (parent == null) {
            Hexwright.LOGGER.warn("staff_creation_screen slot widget '$id' has no parent group")
            return
        }

        val index = parent.widgets.indexOf(original)
        parent.removeWidget(original)

        val replacement = factory(original.selfPositionX, original.selfPositionY)
        replacement.id = id
        replacement.setBackground(original.backgroundTexture)
        replacement.setHoverTooltips(original.tooltipTexts)
        replacement.setDrawHoverOverlay(original.drawHoverOverlay)
        replacement.setDrawHoverTips(original.drawHoverTips)
        replacement.setLocationInfo(false, false)
        parent.addWidget(if (index < 0) parent.widgets.size else index, replacement)
    }

    private fun bindPartGrid(root: WidgetGroup, id: String, firstIndex: Int) {
        val grid = root.getFirstWidgetById("^$id$") as? WidgetGroup
        if (grid == null) {
            Hexwright.LOGGER.warn("staff_creation_screen is missing slot grid '$id'")
            return
        }

        val originals = ArrayList(grid.widgets.filterIsInstance<SlotWidget>())
        if (originals.size != StaffCalculator.MAX_ITEMS_PER_COMPONENT) {
            Hexwright.LOGGER.warn(
                "staff_creation_screen slot grid '$id' has {} slots, expected {} - extras will be left inert; fix the grid's rows/cols in the LDLib editor",
                originals.size, StaffCalculator.MAX_ITEMS_PER_COMPONENT
            )
        }

        grid.clearAllWidgets()
        for ((offset, original) in originals.withIndex()) {
            if (offset >= StaffCalculator.MAX_ITEMS_PER_COMPONENT) {
                original.setCanPutItems(false)
                original.setCanTakeItems(false)
                grid.addWidget(original)
                continue
            }
            val replacement = PartSlotWidget(firstIndex + offset, original.selfPositionX, original.selfPositionY)
            replacement.id = original.id
            replacement.setBackground(original.backgroundTexture)
            replacement.setLocationInfo(false, false)
            grid.addWidget(replacement)
        }
    }

    private fun addStatusBar(
        root: WidgetGroup,
        id: String,
        valueSupplier: () -> Double,
        capSupplier: () -> Double,
        textSupplier: () -> String,
        fillColor: Int,
        overflowColor: Int = COLOR_BAD,
        tooltip: Component? = null
    ) {
        val group = root.getFirstWidgetById("^$id$") as? WidgetGroup
        if (group == null) {
            Hexwright.LOGGER.warn("staff_creation_screen is missing group '$id'")
            return
        }
        val bar = OverflowBarWidget(0, 0, group.sizeWidth, group.sizeHeight, valueSupplier, capSupplier, textSupplier, fillColor, overflowColor)
        if (tooltip != null) {
            bar.setHoverTooltips(tooltip)
        }
        group.addWidget(bar)
    }

    private fun makeGroupClickable(root: WidgetGroup, id: String, onClick: () -> Unit): (Boolean) -> Unit {
        val group = root.getFirstWidgetById("^$id$") as? WidgetGroup
        if (group == null) {
            Hexwright.LOGGER.warn("staff_creation_screen is missing group '$id'")
            return {}
        }

        val button = ButtonWidget(0, 0, group.sizeWidth, group.sizeHeight, IGuiTexture.EMPTY) { _ -> onClick() }
        group.addWidget(button)

        val enabledBackground = group.backgroundTexture ?: IGuiTexture.EMPTY
        val labels = group.getWidgetsByType(TextTextureWidget::class.java)
        val enabledTextColors = labels.map { it.textTexture.color }
        var lastEnabled: Boolean? = null

        return { enabled ->
            if (lastEnabled != enabled) {
                lastEnabled = enabled
                button.isActive = enabled
                group.isActive = enabled
                group.setBackground(if (enabled) enabledBackground else BUTTON_DISABLED_TEXTURE)
                labels.forEachIndexed { i, label ->
                    label.textTexture.setColor(if (enabled) enabledTextColors[i] else COLOR_TEXT_DISABLED)
                }
            }
        }
    }

    private fun slotLimit(slot: Int): Int {
        return if (slot in StaffAssemblyMenu.STAFF_SLOT..StaffAssemblyMenu.CATALYST_LAST) 1 else maxStackSize
    }

    private fun clampComponentStacksToOne() {
        for (slot in StaffAssemblyMenu.STAFF_SLOT..StaffAssemblyMenu.CATALYST_LAST) {
            val stack = items[slot]
            if (!stack.isEmpty && stack.count > 1) {
                stack.count = 1
            }
        }
    }

    private fun canCraftNow(): Boolean {
        val staff = staffItem()
        if (staff.item !is ConfigurableStaffItem) {
            return false
        }
        val live = computeResult()
        val merged = StaffCalculator.mergeWithPersisted(live, StaffAssemblyData.getPersistedStats(staff))
        return StaffCalculator.isCraftable(merged)
    }

    private fun craft(player: Player): Boolean {
        val staff = staffItem()
        if (staff.item !is ConfigurableStaffItem) {
            return false
        }

        val live = computeResult()
        val result = StaffCalculator.mergeWithPersisted(live, StaffAssemblyData.getPersistedStats(staff))
        if (!StaffCalculator.isCraftable(result)) {
            return false
        }

        val currentModelId = StaffAssemblyData.getPart(staff, StaffPartCategory.MODEL)
        val desiredModelId = this.pendingModelId
        if (desiredModelId != null && desiredModelId != currentModelId) {
            val selected = StaffParts.find(StaffPartCategory.MODEL, desiredModelId).orElse(null)
            if (selected != null && StaffParts.isUnlocked(selected, result.qualityRating())) {
                StaffAssemblyData.setPart(staff, StaffPartCategory.MODEL, selected.id())
            }
        }

        StaffAssemblyData.setStats(staff, result)


        StaffAssemblyData.setCoreItem(staff, this.items[StaffAssemblyMenu.CORE_SLOT])

        this.items[StaffAssemblyMenu.CORE_SLOT] = ItemStack.EMPTY
        for (i in StaffAssemblyMenu.BINDING_FIRST..StaffAssemblyMenu.CATALYST_LAST) {
            this.items[i] = ItemStack.EMPTY
        }

        val toGive = staff.copy()
        this.items[StaffAssemblyMenu.STAFF_SLOT] = ItemStack.EMPTY
        if (!player.inventory.add(toGive) || !toGive.isEmpty) {
            player.drop(toGive, false)
        }

        setChanged()
        return true
    }

    private fun staffItem(): ItemStack = this.items[StaffAssemblyMenu.STAFF_SLOT]

    private fun syncPendingModelFromStaff() {
        val staff = staffItem()
        if (staff.isEmpty) {
            pendingModelId = null
            pendingModelDirty = false
            syncedStaffForModel = ItemStack.EMPTY
            return
        }

        val sameItem = ItemStack.isSameItem(staff, syncedStaffForModel)
        val sameItemAndTags = ItemStack.isSameItemSameTags(staff, syncedStaffForModel)
        val shouldSyncFromStaff = pendingModelId == null || !sameItem || (!pendingModelDirty && !sameItemAndTags)

        if (shouldSyncFromStaff) {
            pendingModelId = StaffAssemblyData.getPart(staff, StaffPartCategory.MODEL)
            pendingModelDirty = false
        }
        syncedStaffForModel = staff.copy()
    }

    private fun openModelSelectDialog(ui: ModularUI) {
        val staff = staffItem()
        if (staff.item !is ConfigurableStaffItem) {
            return
        }

        val template = UiTemplates.load("staff_selection_screen")
        if (template == null) {
            Hexwright.LOGGER.error("Failed to load staff_selection_screen UI; model selection is unavailable")
            return
        }
        val root = template.get() ?: return

        val dialog = DialogWidget(ui.mainGroup, true)
        dialog.setClickClose(true)
        dialog.addWidget(ImageWidget(0, 0, ui.width, ui.height, ColorRectTexture(0x4F000000)))

        root.setSelfPosition((ui.width - root.sizeWidth) / 2, (ui.height - root.sizeHeight) / 2)
        dialog.addWidget(root)

        bindStaffSelectionWidgets(
            root,
            mergedResultForUi().qualityRating(),
            pendingModelId,
            { chosenId ->
                pendingModelId = chosenId
                pendingModelDirty = true
                if (level?.isClientSide == true) {
                    HexwrightNetworking.sendStaffModelSelect(worldPosition, chosenId)
                }
            },
            { dialog.close() }
        )
    }

    fun applyModelSelection(modelId: String) {
        pendingModelId = modelId
        pendingModelDirty = true
    }

    private fun bindStaffSelectionWidgets(
        root: WidgetGroup,
        currentQuality: EfficiencyRating,
        selectedId: String?,
        onSelect: (String) -> Unit,
        onClose: () -> Unit
    ) {
        val scrollGroup = root.getWidgetsByType(DraggableScrollableWidgetGroup::class.java).firstOrNull { it.id == "container" }
        val template = root.getFirstWidgetById("^example_staff$") as? WidgetGroup
        val templateTag = (template as? IConfigurableWidget)?.serializeWrapper()

        if (scrollGroup == null || template == null || templateTag == null) {
            Hexwright.LOGGER.warn("staff_selection_screen is missing its 'example_staff' template or grid group")
            return
        }
        scrollGroup.removeWidget(template)

        val cells = ArrayList<Pair<StaffPart, WidgetGroup>>()
        for (model in StaffParts.options(StaffPartCategory.MODEL)) {
            val clone = IConfigurableWidget.deserializeWrapper(templateTag)?.widget() as? WidgetGroup ?: continue
            clone.id = "example_staff_${model.id()}"

            (clone.getFirstWidgetById("^item$") as? StaffItemDisplayWidget)?.setStackSupplier { model.icon() }

            (clone.getFirstWidgetById("^name$") as? TextTextureWidget)?.let {
                it.setClientSideWidget()
                it.textTexture.scale(MODEL_NAME_SCALE)
                it.textTexture.setWidth((it.size.width / MODEL_NAME_SCALE).roundToInt())
                it.setText(model.displayName())
            }

            val unlocked = StaffParts.isUnlocked(model, currentQuality)
            val tooltip = if (unlocked) {
                model.displayName()
            } else {
                Component.translatable(
                    "gui.hexwright.staff_selection.locked",
                    model.displayName(),
                    model.requiredQuality().label()
                )
            }
            clone.setHoverTooltips(tooltip)

            if (unlocked) {
                clone.addWidget(ButtonWidget(0, 0, clone.sizeWidth, clone.sizeHeight) { _ ->
                    onSelect(model.id())
                    onClose()
                })
            } else {
                clone.addWidget(ScrollCellLockedOverlayWidget(clone.sizeWidth, clone.sizeHeight))
            }

            if (model.id() == selectedId) {
                clone.addWidget(SelectedCellWidget(clone.sizeWidth, clone.sizeHeight))
            }

            cells.add(model to clone)
        }

        fun applyFilter(filter: EfficiencyRating?) {
            for ((_, cell) in cells) {
                scrollGroup.removeWidget(cell)
            }
            var shown = 0
            for ((model, cell) in cells) {
                if (filter != null && model.requiredQuality() != filter) continue
                val col = shown % MODEL_GRID_COLUMNS
                val row = shown / MODEL_GRID_COLUMNS
                cell.setSelfPosition(col * (MODEL_CELL_WIDTH + MODEL_CELL_GAP), row * (MODEL_CELL_HEIGHT + MODEL_CELL_GAP))
                scrollGroup.addWidget(cell)
                shown++
            }
            scrollGroup.computeMax()
        }

        applyFilter(null)

        val tabGroups = LinkedHashMap<String, WidgetGroup>()
        for ((tabId, _) in MODEL_FILTER_TABS) {
            (root.getFirstWidgetById("^$tabId$") as? WidgetGroup)?.let { tabGroups[tabId] = it }
        }

        fun highlightTab(activeId: String) {
            for ((tabId, tabGroup) in tabGroups) {
                tabGroup.getWidgetsByType(LabelWidget::class.java).firstOrNull()
                    ?.setTextColor(if (tabId == activeId) TAB_ACTIVE_COLOR else TAB_INACTIVE_COLOR)
            }
        }

        for ((tabId, filter) in MODEL_FILTER_TABS) {
            val tabGroup = tabGroups[tabId] ?: continue
            tabGroup.setBackground(BUTTON_DEFAULT_TEXTURE)
            tabGroup.setHoverTexture(BUTTON_HOVER_TEXTURE)
            tabGroup.addWidget(ButtonWidget(0, 0, tabGroup.sizeWidth, tabGroup.sizeHeight) { _ ->
                applyFilter(filter)
                highlightTab(tabId)
            })
        }
        highlightTab("all_staffs")
    }

    private class ScrollCellLockedOverlayWidget(width: Int, height: Int) : Widget(0, 0, width, height) {
        override fun drawInBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks)
            val pos = position
            val size = this.size
            graphics.fill(pos.x, pos.y, pos.x + size.width, pos.y + size.height, COLOR_DIM_OVERLAY)
        }
    }

    private class SelectedCellWidget(width: Int, height: Int) : Widget(0, 0, width, height) {
        override fun drawInBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks)
            val pos = position
            val size = this.size
            graphics.fill(pos.x - 1, pos.y - 1, pos.x + size.width + 1, pos.y, COLOR_SELECTED_BORDER)
            graphics.fill(pos.x - 1, pos.y + size.height, pos.x + size.width + 1, pos.y + size.height + 1, COLOR_SELECTED_BORDER)
            graphics.fill(pos.x - 1, pos.y, pos.x, pos.y + size.height, COLOR_SELECTED_BORDER)
            graphics.fill(pos.x + size.width, pos.y, pos.x + size.width + 1, pos.y + size.height, COLOR_SELECTED_BORDER)
        }
    }

    private fun coreDescriptionSingleLine(): String {
        val core = mergedResultForUi().core().orElse(null)
            ?: return Component.translatable("gui.hexwright.staff_assembly.no_core").string

        val coreStack = this.items[StaffAssemblyMenu.CORE_SLOT]
        val coreItem = coreStack.item
        if (coreItem is StaffCoreItem) {
            val effectLines = ArrayList<Component>()
            StaffCoreItem.appendEffectTooltip(coreStack, coreItem.kind(), effectLines)
            if (effectLines.isNotEmpty()) {
                return truncateOneLine(effectLines[0].string)
            }
        }
        return truncateOneLine(core.description().joinToString(" ") { it.string })
    }

    private fun truncateOneLine(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.length <= CORE_DESC_CHAR_LIMIT) trimmed else trimmed.take(CORE_DESC_CHAR_LIMIT - 1).trimEnd() + "…"
    }

    private fun mergedResultForUi(): StaffCalculationResult {
        val live = computeResult()
        return StaffCalculator.mergeWithPersisted(live, StaffAssemblyData.getPersistedStats(staffItem()))
    }

    private fun previewItem(): ItemStack {
        val model = pendingModelId?.let { StaffParts.find(StaffPartCategory.MODEL, it).orElse(null) }
        return model?.icon() ?: staffItem()
    }

    private fun persistedStatsForUi() = StaffAssemblyData.getPersistedStats(staffItem())

    private fun hasPersistedStatsForUi(): Boolean = StaffAssemblyData.hasStats(staffItem())

    private fun liveChangedForUi(): Boolean {
        val live = computeResult()
        return live.wrap().itemCount() > 0 || live.focus().itemCount() > 0 || live.catalyst().itemCount() > 0 || live.core().isPresent
    }

    private fun qualityDelta(): Double = mergedResultForUi().quality() - StaffAssemblyData.getQuality(staffItem())

    private fun attunementDelta(): Double = mergedResultForUi().totalAttunement() - StaffAssemblyData.getAttunement(staffItem())

    private fun efficiencyDelta(): Double = mergedResultForUi().overallEfficiency() - StaffAssemblyData.getEfficiency(staffItem())

    private fun gridSizeDelta(): Double {
        val merged = mergedResultForUi().wrap()
        val persisted = persistedStatsForUi().wrap()
        val live = computeResult().wrap()
        return if (hasPersistedStatsForUi() && live.itemCount() > 0) merged.output() - persisted.output() else 0.0
    }

    private fun ambitDelta(): Double {
        val merged = mergedResultForUi().focus()
        val persisted = persistedStatsForUi().focus()
        val live = computeResult().focus()
        return if (hasPersistedStatsForUi() && live.itemCount() > 0) merged.output() - persisted.output() else 0.0
    }

    private fun attunementValueText(): String {
        val merged = mergedResultForUi()
        val base = Component.translatable(
            "gui.hexwright.staff_assembly.attunement.value",
            formatNumber(merged.totalAttunement()),
            formatNumber(StaffCalculator.MAX_ATTUNEMENT)
        ).string
        return base + deltaSuffix(attunementDelta(), false)
    }

    private fun efficiencyValueText(): String {
        val base = Component.translatable("gui.hexwright.staff_assembly.efficiency.value", percent(efficiencyRatio())).string
        return base + deltaSuffix(efficiencyDelta() * 100.0, true)
    }

    private fun gridSizeValueText(): String = formatNumber(mergedResultForUi().wrap().output()) + deltaSuffix(gridSizeDelta(), false)

    private fun ambitValueText(): String = formatNumber(mergedResultForUi().focus().output()) + deltaSuffix(ambitDelta(), false)

    private fun reserveValueText(): String {
        val catalystOutput = mergedResultForUi().catalyst().output()
        val discountPercent = Math.round(StaffAssemblyData.getMediaDiscount(catalystOutput) * 100.0).toInt()
        return if (discountPercent <= 0) {
            "0"
        } else {
            Component.translatable("gui.hexwright.staff_assembly.reserve.value", discountPercent.toString()).string
        }
    }

    private fun reserveTooltip(): Component {
        val catalystOutput = mergedResultForUi().catalyst().output()
        val discountPercent = Math.round(StaffAssemblyData.getMediaDiscount(catalystOutput) * 100.0).toInt()
        return if (discountPercent <= 0) {
            Component.translatable("gui.hexwright.staff_assembly.reserve.tooltip.empty")
        } else {
            Component.translatable("gui.hexwright.staff_assembly.reserve.tooltip", discountPercent)
        }
    }

    private fun deltaSuffix(delta: Double, asPercent: Boolean): String {
        if (delta == 0.0 || !hasPersistedStatsForUi() || !liveChangedForUi()) {
            return ""
        }
        val amount = if (asPercent) formatNumber(delta) + "%" else formatNumber(delta)
        return " (${if (delta > 0) "+" else ""}$amount)"
    }

    private fun efficiencyRatio(): Double {
        val merged = mergedResultForUi()
        val componentAttunement = merged.wrap().attunementCost() + merged.focus().attunementCost() + merged.catalyst().attunementCost()
        if (componentAttunement <= 0.0) {
            return 1.0
        }
        return merged.overallEfficiency().coerceIn(0.0, 1.0)
    }

    private fun percent(value: Double): String = String.format(Locale.ROOT, "%.0f%%", value * 100.0)

    private fun formatNumber(value: Double): String = StaffCalculator.displayed(value).toString()

    private fun computeResult(): StaffCalculationResult {
        val core = this.items[StaffAssemblyMenu.CORE_SLOT]
        val wrap = slotRange(StaffAssemblyMenu.BINDING_FIRST, StaffAssemblyMenu.BINDING_LAST)
        val focus = slotRange(StaffAssemblyMenu.FOCUS_FIRST, StaffAssemblyMenu.FOCUS_LAST)
        val catalyst = slotRange(StaffAssemblyMenu.CATALYST_FIRST, StaffAssemblyMenu.CATALYST_LAST)
        return StaffCalculator.calculate(core, wrap, focus, catalyst)
    }

    private fun slotRange(firstIndex: Int, lastIndex: Int): List<ItemStack> {
        val stacks: MutableList<ItemStack> = ArrayList()
        for (i in firstIndex..lastIndex) {
            val stack = this.items[i]
            if (!stack.isEmpty) {
                stacks.add(stack)
            }
        }
        return stacks
    }

    fun dropContentsOnBreak(level: Level, pos: BlockPos) {
        if (this.coreLocked) {
            this.items[StaffAssemblyMenu.CORE_SLOT] = ItemStack.EMPTY
            this.coreLocked = false
        }
        Containers.dropContents(level, pos, this)
    }

    private fun syncCoreSlot() {
        val staff = this.items[StaffAssemblyMenu.STAFF_SLOT]
        if (StaffAssemblyData.hasStats(staff)) {
            val persistedCore = StaffAssemblyData.getCoreItem(staff)
            val currentCore = this.items[StaffAssemblyMenu.CORE_SLOT]
            if (!ItemStack.isSameItemSameTags(currentCore, persistedCore) || currentCore.count != persistedCore.count) {
                this.items[StaffAssemblyMenu.CORE_SLOT] = persistedCore.copy()
            }
            this.coreLocked = true
        } else if (this.coreLocked) {
            this.items[StaffAssemblyMenu.CORE_SLOT] = ItemStack.EMPTY
            this.coreLocked = false
        }
    }

    private class OverflowBarWidget(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        private val currentSupplier: () -> Double,
        private val capSupplier: () -> Double,
        private val textSupplier: () -> String,
        private val fillColor: Int,
        private val overflowColor: Int
    ) : Widget(x, y, width, height) {
        private val textScale = 0.7f

        override fun drawInBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks)
            val pos = position
            val size = this.size
            val current = currentSupplier().coerceAtLeast(0.0)
            val cap = capSupplier().coerceAtLeast(0.0)

            graphics.fill(pos.x, pos.y, pos.x + size.width, pos.y + size.height, BAR_BG_COLOR)

            val scale = maxOf(current, cap)
            if (scale <= 0.0) {
                return
            }

            val capWidth = (size.width * (cap / scale)).roundToInt().coerceIn(0, size.width)
            val currentWidth = (size.width * (current / scale)).roundToInt().coerceIn(0, size.width)

            val normalWidth = minOf(currentWidth, capWidth)
            if (normalWidth > 0) {
                graphics.fill(pos.x, pos.y, pos.x + normalWidth, pos.y + size.height, fillColor)
            }

            if (currentWidth > capWidth) {
                graphics.fill(pos.x + capWidth, pos.y, pos.x + currentWidth, pos.y + size.height, overflowColor)
            }

            val text = textSupplier()
            if (text.isNotEmpty()) {
                val font = Minecraft.getInstance().font
                val textWidth = (font.width(text) * textScale).roundToInt()
                val textHeight = (8 * textScale).roundToInt()
                val textX = pos.x + (size.width - textWidth) / 2
                val textY = pos.y + (size.height - textHeight) / 2
                graphics.pose().pushPose()
                graphics.pose().translate(textX.toFloat(), textY.toFloat(), 0f)
                graphics.pose().scale(textScale, textScale, 1f)
                graphics.drawString(font, text, 0, 0, TEXT_COLOR, false)
                graphics.pose().popPose()
            }
        }
    }

    private inner class StaffSlotWidget(slotIndex: Int, x: Int, y: Int) : SlotWidget(this@StaffAssemblyBlockEntity, slotIndex, x, y) {
        override fun createSlot(inventory: Container, index: Int): Slot {
            return object : Slot(inventory, index, 0, 0) {
                override fun mayPlace(stack: ItemStack): Boolean = stack.item is ConfigurableStaffItem

                override fun getMaxStackSize(): Int = 1
            }
        }
    }

    private inner class CoreSlotWidget(slotIndex: Int, x: Int, y: Int) : SlotWidget(this@StaffAssemblyBlockEntity, slotIndex, x, y) {
        override fun createSlot(inventory: Container, index: Int): Slot {
            return object : Slot(inventory, index, 0, 0) {
                override fun mayPlace(stack: ItemStack): Boolean = !this@CoreSlotWidget.locked()

                override fun mayPickup(player: Player): Boolean = !this@CoreSlotWidget.locked()

                override fun getMaxStackSize(): Int = 1
            }
        }

        private fun locked(): Boolean = this@StaffAssemblyBlockEntity.coreLocked

        override fun canPutStack(stack: ItemStack): Boolean = super.canPutStack(stack) && !locked()

        override fun canTakeStack(player: Player): Boolean = super.canTakeStack(player) && !locked()
    }

    private inner class PartSlotWidget(slotIndex: Int, x: Int, y: Int) : SlotWidget(this@StaffAssemblyBlockEntity, slotIndex, x, y) {
        override fun createSlot(inventory: Container, index: Int): Slot {
            return object : Slot(inventory, index, 0, 0) {
                override fun getMaxStackSize(): Int = 1
            }
        }
    }

    private inner class StaffAssemblySyncWidget(
        private val staffPreview: StaffItemDisplayWidget?,
        private val qualityLabel: LabelWidget?,
        private val qualityPercent: TextTextureWidget?,
        private val gridSizeValue: TextTextureWidget?,
        private val ambitValue: TextTextureWidget?,
        private val reserveValue: TextTextureWidget?,
        private val coreDesc: LabelWidget?,
        private val setCraftEnabled: (Boolean) -> Unit,
        private val setChangeModelEnabled: (Boolean) -> Unit
    ) : Widget(0, 0, 0, 0) {

        override fun initWidget() {
            super.initWidget()
            if (isRemote()) {
                refresh()
            }
        }

        override fun updateScreen() {
            super.updateScreen()
            refresh()
        }

        private fun refresh() {
            syncPendingModelFromStaff()

            val merged = mergedResultForUi()
            qualityLabel?.setText(literalText(merged.qualityRating().label().string))
            qualityPercent?.setText(Component.literal(literalText(percent(merged.quality()))))
            gridSizeValue?.setText(Component.literal(literalText(gridSizeValueText())))
            ambitValue?.setText(Component.literal(literalText(ambitValueText())))
            reserveValue?.setText(Component.literal(literalText(reserveValueText())))
            reserveValue?.setHoverTooltips(reserveTooltip())

            coreDesc?.setText(literalText(coreDescriptionSingleLine()))
            coreDesc?.setColor(if (merged.core().isEmpty) COLOR_BAD else TEXT_DIM_COLOR)

            setCraftEnabled(canCraftNow())
            setChangeModelEnabled(staffItem().item is ConfigurableStaffItem)
        }

        private fun literalText(text: String): String = if (text.indexOf('%') < 0) text else text.replace("%", "%%")
    }
}
