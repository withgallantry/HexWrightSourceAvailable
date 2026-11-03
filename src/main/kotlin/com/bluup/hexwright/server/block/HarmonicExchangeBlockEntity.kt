package com.bluup.hexwright.server.block

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.server.menu.MenuWidgets
import com.bluup.hexwright.server.menu.UiTemplates
import com.bluup.hexwright.server.harmonic.HarmonicEmitterRegistry
import com.bluup.hexwright.server.harmonic.HarmonicBridgeState
import com.bluup.hexwright.server.harmonic.HarmonicExchangeState
import com.bluup.hexwright.server.harmonic.HarmonicInfoWidget
import com.bluup.hexwright.server.harmonic.HarmonicNetwork
import com.bluup.hexwright.server.harmonic.HarmonicSubscriptions
import com.bluup.hexwright.server.network.EssenceNetwork
import com.bluup.hexwright.server.network.ResonantKeyItem
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder
import com.lowdragmc.lowdraglib.gui.modular.ModularUI
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget
import com.lowdragmc.lowdraglib.gui.widget.Widget
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup
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

class HarmonicExchangeBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.HARMONIC_EXCHANGE_BLOCK_ENTITY, pos, state), Container, IUIHolder.BlockEntityUI {

    companion object {
        const val KEY_SLOT = 0
        const val CONTAINER_SIZE = 1

        const val IMAGE_WIDTH = 198
        const val IMAGE_HEIGHT = 166

        private const val SLOT_ID = "key_slot"

        private val INFO_GROUP_IDS = listOf("channels_info", "channel_info")


        private const val PANEL_COLOR = 0xFF1B1F26.toInt()
        private const val PANEL_BORDER_COLOR = 0xFF45505C.toInt()
        private const val TRANSPARENT_COLOR = 0x00000000

        private val PANEL_BACKGROUND = GuiTextureGroup(ColorRectTexture(PANEL_COLOR), ColorRectTexture(PANEL_BORDER_COLOR))
    }

