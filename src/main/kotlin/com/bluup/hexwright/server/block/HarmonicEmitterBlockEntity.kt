package com.bluup.hexwright.server.block

import com.bluup.hexwright.server.hexpatterns.StoredHex
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.xplat.IXplatAbstractions
import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.server.menu.MenuWidgets
import com.bluup.hexwright.server.menu.UiTemplates
import com.bluup.hexwright.server.harmonic.HarmonicEmitterInfoWidget
import com.bluup.hexwright.server.harmonic.HarmonicEmitterRegistry
import com.bluup.hexwright.server.harmonic.HarmonicNetwork
import com.bluup.hexwright.server.harmonic.TransmissionWindow
import com.bluup.hexwright.server.network.EssenceNetwork
import com.bluup.hexwright.server.network.ResonanceNames
import com.bluup.hexwright.server.network.ResonantKeyItem
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder
import com.lowdragmc.lowdraglib.gui.modular.ModularUI
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget
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
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.Containers
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class HarmonicEmitterBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.HARMONIC_EMITTER_BLOCK_ENTITY, pos, state), Container, IUIHolder.BlockEntityUI {

    companion object {
        const val KEY_SLOT = 0
        const val CONTAINER_SIZE = 1

        const val NO_HARMONIC = -1

        const val CAPACITY = 500L * MediaConstants.DUST_UNIT

        private const val MAX_MESSAGE_LENGTH = 128

        const val IMAGE_WIDTH = 198
        const val IMAGE_HEIGHT = 251

        private const val SLOT_ID = "key_slot"

        private val INFO_GROUP_IDS = listOf("channels_info", "channel_info")


        private const val PANEL_COLOR = 0xFF1B1F26.toInt()
        private const val PANEL_BORDER_COLOR = 0xFF45505C.toInt()
        private const val TRANSPARENT_COLOR = 0x00000000

        private val PANEL_BACKGROUND =
            GuiTextureGroup(ColorRectTexture(PANEL_COLOR), ColorRectTexture(PANEL_BORDER_COLOR))
    }

    private val items: NonNullList<ItemStack> = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY)

    private var harmonic: Int = NO_HARMONIC

    private var hexTag: CompoundTag? = null

    private var hexLength: Int = 0

    private var media: Long = 0L

    private val signals = TransmissionWindow()

    private var lastMessage: String = ""


    fun networkKey(): String? {
        val key = items[KEY_SLOT]
        if (key.item !is ResonantKeyItem) return null
        return ResonantKeyItem.networkKey(key)
    }

    fun dockedKey(): ItemStack = items[KEY_SLOT]

    fun dockKey(key: ItemStack): ItemStack {
        val previous = items[KEY_SLOT]
        items[KEY_SLOT] = key
        setChanged()
        return previous
    }

    fun inTowerRange(): Boolean {
        val level = this.level ?: return true
        return !EssenceNetwork.isKeyOutOfRange(items[KEY_SLOT], level, worldPosition)
    }


    fun harmonic(): Int = harmonic

    fun media(): Long = media

    fun hexLength(): Int = hexLength

    fun hexTag(): CompoundTag? = hexTag

    fun listensTo(expectedNetwork: String, expectedHarmonic: Int): Boolean =
        harmonic == expectedHarmonic && expectedNetwork == networkKey()

    fun tune(harmonic: Int, hex: Iota) {
        this.harmonic = harmonic
        this.hexTag = IotaType.serialize(hex)
        this.hexLength = StoredHex.decode(hex)?.size ?: 0
        clearDisplay()
        setChanged()
    }

    fun untune() {
        harmonic = NO_HARMONIC
        hexTag = null
        hexLength = 0
        clearDisplay()
        setChanged()
    }


    fun payMedia(cost: Long, simulate: Boolean): Long {
        val paid = minOf(cost, media)
        if (!simulate && paid > 0) {
            media -= paid
            if (media <= 0L) {
                setChanged()
            } else {
                super.setChanged()
            }
        }
        return paid
    }

    fun tryChargeFrom(stack: ItemStack, player: ServerPlayer): Boolean {
        val holder = IXplatAbstractions.INSTANCE.findMediaHolder(stack) ?: return false
        if (!holder.canProvide()) return false

        val space = CAPACITY - media
        if (space <= 0L) {
            player.displayClientMessage(
                Component.translatable("message.hexwright.harmonic_emitter.full").withStyle(ChatFormatting.YELLOW),
                true
            )
            return true
        }

        val want = minOf(space, holder.media)
        if (want <= 0L) return false

        val got = holder.withdrawMedia(want, false)
        if (got <= 0L) return false

        media += got
        setChanged()
        player.displayClientMessage(
            Component.translatable(
                "message.hexwright.harmonic_emitter.charged", formatDust(media), formatDust(CAPACITY)
            ).withStyle(ChatFormatting.LIGHT_PURPLE),
            true
        )
        return true
    }


    fun recordSignal() {
        val level = this.level as? ServerLevel ?: return
        signals.record(level.gameTime)
    }

    fun postMessage(message: Component) {
        lastMessage = trim(message.string)
    }

    fun postMishap(message: Component) {
        lastMessage = trim(Component.translatable("gui.hexwright.harmonic_emitter.mishap", message).string)
    }

    fun clearDisplay() {
        lastMessage = ""
    }

    private fun trim(message: String): String =
        if (message.length <= MAX_MESSAGE_LENGTH) message else message.take(MAX_MESSAGE_LENGTH)


    private fun syncRegistration(updateActiveState: Boolean) {
        val level = this.level as? ServerLevel ?: return
        val key = networkKey()
        if (key == null || harmonic == NO_HARMONIC || !inTowerRange()) {
            HarmonicEmitterRegistry.unregister(level.dimension(), worldPosition)
        } else {
            HarmonicEmitterRegistry.register(level.dimension(), worldPosition, key, harmonic)
        }
        if (updateActiveState) {
            syncActiveState()
        }
    }

    override fun setLevel(level: Level) {
        super.setLevel(level)
        syncRegistration(updateActiveState = false)
    }

    override fun setRemoved() {
        super.setRemoved()
        val level = this.level as? ServerLevel ?: return
        HarmonicEmitterRegistry.unregister(level.dimension(), worldPosition)
    }

    private fun isActive(): Boolean {
        val level = this.level as? ServerLevel ?: return false
        val key = networkKey() ?: return false
        if (harmonic == NO_HARMONIC || !inTowerRange() || media <= 0L) return false
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
        harmonic = if (tag.contains("Harmonic")) tag.getInt("Harmonic") else NO_HARMONIC
        hexTag = if (tag.contains("Hex")) tag.getCompound("Hex").copy() else null
        hexLength = tag.getInt("HexLength")
        media = tag.getLong("Media").coerceIn(0L, CAPACITY)
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        ContainerHelper.saveAllItems(tag, items)
        if (harmonic != NO_HARMONIC) tag.putInt("Harmonic", harmonic)
        hexTag?.let { tag.put("Hex", it.copy()) }
        tag.putInt("HexLength", hexLength)
        tag.putLong("Media", media)
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()
    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun setChanged() {
        super.setChanged()
        syncRegistration(updateActiveState = true)
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }


    private fun snapshot(): HarmonicEmitterInfoWidget.Snapshot {
        val level = this.level as? ServerLevel ?: return HarmonicEmitterInfoWidget.Snapshot.empty(media, CAPACITY)
        val keyed = items[KEY_SLOT].item is ResonantKeyItem
        val key = networkKey()
            ?: return HarmonicEmitterInfoWidget.Snapshot.unattuned(keyed, media, CAPACITY)
        val server = level.server
        val inRange = inTowerRange()
        return HarmonicEmitterInfoWidget.Snapshot(
            true,
            true,
            inRange,
            harmonic != NO_HARMONIC && hexTag != null,
            inRange && HarmonicNetwork.isActive(server, key),
            ResonanceNames.nameOf(server, key) ?: "",
            harmonic,
            hexLength,
            media,
            CAPACITY,
            signals.lastMinute(level.gameTime),
            lastMessage
        )
    }

    private fun formatDust(amount: Long): String =
        String.format("%.1f", amount / MediaConstants.DUST_UNIT.toDouble())


    override fun createUI(entityPlayer: Player): ModularUI {
        val root = UiTemplates.load("harmonic_emitter")?.get()
        if (root == null) {
            Hexwright.LOGGER.error("Failed to load harmonic_emitter.ui; falling back to a minimal Harmonic Emitter UI")
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
            Hexwright.LOGGER.warn("harmonic_emitter.ui is missing the info group (tried {})", INFO_GROUP_IDS)
            return
        }
        group.addWidget(
            HarmonicEmitterInfoWidget(0, 0, group.size.width, group.size.height) { snapshot() }
        )
    }

    private fun bindKeySlot(widgetsById: Map<String, List<Widget>>) {
        val template = MenuWidgets.firstById(widgetsById, SLOT_ID)
        if (template !is SlotWidget) {
            Hexwright.LOGGER.warn("harmonic_emitter.ui is missing the '{}' slot widget", SLOT_ID)
            return
        }
        MenuWidgets.bindKeySlot(template, this, KEY_SLOT,
            listOf(Component.translatable("gui.hexwright.harmonic_emitter.key.tooltip")))
    }

    private fun createFallbackUI(entityPlayer: Player): ModularUI {
        val ui = ModularUI(IMAGE_WIDTH, IMAGE_HEIGHT, this, entityPlayer)
        ui.background(PANEL_BACKGROUND)
        ui.widget(LabelWidget(8, 7, Component.translatable("block.hexwright.harmonic_emitter")))
        ui.widget(
            MenuWidgets.singleItemSlot(this, KEY_SLOT, 168, 32)
                .setBackground(ColorRectTexture(TRANSPARENT_COLOR))
                .setHoverTooltips(Component.translatable("gui.hexwright.harmonic_emitter.key.tooltip"))
        )
        ui.widget(HarmonicEmitterInfoWidget(11, 55, 176, 100) { snapshot() })
        return ui
    }
}
