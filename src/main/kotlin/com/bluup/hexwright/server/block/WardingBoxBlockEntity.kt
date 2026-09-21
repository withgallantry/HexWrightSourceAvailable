package com.bluup.hexwright.server.block

import com.bluup.hexwright.server.hexpatterns.PerWorldPatterns
import com.bluup.hexwright.server.hexpatterns.StoredHex
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.xplat.IXplatAbstractions
import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.HexwrightDebug
import com.bluup.hexwright.server.menu.MenuWidgets
import com.bluup.hexwright.server.menu.UiTemplates
import com.bluup.hexwright.client.wardingbox.WardingBoxVisualClient
import com.bluup.hexwright.inits.HexwrightNetworking
import com.bluup.hexwright.server.pocketcaster.PocketCasterData
import com.bluup.hexwright.server.wardingbox.WardingBoxCasting
import com.bluup.hexwright.server.wardingbox.WardingBoxData
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder
import com.lowdragmc.lowdraglib.gui.modular.ModularUI
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib.gui.texture.TextTexture
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget
import com.lowdragmc.lowdraglib.gui.widget.SwitchWidget
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget
import com.lowdragmc.lowdraglib.gui.widget.Widget
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.DiodeBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import java.util.UUID

class WardingBoxBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.WARDING_BOX_BLOCK_ENTITY, pos, state), IUIHolder.BlockEntityUI {

    enum class TriggerMode(val labelKey: String) {
        ALL("gui.hexwright.warding_box.mode.all"),
        PLAYERS("gui.hexwright.warding_box.mode.players"),
        NON_PLAYERS("gui.hexwright.warding_box.mode.non_players"),

        ITEMS("gui.hexwright.warding_box.mode.items");

        fun next(): TriggerMode = entries[(ordinal + 1) % entries.size]
        fun previous(): TriggerMode = entries[(ordinal - 1 + entries.size) % entries.size]
    }

    enum class TargetKind { NONE, ENTITY, ITEM }

    companion object {
        const val MIN_WIDTH = 1
        const val DEFAULT_WIDTH = 5

        const val WIDTH_STEP = 2
        const val MIN_HEIGHT = 1
        const val MAX_HEIGHT = 10
        const val DEFAULT_HEIGHT = 3
        const val COOLDOWN_TICKS = 15L

        fun maxWidthFor(quality: PocketCasterData.Quality): Int = when (quality) {
            PocketCasterData.Quality.CRUDE -> 7
            PocketCasterData.Quality.SOUND -> 9
            PocketCasterData.Quality.FINE -> 13
            PocketCasterData.Quality.EXQUISITE -> 19
            PocketCasterData.Quality.MASTERWORK -> 25
        }

        fun clampWidth(value: Int, quality: PocketCasterData.Quality): Int {
            val odd = value - (value - MIN_WIDTH).mod(WIDTH_STEP)
            return odd.coerceIn(MIN_WIDTH, maxWidthFor(quality))
        }

        private const val TOGGLE_ID = "active_toggle"

        const val IMAGE_WIDTH = 200
        const val IMAGE_HEIGHT = 234

        const val PANEL_X = 12
        const val PANEL_Y = 20
        const val PANEL_WIDTH = 176
        const val PANEL_HEIGHT = 84

        const val MODE_BUTTON_X = 12
        const val MODE_BUTTON_Y = 110
        const val MODE_BUTTON_WIDTH = 74
        const val RADIUS_DOWN_X = 94
        const val RADIUS_UP_X = 114
        const val RADIUS_BUTTON_Y = 110
        const val RADIUS_BUTTON_WIDTH = 16
        const val BUTTON_HEIGHT = 16

        const val HEIGHT_LABEL_X = 12
        const val HEIGHT_LABEL_Y = 134
        const val HEIGHT_DOWN_X = 94
        const val HEIGHT_UP_X = 114
        const val HEIGHT_BUTTON_Y = 130

        const val PLAYER_INV_LEFT = 19
        const val PLAYER_INV_TOP = 152
        const val HOTBAR_TOP = PLAYER_INV_TOP + 3 * 18 + 4

        private const val PANEL_COLOR = 0xFF1B1F26.toInt()
        private const val PANEL_BORDER_COLOR = 0xFF45505C.toInt()
        private const val BUTTON_COLOR = 0xFF2E3A4C.toInt()
        private const val TEXT_COLOR = 0xC8D7FF.toInt()
        private const val TEXT_DIM_COLOR = 0xFF8895AA.toInt()
        private const val BAR_BG_COLOR = 0xFF0E1014.toInt()
        private const val MEDIA_COLOR = 0xFF7FB8DB.toInt()
        private const val ARMED_COLOR = 0xFF9BD87A.toInt()
        private const val WARN_COLOR = 0xFFE0B36B.toInt()
        private const val SPELL_COLOR = 0xFFB77FDB.toInt()
        private const val TRANSPARENT_COLOR = 0x00000000

        private val PANEL_BACKGROUND = GuiTextureGroup(ColorRectTexture(PANEL_COLOR), ColorRectTexture(PANEL_BORDER_COLOR))
    }