    private val items: NonNullList<ItemStack> = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY)

    fun networkKey(): String? {
        val key = items[KEY_SLOT]
        if (key.item !is ResonantKeyItem) return null
        return ResonantKeyItem.networkKey(key)
    }

    fun dockedKey(): ItemStack = items[KEY_SLOT]

    fun inTowerRange(): Boolean {
        val level = this.level ?: return true
        return !EssenceNetwork.isKeyOutOfRange(items[KEY_SLOT], level, worldPosition)
    }

    fun dockKey(key: ItemStack): ItemStack {
        val previous = items[KEY_SLOT]
        items[KEY_SLOT] = key
        setChanged()
        return previous
    }

    private fun syncRegistration(updateActiveState: Boolean) {
        val level = this.level as? ServerLevel ?: return
        val server = level.server
        val state = HarmonicExchangeState.get(server)
        val key = networkKey()?.takeIf { inTowerRange() }
        val changed = if (key == null) {
            state.unregisterAt(level.dimension(), worldPosition) != null
        } else {
            state.register(key, level.dimension(), worldPosition)
        }
        if (changed) {
            HarmonicSubscriptions.refreshAll(server)
            val bridges = HarmonicBridgeState.get(server)
            key?.let { bridges.refreshBridgesOn(server, it) }
        }
        if (updateActiveState) {
            syncActiveState()
        }
    }

    fun onRemovedFromWorld() {
        val level = this.level as? ServerLevel ?: return
        val server = level.server
        val networkKey = HarmonicExchangeState.get(server).unregisterAt(level.dimension(), worldPosition)
        if (networkKey != null) {
            HarmonicSubscriptions.refreshAll(server)
            HarmonicBridgeState.get(server).refreshBridgesOn(server, networkKey)
        }
    }

    fun onNetworkActivity() {
        syncActiveState()
    }

    override fun setLevel(level: Level) {
        super.setLevel(level)
        syncRegistration(updateActiveState = false)
    }

    private fun isActive(): Boolean {
        val level = this.level as? ServerLevel ?: return false
        val key = networkKey() ?: return false
        if (!inTowerRange()) return false
        return HarmonicNetwork.isActive(level.server, key)
    }

    private fun syncActiveState() {
        val level = this.level as? ServerLevel ?: return
        val shouldBeActive = isActive()
        val state = blockState
        if (state.hasProperty(HexwrightBlockStates.ACTIVE) && state.getValue(HexwrightBlockStates.ACTIVE) != shouldBeActive) {
            level.setBlock(worldPosition, state.setValue(HexwrightBlockStates.ACTIVE, shouldBeActive), 3)
        }
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
        slot == KEY_SLOT && stack.item is ResonantKeyItem

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
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        ContainerHelper.saveAllItems(tag, items)
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()
    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun setChanged() {
        super.setChanged()
        syncRegistration(updateActiveState = true)
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    private fun snapshot(): HarmonicInfoWidget.Snapshot {
        val level = this.level as? ServerLevel ?: return HarmonicInfoWidget.Snapshot.empty()
        val key = networkKey() ?: return HarmonicInfoWidget.Snapshot.empty()
        if (!inTowerRange()) return HarmonicInfoWidget.Snapshot.outOfRange()
        val server = level.server
        val state = HarmonicExchangeState.get(server)
        val counts = HarmonicSubscriptions.countsByHarmonic(key)
        val emitters = HarmonicEmitterRegistry.countsByHarmonic(key)
        for (harmonic in counts.indices) counts[harmonic] += emitters[harmonic]
        val retained = BooleanArray(HarmonicExchangeState.HARMONIC_COUNT) { state.hasRetained(key, it) }
        return HarmonicInfoWidget.Snapshot(
            true,
            true,
            HarmonicNetwork.isActive(server, key),
            counts,
            retained,
            state.transmissionsLastMinute(key, level.gameTime)
        )
    }

    override fun createUI(entityPlayer: Player): ModularUI {
        val root = UiTemplates.load("harmonic_exchange")?.get()
        if (root == null) {
            Hexwright.LOGGER.error("Failed to load harmonic_exchange.ui; falling back to a minimal Harmonic Exchange UI")
            return createFallbackUI(entityPlayer)
        }
        bindWidgets(root)
        return ModularUI(root, this, entityPlayer)
    }

    private fun bindWidgets(root: WidgetGroup) {
        val widgetsById = MenuWidgets.indexById(root)

        bindKeySlot(widgetsById)

        val group = INFO_GROUP_IDS
            .firstNotNullOfOrNull { MenuWidgets.firstById(widgetsById, it) as? WidgetGroup }
        if (group == null) {
            Hexwright.LOGGER.warn("harmonic_exchange.ui is missing the channel info group (tried {})", INFO_GROUP_IDS)
            return
        }
        group.addWidget(
            HarmonicInfoWidget(0, 0, group.size.width, group.size.height) { snapshot() }
        )
    }

    private fun bindKeySlot(widgetsById: Map<String, List<Widget>>) {
        val template = MenuWidgets.firstById(widgetsById, SLOT_ID)
        if (template !is SlotWidget) {
            Hexwright.LOGGER.warn("harmonic_exchange.ui is missing the '{}' slot widget", SLOT_ID)
            return
        }
        val authored = template.tooltipTexts
        MenuWidgets.bindKeySlot(template, this, KEY_SLOT,
            if (authored.isNotEmpty()) authored
            else listOf(Component.translatable("gui.hexwright.harmonic_exchange.key.tooltip")))
    }

    private fun createFallbackUI(entityPlayer: Player): ModularUI {
        val ui = ModularUI(IMAGE_WIDTH, IMAGE_HEIGHT, this, entityPlayer)
        ui.background(PANEL_BACKGROUND)
        ui.widget(LabelWidget(8, 7, Component.translatable("block.hexwright.harmonic_exchange")))
        ui.widget(
            MenuWidgets.singleItemSlot(this, KEY_SLOT, 168, 32)
                .setBackground(ColorRectTexture(TRANSPARENT_COLOR))
                .setHoverTooltips(Component.translatable("gui.hexwright.harmonic_exchange.key.tooltip"))
        )
        ui.widget(HarmonicInfoWidget(11, 55, 176, 100) { snapshot() })
        return ui
    }
}
