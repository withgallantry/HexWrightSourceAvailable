package com.bluup.hexwright.server.block

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.server.menu.UiTemplates
import com.bluup.hexwright.client.progression.ClientMastery
import com.bluup.hexwright.client.progression.ClientRecipeUnlocks
import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory
import com.bluup.hexwright.server.block.WorktableRecipes.InfusionStep
import com.bluup.hexwright.server.block.WorktableRecipes.RECIPES
import com.bluup.hexwright.server.block.WorktableRecipes.WorktableRecipe
import com.bluup.hexwright.server.block.WorktableRecipes.defaultRecipe
import com.bluup.hexwright.server.crucible.EssencePouchData
import com.bluup.hexwright.server.item.EndlessPouchItem
import com.bluup.hexwright.server.network.EssenceNetwork
import com.bluup.hexwright.server.pocketcaster.PocketCasterData
import com.bluup.hexwright.server.progression.MakersMark
import com.bluup.hexwright.server.progression.Mastery
import com.bluup.hexwright.server.progression.RecipeUnlocks
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder
import com.lowdragmc.lowdraglib.gui.modular.ModularUI
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture
import com.lowdragmc.lowdraglib.gui.texture.ShaderTexture
import com.lowdragmc.lowdraglib.gui.texture.UIResourceTexture
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget
import com.lowdragmc.lowdraglib.gui.widget.Widget
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
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
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.SimpleContainer
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class WorktableBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.WORKTABLE_BLOCK_ENTITY, pos, state), WorldlyContainer, IUIHolder.BlockEntityUI {

    companion object {
        const val POUCH_SLOT = 0
        const val RESULT_SLOT = 1
        const val CONTAINER_SIZE = 2

        const val PERFECT_FRACTION = 0.4f

        const val FUMBLE_REFUND_FRACTION = 0.5

        const val SUCCESS_ANIM_TICKS = 50L

        internal const val UI_PROJECT_NAME = "essence_forge_drop_down"

        private const val TEXT_COLOR = 0xC8D7FF.toInt()

        private const val BAR_BG_COLOR = 0xFF0E1014.toInt()
        private const val ZONE_COLOR = 0xFF375338.toInt()
        private const val ZONE_CORE_COLOR = 0xFFBEEBB5.toInt()
        private const val NEEDLE_COLOR = 0xFFCD98E0.toInt()
        private const val GOOD_COLOR = 0xFF9BD87A.toInt()
        private const val PERFECT_COLOR = 0xFFF7DE6C.toInt()
        private const val MISS_COLOR = 0xFFE07A6B.toInt()

        private const val TIME_CORRECTION_TICKS = 4.0

        private const val TIME_SNAP_TICKS = 3.0

        private val BAR_OUTLINE_TEXTURE: IGuiTexture =
            ResourceBorderTexture("ldlib:textures/trans-inner.png", 180, 180, 4, 4)

        private val POUCH_SLOT_GUIDE_TEXTURE: IGuiTexture = ResourceTexture("ldlib:textures/menu/secondary_item_input.png")
        private val POUCH_SLOT_FILLED_TEXTURE: IGuiTexture = ResourceTexture("ldlib:textures/menu/secondary_input.png")
    }

    private val items: NonNullList<ItemStack> = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY)

    private val pouchSlotView = SingleItemSlotView(this)

    private var infusing = false

    private var selectedRecipe = defaultRecipe()

    private var round = 0

    private var roundStart = 0L

    private var zoneCenter = 0.5f

    private var scoresMilli = IntArray(0)

    private var judgementKey = ""

    private var judgementMilli = 0

    private var successStartTick = 0L

    private var successQuality = 0

    override fun getContainerSize(): Int = CONTAINER_SIZE

    override fun isEmpty(): Boolean = items.all { it.isEmpty }

    override fun getItem(slot: Int): ItemStack = items[slot]

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        val result = ContainerHelper.removeItem(items, slot, amount)
        if (!result.isEmpty) setChanged()
        return result
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack = ContainerHelper.takeItem(items, slot)

    override fun setItem(slot: Int, stack: ItemStack) {
        items[slot] = stack
        val limit = if (slot == POUCH_SLOT) 1 else maxStackSize
        if (stack.count > limit) stack.count = limit
        setChanged()
    }

    override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)

    override fun clearContent() {
        for (i in items.indices) items[i] = ItemStack.EMPTY
    }


    private val outputSlots = intArrayOf(RESULT_SLOT)

    override fun getSlotsForFace(side: Direction): IntArray =
        if (side == Direction.DOWN) outputSlots else IntArray(0)

    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, side: Direction?): Boolean = false

    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, side: Direction): Boolean =
        slot == RESULT_SLOT && side == Direction.DOWN

    override fun load(tag: CompoundTag) {
        super.load(tag)
        ContainerHelper.loadAllItems(tag, items)
        infusing = tag.getBoolean("Infusing")
        selectedRecipe = tag.getInt("Recipe").coerceIn(0, RECIPES.size - 1)
        round = tag.getInt("Round")
        roundStart = tag.getLong("RoundStart")
        zoneCenter = tag.getFloat("ZoneCenter")
        scoresMilli = tag.getIntArray("Scores")
        judgementKey = tag.getString("Judgement")
        judgementMilli = tag.getInt("JudgementScore")
        successStartTick = tag.getLong("SuccessStart")
        successQuality = tag.getInt("SuccessQuality")
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        ContainerHelper.saveAllItems(tag, items)
        tag.putBoolean("Infusing", infusing)
        tag.putInt("Recipe", selectedRecipe)
        tag.putInt("Round", round)
        tag.putLong("RoundStart", roundStart)
        tag.putFloat("ZoneCenter", zoneCenter)
        tag.putIntArray("Scores", scoresMilli)
        tag.putString("Judgement", judgementKey)
        tag.putInt("JudgementScore", judgementMilli)
        tag.putLong("SuccessStart", successStartTick)
        tag.putInt("SuccessQuality", successQuality)
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun setChanged() {
        super.setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    private fun pouchStack(): ItemStack =
        EssenceNetwork.resolve(items[POUCH_SLOT], level, worldPosition)

    private fun recipe(): WorktableRecipe = RECIPES[selectedRecipe]

    private fun activeSteps(): List<InfusionStep> = recipe().steps

    fun selectRecipe(index: Int, player: Player) {
        val level = this.level ?: return
        if (level.isClientSide || infusing) return
        if (index !in RECIPES.indices || index == selectedRecipe) return
        val serverPlayer = player as? ServerPlayer
        if (serverPlayer != null && !RecipeUnlocks.canCraft(serverPlayer, RECIPES[index].nameKey)) return
        selectedRecipe = index
        judgementKey = ""
        setChanged()
    }

    private fun hasAllEssence(): Boolean {
        val pouch = pouchStack()
        if (pouch.item !is EndlessPouchItem) return false
        return activeSteps().all { EssencePouchData.get(pouch, it.aspect) >= it.amount }
    }

    fun needlePos(time: Double): Float {
        val step = activeSteps().getOrNull(round) ?: return 0f
        val elapsed = time - roundStart
        if (elapsed <= 0) return 0f
        val phase = ((elapsed % step.periodTicks) / step.periodTicks).toFloat()
        return 1f - abs(1f - 2f * phase)
    }

    fun onForgeClicked(player: ServerPlayer) {
        val level = this.level ?: return
        if (level.isClientSide) return

        if (!infusing) {
            beginInfusion(player)
        } else {
            strike(player)
        }
    }

    private fun beginInfusion(player: ServerPlayer) {
        val level = this.level ?: return
        if (!RecipeUnlocks.canCraft(player, recipe().nameKey)) {
            judgementKey = "gui.hexwright.worktable.judgement.undiscovered"
            setChanged()
            return
        }
        val requiredMastery = recipe().requiredMastery
        if (requiredMastery != null && !Mastery.hasMastery(player, requiredMastery)) {
            judgementKey = "gui.hexwright.worktable.judgement.locked"
            setChanged()
            return
        }
        if (!hasAllEssence()) {
            judgementKey = if (EssenceNetwork.isKeyOutOfRange(items[POUCH_SLOT], level, worldPosition)) {
                "gui.hexwright.worktable.judgement.out_of_range"
            } else {
                "gui.hexwright.worktable.judgement.missing_essence"
            }
            setChanged()
            return
        }

        Mastery.checkEssenceMilestones(player, pouchStack())

        val pouch = pouchStack()
        for (step in activeSteps()) {
            EssencePouchData.consume(pouch, step.aspect, step.amount)
        }
        EssenceNetwork.pulseFlow(level, worldPosition, items[POUCH_SLOT], false)

        round = 0
        scoresMilli = IntArray(0)
        judgementKey = ""
        judgementMilli = 0

        if (!recipe().graded) {
            level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.7f, 1.2f)
            completeCraft(player, PocketCasterData.Quality.CRUDE)
            return
        }

        infusing = true
        startRound(level.gameTime)
        level.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.7f, 1.2f)
        setChanged()
    }

    private fun startRound(gameTime: Long) {
        val level = this.level ?: return
        roundStart = gameTime + 15
        zoneCenter = 0.15f + level.random.nextFloat() * 0.7f
    }

    private fun strike(player: ServerPlayer) {
        val level = this.level ?: return
        val step = activeSteps().getOrNull(round) ?: run {
            infusing = false
            setChanged()
            return
        }

        val pos = needlePos(level.gameTime.toDouble())
        val dist = abs(pos - zoneCenter)
        val half = step.zoneHalf
        val perfectHalf = half * PERFECT_FRACTION

        val milli: Int
        when {
            dist <= perfectHalf -> {
                milli = 1000
                judgementKey = "gui.hexwright.worktable.judgement.perfect"
                level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6f, 1.8f)
            }
            dist <= half -> {
                val t = (dist - perfectHalf) / (half - perfectHalf)
                milli = (1000 - 600 * t).roundToInt()
                judgementKey = "gui.hexwright.worktable.judgement.good"
                level.playSound(null, worldPosition, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6f, 1.2f)
            }
            else -> {
                milli = 0
                val pouch = pouchStack()
                if (pouch.item is EndlessPouchItem) {
                    EssencePouchData.add(pouch, step.aspect, step.amount * FUMBLE_REFUND_FRACTION)
                }
                judgementKey = "gui.hexwright.worktable.judgement.fumble"
                level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3f, 1.6f)
            }
        }
        judgementMilli = milli
        scoresMilli = scoresMilli + milli

        round++
        if (round >= activeSteps().size) {
            finishCraft(player)
        } else {
            startRound(level.gameTime)
        }
        setChanged()
    }

    private fun finishCraft(player: ServerPlayer) {
        val avg = if (scoresMilli.isEmpty()) 0 else scoresMilli.sum() / scoresMilli.size
        val quality = when {
            avg >= 930 -> PocketCasterData.Quality.MASTERWORK
            avg >= 750 -> PocketCasterData.Quality.EXQUISITE
            avg >= 550 -> PocketCasterData.Quality.FINE
            avg >= 300 -> PocketCasterData.Quality.SOUND
            else -> PocketCasterData.Quality.CRUDE
        }
        completeCraft(player, quality)
    }

    private fun completeCraft(player: ServerPlayer, quality: PocketCasterData.Quality) {
        val level = this.level as? ServerLevel ?: return
        infusing = false

        val craft = recipe()
        val result = craft.assemble(quality)
        if (craft.graded && quality.ordinal >= PocketCasterData.Quality.FINE.ordinal) {
            MakersMark.apply(result, player)
        }

        deliverResult(result)
        if (craft.graded) {
            Mastery.recordCraft(player, quality, result)
        }

        judgementKey = ""
        judgementMilli = 0

        successStartTick = level.gameTime
        successQuality = if (craft.graded) mapQualityForShader(quality) else 0

        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.0f)
        setChanged()
    }

    private fun deliverResult(result: ItemStack) {
        val level = this.level as? ServerLevel ?: return
        val existing = items[RESULT_SLOT]
        when {
            existing.isEmpty -> {
                items[RESULT_SLOT] = result
                setChanged()
            }
            ItemStack.isSameItemSameTags(existing, result) && existing.count + result.count <= existing.maxStackSize -> {
                existing.grow(result.count)
                setChanged()
            }
            else -> {
                val entity = ItemEntity(
                    level,
                    worldPosition.x + 0.5, worldPosition.y + 1.1, worldPosition.z + 0.5,
                    result
                )
                entity.setDefaultPickUpDelay()
                level.addFreshEntity(entity)
            }
        }
    }

    private fun mapQualityForShader(quality: PocketCasterData.Quality): Int =
        (quality.ordinal - 1).coerceIn(0, 3)

    override fun createUI(entityPlayer: Player): ModularUI {
        val template = UiTemplates.load(UI_PROJECT_NAME)
        if (template == null) {
            Hexwright.LOGGER.error("Failed to load $UI_PROJECT_NAME UI; falling back to minimal worktable UI")
            return createFallbackUI(entityPlayer)
        }

        val root = template.get() ?: return createFallbackUI(entityPlayer)
        bindForgeWidgets(root, entityPlayer)
        return ModularUI(root, this, entityPlayer)
    }

    private fun bindForgeWidgets(root: WidgetGroup, player: Player) {
        val pouchSlot = bindPouchSlot(root)
        bindResultSlot(root)
        val displaySlot = bindDisplaySlot(root)

        val itemName = (root.getFirstWidgetById("^item_name$") as? TextTextureWidget)?.also { it.setClientSideWidget() }
        val forgeButton = root.getFirstWidgetById("^forge$") as? ButtonWidget
        val miniGameGroup = root.getFirstWidgetById("^mini_game$") as? WidgetGroup
        val successGroup = root.getFirstWidgetById("^success$") as? WidgetGroup

        val essencePanel = root.getWidgetsByType(WidgetGroup::class.java)
            .firstOrNull { it.id == "recipe_list" && it.javaClass == WidgetGroup::class.java }
        val recipeScrollGroup = root.getWidgetsByType(DraggableScrollableWidgetGroup::class.java)
            .firstOrNull { it.id == "recipe_list" }

        successGroup?.setVisible(false)

        if (miniGameGroup != null) {
            miniGameGroup.setVisible(false)
            miniGameGroup.addWidget(MiniGameWidget(0, 0, miniGameGroup.sizeWidth, miniGameGroup.sizeHeight))
        }

        var browser: WorktableRecipeBrowser? = null
        if (recipeScrollGroup != null) {
            val candidate = WorktableRecipeBrowser(this, recipeScrollGroup)
            if (candidate.buildRows(root, player)) {
                candidate.bindCategorySelector(root)
                browser = candidate
            }
        }

        forgeButton?.setOnPressCallback { _ ->
            val serverPlayer = player as? ServerPlayer
            if (serverPlayer != null && level?.isClientSide == false) {
                onForgeClicked(serverPlayer)
            }
        }

        root.addWidget(
            ForgeSyncWidget(pouchSlot, displaySlot, itemName, essencePanel, miniGameGroup, successGroup, browser)
        )
    }

    private fun bindPouchSlot(root: WidgetGroup): SlotWidget? {
        val widget = root.getFirstWidgetById("^pouch$")
        if (widget !is SlotWidget) {
            Hexwright.LOGGER.warn("$UI_PROJECT_NAME is missing slot widget 'pouch'")
            return null
        }
        widget.setContainerSlot(pouchSlotView, POUCH_SLOT)
        widget.setCanPutItems(true)
        widget.setCanTakeItems(true)
        widget.setLocationInfo(false, false)
        return widget
    }

    private fun bindResultSlot(root: WidgetGroup) {
        val widget = root.getFirstWidgetById("^result$")
        if (widget !is SlotWidget) {
            Hexwright.LOGGER.warn("$UI_PROJECT_NAME is missing slot widget 'result'")
            return
        }
        widget.setContainerSlot(this, RESULT_SLOT)
        widget.setCanPutItems(false)
        widget.setCanTakeItems(true)
        widget.setLocationInfo(false, false)
    }

    private fun bindDisplaySlot(root: WidgetGroup): SlotWidget? {
        val widget = root.getFirstWidgetById("^fake_item$")
        if (widget !is SlotWidget) {
            Hexwright.LOGGER.warn("$UI_PROJECT_NAME is missing slot widget 'fake_item'")
            return null
        }
        widget.setContainerSlot(SimpleContainer(1), 0)
        widget.setLocationInfo(false, false)
        return widget
    }

    private fun createFallbackUI(player: Player): ModularUI {
        val ui = ModularUI(200, 176, this, player)

        ui.widget(SlotWidget(pouchSlotView, POUCH_SLOT, 20, 20))
        ui.widget(SlotWidget(this, RESULT_SLOT, 60, 20, false, true))

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val slotIndex = col + row * 9 + 9
                ui.widget(SlotWidget(player.inventory, slotIndex, 19 + col * 18, 76 + row * 18))
            }
        }
        for (col in 0 until 9) {
            ui.widget(SlotWidget(player.inventory, col, 19 + col * 18, 134))
        }

        return ui
    }

    private fun unwrapTexture(texture: IGuiTexture?): IGuiTexture? {
        var current = texture
        while (current is UIResourceTexture) {
            current = current.texture
        }
        return current
    }

    private fun aspectIcon(aspect: IngredientCategory): ResourceLocation =
        Hexwright.id("textures/gui/essence/${aspect.name.lowercase(Locale.ROOT)}.png")

    private inner class MiniGameWidget(x: Int, y: Int, width: Int, height: Int) : Widget(x, y, width, height) {
        private val scale = 0.7f
        private val iconSize = 7

        private var renderTime = Double.NaN
        private var lastFrameMillis = 0L

        private fun smoothTime(partialTicks: Float): Double {
            val target = (level?.gameTime ?: 0L).toDouble() + partialTicks
            val now = Util.getMillis()
            val gapMillis = now - lastFrameMillis
            lastFrameMillis = now

            if (renderTime.isNaN() || gapMillis > 500L || abs(target - renderTime) > TIME_SNAP_TICKS) {
                renderTime = target
                return renderTime
            }

            val dtTicks = gapMillis.coerceIn(0L, 100L) / 50.0
            renderTime += dtTicks
            renderTime += (target - renderTime) * (dtTicks / TIME_CORRECTION_TICKS).coerceAtMost(1.0)
            return renderTime
        }

        override fun drawInBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks)
            val step = activeSteps().getOrNull(round) ?: return
            val font = Minecraft.getInstance().font
            val pos = position
            val lineStep = (font.lineHeight * scale).roundToInt() + 2

            val header = Component.translatable(
                "gui.hexwright.worktable.round", round + 1, activeSteps().size,
                EndlessPouchItem.aspectName(step.aspect).string
            ).string
            val judgement = if (judgementKey.isEmpty()) null else Component.translatable(judgementKey).string
            val textHeight = (font.lineHeight * scale).roundToInt()

            val headerHeight = maxOf(iconSize, lineStep)
            val barHeight = 14
            val barWidth = size.width - 8
            val barX = pos.x + (size.width - barWidth) / 2
            val judgementHeight = if (judgement == null) 0 else textHeight + 6
            val blockHeight = headerHeight + 4 + barHeight + judgementHeight
            var lineY = pos.y + ((size.height - blockHeight) / 2).coerceAtLeast(0)

            val headerWidth = iconSize + 2 + (font.width(header) * scale).roundToInt()
            val headerX = pos.x + (size.width - headerWidth) / 2
            drawAspectIcon(graphics, aspectIcon(step.aspect), headerX, lineY)
            drawScaled(graphics, font, header, headerX + iconSize + 2, lineY, TEXT_COLOR)
            lineY += headerHeight + 4

            val barY = lineY
            BAR_OUTLINE_TEXTURE.draw(
                graphics, mouseX, mouseY,
                (barX - 1).toFloat(), (barY - 1).toFloat(), barWidth + 2, barHeight + 2
            )
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, BAR_BG_COLOR)

            val half = step.zoneHalf
            val zoneLeft = barX + ((zoneCenter - half) * barWidth).roundToInt().coerceIn(0, barWidth)
            val zoneRight = barX + ((zoneCenter + half) * barWidth).roundToInt().coerceIn(0, barWidth)
            graphics.fill(zoneLeft, barY, zoneRight, barY + barHeight, ZONE_COLOR)

            val coreHalf = half * PERFECT_FRACTION
            val coreLeft = barX + ((zoneCenter - coreHalf) * barWidth).roundToInt().coerceIn(0, barWidth)
            val coreRight = barX + ((zoneCenter + coreHalf) * barWidth).roundToInt().coerceIn(0, barWidth)
            graphics.fill(coreLeft, barY, coreRight, barY + barHeight, ZONE_CORE_COLOR)

            val needle = needlePos(smoothTime(partialTicks))
            val needleOffset = (needle * barWidth).coerceIn(0f, (barWidth - 1).toFloat())
            val pose = graphics.pose()
            pose.pushPose()
            pose.translate((barX + needleOffset).toDouble(), 0.0, 0.0)
            graphics.fill(-1, barY - 2, 1, barY + barHeight + 2, NEEDLE_COLOR)
            pose.popPose()

            lineY += barHeight + 6

            if (judgement != null) {
                val color = when {
                    judgementMilli >= 1000 -> PERFECT_COLOR
                    judgementMilli > 0 -> GOOD_COLOR
                    else -> MISS_COLOR
                }
                val judgementX = pos.x + (size.width - (font.width(judgement) * scale).roundToInt()) / 2
                drawScaled(graphics, font, judgement, judgementX, lineY, color)
            }
        }

        private fun drawScaled(graphics: GuiGraphics, font: Font, text: String, x: Int, y: Int, color: Int) {
            val pose = graphics.pose()
            pose.pushPose()
            pose.translate(x.toFloat(), y.toFloat(), 0f)
            pose.scale(scale, scale, 1f)
            graphics.drawString(font, text, 0, 0, color, false)
            pose.popPose()
        }

        private fun drawAspectIcon(graphics: GuiGraphics, texture: ResourceLocation, x: Int, y: Int) {
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            val pose = graphics.pose()
            pose.pushPose()
            pose.translate(x.toFloat(), y.toFloat(), 0f)
            val s = iconSize / 64f
            pose.scale(s, s, 1f)
            graphics.blit(texture, 0, 0, 0f, 0f, 64, 64, 64, 64)
            pose.popPose()
        }
    }

    private inner class ForgeSyncWidget(
        private val pouchSlot: SlotWidget?,
        private val displaySlot: SlotWidget?,
        private val itemName: TextTextureWidget?,
        private val essencePanel: WidgetGroup?,
        private val miniGameGroup: WidgetGroup?,
        private val successGroup: WidgetGroup?,
        private val browser: WorktableRecipeBrowser?
    ) : Widget(0, 0, 0, 0) {

        private var lastBoundRecipe = -1
        private var lastEssenceFingerprint = ""
        private var pouchGuideShown = true

        private var lastUnlockVersion = -1

        private var lastMasteryVersion = -1

        private var displayedRecipe = -1
        private var displayStack: ItemStack = ItemStack.EMPTY

        private val essenceRows = mutableListOf<Widget>()

        override fun initWidget() {
            super.initWidget()
            if (isRemote()) {
                updateHeader()
                updateDisplaySlot()
                updateSelectionHighlight()
                updatePouchGuide()
            }
        }

        override fun updateScreen() {
            super.updateScreen()
            refresh()
        }

        override fun detectAndSendChanges() {
            super.detectAndSendChanges()
            updateDisplaySlot()
        }

        private fun refresh() {
            updateHeader()
            updateDisplaySlot()
            updateSelectionHighlight()
            updateVisibleRows()
            updateEssencePanel()
            updatePouchGuide()
            miniGameGroup?.setVisible(infusing)
            updateSuccessAnimation()
        }

        private fun updatePouchGuide() {
            val slot = pouchSlot ?: return
            val host = slot.parent ?: return
            val empty = slot.item.isEmpty
            if (empty != pouchGuideShown) {
                pouchGuideShown = empty
                host.setBackground(if (pouchGuideShown) POUCH_SLOT_GUIDE_TEXTURE else POUCH_SLOT_FILLED_TEXTURE)
            }
        }

        private fun updateHeader() {
            if (selectedRecipe == lastBoundRecipe) return
            lastBoundRecipe = selectedRecipe
            val recipe = recipe()
            itemName?.setText(Component.translatable(recipe.nameKey))
        }

        private fun updateDisplaySlot() {
            val slot = displaySlot ?: return
            if (displayedRecipe != selectedRecipe) {
                displayedRecipe = selectedRecipe
                displayStack = recipe().preview()
            }
            if (!ItemStack.matches(slot.item, displayStack)) {
                slot.setItem(displayStack.copy())
            }
        }

        private fun updateSelectionHighlight() {
            browser?.highlightSelection(selectedRecipe)
        }

        private fun updateVisibleRows() {
            val unlockVersion = ClientRecipeUnlocks.version()
            val masteryVersion = ClientMastery.version()
            if (unlockVersion == lastUnlockVersion && masteryVersion == lastMasteryVersion) return
            lastUnlockVersion = unlockVersion
            lastMasteryVersion = masteryVersion
            browser?.refilter()
        }

        private fun updateEssencePanel() {
            val group = essencePanel ?: return

            if (infusing) {
                if (essenceRows.isNotEmpty()) {
                    essenceRows.forEach { group.removeWidget(it) }
                    essenceRows.clear()
                }
                lastEssenceFingerprint = ""
                return
            }

            val pouch = pouchStack()
            val hasPouch = pouch.item is EndlessPouchItem
            val steps = activeSteps()

            val fingerprint = buildString {
                append(selectedRecipe).append('|').append(hasPouch).append('|').append(judgementKey)
                for (step in steps) {
                    append('|').append(step.aspect.name).append(':')
                    append(if (hasPouch) EssencePouchData.get(pouch, step.aspect) else 0.0)
                }
            }
            if (fingerprint == lastEssenceFingerprint) return
            lastEssenceFingerprint = fingerprint

            essenceRows.forEach { group.removeWidget(it) }
            essenceRows.clear()

            var y = 2
            for (step in steps) {
                val have = if (hasPouch) EssencePouchData.get(pouch, step.aspect) else 0.0
                val enough = have >= step.amount
                val icon = ImageWidget(2, y, 8, 8, ResourceTexture(aspectIcon(step.aspect)))
                group.addWidget(icon)
                essenceRows.add(icon)
                val line = "${EndlessPouchItem.aspectName(step.aspect).string}: " +
                    "${EndlessPouchItem.formatAmount(have)} / ${EndlessPouchItem.formatAmount(step.amount)}"
                val label = LabelWidget(12, y, line)
                label.setColor(if (enough) GOOD_COLOR else MISS_COLOR)
                group.addWidget(label)
                essenceRows.add(label)
                y += 10
            }

            if (judgementKey.isNotEmpty()) {
                val message = LabelWidget(2, y + 2, Component.translatable(judgementKey).string)
                message.setColor(MISS_COLOR)
                group.addWidget(message)
                essenceRows.add(message)
            }
        }

        private fun updateSuccessAnimation() {
            val group = successGroup ?: return
            val lvl = level
            if (successStartTick <= 0L || lvl == null) {
                group.setVisible(false)
                return
            }

            val elapsed = lvl.gameTime - successStartTick
            if (elapsed > SUCCESS_ANIM_TICKS) {
                group.setVisible(false)
                return
            }

            group.setVisible(true)
            val progress = (elapsed.toFloat() / SUCCESS_ANIM_TICKS.toFloat()).coerceIn(0f, 1f)
            val shader = unwrapTexture(group.backgroundTexture) as? ShaderTexture
            shader?.setUniformCache { cache ->
                cache.glUniform1F("uProgress", progress)
                cache.glUniform1F("uQuality", successQuality.toFloat())
            }
        }
    }
}
