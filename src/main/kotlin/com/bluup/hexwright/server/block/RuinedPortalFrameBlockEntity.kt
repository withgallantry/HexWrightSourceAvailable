package com.bluup.hexwright.server.block

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.Level
import com.bluup.hexwright.server.worldgen.ruinedportal.RuinedPortalManager
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import software.bernie.geckolib.animatable.GeoBlockEntity
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.core.animation.AnimatableManager
import software.bernie.geckolib.core.animation.AnimationController
import software.bernie.geckolib.core.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil

class RuinedPortalFrameBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.RUINED_PORTAL_FRAME_BLOCK_ENTITY, pos, state), GeoBlockEntity {

    companion object {
        private const val TAG_OUTCOME = "Outcome"
        private const val TAG_DIMENSION = "Dimension"
        private const val TAG_X = "X"
        private const val TAG_Y = "Y"
        private const val TAG_Z = "Z"
        private const val TAG_START_CHUNK = "StartChunk"
        private const val TAG_HAS_START_CHUNK = "HasStartChunk"

        private val POSE: RawAnimation = RawAnimation.begin().thenLoop("pose")
    }

    private val animatableCache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "pose", 0) { state ->
                state.setAndContinue(POSE)
            }
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animatableCache

    data class Binding(
        val outcome: Outcome,
        val dimension: ResourceKey<Level>,
        val pos: BlockPos,
        val startChunk: Long?
    )

    var binding: Binding? = null
        private set

    fun bind(binding: Binding) {
        this.binding = binding
        setChanged()
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        val name = tag.getString(TAG_OUTCOME)
        val outcome = Outcome.entries.firstOrNull { it.name == name } ?: return
        val dimensionId = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION)) ?: return
        val dimension = ResourceKey.create(Registries.DIMENSION, dimensionId)
        val pos = BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z))
        val startChunk = if (tag.getBoolean(TAG_HAS_START_CHUNK)) tag.getLong(TAG_START_CHUNK) else null
        this.binding = Binding(outcome, dimension, pos, startChunk)
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        val binding = this.binding ?: return
        tag.putString(TAG_OUTCOME, binding.outcome.name)
        tag.putString(TAG_DIMENSION, binding.dimension.location().toString())
        tag.putInt(TAG_X, binding.pos.x)
        tag.putInt(TAG_Y, binding.pos.y)
        tag.putInt(TAG_Z, binding.pos.z)
        tag.putBoolean(TAG_HAS_START_CHUNK, binding.startChunk != null)
        if (binding.startChunk != null) {
            tag.putLong(TAG_START_CHUNK, binding.startChunk)
        }
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()
    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    fun serverTick() {
        val level = this.level as? ServerLevel ?: return
        RuinedPortalManager.markLoaded(level.dimension(), worldPosition)
    }

    fun clientTick() {
        val level = this.level ?: return
        com.bluup.hexwright.client.block.RuinedPortalRifts.clientTick(level, worldPosition)
    }

    override fun setRemoved() {
        super.setRemoved()
        val level = this.level ?: return
        if (level.isClientSide) {
            com.bluup.hexwright.client.block.RuinedPortalRifts.removed(worldPosition)
        } else {
            RuinedPortalManager.unmark(level.dimension(), worldPosition)
        }
    }
}

enum class Outcome {
    DUNGEON, GOLEM, VAULT,
    PERIL_LAVA, PERIL_NETHER_LAVA, PERIL_DEEP_OCEAN, PERIL_DEEP_DARK,
    PERIL_HIGH_FALL, PERIL_VOID_POCKET, PERIL_MOB_DEN
}
