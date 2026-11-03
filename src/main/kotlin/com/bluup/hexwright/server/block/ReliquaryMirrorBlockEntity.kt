package com.bluup.hexwright.server.block

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.server.menu.MenuWidgets
import com.bluup.hexwright.server.menu.UiTemplates
import com.bluup.hexwright.server.reliquary.HexDisplayContainer
import com.bluup.hexwright.server.reliquary.ReliquarySealItem
import com.bluup.hexwright.server.reliquary.ChestCastEnv
import com.bluup.hexwright.server.reliquary.ReliquaryStore
import com.bluup.hexwright.server.reliquary.ReliquaryWindow
import com.bluup.hexwright.server.reliquary.SatchelItem
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder
import com.lowdragmc.lowdraglib.gui.modular.ModularUI
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture
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
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.Container
import net.minecraft.world.phys.Vec3
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.InteractionHand
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.ChestLidController
import net.minecraft.world.level.block.entity.LidBlockEntity
import net.minecraft.world.level.block.state.BlockState

class ReliquaryMirrorBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.RELIQUARY_MIRROR_BLOCK_ENTITY, pos, state), Container, IUIHolder.BlockEntityUI, LidBlockEntity {

    companion object {
        const val HELD_SLOT = 0
        const val OPEN_SLOT = 1
        const val DEPOSIT_SLOT = 2
        const val WITHDRAW_SLOT = 3
        const val CONTAINER_SIZE = 4

        const val IMAGE_WIDTH = 176
        const val IMAGE_HEIGHT = 252
        const val SEAL_X = 8
        const val OPEN_X = 44
        const val DEPOSIT_X = 80
        const val WITHDRAW_X = 116
        const val TOP_LABEL_Y = 19
        const val SEAL_Y = 29
        const val PLAYER_INV_LEFT = 8
        const val PLAYER_INV_TOP = 172
        const val HOTBAR_TOP = PLAYER_INV_TOP + 3 * 18 + 4

        private const val PANEL_COLOR = 0xFF1B1F26.toInt()
        private const val PANEL_BORDER_COLOR = 0xFF45505C.toInt()
        private const val TRANSPARENT_COLOR = 0x00000000
        private const val OPEN_ANIMATION_TICKS = 12

        private val PANEL_BACKGROUND = GuiTextureGroup(ColorRectTexture(PANEL_COLOR), ColorRectTexture(PANEL_BORDER_COLOR))

        private val HELD_SLOT_GUIDE_TEXTURE: IGuiTexture = ResourceTexture("ldlib:textures/menu/secondary_hand.png")
        private val HELD_SLOT_FILLED_TEXTURE: IGuiTexture = ResourceTexture("ldlib:textures/menu/secondary_input.png")
    }

