package com.bluup.hexwright.server.block

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.server.fluid.HexidTank
import com.bluup.hexwright.server.fluid.HexidTankBlockEntity
import com.bluup.hexwright.server.fluid.HexidPipeNetwork
import com.bluup.hexwright.server.fluid.HexidTankColumn
import com.bluup.hexwright.server.fluid.TankRemnants
import com.bluup.hexwright.server.menu.MenuWidgets
import com.bluup.hexwright.server.menu.UiTemplates
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder
import com.lowdragmc.lowdraglib.gui.modular.ModularUI
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget
import com.lowdragmc.lowdraglib.gui.widget.SliderWidget
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget
import com.lowdragmc.lowdraglib.gui.widget.Widget
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class AlembixBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.ALEMBIX_BLOCK_ENTITY, pos, state), IUIHolder.BlockEntityUI {

    companion object {
        const val INPUTS = 4

        const val TOTAL = 100

        val INPUT_SIDES = arrayOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)

        const val PERIOD_TICKS = 2

        const val STEP_DRAMS = HexidTank.DRAMS_PER_BLOCK / 20.0

        private const val MIN_STEP_DRAMS = 0.1

        private const val UI_PROJECT = "alembix_horizontal"

        private const val COMMIT_ACTION = 1

        private const val TAG_RATIO = "Ratio"

        private const val OUTPUT_ID = "output_label"
        private const val STATUS_ID = "status"
        private const val ACTION_ID = "action"
        private fun mixerId(index: Int) = "rem_${index + 1}_mixer"
        private fun amountId(index: Int) = "rem_${index + 1}_amount"
        private fun labelId(index: Int) = "rem_${index + 1}_label"

        private const val IMAGE_WIDTH = 266
        private const val IMAGE_HEIGHT = 172

        private const val PANEL_COLOR = 0xFF1B1F26.toInt()

        private const val EMPTY_TINT = 0x555F6B

        private const val FACE_CACHE_TICKS = 10L

        private val DISABLED_DIM: IGuiTexture = ColorRectTexture(0xB2101010.toInt())
    }

    private val ratio = IntArray(INPUTS)

    private var countdown = PERIOD_TICKS

    private val faceHolds = arrayOfNulls<TankRemnants>(INPUTS)
    private val facePlumbed = BooleanArray(INPUTS)
    private var facesReadAt = Long.MIN_VALUE

    fun ratio(index: Int): Int = ratio.getOrElse(index) { 0 }

    fun isConfigured(): Boolean = ratio.sum() == TOTAL


    fun columnsAt(side: Direction): List<HexidTankBlockEntity> {
        val level = this.level ?: return emptyList()
        val pos = worldPosition.relative(side)
        val state = level.getBlockState(pos)
        if (HexidTankColumn.isTank(state)) {
            return listOfNotNull(HexidTankColumn.controller(level, pos))
        }
        if (state.`is`(HexwrightBlocks.HEXID_PIPE_BLOCK)) {
            return HexidPipeNetwork.tanksOn(level, pos)
        }
        return emptyList()
    }

    fun inputColumns(index: Int): List<HexidTankBlockEntity> = columnsAt(INPUT_SIDES[index])

    fun outputColumns(): List<HexidTankBlockEntity> =
        columnsAt(Direction.UP).filter { it.canAcceptRemnants() }

    private fun refreshFaces() {
        val now = level?.gameTime ?: return
        if (facesReadAt != Long.MIN_VALUE && now - facesReadAt < FACE_CACHE_TICKS) {
            return
        }
        facesReadAt = now
        for (index in 0 until INPUTS) {
            val columns = inputColumns(index)
            facePlumbed[index] = columns.isNotEmpty()
            var pooled = TankRemnants.EMPTY
            for (column in columns) {
                pooled = pooled.plusAll(column.remnants())
            }
            faceHolds[index] = pooled
        }
    }

    private fun inputRemnants(index: Int): TankRemnants {
        refreshFaces()
        return faceHolds[index] ?: TankRemnants.EMPTY
    }

    fun canDrawFrom(index: Int): Boolean {
        refreshFaces()
        return facePlumbed[index] && (faceHolds[index]?.kinds() ?: 0) <= 1
    }

    fun inputLabel(index: Int): Component {
        val held = inputRemnants(index)
        if (!facePlumbed[index]) {
            return Component.translatable("gui.hexwright.alembix.input.none")
        }
        return when {
            held.isEmpty -> Component.translatable("gui.hexwright.alembix.input.empty")
            held.kinds() == 1 -> held.largest()!!.label()
            else -> Component.translatable("gui.hexwright.alembix.input.mixture", held.kinds())
        }
    }


    fun commitRatio(next: IntArray) {
        val clean = IntArray(INPUTS) { next.getOrElse(it) { 0 }.coerceIn(0, TOTAL) }
        if (clean.sum() != TOTAL || clean.contentEquals(ratio)) {
            return
        }
        clean.copyInto(ratio)
        setChanged()
        sync()
    }

    fun serverTick() {
        if (countdown > 0) {
            countdown--
            return
        }
        countdown = PERIOD_TICKS - 1
        if (!isConfigured() || level !is ServerLevel) {
            return
        }

        val sinks = outputColumns()
        if (sinks.isEmpty()) {
            return
        }
        var batch = minOf(STEP_DRAMS, sinks.sumOf { it.remnantHeadroom() })

        val sources = arrayOfNulls<List<HexidTankBlockEntity>>(INPUTS)
        for (index in 0 until INPUTS) {
            val share = ratio[index]
            if (share <= 0) continue
            val columns = inputColumns(index).filter { column -> sinks.none { it === column } }
            var pooled = TankRemnants.EMPTY
            for (column in columns) pooled = pooled.plusAll(column.remnants())
            if (pooled.kinds() > 1) return
            val available = pooled.total()
            if (available < TankRemnants.MIN_DRAMS) return
            sources[index] = columns
            batch = minOf(batch, available * TOTAL / share)
        }
        if (batch < MIN_STEP_DRAMS) {
            return
        }

        var blend = TankRemnants.EMPTY
        for (index in 0 until INPUTS) {
            val columns = sources[index] ?: continue
            blend = blend.plusAll(drawFrom(columns, batch * ratio[index] / TOTAL))
        }
        pourInto(sinks, blend)
    }

    private fun drawFrom(columns: List<HexidTankBlockEntity>, rems: Double): TankRemnants {
        var left = rems
        var taken = TankRemnants.EMPTY
        for (column in columns) {
            if (left < TankRemnants.MIN_DRAMS) break
            val got = column.drawMixture(left)
            taken = taken.plusAll(got)
            left -= got.total()
        }
        return taken
    }

    private fun pourInto(columns: List<HexidTankBlockEntity>, blend: TankRemnants) {
        var left = blend
        for (column in columns) {
            if (left.isEmpty) break
            val poured = column.pourMixture(left)
            if (poured <= 0.0) continue
            left = if (poured >= left.total()) TankRemnants.EMPTY
            else left.portion(1.0 - poured / left.total())
        }
    }


    override fun load(tag: CompoundTag) {
        super.load(tag)
        val saved = tag.getIntArray(TAG_RATIO)
        for (index in 0 until INPUTS) {
            ratio[index] = saved.getOrElse(index) { 0 }.coerceIn(0, TOTAL)
        }
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        tag.putIntArray(TAG_RATIO, ratio)
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> =
        ClientboundBlockEntityDataPacket.create(this)

    private fun sync() {
        val level = this.level ?: return
        if (!level.isClientSide) {
            level.sendBlockUpdated(worldPosition, blockState, blockState, 3)
        }
    }


    override fun createUI(entityPlayer: Player): ModularUI {
        val root = UiTemplates.load(UI_PROJECT)?.get() ?: run {
            Hexwright.LOGGER.error("Failed to load {}.ui; falling back to a minimal Alembix UI", UI_PROJECT)
            fallbackRoot()
        }
        bind(root)
        return ModularUI(root, this, entityPlayer)
    }

    private fun bind(root: WidgetGroup) {
        val byId = MenuWidgets.indexById(root)

        val mixers = ArrayList<SliderWidget>(INPUTS)
        for (index in 0 until INPUTS) {
            val mixer = MenuWidgets.firstById(byId, mixerId(index)) as? SliderWidget
            if (mixer == null) {
                Hexwright.LOGGER.warn("{}.ui is missing mixer '{}'", UI_PROJECT, mixerId(index))
                return
            }
            mixer.setClientSideWidget()
            mixers.add(mixer)
        }

        val panel = MixerPanel(mixers, byId)
        val pending = ArrayList<Widget>()
        for (index in 0 until INPUTS) {
            bindText(byId, labelId(index), clientSide = false) { inputLabel(index) }
            bindText(byId, amountId(index), clientSide = true) {
                Component.literal(panel.percent(index).toString())
            }?.let(pending::add)
        }
        bindText(byId, OUTPUT_ID, clientSide = true) {
            Component.translatable("gui.hexwright.alembix.output", panel.total(), TOTAL)
        }?.let(pending::add)

        val sync = SyncWidget(panel)
        root.addWidget(sync)
        panel.bindAction(sync)

        if (level?.isClientSide == true) {
            panel.open()
            for (widget in pending) widget.updateScreen()
        }
    }

    private fun bindText(
        byId: Map<String, List<Widget>>,
        id: String,
        clientSide: Boolean,
        text: () -> Component
    ): TextTextureWidget? {
        val widget = MenuWidgets.firstById(byId, id) as? TextTextureWidget
        if (widget == null) {
            Hexwright.LOGGER.warn("{}.ui is missing readout '{}'", UI_PROJECT, id)
            return null
        }
        if (clientSide) {
            widget.setClientSideWidget()
        }
        widget.setText { text() }
        return widget
    }

    private inner class MixerPanel(
        private val mixers: List<SliderWidget>,
        byId: Map<String, List<Widget>>
    ) {
        private val status = MenuWidgets.firstById(byId, STATUS_ID)
        private val actionGroup = MenuWidgets.firstById(byId, ACTION_ID) as? WidgetGroup
        private var actionButton: ButtonWidget? = null

        fun percent(index: Int): Int = Math.round(mixers[index].amount)

        fun total(): Int = mixers.indices.sumOf { percent(it) }

        @Environment(EnvType.CLIENT)
        fun open() {
            for (index in mixers.indices) {
                val mixer = mixers[index]
                mixer.setAmount(ratio(index).toFloat())
                mixer.setBackground(
                    MixerFillTexture({ mixer.sliderValue }, { inputRemnants(index).tint(EMPTY_TINT) }))
                mixer.setSliderCallback { clamp(index) }
                mixer.updateScreen()
            }
            refresh()
        }

        private fun clamp(index: Int) {
            var others = 0
            for (other in mixers.indices) {
                if (other != index) others += percent(other)
            }
            val cap = (TOTAL - others).coerceAtLeast(0)
            if (percent(index) > cap) {
                mixers[index].setAmount(cap.toFloat())
            }
        }

        fun bindAction(sync: SyncWidget) {
            val group = actionGroup
            if (group == null) {
                Hexwright.LOGGER.warn("{}.ui is missing the '{}' button group", UI_PROJECT, ACTION_ID)
                return
            }
            val button = ButtonWidget(0, 0, group.sizeWidth, group.sizeHeight) { _ ->
                if (total() == TOTAL) {
                    sync.sendCommit(IntArray(INPUTS) { percent(it) })
                }
            }
            button.setClientSideWidget()
            actionButton = button
            group.addWidget(button)
        }

        fun refresh() {
            for (index in mixers.indices) {
                val usable = canDrawFrom(index)
                mixers[index].isActive = usable
                if (!usable && percent(index) != 0) {
                    mixers[index].setAmount(0f)
                }
            }
            val ready = total() == TOTAL
            status?.isVisible = !ready
            actionGroup?.isActive = ready
            actionButton?.isActive = ready
            actionButton?.setBackground(if (ready) IGuiTexture.EMPTY else DISABLED_DIM)
        }
    }

    private inner class SyncWidget(private val panel: MixerPanel) : Widget(0, 0, 0, 0) {

        fun sendCommit(values: IntArray) {
            writeClientAction(COMMIT_ACTION) { buffer ->
                for (value in values) buffer.writeVarInt(value)
            }
        }

        override fun handleClientAction(id: Int, buffer: FriendlyByteBuf) {
            super.handleClientAction(id, buffer)
            if (id == COMMIT_ACTION) {
                commitRatio(IntArray(INPUTS) { buffer.readVarInt() })
            }
        }

        @Environment(EnvType.CLIENT)
        override fun updateScreen() {
            super.updateScreen()
            panel.refresh()
        }
    }

    private fun fallbackRoot(): WidgetGroup {
        val root = WidgetGroup(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT)
        root.setBackground(ColorRectTexture(PANEL_COLOR))
        root.addWidget(LabelWidget(8, 7, Component.translatable("block.hexwright.alembix")))
        for (index in 0 until INPUTS) {
            val y = 26 + index * 24
            root.addWidget(TextTextureWidget(8, y, 60, 14).also { it.id = labelId(index) })
            root.addWidget(SliderWidget(72, y, 140, 14).also {
                it.id = mixerId(index)
                it.maxAmount = TOTAL.toFloat()
                it.valueStep = TOTAL
            })
            root.addWidget(TextTextureWidget(216, y, 34, 14).also { it.id = amountId(index) })
        }
        root.addWidget(TextTextureWidget(8, 132, 60, 14).also { it.id = OUTPUT_ID })
        root.addWidget(TextTextureWidget(72, 132, 120, 14).also { it.id = STATUS_ID })
        root.addWidget(WidgetGroup(198, 130, 54, 21).also { it.id = ACTION_ID })
        return root
    }
}

private class MixerFillTexture(
    private val fraction: () -> Float,
    private val tint: () -> Int
) : IGuiTexture {

    @Environment(EnvType.CLIENT)
    override fun draw(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        x: Float,
        y: Float,
        width: Int,
        height: Int
    ) {
        if (width <= 2 || height <= 2) return
        val handle = HANDLE_SIZE.coerceAtMost(width)
        val filled = ((width - handle) * fraction().coerceIn(0f, 1f) + handle / 2f).toInt()
        val drawn = filled.coerceIn(0, width - 2)
        if (drawn <= 0) return
        val left = x.toInt() + 1
        val top = y.toInt() + 1
        graphics.fill(left, top, left + drawn, y.toInt() + height - 1, (ALPHA shl 24) or (tint() and 0xFFFFFF))
    }

    private companion object {
        const val HANDLE_SIZE = 8

        const val ALPHA = 0xAA
    }
}