    var quality: PocketCasterData.Quality = PocketCasterData.Quality.CRUDE
        private set

    var media = 0L
        private set

    private var spellTag: CompoundTag? = null

    var spellSize = 0
        private set

    private var active = true

    private var mode = TriggerMode.ALL

    private var targetKind = TargetKind.NONE

    private var targetId = ""

    var width = DEFAULT_WIDTH
        private set

    var height = DEFAULT_HEIGHT
        private set

    private var dungeonTrap = false

    private var trapSpellChecked = false

    private var ownerId: UUID? = null
    private var ownerName = ""

    private var pigmentTag: CompoundTag? = null

    private var makerTag: CompoundTag? = null

    private var lastTrigger = ""

    private var lastMessage = ""

    private var displayMsg: Component? = null
    private var displayItem: ItemStack? = null


    private val insideLastTick = HashSet<UUID>()
    private var cooldownUntil = 0L


    override fun clearRemoved() {
        super.clearRemoved()
        if (level?.isClientSide == true) {
            WardingBoxVisualClient.track(this)
        }
    }

    override fun setRemoved() {
        if (level?.isClientSide == true) {
            WardingBoxVisualClient.untrack(this)
        }
        super.setRemoved()
    }


    override fun load(tag: CompoundTag) {
        super.load(tag)
        quality = runCatching { PocketCasterData.Quality.valueOf(tag.getString("Quality")) }
            .getOrDefault(PocketCasterData.Quality.CRUDE)
        media = tag.getLong("Media")
        spellTag = if (tag.contains("Spell")) tag.getCompound("Spell") else null
        spellSize = tag.getInt("SpellSize")
        active = !tag.contains("Active") || tag.getBoolean("Active")
        mode = TriggerMode.entries.getOrNull(tag.getInt("Mode")) ?: TriggerMode.ALL
        targetKind = TargetKind.entries.getOrNull(tag.getInt("TargetKind")) ?: TargetKind.NONE
        targetId = tag.getString("TargetId")
        if (targetId.isEmpty()) targetKind = TargetKind.NONE
        dungeonTrap = tag.getBoolean("DungeonTrap")
        width = clampWidth(
            when {
                tag.contains("Width") -> tag.getInt("Width")
                tag.contains("Radius") -> tag.getInt("Radius") * 2 + 1
                else -> DEFAULT_WIDTH
            },
            quality
        )
        height = if (tag.contains("Height")) tag.getInt("Height").coerceIn(MIN_HEIGHT, MAX_HEIGHT) else DEFAULT_HEIGHT
        ownerId = if (tag.hasUUID("Owner")) tag.getUUID("Owner") else null
        ownerName = tag.getString("OwnerName")
        pigmentTag = if (tag.contains("Pigment")) tag.getCompound("Pigment") else null
        makerTag = if (tag.contains("MakersMark")) tag.getCompound("MakersMark") else null
        lastTrigger = tag.getString("LastTrigger")
        lastMessage = tag.getString("LastMessage")
        if (tag.contains("DisplayMsg") && tag.contains("DisplayItem")) {
            displayMsg = Component.Serializer.fromJson(tag.getString("DisplayMsg"))
            displayItem = ItemStack.of(tag.getCompound("DisplayItem"))
            if (displayMsg == null) {
                displayItem = null
            }
        } else {
            displayMsg = null
            displayItem = null
        }
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        tag.putString("Quality", quality.name)
        tag.putLong("Media", media)
        spellTag?.let { tag.put("Spell", it) }
        tag.putInt("SpellSize", spellSize)
        tag.putBoolean("Active", active)
        tag.putInt("Mode", mode.ordinal)
        tag.putInt("TargetKind", targetKind.ordinal)
        tag.putString("TargetId", targetId)
        if (dungeonTrap) tag.putBoolean("DungeonTrap", true)
        tag.putInt("Width", width)
        tag.putInt("Height", height)
        ownerId?.let { tag.putUUID("Owner", it) }
        tag.putString("OwnerName", ownerName)
        pigmentTag?.let { tag.put("Pigment", it) }
        makerTag?.let { tag.put("MakersMark", it) }
        tag.putString("LastTrigger", lastTrigger)
        tag.putString("LastMessage", lastMessage)
        val msg = displayMsg
        val icon = displayItem
        if (msg != null && icon != null) {
            tag.putString("DisplayMsg", Component.Serializer.toJson(msg))
            tag.put("DisplayItem", icon.save(CompoundTag()))
        }
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun setChanged() {
        super.setChanged()
        val level = this.level ?: return
        level.sendBlockUpdated(worldPosition, blockState, blockState, 3)
        if (!level.isClientSide) {
            level.updateNeighbourForOutputSignal(worldPosition, blockState.block)
        }
    }


    fun initialize(placer: LivingEntity?, stack: ItemStack) {
        WardingBoxData.getQuality(stack).ifPresent { quality = it }
        media = WardingBoxData.getMedia(stack).coerceIn(0L, capacity())
        spellTag = WardingBoxData.getSpellTag(stack)?.copy()
        spellSize = WardingBoxData.getSpellSize(stack)
        makerTag = stack.getTagElement("hexwright_makers_mark")?.copy()
        val player = placer as? ServerPlayer
        if (player != null) {
            ownerId = player.uuid
            ownerName = player.gameProfile.name
        }
        setChanged()
    }

    fun installDungeonTrap(
        grade: PocketCasterData.Quality,
        charge: Long,
        spell: CompoundTag,
        patterns: Int,
        wardWidth: Int,
        wardHeight: Int
    ) {
        quality = grade
        media = charge.coerceIn(0L, capacity())
        spellTag = spell.copy()
        spellSize = patterns
        mode = TriggerMode.PLAYERS
        active = true
        dungeonTrap = true
        resizeVolume(wardWidth, wardHeight)
        setChanged()
    }

    fun isDungeonTrap(): Boolean = dungeonTrap

    private fun recutTrapSpell(serverLevel: ServerLevel) {
        val tag = spellTag ?: return
        val iota = try {
            IotaType.deserialize(tag, serverLevel)
        } catch (e: RuntimeException) {
            return
        }
        val hex = StoredHex.decode(iota) ?: return
        val recut = PerWorldPatterns.rescramble(hex, serverLevel) ?: return
        spellTag = IotaType.serialize(ListIota(recut))
        spellSize = recut.size
        HexwrightDebug.log(
            HexwrightDebug.CONTENT,
            "Re-cut the Great Spell inscribed in the dungeon trap at {} to this world's drawing",
            worldPosition
        )
        setChanged()
    }

    fun toItemStack(): ItemStack {
        val stack = WardingBoxData.create(quality)
        WardingBoxData.saveToItem(stack, quality, media, spellTag?.copy(), spellSize)
        makerTag?.let { stack.orCreateTag.put("hexwright_makers_mark", it.copy()) }
        return stack
    }


    fun getSpellTag(): CompoundTag? = spellTag

    fun capacity(): Long = WardingBoxData.capacityFor(quality)

    fun cooldownTicks(): Long = COOLDOWN_TICKS

    fun writeSpell(iota: Iota) {
        spellTag = IotaType.serialize(iota)
        spellSize = StoredHex.decode(iota)?.size ?: 0
        dungeonTrap = false
        lastMessage = ""
        displayMsg = null
        displayItem = null
        setChanged()
    }

    fun ownerIfOnline(level: ServerLevel): ServerPlayer? =
        ownerId?.let { level.server.playerList.getPlayer(it) }

    fun payMedia(cost: Long, simulate: Boolean): Long {
        val paid = minOf(cost, media)
        if (!simulate && paid > 0) {
            media -= paid
            setChanged()
        }
        return paid
    }

    fun tryChargeFrom(stack: ItemStack, player: ServerPlayer): Boolean {
        val holder = IXplatAbstractions.INSTANCE.findMediaHolder(stack) ?: return false
        if (!holder.canProvide()) return false

        val space = capacity() - media
        if (space <= 0L) {
            player.displayClientMessage(
                Component.translatable("message.hexwright.warding_box.full").withStyle(ChatFormatting.YELLOW), true
            )
            return true
        }

        val want = minOf(space, holder.media)
        if (want <= 0L) return false

        val got = holder.withdrawMedia(want, false)
        if (got <= 0L) return false

        media += got
        setChanged()
        level?.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7f, 1.1f)
        player.displayClientMessage(
            Component.translatable(
                "message.hexwright.warding_box.charged", formatDust(media), formatDust(capacity())
            ).withStyle(ChatFormatting.AQUA),
            true
        )
        return true
    }