    private val items: NonNullList<ItemStack> = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY)
    private val chestLidController = ChestLidController()
    private var shouldAnimateOpen = false
    private var openAnimationTicks = 0

    fun onOpenedByPlayer() {
        if (level?.isClientSide == true) {
            return
        }
        openAnimationTicks = OPEN_ANIMATION_TICKS
        setOpenState(true)
    }

    fun tickChestAnimation(level: Level) {
        if (!level.isClientSide) {
            if (openAnimationTicks > 0) {
                openAnimationTicks--
                if (openAnimationTicks == 0) {
                    setOpenState(false)
                }
            }
        }
        chestLidController.shouldBeOpen(shouldAnimateOpen)
        chestLidController.tickLid()
    }

    private fun setOpenState(open: Boolean) {
        if (shouldAnimateOpen == open) {
            return
        }
        shouldAnimateOpen = open
        val currentLevel = level ?: return
        currentLevel.blockEvent(worldPosition, blockState.block, 1, if (open) 1 else 0)
    }

    override fun triggerEvent(id: Int, type: Int): Boolean {
        if (id == 1) {
            shouldAnimateOpen = type > 0
            return true
        }
        return super.triggerEvent(id, type)
    }

    override fun getOpenNess(partialTick: Float): Float = chestLidController.getOpenness(Mth.clamp(partialTick, 0.0f, 1.0f))

    private fun sealKey(): String? {
        val seal = items[HELD_SLOT]
        if (seal.item !is ReliquarySealItem) return null
        return ReliquarySealItem.storeKey(seal)
    }

    override fun getContainerSize(): Int = CONTAINER_SIZE
    override fun isEmpty(): Boolean = items.all { it.isEmpty }
    override fun getItem(slot: Int): ItemStack = items[slot]

    override fun getMaxStackSize(): Int = 1

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        val result = ContainerHelper.removeItem(items, slot, amount)
        if (!result.isEmpty) setChanged()
        return result
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack = ContainerHelper.takeItem(items, slot)

    override fun setItem(slot: Int, stack: ItemStack) {
        items[slot] = if (stack.isEmpty) ItemStack.EMPTY else stack.copyWithCount(1)
        setChanged()
    }

    override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = when (slot) {
        HELD_SLOT -> true
        OPEN_SLOT, DEPOSIT_SLOT, WITHDRAW_SLOT ->
            at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE.findDataHolder(stack) != null
        else -> false
    }

    override fun clearContent() {
        for (i in items.indices) items[i] = ItemStack.EMPTY
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
        val serverLevel = level as? ServerLevel
        if (serverLevel != null) {
            val key = sealKey()
            if (key != null) {
                val store = ReliquaryStore.get(serverLevel.server)
                store.setHook(key, SatchelItem.Hook.OPEN, items[OPEN_SLOT])
                store.setHook(key, SatchelItem.Hook.DEPOSIT, items[DEPOSIT_SLOT])
                store.setHook(key, SatchelItem.Hook.WITHDRAW, items[WITHDRAW_SLOT])
            }
        }
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    private fun effectiveHookFocus(
        opener: ServerPlayer,
        key: String?,
        slot: Int,
        hook: SatchelItem.Hook
    ): ItemStack {
        val local = items[slot]
        if (!local.isEmpty) {
            return local
        }
        if (key == null) {
            return ItemStack.EMPTY
        }
        return ReliquaryStore.get(opener.server).getHook(key, hook)
    }

    override fun createUI(entityPlayer: Player): ModularUI {
        val template = UiTemplates.load("container")
        val root = template?.get()
        if (root == null) {
            Hexwright.LOGGER.error("Failed to load container UI project; falling back to minimal reliquary mirror UI")
            return createFallbackUI(entityPlayer)
        }
        bindMirrorWidgets(root, entityPlayer)
        return ModularUI(root, this, entityPlayer)
    }

    private fun bindMirrorWidgets(root: WidgetGroup, entityPlayer: Player) {
        val widgetsById = MenuWidgets.indexById(root)

        bindControlSlot(widgetsById, "held", HELD_SLOT, "gui.hexwright.mirror.held.tooltip")
        bindControlSlot(widgetsById, "list", OPEN_SLOT, SatchelItem.Hook.OPEN.translationKey() + ".tooltip")
        bindControlSlot(widgetsById, "in", DEPOSIT_SLOT, SatchelItem.Hook.DEPOSIT.translationKey() + ".tooltip")
        bindControlSlot(widgetsById, "out", WITHDRAW_SLOT, SatchelItem.Hook.WITHDRAW.translationKey() + ".tooltip")

        val hoard = hoardView(entityPlayer)
        ReliquaryWindow.bindHoardGrid(widgetsById, hoard)
        MenuWidgets.bindPlayerInventory(widgetsById, entityPlayer)
    }

    private fun hoardView(entityPlayer: Player): Container {
        val level = this.level
        if (level == null || level.isClientSide || entityPlayer !is ServerPlayer) {
            return ReliquaryWindow.hoardView(true, emptyList(), null)
        }
        val heldSlot = object : ChestCastEnv.HeldSlot {
            override fun get(): ItemStack = items[HELD_SLOT]

            override fun set(stack: ItemStack) {
                items[HELD_SLOT] = stack
                setChanged()
            }
        }

        fun focus(key: String?, slot: Int, hook: SatchelItem.Hook): ItemStack =
            effectiveHookFocus(entityPlayer, key, slot, hook)

        fun runOpen(key: String?): List<ItemStack> = ReliquaryWindow.runOpen(
            entityPlayer, InteractionHand.MAIN_HAND, focus(key, OPEN_SLOT, SatchelItem.Hook.OPEN), heldSlot
        )

        fun withdraw(key: String?, stack: ItemStack, position: Int): ItemStack =
            ReliquaryWindow.fireWithdraw(
                entityPlayer, InteractionHand.MAIN_HAND,
                focus(key, WITHDRAW_SLOT, SatchelItem.Hook.WITHDRAW), heldSlot, stack, position
            )

        val trigger = object : HexDisplayContainer.Trigger {
            override fun onWithdraw(
                position: Int,
                clicked: ItemStack,
                grantToInventory: Boolean
            ): HexDisplayContainer.WithdrawResult {
                val key = sealKey()
                val gathered = withdraw(key, clicked, position)
                val withdrew = !gathered.isEmpty
                if (withdrew) {
                    ReliquaryWindow.grantWithdrawal(entityPlayer, gathered, grantToInventory)
                }
                return HexDisplayContainer.WithdrawResult(
                    if (withdrew) gathered else null, runOpen(key)
                )
            }

            override fun onEvict(slot: Int, displayed: ItemStack): ItemStack =
                withdraw(sealKey(), displayed, slot)

            override fun onDeposit(slot: Int, offered: ItemStack): List<ItemStack> {
                val key = sealKey()
                val center = Vec3.atCenterOf(worldPosition)
                val entity = ItemEntity(level, center.x, center.y, center.z, offered)
                entity.setPickUpDelay(10)
                level.addFreshEntity(entity)
                ReliquaryWindow.fireDeposit(
                    entityPlayer, InteractionHand.MAIN_HAND,
                    focus(key, DEPOSIT_SLOT, SatchelItem.Hook.DEPOSIT), heldSlot, entity, slot
                )
                return runOpen(key)
            }
        }
        return ReliquaryWindow.hoardView(false, runOpen(sealKey()), trigger)
    }

    private fun bindControlSlot(widgetsById: Map<String, List<Widget>>, id: String, slot: Int, tooltipKey: String) {
        val template = MenuWidgets.firstById(widgetsById, id)
        if (template !is SlotWidget) {
            Hexwright.LOGGER.warn("container.ui is missing control slot widget '{}'", id)
            return
        }
        val isHeldSlot = id == "held"
        val guideHost = template.parent ?: return
        val authored = template.tooltipTexts

        val replacement = MenuWidgets.replaceInPlace(template) { x, y ->
            object : SlotWidget(this, slot, x, y) {
                private var guideShown = true

                override fun createSlot(inventory: Container, index: Int): Slot {
                    return object : Slot(inventory, index, 0, 0) {
                        override fun mayPlace(stack: ItemStack): Boolean = canPlaceItem(index, stack)
                        override fun getMaxStackSize(): Int = 1
                    }
                }

                override fun updateScreen() {
                    super.updateScreen()
                    if (!isHeldSlot) return
                    val empty = item.isEmpty
                    if (empty != guideShown) {
                        guideShown = empty
                        guideHost.setBackground(if (guideShown) HELD_SLOT_GUIDE_TEXTURE else HELD_SLOT_FILLED_TEXTURE)
                    }
                }
            }
        } ?: return
        replacement.setBackground(ColorRectTexture(TRANSPARENT_COLOR))
        replacement.setHoverTooltips(if (authored.isNotEmpty()) authored else listOf(Component.translatable(tooltipKey)))
    }

    private fun createFallbackUI(entityPlayer: Player): ModularUI {
        val ui = ModularUI(IMAGE_WIDTH, IMAGE_HEIGHT, this, entityPlayer)
        ui.background(PANEL_BACKGROUND)
        ui.widget(LabelWidget(8, 7, Component.translatable("block.hexwright.reliquary_mirror")))

        val slotXs = intArrayOf(SEAL_X, OPEN_X, DEPOSIT_X, WITHDRAW_X)
        val slotLabels = arrayOf(
            "gui.hexwright.satchel.held",
            SatchelItem.Hook.OPEN.translationKey(),
            SatchelItem.Hook.DEPOSIT.translationKey(),
            SatchelItem.Hook.WITHDRAW.translationKey()
        )
        val slotTooltips = arrayOf(
            "gui.hexwright.mirror.held.tooltip",
            SatchelItem.Hook.OPEN.translationKey() + ".tooltip",
            SatchelItem.Hook.DEPOSIT.translationKey() + ".tooltip",
            SatchelItem.Hook.WITHDRAW.translationKey() + ".tooltip"
        )
        for (i in 0 until CONTAINER_SIZE) {
            ui.widget(LabelWidget(slotXs[i], TOP_LABEL_Y, Component.translatable(slotLabels[i])))
            ui.widget(
                MenuWidgets.singleItemSlot(this, i, slotXs[i] - 1, SEAL_Y - 1)
                    .setBackground(ColorRectTexture(TRANSPARENT_COLOR))
                    .setHoverTooltips(Component.translatable(slotTooltips[i]))
            )
        }

        val hoard = hoardView(entityPlayer)
        ReliquaryWindow.bindHoardGrid(ui, hoard)

        fun inventorySlotWidget(slotIndex: Int, x: Int, y: Int): SlotWidget =
            SlotWidget(entityPlayer.inventory, slotIndex, x, y)

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val slotIndex = col + row * 9 + 9
                ui.widget(
                    inventorySlotWidget(slotIndex, PLAYER_INV_LEFT + col * 18 - 1, PLAYER_INV_TOP + row * 18 - 1)
                        .setLocationInfo(true, false)
                        .setBackground(ColorRectTexture(TRANSPARENT_COLOR))
                )
            }
        }
        for (col in 0 until 9) {
            ui.widget(
                inventorySlotWidget(col, PLAYER_INV_LEFT + col * 18 - 1, HOTBAR_TOP - 1)
                    .setLocationInfo(true, true)
                    .setBackground(ColorRectTexture(TRANSPARENT_COLOR))
            )
        }
        return ui
    }
}
