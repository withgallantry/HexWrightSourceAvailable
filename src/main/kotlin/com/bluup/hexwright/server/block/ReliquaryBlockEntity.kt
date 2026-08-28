package com.bluup.hexwright.server.block

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.server.menu.MenuWidgets
import com.bluup.hexwright.server.menu.UiTemplates
import com.bluup.hexwright.client.block.ManifoldVaultVisualClient
import com.bluup.hexwright.server.reliquary.BulkSlotWidget
import com.bluup.hexwright.server.reliquary.ReliquaryStore
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder
import com.lowdragmc.lowdraglib.gui.modular.ModularUI
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.Containers
import net.minecraft.world.ContainerHelper
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class ReliquaryBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.RELIQUARY_BLOCK_ENTITY, pos, state), WorldlyContainer, IUIHolder.BlockEntityUI {

    companion object {
        const val IMAGE_WIDTH = 176
        const val IMAGE_HEIGHT = 252

        const val GRID_LEFT = 8
        const val GRID_TOP = 54
        const val PLAYER_INV_LEFT = 8
        const val PLAYER_INV_TOP = 172
        const val HOTBAR_TOP = PLAYER_INV_TOP + 3 * 18 + 4

        private const val PANEL_COLOR = 0xFF1B1F26.toInt()
        private const val PANEL_BORDER_COLOR = 0xFF45505C.toInt()
        private const val TRANSPARENT_COLOR = 0x00000000

        private val PANEL_BACKGROUND = GuiTextureGroup(ColorRectTexture(PANEL_COLOR), ColorRectTexture(PANEL_BORDER_COLOR))

        private val ALL_SLOTS = IntArray(ReliquaryStore.SLOTS) { it }

    }

    private var clientCache: NonNullList<ItemStack> = NonNullList.withSize(ReliquaryStore.SLOTS, ItemStack.EMPTY)

    private fun storeKey(): String {
        val level = this.level
        val dimension = level?.dimension() ?: Level.OVERWORLD
        return ReliquaryStore.key(dimension, worldPosition)
    }

    private fun items(): NonNullList<ItemStack> {
        val level = this.level
        if (level is ServerLevel) {
            return ReliquaryStore.get(level.server).inventory(storeKey())
        }
        return clientCache
    }

    fun dropAllOnBreak() {
        val level = this.level as? ServerLevel ?: return
        val contents = ReliquaryStore.get(level.server).remove(storeKey())
        for (stack in contents) {
            if (!stack.isEmpty) {
                Containers.dropItemStack(level, worldPosition.x + 0.5, worldPosition.y + 0.5, worldPosition.z + 0.5, stack)
            }
        }
    }


    override fun getContainerSize(): Int = ReliquaryStore.SLOTS
    override fun isEmpty(): Boolean = items().all { it.isEmpty }
    override fun getItem(slot: Int): ItemStack = items()[slot]

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        val result = ContainerHelper.removeItem(items(), slot, amount)
        if (!result.isEmpty) setChanged()
        return result
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack = ContainerHelper.takeItem(items(), slot)

    override fun setItem(slot: Int, stack: ItemStack) {
        items()[slot] = stack
        if (stack.count > maxStackSize) stack.count = maxStackSize
        setChanged()
    }

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = true

    override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)

    override fun clearContent() {
        val list = items()
        for (i in list.indices) list[i] = ItemStack.EMPTY
        setChanged()
    }

    override fun getMaxStackSize(): Int = ReliquaryStore.SLOT_CAP

    override fun getSlotsForFace(side: Direction): IntArray = ALL_SLOTS
    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, side: Direction?): Boolean = true
    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, side: Direction): Boolean = true


    override fun load(tag: CompoundTag) {
        super.load(tag)
        val snapshot = NonNullList.withSize(ReliquaryStore.SLOTS, ItemStack.EMPTY)
        if (tag.contains("BigItems")) {
            ReliquaryStore.loadList(tag, snapshot)
        } else {
            ContainerHelper.loadAllItems(tag, snapshot)
        }
        clientCache = snapshot
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        tag.merge(ReliquaryStore.saveList(items()))
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()
    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun setChanged() {
        super.setChanged()
        val level = this.level
        if (level is ServerLevel) {
            ReliquaryStore.get(level.server).touch()
        }
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    fun clientTick() {
        val level = this.level ?: return
        val active = blockState.block == HexwrightBlocks.MANIFOLD_VAULT_BLOCK && !isEmpty
        ManifoldVaultVisualClient.setActive(level, worldPosition, active)
    }

    override fun setRemoved() {
        val lvl = level
        if (lvl != null && lvl.isClientSide) {
            ManifoldVaultVisualClient.setActive(lvl, worldPosition, false)
        }
        super.setRemoved()
    }


    override fun createUI(entityPlayer: Player): ModularUI {
        if (blockState.block == HexwrightBlocks.MANIFOLD_VAULT_BLOCK) {
            val root = UiTemplates.load("vault")?.get()
            if (root != null) {
                val widgetsById = MenuWidgets.indexById(root)
                MenuWidgets.bindContainerSlots(widgetsById, this, ReliquaryStore.SLOTS) { container, index, x, y ->
                    BulkSlotWidget(container, index, x, y, true, null)
                }
                MenuWidgets.bindPlayerInventory(widgetsById, entityPlayer)
                return ModularUI(root, this, entityPlayer)
            }
            Hexwright.LOGGER.error("Failed to load vault.ui project; falling back to minimal Manifold Vault UI")
        }
        return createFallbackUI(entityPlayer)
    }

    private fun createFallbackUI(entityPlayer: Player): ModularUI {
        val ui = ModularUI(IMAGE_WIDTH, IMAGE_HEIGHT, this, entityPlayer)
        ui.background(PANEL_BACKGROUND)
        ui.widget(LabelWidget(8, 7, Component.translatable("block.hexwright.reliquary")))

        for (row in 0 until 6) {
            for (col in 0 until 9) {
                ui.widget(
                    BulkSlotWidget(
                        this, col + row * 9, GRID_LEFT + col * 18 - 1, GRID_TOP + row * 18 - 1,
                        true, null
                    ).setBackground(ColorRectTexture(TRANSPARENT_COLOR))
                )
            }
        }
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val slotIndex = col + row * 9 + 9
                ui.widget(
                    SlotWidget(entityPlayer.inventory, slotIndex, PLAYER_INV_LEFT + col * 18 - 1, PLAYER_INV_TOP + row * 18 - 1)
                        .setLocationInfo(true, false)
                        .setBackground(ColorRectTexture(TRANSPARENT_COLOR))
                )
            }
        }
        for (col in 0 until 9) {
            ui.widget(
                SlotWidget(entityPlayer.inventory, col, PLAYER_INV_LEFT + col * 18 - 1, HOTBAR_TOP - 1)
                    .setLocationInfo(true, true)
                    .setBackground(ColorRectTexture(TRANSPARENT_COLOR))
            )
        }
        return ui
    }
}
