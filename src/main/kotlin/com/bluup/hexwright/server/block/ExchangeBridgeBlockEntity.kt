package com.bluup.hexwright.server.block

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.server.menu.MenuWidgets
import com.bluup.hexwright.server.menu.UiTemplates
import com.bluup.hexwright.server.harmonic.ExchangeBridgeInfoWidget
import com.bluup.hexwright.server.harmonic.HarmonicBridgeState
import com.bluup.hexwright.server.harmonic.HarmonicChannelTarget
import com.bluup.hexwright.server.harmonic.HarmonicExchangeState
import com.bluup.hexwright.server.harmonic.HarmonicNetwork
import com.bluup.hexwright.server.harmonic.TransmissionWindow
import com.bluup.hexwright.server.network.ResonanceNames
import com.bluup.hexwright.server.network.ResonantKeyItem
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder
import com.lowdragmc.lowdraglib.gui.modular.ModularUI
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget
import com.lowdragmc.lowdraglib.gui.widget.Widget
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.Containers
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.util.function.Supplier

class ExchangeBridgeBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.EXCHANGE_BRIDGE_BLOCK_ENTITY, pos, state),
    Container,
    IUIHolder.BlockEntityUI,
    HarmonicChannelTarget {

    companion object {
        const val KEY_SLOT_1 = 0
        const val KEY_SLOT_2 = 1
        const val CONTAINER_SIZE = 2

        const val NO_HARMONIC = -1

        const val IMAGE_WIDTH = 198
        const val IMAGE_HEIGHT = 251

        private val SLOT_IDS = listOf("key_slot_1", "key_slot_2")

        private val NETWORK_LABEL_IDS = listOf("network_1", "network_2")

        private val INFO_GROUP_IDS = listOf("channels_info", "channel_info")


        private const val PANEL_COLOR = 0xFF1B1F26.toInt()
        private const val PANEL_BORDER_COLOR = 0xFF45505C.toInt()
        private const val TRANSPARENT_COLOR = 0x00000000

        private val PANEL_BACKGROUND =
            GuiTextureGroup(ColorRectTexture(PANEL_COLOR), ColorRectTexture(PANEL_BORDER_COLOR))
    }

    private val items: NonNullList<ItemStack> = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY)

    private var harmonic: Int = NO_HARMONIC

    private val crossings = TransmissionWindow()


    fun networkKey(slot: Int): String? {
        val key = items.getOrNull(slot) ?: return null
        if (key.item !is ResonantKeyItem) return null
        return ResonantKeyItem.networkKey(key)
    }

    fun keyCount(): Int = items.count { it.item is ResonantKeyItem }

    fun dockKey(key: ItemStack): Boolean {
        val slot = items.indexOfFirst { it.isEmpty }
        if (slot < 0) return false
        items[slot] = key
        setChanged()
        return true
    }

    override fun networkKeys(): List<String> = listOfNotNull(networkKey(KEY_SLOT_1), networkKey(KEY_SLOT_2))

    override fun harmonic(): Int = harmonic

    override fun tune(harmonic: Int) {
        this.harmonic = harmonic
        setChanged()
    }

    override fun untune() {
        harmonic = NO_HARMONIC
        setChanged()
    }

    override fun tuningRefusal(): String? {
        val first = networkKey(KEY_SLOT_1)
        val second = networkKey(KEY_SLOT_2)
        return when {
            keyCount() < 2 -> "hexwright_bridge_unkeyed"
            first == null || second == null -> "hexwright_bridge_unattuned"
            first == second -> "hexwright_bridge_same_network"
            else -> null
        }
    }


    fun carries(networkA: String, networkB: String, harmonic: Int): Boolean {
        if (this.harmonic != harmonic) return false
        val first = networkKey(KEY_SLOT_1) ?: return false
        val second = networkKey(KEY_SLOT_2) ?: return false
        return (first == networkA && second == networkB) || (first == networkB && second == networkA)
    }

    fun recordCrossing() {
        val level = this.level as? ServerLevel ?: return
        crossings.record(level.gameTime)
        syncActiveState()
    }

    fun onNetworkActivity() {
        syncActiveState()
    }

    private fun syncRegistration(updateActiveState: Boolean) {
        val level = this.level as? ServerLevel ?: return
        val state = HarmonicBridgeState.get(level.server)
        val first = networkKey(KEY_SLOT_1)
        val second = networkKey(KEY_SLOT_2)
        if (first == null || second == null || first == second
            || !HarmonicExchangeState.isValidHarmonic(harmonic)
        ) {
            state.unregisterAt(level.dimension(), worldPosition)
        } else {
            state.register(level.dimension(), worldPosition, first, second, harmonic)
        }
        if (updateActiveState) {
            syncActiveState()
        }
    }

    private fun isActive(): Boolean {
        val level = this.level as? ServerLevel ?: return false
        if (tuningRefusal() != null || harmonic == NO_HARMONIC) return false
        val server = level.server
        return networkKeys().all { HarmonicNetwork.isActive(server, it) }
    }

    private fun syncActiveState() {
        val level = this.level as? ServerLevel ?: return
        val shouldBeActive = isActive()
        val state = blockState
        if (state.hasProperty(HexwrightBlockStates.ACTIVE)
            && state.getValue(HexwrightBlockStates.ACTIVE) != shouldBeActive
        ) {
            level.setBlock(worldPosition, state.setValue(HexwrightBlockStates.ACTIVE, shouldBeActive), 3)
        }
    }

    override fun setLevel(level: Level) {
        super.setLevel(level)
        syncRegistration(updateActiveState = false)
    }

    fun onRemovedFromWorld() {
        val level = this.level as? ServerLevel ?: return
        HarmonicBridgeState.get(level.server).unregisterAt(level.dimension(), worldPosition)
    }

    override fun getContainerSize(): Int = CONTAINER_SIZE
    override fun isEmpty(): Boolean = items.all { it.isEmpty }
    override fun getItem(slot: Int): ItemStack = items[slot]

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        val result = ContainerHelper.removeItem(items, slot, amount)
        if (!result.isEmpty) setChanged()
        return result
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack {
        val result = ContainerHelper.takeItem(items, slot)
        if (!result.isEmpty) setChanged()
        return result
    }

    override fun setItem(slot: Int, stack: ItemStack) {
        items[slot] = if (stack.isEmpty) ItemStack.EMPTY else stack.copyWithCount(1)
        setChanged()
    }

    override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean =
        (slot == KEY_SLOT_1 || slot == KEY_SLOT_2) && stack.item is ResonantKeyItem

    override fun getMaxStackSize(): Int = 1

    override fun clearContent() {
        for (i in items.indices) items[i] = ItemStack.EMPTY
    }

    fun dropAllOnBreak() {
        val level = this.level ?: return
        Containers.dropContents(level, worldPosition, this)
        clearContent()
    }


    override fun load(tag: CompoundTag) {
        super.load(tag)
        ContainerHelper.loadAllItems(tag, items)
        harmonic = if (tag.contains("Harmonic")) tag.getInt("Harmonic") else NO_HARMONIC
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        ContainerHelper.saveAllItems(tag, items)
        if (harmonic != NO_HARMONIC) tag.putInt("Harmonic", harmonic)
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()
    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun setChanged() {
        super.setChanged()
        syncRegistration(updateActiveState = true)
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    private fun snapshot(): ExchangeBridgeInfoWidget.Snapshot {
        val level = this.level as? ServerLevel ?: return ExchangeBridgeInfoWidget.Snapshot.empty()
        val server = level.server
        val first = networkKey(KEY_SLOT_1)
        val second = networkKey(KEY_SLOT_2)
        return ExchangeBridgeInfoWidget.Snapshot(
            keyCount(),
            first != null && second != null,
            first != null && second != null && first != second,
            harmonic != NO_HARMONIC,
            first != null && HarmonicNetwork.isActive(server, first),
            second != null && HarmonicNetwork.isActive(server, second),
            harmonic.coerceAtLeast(0),
            crossings.lastMinute(level.gameTime)
        )
    }

    private fun networkLabel(slot: Int): Component {
        val level = this.level as? ServerLevel
            ?: return Component.translatable("gui.hexwright.exchange_bridge.network.unkeyed")
        val key = networkKey(slot)
            ?: return Component.translatable("gui.hexwright.exchange_bridge.network.unkeyed")
        val name = ResonanceNames.nameOf(level.server, key)
        return if (HarmonicNetwork.isActive(level.server, key)) {
            Component.literal(name)
        } else {
            Component.literal(name).withStyle(ChatFormatting.RED)
        }
    }

    override fun createUI(entityPlayer: Player): ModularUI {
        val root = UiTemplates.load("exchange_bridge")?.get()
        if (root == null) {
            Hexwright.LOGGER.error("Failed to load exchange_bridge.ui; falling back to a minimal Exchange Bridge UI")
            return createFallbackUI(entityPlayer)
        }
        bindWidgets(root)
        return ModularUI(root, this, entityPlayer)
    }

    private fun bindWidgets(root: WidgetGroup) {
        val widgetsById = MenuWidgets.indexById(root)

        SLOT_IDS.forEachIndexed { slot, id -> bindKeySlot(widgetsById, id, slot) }
        NETWORK_LABEL_IDS.forEachIndexed { slot, id -> bindNetworkLabel(widgetsById, id, slot) }

        val group = INFO_GROUP_IDS
            .firstNotNullOfOrNull { MenuWidgets.firstById(widgetsById, it) as? WidgetGroup }
        if (group == null) {
            Hexwright.LOGGER.warn("exchange_bridge.ui is missing the info group (tried {})", INFO_GROUP_IDS)
            return
        }
        group.addWidget(
            ExchangeBridgeInfoWidget(0, 0, group.size.width, group.size.height) { snapshot() }
        )
    }

    private fun bindKeySlot(widgetsById: Map<String, List<Widget>>, id: String, slot: Int) {
        val template = MenuWidgets.firstById(widgetsById, id)
        if (template !is SlotWidget) {
            Hexwright.LOGGER.warn("exchange_bridge.ui is missing the '{}' slot widget", id)
            return
        }
        MenuWidgets.bindKeySlot(template, this, slot,
            listOf(Component.translatable("gui.hexwright.exchange_bridge.key.tooltip", slot + 1)))
    }

    private fun bindNetworkLabel(widgetsById: Map<String, List<Widget>>, id: String, slot: Int) {
        val label = MenuWidgets.firstById(widgetsById, id) as? TextTextureWidget
        if (label == null) {
            Hexwright.LOGGER.warn("exchange_bridge.ui is missing the '{}' label widget", id)
            return
        }
        val authoredColor = label.textTexture.color
        label.setText(Supplier { networkLabel(slot) })
        label.textTexture.setSupplier {
            val component = label.lastComponent
            label.textTexture.color =
                component?.style?.color?.let { 0xFF000000.toInt() or it.value } ?: authoredColor
            component?.string ?: ""
        }
    }

    private fun createFallbackUI(entityPlayer: Player): ModularUI {
        val ui = ModularUI(IMAGE_WIDTH, IMAGE_HEIGHT, this, entityPlayer)
        ui.background(PANEL_BACKGROUND)
        ui.widget(LabelWidget(8, 7, Component.translatable("block.hexwright.exchange_bridge")))
        for (slot in 0 until CONTAINER_SIZE) {
            ui.widget(
                MenuWidgets.singleItemSlot(this, slot, 8 + slot * 22, 26)
                    .setBackground(ColorRectTexture(TRANSPARENT_COLOR))
                    .setHoverTooltips(
                        Component.translatable("gui.hexwright.exchange_bridge.key.tooltip", slot + 1)
                    )
            )
        }
        ui.widget(ExchangeBridgeInfoWidget(11, 55, 176, 46) { snapshot() })
        return ui
    }
}