    fun drinkAreaMedia(level: ServerLevel): Long {
        var absorbed = 0L
        val mediaItems = level.getEntitiesOfClass(ItemEntity::class.java, wardedArea()) { it.isAlive }
        for (entity in mediaItems) {
            val space = capacity() - media
            if (space <= 0L) break

            val stack = entity.item.copy()
            val holder = IXplatAbstractions.INSTANCE.findMediaHolder(stack) ?: continue
            if (!holder.canProvide()) continue

            val want = minOf(space, holder.media)
            if (want <= 0L) continue
            val got = holder.withdrawMedia(want, false)
            if (got <= 0L) continue

            media += got
            absorbed += got
            entity.item = stack
            if (stack.isEmpty) {
                entity.discard()
            }
        }
        if (absorbed > 0) {
            setChanged()
        }
        return absorbed
    }

    fun getPigment(): FrozenPigment {
        val tag = pigmentTag ?: return FrozenPigment.DEFAULT.get()
        return FrozenPigment.fromNBT(tag)
    }

    fun setPigmentFromHex(pigment: FrozenPigment?): FrozenPigment? {
        pigmentTag = pigment?.serializeToNBT()
        setChanged()
        return pigment
    }

    fun postMessage(message: Component) {
        lastMessage = message.string
        val serverLevel = level as? ServerLevel
        if (serverLevel != null) {
            ownerIfOnline(serverLevel)?.displayClientMessage(message, false)
        }
        postDisplay(message, ItemStack(Items.BOOK))
    }


