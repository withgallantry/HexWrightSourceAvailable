package com.bluup.hexwright.server.block

import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.xplat.IXplatAbstractions
import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.server.menu.UiTemplates
import com.bluup.hexwright.client.block.CrucibleFlameVisualClient
import com.bluup.hexwright.common.aspects.AspectMappings
import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory
import com.bluup.hexwright.server.crucible.EssencePouchData
import com.bluup.hexwright.server.item.EndlessPouchItem
import com.bluup.hexwright.server.journal.InvestigationProgress
import com.bluup.hexwright.server.network.EssenceNetwork
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder
import com.lowdragmc.lowdraglib.gui.modular.ModularUI
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture
import com.lowdragmc.lowdraglib.gui.texture.ShaderTexture
import com.lowdragmc.lowdraglib.gui.texture.UIResourceTexture
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup
import com.lowdragmc.lowdraglib.gui.widget.DraggableWidgetGroup
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget
import com.lowdragmc.lowdraglib.gui.widget.Widget
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.lang.reflect.Method
import java.util.Locale
import java.util.function.DoubleSupplier
import java.util.function.Supplier
import kotlin.math.roundToInt

class CrucibleBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.CRUCIBLE_BLOCK_ENTITY, pos, state), WorldlyContainer, IUIHolder.BlockEntityUI {

    companion object {
        const val POUCH_SLOT = 0
        const val ITEM_SLOT = 1
        const val FUEL_SLOT = 2
        const val CONTAINER_SIZE = 3

        private const val FALLBACK_UI_WIDTH = 176
        private const val FALLBACK_UI_HEIGHT = 166

        private const val FUEL_ITEMS_DUST = 1
        private const val FUEL_ITEMS_SHARD = 2
        private const val FUEL_ITEMS_CRYSTAL = 5
        private const val FUEL_ITEMS_CHARGED = 8

        private const val ESSENCE_LINE_HEIGHT = 10

        private const val ESSENCE_PANEL_MARGIN = 2

        private var SLOT_WIDGET_UPDATE_SLOT: Method? = null

        private val POUCH_SLOT_GUIDE_TEXTURE: IGuiTexture = ResourceTexture("ldlib:textures/menu/secondary_item_input.png")
        private val POUCH_SLOT_FILLED_TEXTURE: IGuiTexture = ResourceTexture("ldlib:textures/menu/secondary_input.png")
    }

