package com.bluup.hexwright.server.block

import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.xplat.IXplatAbstractions
import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.server.menu.MenuWidgets
import com.bluup.hexwright.server.menu.UiTemplates
import com.bluup.hexwright.server.harmonic.HarmonicChannelTarget
import com.bluup.hexwright.server.harmonic.HarmonicNetwork
import com.bluup.hexwright.server.harmonic.TransducerTriggers
import com.bluup.hexwright.server.network.EssenceNetwork
import com.bluup.hexwright.server.network.ResonanceNames
import com.bluup.hexwright.server.network.ResonantKeyItem
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder
import com.lowdragmc.lowdraglib.gui.modular.ModularUI
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget
import com.lowdragmc.lowdraglib.gui.widget.SwitchWidget
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
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.Containers
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class HarmonicTransducerBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.HARMONIC_TRANSDUCER_BLOCK_ENTITY, pos, state),
    Container,
    IUIHolder.BlockEntityUI,
    HarmonicChannelTarget {

    companion object {
        const val KEY_SLOT = 0
        const val CONTAINER_SIZE = 1

        const val NO_HARMONIC = -1

        const val CAPACITY = 500L * MediaConstants.DUST_UNIT

        const val PUBLISH_COST = MediaConstants.DUST_UNIT / 8

        private const val POLL_INTERVAL = 2

        private const val SLOT_ID = "key_slot"
        private const val TRIGGER_VALUE_ID = "trigger_value"
        private const val MEDIA_ID = "media"
        private const val TOGGLE_ID = "active_toggle"
        private const val LEFT_ID = "left_trigger"
        private const val RIGHT_ID = "right_trigger"

        const val IMAGE_WIDTH = 198
        const val IMAGE_HEIGHT = 214


        private const val PANEL_COLOR = 0xFF1B1F26.toInt()
        private const val PANEL_BORDER_COLOR = 0xFF45505C.toInt()
        private const val TRANSPARENT_COLOR = 0x00000000

        private val PANEL_BACKGROUND =
            GuiTextureGroup(ColorRectTexture(PANEL_COLOR), ColorRectTexture(PANEL_BORDER_COLOR))
    }

    private val items: NonNullList<ItemStack> = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY)

    private var harmonic: Int = NO_HARMONIC

    private var trigger: String = TransducerTriggers.ANY

    private var active: Boolean = true

    private var media: Long = 0L

    private var lastReading: String? = null

    private var pollCountdown: Int = 0


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


    override fun harmonic(): Int = harmonic

    fun media(): Long = media

    override fun tune(harmonic: Int) {
        this.harmonic = harmonic
        setChanged()
    }

    override fun untune() {
        harmonic = NO_HARMONIC
        setChanged()
    }

    override fun networkKeys(): List<String> = listOfNotNull(networkKey())

    override fun tuningRefusal(): String? = when {
        networkKey() == null ->
            if (dockedKey().isEmpty) "hexwright_transducer_unkeyed" else "hexwright_transducer_unattuned"
        !inTowerRange() -> "hexwright_transducer_out_of_range"
        else -> null
    }


    private fun watchedPos(): BlockPos = HarmonicTransducerBlock.watchedPos(worldPosition, blockState)

    private fun watchedState(): BlockState? = level?.getBlockState(watchedPos())

    private fun availableTriggers(): List<String> {
        val watched = watchedState() ?: return listOf(TransducerTriggers.ANY)
        return TransducerTriggers.available(watched)
    }

    private fun cycleTrigger(forward: Boolean) {
        val options = availableTriggers()
        if (options.isEmpty()) return
        val current = options.indexOf(trigger)
        val next = if (current < 0) {
            0
        } else {
            Math.floorMod(current + if (forward) 1 else -1, options.size)
        }
        trigger = options[next]
        lastReading = null
        setChanged()
    }

    fun triggerLabel(): String {
        val watched = watchedState()
        val label = TransducerTriggers.label(trigger)
        return if (watched == null || TransducerTriggers.applies(trigger, watched)) {
            label
        } else {
            "gui.hexwright.harmonic_transducer.trigger.missing"
        }
    }


    fun isActive(): Boolean = active

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        lastReading = null
        setChanged()
    }


    fun tryChargeFrom(stack: ItemStack, player: ServerPlayer): Boolean {
        val holder = IXplatAbstractions.INSTANCE.findMediaHolder(stack) ?: return false
        if (!holder.canProvide()) return false

        val space = CAPACITY - media
        if (space <= 0L) {
            player.displayClientMessage(
                Component.translatable("message.hexwright.harmonic_transducer.full").withStyle(ChatFormatting.YELLOW),
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
                "message.hexwright.harmonic_transducer.charged", formatDust(media), formatDust(CAPACITY)
            ).withStyle(ChatFormatting.LIGHT_PURPLE),
            true
        )
        return true
    }

    private fun formatDust(amount: Long): String {
        val tenths = amount * 10 / MediaConstants.DUST_UNIT
        return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }

    private fun mediaLine(): String {
        val prefix = when {
            networkKey() == null -> if (dockedKey().isEmpty) "No key" else "Unattuned key"
            !inTowerRange() -> "Out of range"
            harmonic == NO_HARMONIC -> "Untuned"
            else -> "Harmonic $harmonic"
        }
        return "$prefix - ${formatDust(media)} / ${formatDust(CAPACITY)} dust"
    }


    private fun canPublish(): Boolean {
        val level = this.level as? ServerLevel ?: return false
        if (!active || harmonic == NO_HARMONIC || media < PUBLISH_COST) return false
        val key = networkKey() ?: return false
        if (!inTowerRange()) return false
        return HarmonicNetwork.isActive(level.server, key)
    }

    fun serverTick() {
        if (pollCountdown > 0) {
            pollCountdown--
            return
        }
        pollCountdown = POLL_INTERVAL - 1

        val level = this.level as? ServerLevel ?: return
        if (!canPublish()) {
            lastReading = null
            return
        }

        val watchedPos = watchedPos()
        val watched = level.getBlockState(watchedPos)
        val reading = TransducerTriggers.read(trigger, level, watchedPos, watched) ?: return

        val previous = lastReading
        lastReading = reading
        if (previous == null || previous == reading) {
            return
        }

        publish(level, TransducerTriggers.payload(trigger, reading, watched, watchedPos))
    }

    private fun publish(level: ServerLevel, payload: Iota) {
        val key = networkKey() ?: return
        media -= PUBLISH_COST
        ResonanceNames.nameOrAssign(level.server, key)
        HarmonicNetwork.publish(level.server, key, harmonic, payload)
        super.setChanged()
        syncActiveState()
    }

    private fun syncActiveState() {
        val level = this.level as? ServerLevel ?: return
        val shouldBeActive = canPublish()
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
        trigger = if (tag.contains("Trigger")) tag.getString("Trigger") else TransducerTriggers.ANY
        active = !tag.contains("Active") || tag.getBoolean("Active")
        media = tag.getLong("Media").coerceIn(0L, CAPACITY)
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        ContainerHelper.saveAllItems(tag, items)
        if (harmonic != NO_HARMONIC) tag.putInt("Harmonic", harmonic)
        tag.putString("Trigger", trigger)
        tag.putBoolean("Active", active)
        tag.putLong("Media", media)
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()
    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun setChanged() {
        super.setChanged()
        syncActiveState()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }


    override fun createUI(entityPlayer: Player): ModularUI {
        val root = UiTemplates.load("harmonic_transducer")?.get()
        if (root == null) {
            Hexwright.LOGGER.error(
                "Failed to load harmonic_transducer.ui; falling back to a minimal Harmonic Transducer UI"
            )
            return createFallbackUI(entityPlayer)
        }
        bindWidgets(root, entityPlayer)
        return ModularUI(root, this, entityPlayer)
    }

    private fun bindWidgets(root: WidgetGroup, entityPlayer: Player) {
        val widgetsById = MenuWidgets.indexById(root)

        bindKeySlot(widgetsById, root)

        bindStepper(widgetsById, RIGHT_ID, entityPlayer) { cycleTrigger(true) }
        bindStepper(widgetsById, LEFT_ID, entityPlayer) { cycleTrigger(false) }

        bindValueLabel(widgetsById, TRIGGER_VALUE_ID) { triggerLabel() }
        bindValueLabel(widgetsById, MEDIA_ID) { mediaLine() }

        bindToggle(widgetsById, entityPlayer)
    }

    private fun bindToggle(widgetsById: Map<String, List<Widget>>, entityPlayer: Player) {
        val toggle = MenuWidgets.firstById(widgetsById, TOGGLE_ID) as? SwitchWidget
        if (toggle == null) {
            Hexwright.LOGGER.warn("harmonic_transducer.ui is missing the '{}' switch", TOGGLE_ID)
            return
        }
        toggle.setSupplier { isActive() }
        toggle.setOnPressCallback { _, pressed ->
            if (entityPlayer is ServerPlayer && level?.isClientSide == false) {
                setActive(pressed)
            }
        }
    }

    private fun bindKeySlot(widgetsById: Map<String, List<Widget>>, root: WidgetGroup) {
        val template = MenuWidgets.firstById(widgetsById, SLOT_ID) as? SlotWidget
            ?: soleUnnamedSlot(root)
        if (template == null) {
            Hexwright.LOGGER.warn("harmonic_transducer.ui is missing the '{}' slot widget", SLOT_ID)
            return
        }
        template.id = SLOT_ID
        MenuWidgets.bindKeySlot(template, this, KEY_SLOT,
            listOf(Component.translatable("gui.hexwright.harmonic_transducer.key.tooltip")))
    }

    private fun soleUnnamedSlot(root: WidgetGroup): SlotWidget? {
        val found = mutableListOf<SlotWidget>()
        fun walk(widget: Widget) {
            if (widget is SlotWidget && widget.id?.startsWith("player_inv_") != true) {
                found.add(widget)
            }
            if (widget is WidgetGroup) {
                for (child in ArrayList(widget.widgets)) walk(child)
            }
        }
        walk(root)
        return found.singleOrNull()
    }

    private fun bindValueLabel(
        widgetsById: Map<String, List<Widget>>,
        id: String,
        text: () -> String
    ) {
        when (val widget = MenuWidgets.firstById(widgetsById, id)) {
            is LabelWidget -> {
                widget.setTextProvider { text() }
                alignNow(widget)
            }
            is TextTextureWidget -> {
                widget.setText { Component.translatable(text()) }
                alignNow(widget)
            }
            else -> Hexwright.LOGGER.warn("harmonic_transducer.ui is missing value label '{}'", id)
        }
    }

    private fun alignNow(widget: Widget) {
        if (level?.isClientSide != true) return
        widget.updateScreen()
    }

    private fun bindStepper(
        widgetsById: Map<String, List<Widget>>,
        id: String,
        entityPlayer: Player,
        action: () -> Unit
    ) {
        val group = MenuWidgets.firstById(widgetsById, id) as? WidgetGroup
        if (group == null) {
            Hexwright.LOGGER.warn("harmonic_transducer.ui is missing button widget '{}'", id)
            return
        }
        val click = ButtonWidget(0, 0, group.sizeWidth, group.sizeHeight) { _ ->
            if (entityPlayer is ServerPlayer && level?.isClientSide == false) {
                action()
            }
        }
        group.addWidget(click)
    }

    private fun createFallbackUI(entityPlayer: Player): ModularUI {
        val ui = ModularUI(IMAGE_WIDTH, IMAGE_HEIGHT, this, entityPlayer)
        ui.background(PANEL_BACKGROUND)
        ui.widget(LabelWidget(8, 7, Component.translatable("block.hexwright.harmonic_transducer")))
        ui.widget(LabelWidget(8, 24) { mediaLine() })
        ui.widget(LabelWidget(8, 38) { triggerLabel() })
        ui.widget(
            ButtonWidget(8, 52, 20, 20, GuiTextureGroup(ColorRectTexture(PANEL_BORDER_COLOR))) { _ ->
                if (entityPlayer is ServerPlayer && level?.isClientSide == false) cycleTrigger(false)
            }
        )
        ui.widget(
            ButtonWidget(32, 52, 20, 20, GuiTextureGroup(ColorRectTexture(PANEL_BORDER_COLOR))) { _ ->
                if (entityPlayer is ServerPlayer && level?.isClientSide == false) cycleTrigger(true)
            }
        )
        ui.widget(
            MenuWidgets.singleItemSlot(this, KEY_SLOT, 168, 32)
                .setBackground(ColorRectTexture(TRANSPARENT_COLOR))
                .setHoverTooltips(Component.translatable("gui.hexwright.harmonic_transducer.key.tooltip"))
        )
        return ui
    }
}