    fun getDisplayMsg(): Component? = displayMsg

    fun getDisplayItem(): ItemStack? = displayItem

    fun postDisplay(message: Component, icon: ItemStack) {
        displayMsg = message
        displayItem = icon
        setChanged()
    }

    fun postMishap(message: Component) {
        postDisplay(message, ItemStack(Items.MUSIC_DISC_11))
    }

    fun clearDisplay() {
        if (displayMsg == null && displayItem == null) return
        displayMsg = null
        displayItem = null
        setChanged()
    }

    fun formatDust(amount: Long): String {
        val tenths = amount * 10 / MediaConstants.DUST_UNIT
        return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }

    fun mediaLine(): Component = Component.translatable(
        "gui.hexwright.warding_box.media", formatDust(media), formatDust(capacity())
    )

    fun statusLine(): Component = when {
        !active ->
            Component.translatable("gui.hexwright.warding_box.status.off").withStyle(ChatFormatting.GRAY)
        spellTag == null ->
            Component.translatable("gui.hexwright.warding_box.status.no_spell").withStyle(ChatFormatting.GOLD)
        media <= 0L ->
            Component.translatable("gui.hexwright.warding_box.status.no_media").withStyle(ChatFormatting.GOLD)
        else ->
            Component.translatable("gui.hexwright.warding_box.status.armed").withStyle(ChatFormatting.GREEN)
    }

    fun triggerName(): Component = targetName() ?: Component.translatable(mode.labelKey)


    fun isArmed(): Boolean = active && media > 0 && spellTag != null