    private val items: NonNullList<ItemStack> = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY)

    private val pouchSlotView = SingleItemSlotView(this)

    private fun slotContainer(slot: Int): Container = if (slot == POUCH_SLOT) pouchSlotView else this

    private var progress = 0

    private var burningItem: Item = Items.AIR

    private var fuelTicksRemaining = 0

    private var fuelTicksTotal = 0

    private var lastOpener: java.util.UUID? = null

    override fun getContainerSize(): Int = CONTAINER_SIZE

    override fun isEmpty(): Boolean = items.all { it.isEmpty }

    override fun getItem(slot: Int): ItemStack = items[slot]

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        val result = ContainerHelper.removeItem(items, slot, amount)
        if (!result.isEmpty) setChanged()
        return result
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack = ContainerHelper.takeItem(items, slot)

    override fun setItem(slot: Int, stack: ItemStack) {
        items[slot] = stack
        val limit = if (slot == POUCH_SLOT) 1 else maxStackSize
        if (stack.count > limit) {
            stack.count = limit
        }
        setChanged()
    }

    override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)

    override fun clearContent() {
        for (i in items.indices) {
            items[i] = ItemStack.EMPTY
        }
    }

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = when (slot) {
        POUCH_SLOT -> stack.item is EndlessPouchItem || EssenceNetwork.isEssenceSource(stack)
        ITEM_SLOT -> !EssenceNetwork.isEssenceSource(stack)
        FUEL_SLOT -> isAcceptedCrucibleFuel(stack)
        else -> false
    }

    private val processSlots = intArrayOf(ITEM_SLOT, FUEL_SLOT)
    private val slotsForUp = intArrayOf(ITEM_SLOT)
    private val slotsForSides = intArrayOf(FUEL_SLOT)

    override fun getSlotsForFace(side: Direction): IntArray = when (side) {
        Direction.UP -> slotsForUp
        Direction.DOWN -> processSlots
        else -> slotsForSides
    }

    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, side: Direction?): Boolean =
        slot in processSlots && canPlaceItem(slot, stack)

    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, side: Direction): Boolean =
        side == Direction.DOWN && slot == ITEM_SLOT && !isBurnableInput(stack)

    override fun load(tag: CompoundTag) {
        super.load(tag)
        ContainerHelper.loadAllItems(tag, items)
        progress = tag.getInt("Progress")
        fuelTicksRemaining = tag.getInt("FuelTicksRemaining")
        fuelTicksTotal = tag.getInt("FuelTicksTotal")
        burningItem = if (tag.contains("BurningItem")) {
            BuiltInRegistries.ITEM.get(ResourceLocation(tag.getString("BurningItem")))
        } else {
            Items.AIR
        }
        lastOpener = if (tag.hasUUID("LastOpener")) tag.getUUID("LastOpener") else null
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        ContainerHelper.saveAllItems(tag, items)
        tag.putInt("Progress", progress)
        tag.putInt("FuelTicksRemaining", fuelTicksRemaining)
        tag.putInt("FuelTicksTotal", fuelTicksTotal)
        if (burningItem != Items.AIR) {
            tag.putString("BurningItem", BuiltInRegistries.ITEM.getKey(burningItem).toString())
        }
        lastOpener?.let { tag.putUUID("LastOpener", it) }
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun setChanged() {
        super.setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    private fun pouchStack(): ItemStack = EssenceNetwork.resolve(items[POUCH_SLOT], level, worldPosition)

    private fun uiPouchStack(): ItemStack {
        val raw = items[POUCH_SLOT]
        val resolved = EssenceNetwork.resolve(raw, level, worldPosition)
        if (resolved.item is EndlessPouchItem) {
            return resolved
        }
        if (raw.item is EndlessPouchItem) {
            return raw
        }
        return ItemStack.EMPTY
    }

    private fun processingStack(): ItemStack = items[ITEM_SLOT]

    private fun processingProfile() = AspectMappings.profileFor(processingStack().item).orElse(null)

    private fun currentBurnTicks(): Int = processingProfile()?.burnTicks() ?: 0

    private fun canProcessNow(): Boolean {
        val pouch = pouchStack().item
        if (pouch !is EndlessPouchItem) return false
        return isBurnableInput(processingStack())
    }

    private fun isBurnableInput(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val profile = AspectMappings.profileFor(stack.item).orElse(null) ?: return false
        return profile.data().categories().isNotEmpty() && profile.essenceYield() > 0
    }

    private fun isAcceptedCrucibleFuel(stack: ItemStack): Boolean = fuelItemsPerUnit(stack) > 0

    private fun fuelItemsPerUnit(stack: ItemStack): Int {
        if (stack.isEmpty) return 0

        return when (stack.item) {
            HexItems.AMETHYST_DUST -> FUEL_ITEMS_DUST
            Items.AMETHYST_SHARD -> FUEL_ITEMS_SHARD
            Items.AMETHYST_CLUSTER -> FUEL_ITEMS_CRYSTAL
            HexItems.CHARGED_AMETHYST -> FUEL_ITEMS_CHARGED
            else -> mediaFuelItemsPerUnit(stack)
        }
    }

    private fun mediaFuelItemsPerUnit(stack: ItemStack): Int {
        val holder = IXplatAbstractions.INSTANCE.findMediaHolder(stack) ?: return 0
        if (!holder.canProvide() || !holder.canConstructBattery()) return 0
        val perItem = holder.media / stack.count.coerceAtLeast(1)
        if (perItem <= 0) return 0
        return (perItem / MediaConstants.DUST_UNIT).toInt().coerceAtLeast(1)
    }

    private fun fuelTicksFor(stack: ItemStack, processTicks: Int): Int {
        val items = fuelItemsPerUnit(stack)
        if (items <= 0) return 0
        return (items * processTicks).coerceAtLeast(1)
    }

    private fun consumeFuelUnit(): Boolean {
        val fuelStack = items[FUEL_SLOT]
        if (fuelStack.isEmpty) return false

        val burnTicks = fuelTicksFor(fuelStack, currentBurnTicks().coerceAtLeast(1))
        if (burnTicks <= 0) return false

        fuelStack.shrink(1)
        if (fuelStack.isEmpty) {
            items[FUEL_SLOT] = ItemStack.EMPTY
        }

        fuelTicksRemaining = burnTicks
        fuelTicksTotal = burnTicks
        return true
    }

    fun noteOpenedBy(player: ServerPlayer) {
        if (lastOpener != player.uuid) {
            lastOpener = player.uuid
            setChanged()
        }
    }

    private fun recordJournalEssence(aspect: IngredientCategory, amount: Double) {
        val owner = lastOpener ?: return
        val server = (level as? ServerLevel)?.server ?: return
        InvestigationProgress.recordCrucibleEssence(server, owner, aspect, amount)
    }

    fun serverTick() {
        val level = this.level ?: return
        var syncNeeded = false

        val canProcess = canProcessNow()

        if (fuelTicksRemaining > 0) {
            fuelTicksRemaining--
            if (fuelTicksRemaining == 0) {
                if (canProcess && consumeFuelUnit()) {
                    syncNeeded = true
                } else {
                    fuelTicksTotal = 0
                    syncNeeded = true
                }
            }
        } else if (canProcess && consumeFuelUnit()) {
            syncNeeded = true
        }

        if (!canProcess) {
            if (progress != 0 || burningItem != Items.AIR) {
                progress = 0
                burningItem = Items.AIR
                syncNeeded = true
            }
        } else {
            val stack = processingStack()
            val profile = processingProfile()

            if (profile == null) {
                if (progress != 0 || burningItem != Items.AIR) {
                    progress = 0
                    burningItem = Items.AIR
                    syncNeeded = true
                }
            } else {
                if (burningItem != stack.item) {
                    burningItem = stack.item
                    progress = 0
                    syncNeeded = true
                }

                if (fuelTicksRemaining > 0) {
                    progress++
                    if (progress >= profile.burnTicks()) {
                        val pouch = pouchStack()
                        for (aspect in profile.data().categories()) {
                            EssencePouchData.add(pouch, aspect, profile.essenceYield())
                            recordJournalEssence(aspect, profile.essenceYield())
                        }
                        EssenceNetwork.pulseFlow(level, worldPosition, items[POUCH_SLOT], true)

                        stack.shrink(1)
                        if (stack.isEmpty) {
                            items[ITEM_SLOT] = ItemStack.EMPTY
                        }

                        progress = 0
                        burningItem = Items.AIR
                        syncNeeded = true
                    }
                }
            }
        }

        val wasActive = blockState.getValue(HexwrightBlockStates.ACTIVE)
        val shouldBeActive = fuelTicksRemaining > 0
        if (wasActive != shouldBeActive) {
            level.setBlock(worldPosition, blockState.setValue(HexwrightBlockStates.ACTIVE, shouldBeActive), 3)
            syncNeeded = true
        }

        if (syncNeeded) {
            setChanged()
        }
    }

    fun clientTick() {
        if (fuelTicksRemaining > 0) {
            fuelTicksRemaining--
            if (fuelTicksRemaining == 0) {
                if (canProcessNow()) {
                    val predicted = fuelTicksFor(items[FUEL_SLOT], currentBurnTicks().coerceAtLeast(1))
                    if (predicted > 0) {
                        fuelTicksRemaining = predicted
                        fuelTicksTotal = predicted
                    } else {
                        fuelTicksTotal = 0
                    }
                } else {
                    fuelTicksTotal = 0
                }
            }
        } else if (canProcessNow()) {
            val predicted = fuelTicksFor(items[FUEL_SLOT], currentBurnTicks().coerceAtLeast(1))
            if (predicted > 0) {
                fuelTicksRemaining = predicted
                fuelTicksTotal = predicted
            }
        }

        if (blockState.getValue(HexwrightBlockStates.ACTIVE) && canProcessNow()) {
            val total = currentBurnTicks()
            if (total > 0 && progress < total) {
                progress++
            }
        }

        level?.let { CrucibleFlameVisualClient.setActive(it, worldPosition, blockState.getValue(HexwrightBlockStates.ACTIVE)) }
    }

    override fun setRemoved() {
        val lvl = level
        if (lvl != null && lvl.isClientSide) {
            CrucibleFlameVisualClient.setActive(lvl, worldPosition, false)
        }
        super.setRemoved()
    }

    override fun createUI(entityPlayer: Player): ModularUI {
        val template = UiTemplates.load("crucible_v3")
        if (template == null) {
            Hexwright.LOGGER.error("Failed to load crucible_v3 UI; falling back to minimal crucible UI")
            return createFallbackUI(entityPlayer)
        }

        val root = template.get() ?: return createFallbackUI(entityPlayer)
        bindCrucibleWidgets(root, entityPlayer)
        return ModularUI(root, this, entityPlayer)
    }

    private fun bindCrucibleWidgets(root: WidgetGroup, player: Player) {
        bindMachineSlot(root, "item_to_process", ITEM_SLOT)
        bindMachineSlot(root, "fuel", FUEL_SLOT)
        val pouchSlot = bindMachineSlot(root, "pouch", POUCH_SLOT)
        bindPlayerInventorySlots(root, player)

        val itemProgress = root.getFirstWidgetById("^item_progress$") as? ProgressWidget
        if (itemProgress != null) {
            itemProgress.progressSupplier = DoubleSupplier {
                val total = currentBurnTicks()
                if (total <= 0 || !canProcessNow()) {
                    0.0
                } else {
                    (progress.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
                }
            }
        }

        val noPouchWidget = root.getFirstWidgetById("^no_pouch$")
        bindNoPouchPlacard(noPouchWidget)
        val fireWidget = root.getFirstWidgetById("^fire$")

        val essenceRoot = root.getFirstWidgetById("^essence_contaioner$|^essence_container$")
        val essenceGroup =
            (essenceRoot as? WidgetGroup)
                ?: findFirstWidgetGroupWithToken(root, "essence")
        val essencePanel = essenceGroup?.parent

        val syncWidget = UiSyncWidget(
            pouchSlot,
            noPouchWidget,
            fireWidget,
            essenceGroup,
            essencePanel,
            essenceGroup?.sizeHeight ?: 0,
            essencePanel?.sizeHeight ?: 0,
            root.sizeHeight
        )
        root.addWidget(syncWidget)

        if (level?.isClientSide == true) {
            syncWidget.applyBoundState()
        }
    }

    private fun bindNoPouchPlacard(widget: Widget?) {
        val placard = widget as? TextTextureWidget ?: return
        val authored = placard.lastComponent ?: Component.translatable("gui.hexwright.crucible.no_pouch")
        placard.setText(Supplier {
            if (EssenceNetwork.isKeyOutOfRange(items[POUCH_SLOT], level, worldPosition)) {
                Component.translatable("gui.hexwright.crucible.key_out_of_range", EssenceNetwork.keyRange())
            } else {
                authored
            }
        })
    }

    private fun bindMachineSlot(root: WidgetGroup, id: String, slot: Int): SlotWidget? {
        val widget = root.getFirstWidgetById("^$id$")
        if (widget !is SlotWidget) {
            Hexwright.LOGGER.warn("crucible_v3 is missing slot widget '{}'", id)
            return null
        }

        bindFilteredContainerSlot(widget, slot)
        widget.setCanPutItems(true)
        widget.setCanTakeItems(true)
        widget.setLocationInfo(false, false)
        return widget
    }

    private fun bindFilteredContainerSlot(widget: SlotWidget, slotIndex: Int) {
        val filtered = object : Slot(slotContainer(slotIndex), slotIndex, 0, 0) {
            override fun mayPlace(stack: ItemStack): Boolean = canPlaceItem(slotIndex, stack)
        }

        try {
            if (SLOT_WIDGET_UPDATE_SLOT == null) {
                SLOT_WIDGET_UPDATE_SLOT = SlotWidget::class.java.getDeclaredMethod("updateSlot", Slot::class.java).apply {
                    isAccessible = true
                }
            }
            SLOT_WIDGET_UPDATE_SLOT?.invoke(widget, filtered)
        } catch (t: Throwable) {
            Hexwright.LOGGER.error("Failed to bind filtered crucible slot widget; falling back to default slot binding", t)
            widget.setContainerSlot(slotContainer(slotIndex), slotIndex)
        }
    }

    private fun bindPlayerInventorySlots(root: WidgetGroup, player: Player) {
        for (index in 0 until 36) {
            val widget = root.getFirstWidgetById("^player_inv_$index$")
            if (widget is SlotWidget) {
                widget.setContainerSlot(player.inventory, index)
                widget.setCanPutItems(true)
                widget.setCanTakeItems(true)
                widget.setLocationInfo(true, index < 9)
            }
        }
    }

    private fun findFirstWidgetGroupWithToken(widget: Widget, token: String): WidgetGroup? {
        val normalized = token.lowercase(Locale.ROOT)
        val id = widget.id
        if (widget is WidgetGroup && id != null && id.lowercase(Locale.ROOT).contains(normalized)) {
            return widget
        }
        if (widget is WidgetGroup) {
            for (child in widget.widgets) {
                val found = findFirstWidgetGroupWithToken(child, token)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    private fun createFallbackUI(player: Player): ModularUI {
        val ui = ModularUI(FALLBACK_UI_WIDTH, FALLBACK_UI_HEIGHT, this, player)

        ui.widget(FilteredSlotWidget(ITEM_SLOT, 56, 17))
        ui.widget(FilteredSlotWidget(FUEL_SLOT, 56, 53))
        ui.widget(FilteredSlotWidget(POUCH_SLOT, 26, 35))

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val slotIndex = col + row * 9 + 9
                ui.widget(SlotWidget(player.inventory, slotIndex, 8 + col * 18, 84 + row * 18))
            }
        }
        for (col in 0 until 9) {
            ui.widget(SlotWidget(player.inventory, col, 8 + col * 18, 142))
        }

        return ui
    }

    private inner class FilteredSlotWidget(slotIndex: Int, x: Int, y: Int) :
        SlotWidget(this@CrucibleBlockEntity.slotContainer(slotIndex), slotIndex, x, y) {
        override fun createSlot(inventory: Container, index: Int): Slot {
            return object : Slot(inventory, index, 0, 0) {
                override fun mayPlace(stack: ItemStack): Boolean = canPlaceItem(index, stack)
            }
        }
    }

    private inner class UiSyncWidget(
        private val pouchSlot: SlotWidget?,
        private val noPouchWidget: Widget?,
        private val fireWidget: Widget?,
        private val essenceGroup: WidgetGroup?,
        private val essencePanel: WidgetGroup?,
        private val essenceContainerBaseHeight: Int,
        private val essencePanelBaseHeight: Int,
        private val rootBaseHeight: Int
    ) : Widget(0, 0, 0, 0) {

        private var lastEssenceFingerprint = ""
        private var lastEssenceValues: Map<IngredientCategory, Double> = emptyMap()
        private var hasEssenceBaseline = false
        private var pouchGuideShown = true

        override fun updateScreen() {
            super.updateScreen()
            applyBoundState()
            refreshEssenceList()
        }

        fun applyBoundState() {
            val hasPouch = uiPouchStack().item is EndlessPouchItem
            noPouchWidget?.setVisible(!hasPouch)
            updatePouchGuide()
            updateFuelVisual()
        }

        private fun updatePouchGuide() {
            val slot = pouchSlot ?: return
            val host = slot.parent ?: return
            val empty = slot.item.isEmpty
            if (empty != pouchGuideShown) {
                pouchGuideShown = empty
                host.setBackground(if (pouchGuideShown) POUCH_SLOT_GUIDE_TEXTURE else POUCH_SLOT_FILLED_TEXTURE)
            }
        }

        private fun updateFuelVisual() {
            val ratio = if (fuelTicksTotal <= 0) 0.0 else (fuelTicksRemaining.toDouble() / fuelTicksTotal.toDouble()).coerceIn(0.0, 1.0)
            fireWidget?.let { applyFireVisual(it, ratio) }
            fireWidget?.setVisible(ratio > 0.0)
        }

        private fun unwrapTexture(texture: IGuiTexture?): IGuiTexture? {
            var current = texture
            while (current is UIResourceTexture) {
                current = current.texture
            }
            return current
        }

        private fun applyFireVisual(widget: Widget, ratio: Double) {
            if (widget is ProgressWidget) {
                widget.progressSupplier = DoubleSupplier { ratio }
            }

            if (widget is ImageWidget) {
                val image = unwrapTexture(widget.image)
                if (image is ProgressTexture) {
                    image.setProgress(ratio)
                } else if (image is ShaderTexture) {
                    image.setUniformCache { cache ->
                        cache.glUniform1F("fuelLevel", ratio.toFloat())
                    }
                    val alpha = (ratio * 255.0).roundToInt().coerceIn(0, 255)
                    image.setColor((alpha shl 24) or 0x00FFFFFF)
                }
            }

            if (widget is WidgetGroup) {
                for (child in widget.widgets) {
                    applyFireVisual(child, ratio)
                }
            }
        }

        private fun refreshEssenceList() {
            val group = essenceGroup ?: return
            val pouch = uiPouchStack()
            val hasPouch = pouch.item is EndlessPouchItem
            val values = if (hasPouch) EssencePouchData.getAll(pouch) else emptyMap()
            val fingerprint = buildString {
                append(hasPouch)
                for ((aspect, amount) in values.toSortedMap(compareBy { it.name })) {
                    append('|').append(aspect.name).append(':').append(amount)
                }
            }

            if (fingerprint == lastEssenceFingerprint) {
                return
            }

            val gains: Map<IngredientCategory, Double> = if (hasEssenceBaseline) {
                values.mapValues { (aspect, amount) -> amount - (lastEssenceValues[aspect] ?: 0.0) }
                    .filterValues { it > 1.0E-6 }
            } else {
                emptyMap()
            }

            lastEssenceFingerprint = fingerprint
            lastEssenceValues = values
            hasEssenceBaseline = hasPouch

            group.clearAllWidgets()

            if (hasPouch && values.isEmpty()) {
                group.addWidget(LabelWidget(6, 2, Component.translatable("gui.hexwright.crucible.pouch_empty")))
                resizeEssencePanel(group, 0)
                recomputeScrollable(group)
                return
            }

            var y = 2
            val sorted = values.entries.sortedByDescending { it.value }
            for ((aspect, amount) in sorted) {
                val icon = ResourceLocation(
                    Hexwright.MOD_ID,
                    "textures/gui/essence/${aspect.name.lowercase(Locale.ROOT)}.png"
                )
                group.addWidget(ImageWidget(2, y, 8, 8, ResourceTexture(icon)))

                val line = EndlessPouchItem.aspectLine(aspect, amount)
                val gained = gains[aspect]
                if (gained != null) {
                    line.append(
                        Component.literal(" ").append(
                            Component.translatable(
                                "label.hexwright.essence.gain",
                                EndlessPouchItem.formatAmount(gained)
                            )
                        ).withStyle(ChatFormatting.GREEN)
                    )
                }
                group.addWidget(LabelWidget(12, y, line))
                y += 10
            }

            resizeEssencePanel(group, sorted.size)
            recomputeScrollable(group)
        }

        private fun resizeEssencePanel(group: WidgetGroup, entryCount: Int) {
            val requiredContentHeight = ESSENCE_PANEL_MARGIN + entryCount * ESSENCE_LINE_HEIGHT + ESSENCE_PANEL_MARGIN
            val containerHeight = maxOf(essenceContainerBaseHeight, requiredContentHeight)
            group.setSize(group.sizeWidth, containerHeight)

            val panel = essencePanel ?: return
            val panelHeight = maxOf(essencePanelBaseHeight, containerHeight + ESSENCE_PANEL_MARGIN * 2)
            panel.setSize(panel.sizeWidth, panelHeight)

            val modularUI = group.gui ?: return
            val rootHeight = maxOf(rootBaseHeight, panelHeight)
            modularUI.setSize(modularUI.width, rootHeight)
        }

        private fun recomputeScrollable(group: WidgetGroup) {
            when (group) {
                is DraggableScrollableWidgetGroup -> group.computeMax()
                is DraggableWidgetGroup -> Unit
            }
        }
    }
}
