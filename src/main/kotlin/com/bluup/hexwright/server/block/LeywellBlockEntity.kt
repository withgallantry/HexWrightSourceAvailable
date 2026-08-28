package com.bluup.hexwright.server.block

import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory
import com.bluup.hexwright.server.crucible.EssencePouchData
import com.bluup.hexwright.server.item.EndlessPouchItem
import com.bluup.hexwright.server.network.EssenceNetwork
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BiomeTags
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class LeywellBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.LEYWELL_BLOCK_ENTITY, pos, state), Container {

    companion object {
        const val SOURCE_SLOT = 0
        const val CONTAINER_SIZE = 1

        const val WORK_INTERVAL = 600L

        const val SATURATION_RANGE = 24.0

        private val WELLS = HashMap<net.minecraft.resources.ResourceKey<Level>, MutableSet<BlockPos>>()

        fun saturationCount(level: Level, pos: BlockPos): Int {
            val set = WELLS[level.dimension()] ?: return 1
            var count = 0
            val rangeSq = SATURATION_RANGE * SATURATION_RANGE
            val iterator = set.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                if (other != pos) {
                    if (!level.isLoaded(other) || level.getBlockEntity(other) !is LeywellBlockEntity) {
                        iterator.remove()
                        continue
                    }
                }
                if (other.distSqr(pos) <= rangeSq) {
                    count++
                }
            }
            return maxOf(1, count)
        }

        fun attunement(level: Level, pos: BlockPos): LinkedHashMap<IngredientCategory, Double> {
            val yield = LinkedHashMap<IngredientCategory, Double>()
            when {
                level.dimension() == Level.NETHER -> {
                    yield[IngredientCategory.NETHER] = 1.0
                    yield[IngredientCategory.FIRE] = 0.5
                }
                level.dimension() == Level.END -> {
                    yield[IngredientCategory.END] = 1.0
                    yield[IngredientCategory.SPATIAL] = 0.5
                }
                level.getBiome(pos).`is`(Biomes.DEEP_DARK) -> {
                    yield[IngredientCategory.ECHO] = 1.0
                }
                pos.y >= 160 && level.canSeeSky(pos.above()) -> {
                    yield[IngredientCategory.RADIANT] = 1.0
                    yield[IngredientCategory.LIGHT] = 0.5
                }
                pos.y < 0 -> {
                    yield[IngredientCategory.CRYSTAL] = 0.5
                    yield[IngredientCategory.METALLIC] = 0.5
                }
                level.getBiome(pos).`is`(BiomeTags.IS_FOREST) || level.getBiome(pos).`is`(BiomeTags.IS_JUNGLE) -> {
                    yield[IngredientCategory.ORGANIC] = 1.0
                }
                level.getBiome(pos).`is`(BiomeTags.IS_OCEAN) || level.getBiome(pos).`is`(BiomeTags.IS_RIVER) -> {
                    yield[IngredientCategory.FLEXIBLE] = 0.75
                }
                else -> {
                    yield[IngredientCategory.LIGHT] = 0.5
                }
            }
            return yield
        }
    }

    private val items: NonNullList<ItemStack> = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY)

    fun dockSource(source: ItemStack): ItemStack {
        val previous = items[SOURCE_SLOT]
        items[SOURCE_SLOT] = source
        syncActiveState()
        setChanged()
        return previous
    }

    fun serverTick() {
        val level = this.level as? ServerLevel ?: return
        WELLS.getOrPut(level.dimension()) { HashSet() }.add(worldPosition.immutable())
        if (level.gameTime % WORK_INTERVAL != 0L) return
        if (level.hasNeighborSignal(worldPosition)) return
        val pouch = EssenceNetwork.resolve(items[SOURCE_SLOT], level, worldPosition)
        if (pouch.item !is EndlessPouchItem) return

        val saturation = saturationCount(level, worldPosition)
        for ((aspect, amount) in attunement(level, worldPosition)) {
            EssencePouchData.add(pouch, aspect, amount / saturation)
        }
        EssenceNetwork.pulseFlow(level, worldPosition, items[SOURCE_SLOT], true)
        setChanged()
    }

    override fun setRemoved() {
        super.setRemoved()
        val level = this.level ?: return
        WELLS[level.dimension()]?.remove(worldPosition)
    }

    private fun syncActiveState() {
        val level = this.level ?: return
        val shouldBeActive = !items[SOURCE_SLOT].isEmpty
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
        if (!result.isEmpty) {
            syncActiveState()
            setChanged()
        }
        return result
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack = ContainerHelper.takeItem(items, slot)

    override fun setItem(slot: Int, stack: ItemStack) {
        items[slot] = stack
        syncActiveState()
        setChanged()
    }

    override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)

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
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }
}