    fun isActive(): Boolean = active

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        insideLastTick.clear()
        setChanged()
    }

    private fun redstoneSignal(serverLevel: ServerLevel): Boolean? {
        var connected = false
        var powered = false
        for (direction in Direction.values()) {
            val neighborPos = worldPosition.relative(direction)
            val neighborState = serverLevel.getBlockState(neighborPos)
            if (!neighborState.isSignalSource) continue
            if (neighborState.block is DiodeBlock &&
                neighborState.getValue(HorizontalDirectionalBlock.FACING) != direction.opposite
            ) {
                continue
            }
            connected = true
            if (serverLevel.getSignal(neighborPos, direction) > 0) {
                powered = true
            }
        }
        return if (connected) powered else null
    }

    fun wardedArea(): AABB {
        val cx = worldPosition.x + 0.5
        val cz = worldPosition.z + 0.5
        val floor = worldPosition.y.toDouble()
        val half = width / 2.0
        return AABB(cx - half, floor, cz - half, cx + half, floor + height, cz + half)
    }

    fun serverTick() {
        val serverLevel = level as? ServerLevel ?: return
        if (dungeonTrap && !trapSpellChecked) {
            trapSpellChecked = true
            recutTrapSpell(serverLevel)
        }
        redstoneSignal(serverLevel)?.let { powered ->
            if (active != powered) {
                active = powered
                insideLastTick.clear()
                setChanged()
            }
        }
        val armed = isArmed()
        if (!armed) {
            insideLastTick.clear()
            syncBlockState(serverLevel, false)
            return
        }

        val present = serverLevel.getEntitiesOfClass(Entity::class.java, wardedArea()) { it.isAlive && matchesTrigger(it) }

        val remembered = HashSet<UUID>(present.size * 2)
        var canCast = serverLevel.gameTime >= cooldownUntil
        for (entity in present) {
            if (entity.uuid in insideLastTick) {
                remembered.add(entity.uuid)
                continue
            }
            if (!canCast) {
                continue
            }

            canCast = false
            cooldownUntil = serverLevel.gameTime + cooldownTicks()
            remembered.add(entity.uuid)

            if (WardingBoxCasting.cast(serverLevel, this, entity)) {
                lastTrigger = entity.displayName.string
                serverLevel.playSound(
                    null, worldPosition,
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.4f
                )
                serverLevel.sendParticles(
                    ParticleTypes.ENCHANT,
                    worldPosition.x + 0.5, worldPosition.y + 1.2, worldPosition.z + 0.5,
                    16, width * 0.175, 0.5, width * 0.175, 0.0
                )
                HexwrightNetworking.sendWardTrigger(serverLevel, worldPosition, width, height)
                setChanged()
            }
        }
        insideLastTick.clear()
        insideLastTick.addAll(remembered)
        syncBlockState(serverLevel, true)
    }

    private fun matchesTrigger(entity: Entity): Boolean = when (targetKind) {
        TargetKind.ENTITY -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString() == targetId
        TargetKind.ITEM ->
            entity is ItemEntity && BuiltInRegistries.ITEM.getKey(entity.item.item).toString() == targetId
        TargetKind.NONE -> when (mode) {
            TriggerMode.ALL -> true
            TriggerMode.PLAYERS -> entity is Player
            TriggerMode.NON_PLAYERS -> entity !is Player
            TriggerMode.ITEMS -> entity is ItemEntity
        }
    }


    fun attuneToEntityType(type: EntityType<*>) {
        attune(TargetKind.ENTITY, BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())
    }

    fun attuneToItem(item: Item) {
        attune(TargetKind.ITEM, BuiltInRegistries.ITEM.getKey(item).toString())
    }

    private fun attune(kind: TargetKind, id: String) {
        targetKind = kind
        targetId = id
        insideLastTick.clear()
        setChanged()
    }

    fun targetName(): Component? {
        if (targetKind == TargetKind.NONE) {
            return null
        }
        val id = ResourceLocation.tryParse(targetId)
        val known = if (id == null) null else when (targetKind) {
            TargetKind.ENTITY -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).map { it.description }.orElse(null)
            TargetKind.ITEM -> BuiltInRegistries.ITEM.getOptional(id).map { ItemStack(it).hoverName }.orElse(null)
            TargetKind.NONE -> null
        }
        return known ?: Component.literal(targetId)
    }

    fun triggerLabel(): String {
        if (targetKind == TargetKind.NONE) {
            return mode.labelKey
        }
        val id = ResourceLocation.tryParse(targetId)
        val key = if (id == null) null else when (targetKind) {
            TargetKind.ENTITY -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).map { it.descriptionId }.orElse(null)
            TargetKind.ITEM -> BuiltInRegistries.ITEM.getOptional(id).map { it.descriptionId }.orElse(null)
            TargetKind.NONE -> null
        }
        return key ?: targetId
    }

    private fun syncBlockState(serverLevel: ServerLevel, armed: Boolean) {
        val state = blockState
        var next = state
        if (state.hasProperty(HexwrightBlockStates.ACTIVE)) {
            next = next.setValue(HexwrightBlockStates.ACTIVE, armed)
        }
        if (state.hasProperty(BlockStateProperties.TRIGGERED)) {
            next = next.setValue(BlockStateProperties.TRIGGERED, serverLevel.gameTime < cooldownUntil)
        }
        if (next != state) {
            serverLevel.setBlock(worldPosition, next, 3)
        }
    }

    fun mediaSignal(): Int {
        val capacity = capacity()
        if (media <= 0L || capacity <= 0L) return 0
        return Mth.floor(media.toDouble() / capacity * 14.0) + 1
    }

    private fun cycleMode(forward: Boolean) {
        if (targetKind != TargetKind.NONE) {
            targetKind = TargetKind.NONE
            targetId = ""
        } else {
            mode = if (forward) mode.next() else mode.previous()
        }
        insideLastTick.clear()
        setChanged()
    }

    private fun changeWidth(steps: Int) = resizeVolume(width + steps * WIDTH_STEP, height)

    private fun changeHeight(delta: Int) = resizeVolume(width, height + delta)

    fun maxWidth(): Int = maxWidthFor(quality)

    fun resizeVolume(newWidth: Int, newHeight: Int) {
        val nextWidth = clampWidth(newWidth, quality)
        val nextHeight = newHeight.coerceIn(MIN_HEIGHT, MAX_HEIGHT)
        if (nextWidth == width && nextHeight == height) return

        width = nextWidth
        height = nextHeight
        insideLastTick.clear()
        setChanged()
    }


    override fun createUI(entityPlayer: Player): ModularUI {
        val root = UiTemplates.load("hex_ward")?.get()
        if (root == null) {
            Hexwright.LOGGER.error("Failed to load hex_ward.ui; falling back to a minimal Warding Box UI")
            return createFallbackUI(entityPlayer)
        }
        bindWidgets(root, entityPlayer)
        return ModularUI(root, this, entityPlayer)
    }

    private fun bindWidgets(root: WidgetGroup, entityPlayer: Player) {
        val widgetsById = MenuWidgets.indexById(root)

        bindStepper(widgetsById, "increase_height", entityPlayer) { changeHeight(1) }
        bindStepper(widgetsById, "decrease_height", entityPlayer) { changeHeight(-1) }
        bindStepper(widgetsById, "increase_width", entityPlayer) { changeWidth(1) }
        bindStepper(widgetsById, "decrease_width", entityPlayer) { changeWidth(-1) }
        bindStepper(widgetsById, "right_trigger", entityPlayer) { cycleMode(true) }
        bindStepper(widgetsById, "left_trigger", entityPlayer) { cycleMode(false) }

        bindValueLabel(widgetsById, "height_value") { height.toString() }
        bindValueLabel(widgetsById, "width_value") { width.toString() }
        bindValueLabel(widgetsById, "trigger_value") { triggerLabel() }
        bindValueLabel(widgetsById, "media") { "${formatDust(media)} / ${formatDust(capacity())} dust" }

        bindToggle(widgetsById, entityPlayer)
    }

    private fun bindToggle(widgetsById: Map<String, List<Widget>>, entityPlayer: Player) {
        val toggle = MenuWidgets.firstById(widgetsById, TOGGLE_ID) as? SwitchWidget
        if (toggle == null) {
            Hexwright.LOGGER.warn("hex_ward.ui is missing the '{}' switch", TOGGLE_ID)
            return
        }
        toggle.setSupplier { isActive() }
        toggle.setOnPressCallback { _, pressed ->
            if (entityPlayer is ServerPlayer && level?.isClientSide == false) {
                setActive(pressed)
            }
        }
        toggle.setHoverTooltips(Component.translatable("gui.hexwright.warding_box.active.tooltip"))
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
            else -> Hexwright.LOGGER.warn("hex_ward.ui is missing value label '{}'", id)
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
            Hexwright.LOGGER.warn("hex_ward.ui is missing button widget '{}'", id)
            return
        }
        val click = ButtonWidget(0, 0, group.sizeWidth, group.sizeHeight) { _ ->
            val serverPlayer = entityPlayer as? ServerPlayer
            if (serverPlayer != null && level?.isClientSide == false) {
                action()
            }
        }
        group.addWidget(click)
    }

    private fun createFallbackUI(entityPlayer: Player): ModularUI {
        val ui = ModularUI(IMAGE_WIDTH, IMAGE_HEIGHT, this, entityPlayer)
        ui.background(PANEL_BACKGROUND)

        ui.widget(LabelWidget(12, 8, Component.translatable("block.hexwright.warding_box")))

        ui.widget(StatusPanelWidget(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT))

        ui.widget(
            ButtonWidget(
                MODE_BUTTON_X, MODE_BUTTON_Y, MODE_BUTTON_WIDTH, BUTTON_HEIGHT,
                GuiTextureGroup(ColorRectTexture(BUTTON_COLOR), TextTexture("gui.hexwright.warding_box.mode"))
            ) { _ ->
                val serverPlayer = entityPlayer as? ServerPlayer
                if (serverPlayer != null && level?.isClientSide == false) {
                    cycleMode(true)
                }
            }
                .setHoverBorderTexture(1, 0xB0D8C486.toInt())
                .setHoverTooltips(Component.translatable("gui.hexwright.warding_box.mode.tooltip"))
        )

        ui.widget(
            ButtonWidget(
                RADIUS_DOWN_X, RADIUS_BUTTON_Y, RADIUS_BUTTON_WIDTH, BUTTON_HEIGHT,
                GuiTextureGroup(ColorRectTexture(BUTTON_COLOR), TextTexture("-"))
            ) { _ ->
                val serverPlayer = entityPlayer as? ServerPlayer
                if (serverPlayer != null && level?.isClientSide == false) {
                    changeWidth(-1)
                }
            }
                .setHoverBorderTexture(1, 0xB0D8C486.toInt())
                .setHoverTooltips(Component.translatable("gui.hexwright.warding_box.radius.tooltip"))
        )
        ui.widget(
            ButtonWidget(
                RADIUS_UP_X, RADIUS_BUTTON_Y, RADIUS_BUTTON_WIDTH, BUTTON_HEIGHT,
                GuiTextureGroup(ColorRectTexture(BUTTON_COLOR), TextTexture("+"))
            ) { _ ->
                val serverPlayer = entityPlayer as? ServerPlayer
                if (serverPlayer != null && level?.isClientSide == false) {
                    changeWidth(1)
                }
            }
                .setHoverBorderTexture(1, 0xB0D8C486.toInt())
                .setHoverTooltips(Component.translatable("gui.hexwright.warding_box.radius.tooltip"))
        )

        ui.widget(LabelWidget(HEIGHT_LABEL_X, HEIGHT_LABEL_Y, Component.translatable("gui.hexwright.warding_box.height")))
        ui.widget(
            ButtonWidget(
                HEIGHT_DOWN_X, HEIGHT_BUTTON_Y, RADIUS_BUTTON_WIDTH, BUTTON_HEIGHT,
                GuiTextureGroup(ColorRectTexture(BUTTON_COLOR), TextTexture("-"))
            ) { _ ->
                val serverPlayer = entityPlayer as? ServerPlayer
                if (serverPlayer != null && level?.isClientSide == false) {
                    changeHeight(-1)
                }
            }
                .setHoverBorderTexture(1, 0xB0D8C486.toInt())
                .setHoverTooltips(Component.translatable("gui.hexwright.warding_box.height.tooltip"))
        )
        ui.widget(
            ButtonWidget(
                HEIGHT_UP_X, HEIGHT_BUTTON_Y, RADIUS_BUTTON_WIDTH, BUTTON_HEIGHT,
                GuiTextureGroup(ColorRectTexture(BUTTON_COLOR), TextTexture("+"))
            ) { _ ->
                val serverPlayer = entityPlayer as? ServerPlayer
                if (serverPlayer != null && level?.isClientSide == false) {
                    changeHeight(1)
                }
            }
                .setHoverBorderTexture(1, 0xB0D8C486.toInt())
                .setHoverTooltips(Component.translatable("gui.hexwright.warding_box.height.tooltip"))
        )

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val slotIndex = col + row * 9 + 9
                ui.widget(
                    SlotWidget(
                        entityPlayer.inventory, slotIndex,
                        PLAYER_INV_LEFT + col * 18 - 1, PLAYER_INV_TOP + row * 18 - 1
                    )
                        .setLocationInfo(true, false)
                        .setBackground(ColorRectTexture(TRANSPARENT_COLOR))
                )
            }
        }
        for (col in 0 until 9) {
            ui.widget(
                SlotWidget(
                    entityPlayer.inventory, col,
                    PLAYER_INV_LEFT + col * 18 - 1, HOTBAR_TOP - 1
                )
                    .setLocationInfo(true, true)
                    .setBackground(ColorRectTexture(TRANSPARENT_COLOR))
            )
        }

        return ui
    }

    private inner class StatusPanelWidget(x: Int, y: Int, width: Int, height: Int) : Widget(x, y, width, height) {

        override fun drawInBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks)
            val pos = position
            val size = this.size

            graphics.fill(pos.x - 2, pos.y - 2, pos.x + size.width + 2, pos.y + size.height + 2, PANEL_BORDER_COLOR)
            graphics.fill(pos.x - 1, pos.y - 1, pos.x + size.width + 1, pos.y + size.height + 1, BAR_BG_COLOR)

            val font = Minecraft.getInstance().font
            val left = pos.x + 5
            var y = pos.y + 4

            val gradeName = Component.translatable(quality.translationKey()).string
            val header = if (ownerName.isEmpty()) {
                Component.translatable("gui.hexwright.warding_box.grade", gradeName).string
            } else {
                Component.translatable("gui.hexwright.warding_box.grade_owner", gradeName, ownerName).string
            }
            graphics.drawString(font, header, left, y, TEXT_COLOR, false)
            y += font.lineHeight + 3

            val barWidth = 80
            val barHeight = 6
            graphics.fill(left, y, left + barWidth, y + barHeight, PANEL_COLOR)
            val cap = capacity()
            val filled = if (cap <= 0L) 0 else ((media.toDouble() / cap) * barWidth).toInt()
            if (filled > 0) {
                graphics.fill(left, y, left + filled.coerceAtMost(barWidth), y + barHeight, MEDIA_COLOR)
            }
            val mediaText = Component.translatable(
                "gui.hexwright.warding_box.media", formatDust(media), formatDust(cap)
            ).string
            graphics.drawString(font, mediaText, left + barWidth + 5, y - 1, TEXT_COLOR, false)
            y += barHeight + 5

            val spellText = if (spellTag == null) {
                Component.translatable("gui.hexwright.warding_box.spell.none").string
            } else {
                Component.translatable("gui.hexwright.warding_box.spell", spellSize).string
            }
            graphics.drawString(font, spellText, left, y, SPELL_COLOR, false)
            y += font.lineHeight + 3

            val statusKey: String
            val statusColor: Int
            when {
                !active -> {
                    statusKey = "gui.hexwright.warding_box.status.off"; statusColor = TEXT_DIM_COLOR
                }
                spellTag == null -> {
                    statusKey = "gui.hexwright.warding_box.status.no_spell"; statusColor = WARN_COLOR
                }
                media <= 0L -> {
                    statusKey = "gui.hexwright.warding_box.status.no_media"; statusColor = WARN_COLOR
                }
                else -> {
                    statusKey = "gui.hexwright.warding_box.status.armed"; statusColor = ARMED_COLOR
                }
            }
            graphics.drawString(font, Component.translatable(statusKey).string, left, y, statusColor, false)
            y += font.lineHeight + 3

            val attuned = targetName()
            val detail = if (attuned != null) {
                Component.translatable("gui.hexwright.warding_box.detail.target", attuned, width, height).string
            } else {
                val modeText = Component.translatable(mode.labelKey).string
                Component.translatable("gui.hexwright.warding_box.detail", modeText, width, height).string
            }
            graphics.drawString(font, detail, left, y, TEXT_DIM_COLOR, false)
            y += font.lineHeight + 2
            if (lastTrigger.isNotEmpty()) {
                val last = Component.translatable("gui.hexwright.warding_box.last", lastTrigger).string
                graphics.drawString(font, last, left, y, TEXT_DIM_COLOR, false)
                y += font.lineHeight + 2
            }
            if (lastMessage.isNotEmpty()) {
                val trimmed = font.plainSubstrByWidth(lastMessage, size.width - 12)
                graphics.drawString(font, trimmed, left, y, TEXT_DIM_COLOR, false)
            }
        }
    }
}
