package com.bluup.hexwright.server.block

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.client.block.CoalescerFormVisualClient
import com.bluup.hexwright.server.menu.MenuWidgets
import com.bluup.hexwright.server.menu.UiTemplates
import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory
import com.bluup.hexwright.server.coalescence.CoalescenceDenials
import com.bluup.hexwright.server.coalescence.CoalescenceRecipes
import com.bluup.hexwright.server.crucible.EssencePouchData
import com.bluup.hexwright.server.item.EndlessPouchItem
import com.bluup.hexwright.server.network.EssenceNetwork
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder
import com.lowdragmc.lowdraglib.gui.modular.ModularUI
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture
import com.lowdragmc.lowdraglib.gui.texture.TextTexture
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget
import com.lowdragmc.lowdraglib.gui.widget.TextBoxWidget
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget
import com.lowdragmc.lowdraglib.gui.widget.Widget
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
import net.minecraft.core.particles.ItemParticleOption
import net.minecraft.core.particles.ParticleTypes
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
import net.minecraft.world.Containers
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.util.EnumMap
import java.util.function.DoubleSupplier

class CoalescerBlockEntity(
    pos: BlockPos,
    state: BlockState
) : BlockEntity(HexwrightBlocks.COALESCER_BLOCK_ENTITY, pos, state), WorldlyContainer, IUIHolder.BlockEntityUI {

    companion object {
        const val POUCH_SLOT = 0
        const val OUTPUT_SLOT = 1

        const val SEED_SLOT = 2
        const val CONTAINER_SIZE = 3

        private const val TAG_FAKE_SEED = "HexwrightFakeSeed"

        private const val FAKE_SEED_TINT = 0x5000BFFF.toInt()

        const val AUTO_WORK_INTERVAL = 40L

        const val CRAFT_TICKS = 30


        private const val AMOUNT_MAX_DIGITS = 3

        const val IMAGE_WIDTH = 198
        const val IMAGE_HEIGHT = 216

        const val KEY_SLOT_X = 16
        const val SEED_SLOT_X = 80
        const val RESULT_SLOT_X = 164
        const val TOP_SLOT_Y = 26

        const val PANEL_X = 14
        const val PANEL_Y = 52
        const val PANEL_WIDTH = 170
        const val PANEL_HEIGHT = 52

        const val AMOUNT_LABEL_X = 14
        const val AMOUNT_ROW_Y = 112
        const val AMOUNT_DOWN_X = 62
        const val AMOUNT_VALUE_X = 84
        const val AMOUNT_UP_X = 100
        const val STEPPER_WIDTH = 16
        const val ACTION_X = 122
        const val ACTION_WIDTH = 62
        const val BUTTON_HEIGHT = 16

        const val PLAYER_INV_LEFT = 19
        const val PLAYER_INV_TOP = 134
        const val HOTBAR_TOP = PLAYER_INV_TOP + 3 * 18 + 4

        private const val PANEL_COLOR = 0xFF1B1F26.toInt()
        private const val PANEL_BORDER_COLOR = 0xFF45505C.toInt()
        private const val BUTTON_COLOR = 0xFF2E3A4C.toInt()
        private const val TEXT_COLOR = 0xC8D7FF.toInt()
        private const val BAR_BG_COLOR = 0xFF0E1014.toInt()
        private const val TRANSPARENT_COLOR = 0x00000000

        private val PANEL_BACKGROUND = GuiTextureGroup(ColorRectTexture(PANEL_COLOR), ColorRectTexture(PANEL_BORDER_COLOR))

        private val POUCH_SLOT_GUIDE_TEXTURE: IGuiTexture = ResourceTexture("ldlib:textures/menu/secondary_item_input.png")
        private val POUCH_SLOT_FILLED_TEXTURE: IGuiTexture = ResourceTexture("ldlib:textures/menu/secondary_input.png")

        private const val GOOD_COLOR = 0xFF9BD87A.toInt()
        private const val MISS_COLOR = 0xFFE07A6B.toInt()
        private const val COST_ROW_START_Y = 30
        private const val COST_ROW_HEIGHT = 9
        private const val COST_ICON_X = 2
        private const val COST_ICON_SIZE = 8
        private const val COST_LABEL_X = 12

        private const val BUTTON_DISABLED_TINT = 0x80000000.toInt()

        private const val NOTICE_INSUFFICIENT = "gui.hexwright.coalescer.notice.insufficient"
    }

    private val items: NonNullList<ItemStack> = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY)

    private var requestedAmount = 1

    private var seedPrice: Map<IngredientCategory, Double> = emptyMap()

    private var noticeKey = ""

    private var craftTicks = 0

    private var craftResult: ItemStack = ItemStack.EMPTY

    private var fxCooldown = 0


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
        if (stack.count > maxStackSize) stack.count = maxStackSize
        setChanged()
    }

    override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = when (slot) {
        POUCH_SLOT -> EssenceNetwork.isEssenceSource(stack)
        SEED_SLOT -> true
        else -> false
    }

    override fun clearContent() {
        for (i in items.indices) items[i] = ItemStack.EMPTY
    }


    private val outputSlots = intArrayOf(OUTPUT_SLOT)

    override fun getSlotsForFace(side: Direction): IntArray =
        if (side == Direction.DOWN) outputSlots else IntArray(0)

    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, side: Direction?): Boolean = false

    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, side: Direction): Boolean =
        slot == OUTPUT_SLOT && side == Direction.DOWN


    fun serverTick() {
        val level = this.level as? ServerLevel ?: return

        if (craftTicks > 0) {
            craftTicks--
            if (craftTicks == 0) {
                finishCraft(level)
            }
        }

        if (level.gameTime % AUTO_WORK_INTERVAL != 0L) return
        if (!level.hasNeighborSignal(worldPosition)) return
        if (attemptCoalesce() == null) {
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.35f, 0.8f)
            setChanged()
        }
    }

    fun clientTick() {
        val level = this.level ?: return
        if (fxCooldown > 0) {
            fxCooldown--
        }
        if (craftTicks == CRAFT_TICKS && fxCooldown == 0) {
            fxCooldown = CRAFT_TICKS
            CoalescerFormVisualClient.play(level, worldPosition)
        }
        if (craftTicks > 1) {
            craftTicks--
        }
    }


    override fun load(tag: CompoundTag) {
        super.load(tag)
        ContainerHelper.loadAllItems(tag, items)
        requestedAmount = if (tag.contains("Amount")) tag.getInt("Amount") else 1
        seedPrice = readPrice(tag.getCompound("SeedPrice"))
        noticeKey = tag.getString("Notice")
        craftTicks = tag.getInt("CraftTicks")
        craftResult = if (tag.contains("CraftResult")) ItemStack.of(tag.getCompound("CraftResult")) else ItemStack.EMPTY
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        ContainerHelper.saveAllItems(tag, items)
        tag.putInt("Amount", amount())
        tag.put("SeedPrice", writePrice(seedPrice))
        tag.putString("Notice", noticeKey)
        tag.putInt("CraftTicks", craftTicks)
        if (!craftResult.isEmpty) {
            tag.put("CraftResult", craftResult.save(CompoundTag()))
        }
    }

    private fun writePrice(price: Map<IngredientCategory, Double>): CompoundTag {
        val out = CompoundTag()
        for ((aspect, amount) in price) {
            out.putDouble(aspect.name, amount)
        }
        return out
    }

    private fun readPrice(tag: CompoundTag): Map<IngredientCategory, Double> {
        if (tag.isEmpty) return emptyMap()
        val price = EnumMap<IngredientCategory, Double>(IngredientCategory::class.java)
        for (aspect in IngredientCategory.values()) {
            if (tag.contains(aspect.name)) {
                price[aspect] = tag.getDouble(aspect.name)
            }
        }
        return price
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()
    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun setChanged() {
        if (level?.isClientSide == false) {
            seedPrice = CoalescenceRecipes.priceFor(items[SEED_SLOT].item) ?: emptyMap()
        }
        super.setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }


    fun maxAmount(): Int {
        val seed = items[SEED_SLOT]
        return if (seed.isEmpty) 1 else seed.maxStackSize.coerceAtLeast(1)
    }

    fun amount(): Int = requestedAmount.coerceIn(1, maxAmount())

    fun setAmount(value: Int) {
        val clamped = value.coerceIn(1, maxAmount())
        if (clamped == amount()) {
            requestedAmount = clamped
            return
        }
        requestedAmount = clamped
        noticeKey = ""
        setChanged()
    }

    fun batchPrice(batch: Int = amount()): Map<IngredientCategory, Double> {
        if (seedPrice.isEmpty()) return emptyMap()
        val total = EnumMap<IngredientCategory, Double>(IngredientCategory::class.java)
        for ((aspect, per) in seedPrice) {
            total[aspect] = per * batch
        }
        return total
    }

    data class CostEntry(val aspect: IngredientCategory, val need: Double, val have: Double) {
        val enough: Boolean get() = have >= need
    }

    fun costEntries(): List<CostEntry> {
        val cost = batchPrice()
        if (cost.isEmpty()) return emptyList()
        val pouch = pouchStack()
        val hasPouch = pouch.item is EndlessPouchItem
        return cost.map { (aspect, need) ->
            CostEntry(aspect, need, if (hasPouch) EssencePouchData.get(pouch, aspect) else 0.0)
        }
    }

    fun insufficientEssence(): Boolean {
        val entries = costEntries()
        return entries.isNotEmpty() && entries.any { !it.enough }
    }


    private fun pouchStack(): ItemStack =
        EssenceNetwork.resolve(items[POUCH_SLOT], level, worldPosition)

    private fun noPouchNoticeKey(): String =
        if (EssenceNetwork.isKeyOutOfRange(items[POUCH_SLOT], level, worldPosition)) {
            "gui.hexwright.coalescer.notice.out_of_range"
        } else {
            "gui.hexwright.coalescer.notice.no_pouch"
        }

    private fun attemptCoalesce(requestedBatch: Int = amount()): String? {
        if (craftTicks > 0) return "gui.hexwright.coalescer.notice.busy"

        val seed = items[SEED_SLOT]
        if (seed.isEmpty) return "gui.hexwright.coalescer.notice.no_seed"
        if (CoalescenceDenials.isDenied(seed.item)) return "gui.hexwright.coalescer.notice.denied"

        seedPrice = CoalescenceRecipes.priceFor(seed.item) ?: emptyMap()

        val batch = requestedBatch.coerceIn(1, maxAmount())
        val cost = batchPrice(batch)
        if (cost.isEmpty()) return "gui.hexwright.coalescer.notice.unpriced"

        val pouch = pouchStack()
        if (pouch.item !is EndlessPouchItem) return noPouchNoticeKey()

        val result = ItemStack(seed.item, batch)
        if (!canFitOutput(result)) return "gui.hexwright.coalescer.notice.output_full"
        if (cost.any { (aspect, need) -> EssencePouchData.get(pouch, aspect) < need }) {
            return NOTICE_INSUFFICIENT
        }

        for ((aspect, need) in cost) {
            EssencePouchData.consume(pouch, aspect, need)
        }
        level?.let { EssenceNetwork.pulseFlow(it, worldPosition, items[POUCH_SLOT], false) }
        beginCraft(result)
        return null
    }

    private fun beginCraft(result: ItemStack) {
        craftResult = result
        craftTicks = CRAFT_TICKS
        setChanged()
    }

    private fun finishCraft(level: ServerLevel) {
        val result = craftResult
        craftResult = ItemStack.EMPTY
        if (!result.isEmpty) {
            if (canFitOutput(result)) {
                insertOutput(result)
            } else {
                Containers.dropItemStack(
                    level,
                    worldPosition.x + 0.5, worldPosition.y + 1.0, worldPosition.z + 0.5,
                    result
                )
            }
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5f, 1.3f)
        }
        setChanged()
    }

    fun takePendingCraft(): ItemStack {
        val pending = craftResult
        craftResult = ItemStack.EMPTY
        craftTicks = 0
        return pending
    }

    fun isCrafting(): Boolean = craftTicks > 0

    fun craftFraction(partialTicks: Float = 0f): Float {
        if (craftTicks <= 0) return 0f
        val remaining = (craftTicks - partialTicks).coerceAtLeast(0f)
        return ((CRAFT_TICKS - remaining) / (CRAFT_TICKS - 1f)).coerceIn(0f, 1f)
    }

    fun onCoalesceClicked(player: ServerPlayer) {
        val level = this.level as? ServerLevel ?: return
        val refusal = attemptCoalesce()
        if (refusal != null) {
            notice(refusal)
            return
        }
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 0.9f)
    }

    fun workOnce(): Boolean {
        val level = this.level as? ServerLevel ?: return false
        if (attemptCoalesce() != null) return false
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.35f, 0.9f)
        setChanged()
        return true
    }

    fun workOnce(amount: Int): Boolean {
        val level = this.level as? ServerLevel ?: return false
        if (attemptCoalesce(amount) != null) return false
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.35f, 0.9f)
        setChanged()
        return true
    }

    fun isFakeSeed(stack: ItemStack): Boolean = !stack.isEmpty && stack.tag?.getBoolean(TAG_FAKE_SEED) == true

    fun seedFromType(item: Item): Boolean {
        val level = this.level as? ServerLevel ?: return false
        if (item == Items.AIR) return false
        if (CoalescenceDenials.isDenied(item)) return false
        val current = items[SEED_SLOT]
        if (!current.isEmpty && current.item == item) return true

        if (!current.isEmpty) {
            Containers.dropItemStack(
                level,
                worldPosition.x + 0.5, worldPosition.y + 1.0, worldPosition.z + 0.5,
                current
            )
        }
        val fake = ItemStack(item)
        fake.orCreateTag.putBoolean(TAG_FAKE_SEED, true)
        items[SEED_SLOT] = fake
        noticeKey = ""
        setChanged()
        return true
    }

    private fun breakFakeSeed() {
        val level = this.level as? ServerLevel ?: return
        val stack = items[SEED_SLOT]
        if (!isFakeSeed(stack)) return
        items[SEED_SLOT] = ItemStack.EMPTY
        level.playSound(null, worldPosition, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.5f, 1.0f)
        level.sendParticles(
            ItemParticleOption(ParticleTypes.ITEM, stack),
            worldPosition.x + 0.5, worldPosition.y + 1.2, worldPosition.z + 0.5,
            12, 0.15, 0.15, 0.15, 0.08
        )
        noticeKey = ""
        setChanged()
    }

    private fun canFitOutput(result: ItemStack): Boolean {
        val out = items[OUTPUT_SLOT]
        if (out.isEmpty) return result.count <= result.maxStackSize
        return ItemStack.isSameItemSameTags(out, result) && out.count + result.count <= out.maxStackSize
    }

    private fun insertOutput(result: ItemStack) {
        val out = items[OUTPUT_SLOT]
        if (out.isEmpty) {
            items[OUTPUT_SLOT] = result
        } else {
            out.grow(result.count)
        }
    }

    private fun notice(key: String) {
        noticeKey = key
        setChanged()
    }


    fun summaryLines(): List<String> {
        val lines = ArrayList<String>()
        if (craftTicks > 0 && !craftResult.isEmpty) {
            lines.add(line("gui.hexwright.coalescer.summary.working", craftResult.count, craftResult.hoverName.string))
        }
        val seed = items[SEED_SLOT]
        if (seed.isEmpty) {
            lines.add(line("gui.hexwright.coalescer.summary.no_seed"))
        } else if (CoalescenceDenials.isDenied(seed.item)) {
            lines.add(line("gui.hexwright.coalescer.summary.denied", seed.hoverName.string))
        } else if (seedPrice.isEmpty()) {
            lines.add(line("gui.hexwright.coalescer.summary.unpriced", seed.hoverName.string))
        } else {
            val batch = amount()
            lines.add(line("gui.hexwright.coalescer.summary.head", batch, seed.hoverName.string))
            if (noticeKey.isEmpty() && pouchStack().item !is EndlessPouchItem) {
                lines.add(line(noPouchNoticeKey()))
            }
        }
        val staleInsufficient = noticeKey == NOTICE_INSUFFICIENT && !insufficientEssence()
        if (noticeKey.isNotEmpty() && !staleInsufficient) {
            lines.add(line(noticeKey))
        }
        return lines
    }

    private fun line(key: String, vararg args: Any): String =
        Component.translatable(key, *args).string.replace("%", "%%")


    override fun createUI(entityPlayer: Player): ModularUI {
        val root = UiTemplates.load("coalescer")?.get()
        if (root == null) {
            Hexwright.LOGGER.error("Failed to load coalescer.ui; falling back to a minimal Coalescer UI")
            return createFallbackUI(entityPlayer)
        }
        bindWidgets(root, entityPlayer)
        return ModularUI(root, this, entityPlayer)
    }

    private fun bindWidgets(root: WidgetGroup, entityPlayer: Player) {
        val widgetsById = MenuWidgets.indexById(root)

        replaceWithSourceSlot(widgetsById)
        replaceWithSeedSlot(widgetsById)
        val resultSlot = bindSlotInPlace(widgetsById, "result", OUTPUT_SLOT, canPut = false)
        MenuWidgets.bindPlayerInventory(widgetsById, entityPlayer)

        bindAmountField(widgetsById)
        val actionGroup = MenuWidgets.firstById(widgetsById, "action") as? WidgetGroup
        val actionButton = bindAction(actionGroup, entityPlayer)

        val summary = MenuWidgets.firstById(widgetsById, "summary") as? TextBoxWidget
        if (summary == null) {
            Hexwright.LOGGER.warn("coalescer.ui is missing the summary text box 'summary'")
        }
        val costPanel = MenuWidgets.firstById(widgetsById, "channels_info") as? WidgetGroup
        if (costPanel == null) {
            Hexwright.LOGGER.warn("coalescer.ui is missing the cost panel 'channels_info'")
        }
        root.addWidget(SummarySyncWidget(summary, costPanel, actionGroup, actionButton))

        val progress = MenuWidgets.firstById(widgetsById, "craft_progress") as? ProgressWidget
        progress?.progressSupplier = DoubleSupplier { craftFraction().toDouble() }

        if (resultSlot != null) {
            root.addWidget(CraftGhostWidget(resultSlot))
        }
    }

    private fun bindSlotInPlace(
        widgetsById: Map<String, List<Widget>>,
        id: String,
        slot: Int,
        canPut: Boolean
    ): SlotWidget? {
        val widget = MenuWidgets.firstById(widgetsById, id) as? SlotWidget
        if (widget == null) {
            Hexwright.LOGGER.warn("coalescer.ui is missing slot widget '{}'", id)
            return null
        }
        widget.setContainerSlot(this, slot)
        widget.setCanPutItems(canPut)
        widget.setCanTakeItems(true)
        widget.setLocationInfo(false, false)
        return widget
    }

    private fun replaceWithSourceSlot(widgetsById: Map<String, List<Widget>>) {
        val template = MenuWidgets.firstById(widgetsById, "key_slot") as? SlotWidget
        if (template == null) {
            Hexwright.LOGGER.warn("coalescer.ui is missing slot widget 'key_slot'")
            return
        }
        val tooltips = ArrayList(template.tooltipTexts)
        val replacement = MenuWidgets.replaceInPlace(template) { x, y ->
            SourceSlotWidget(x, y, swapGuideBackground = true)
        } ?: return
        if (tooltips.isEmpty()) {
            replacement.setHoverTooltips(Component.translatable("gui.hexwright.coalescer.pouch.tooltip"))
        } else {
            replacement.setHoverTooltips(tooltips)
        }
    }

    private fun replaceWithSeedSlot(widgetsById: Map<String, List<Widget>>) {
        val template = MenuWidgets.firstById(widgetsById, "seed_item") as? SlotWidget
        if (template == null) {
            Hexwright.LOGGER.warn("coalescer.ui is missing slot widget 'seed_item'")
            return
        }
        val tooltips = ArrayList(template.tooltipTexts)
        val replacement = MenuWidgets.replaceInPlace(template) { x, y -> SeedSlotWidget(x, y) } ?: return
        if (tooltips.isEmpty()) {
            replacement.setHoverTooltips(Component.translatable("gui.hexwright.coalescer.seed.tooltip"))
        } else {
            replacement.setHoverTooltips(tooltips)
        }
        replacement.setOnAddedTooltips { widget, list ->
            if (isFakeSeed(widget.getItem())) {
                list.add(
                    Component.translatable("gui.hexwright.coalescer.seed.fake_hint")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
                )
            }
        }
    }

    private fun bindAmountField(widgetsById: Map<String, List<Widget>>) {
        val field = MenuWidgets.firstById(widgetsById, "amount") as? TextFieldWidget
        if (field == null) {
            Hexwright.LOGGER.warn("coalescer.ui is missing the amount text field 'amount'")
            return
        }
        field.setMaxStringLength(AMOUNT_MAX_DIGITS)
        field.setValidator { raw -> clampAmountText(raw) }
        field.setWheelDur(1f)
        field.setTextSupplier { amount().toString() }
        field.setTextResponder { text -> setAmount(text.toIntOrNull() ?: 1) }
        field.setCurrentString(amount().toString())
    }

    private fun clampAmountText(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return "1"
        val value = trimmed.toIntOrNull() ?: return amount().toString()
        return value.coerceIn(1, maxAmount()).toString()
    }

    private fun bindAction(group: WidgetGroup?, entityPlayer: Player): ButtonWidget? {
        if (group == null) {
            Hexwright.LOGGER.warn("coalescer.ui is missing the action button 'action'")
            return null
        }
        val click = ButtonWidget(0, 0, group.sizeWidth, group.sizeHeight) { _ ->
            val serverPlayer = entityPlayer as? ServerPlayer
            if (serverPlayer != null && level?.isClientSide == false) {
                onCoalesceClicked(serverPlayer)
            }
        }
        group.addWidget(click)
        return click
    }

    private inner class SourceSlotWidget(
        x: Int,
        y: Int,
        private val swapGuideBackground: Boolean = false
    ) :
        SlotWidget(this@CoalescerBlockEntity, POUCH_SLOT, x, y, true, true) {

        private var pouchGuideShown = true

        override fun updateScreen() {
            super.updateScreen()
            if (!swapGuideBackground) return
            val host = parent ?: return
            val empty = item.isEmpty
            if (empty != pouchGuideShown) {
                pouchGuideShown = empty
                host.setBackground(if (pouchGuideShown) POUCH_SLOT_GUIDE_TEXTURE else POUCH_SLOT_FILLED_TEXTURE)
            }
        }

        override fun canPutStack(stack: ItemStack): Boolean =
            super.canPutStack(stack) && EssenceNetwork.isEssenceSource(stack)
    }

    private inner class SeedSlotWidget(x: Int, y: Int) :
        SlotWidget(this@CoalescerBlockEntity, SEED_SLOT, x, y, true, true) {

        override fun createSlot(inventory: Container, index: Int): Slot =
            object : Slot(inventory, index, 0, 0) {
                override fun mayPickup(playerIn: Player): Boolean = !isFakeSeed(item)
            }

        override fun slotClick(dragType: Int, clickTypeIn: ClickType, player: Player): ItemStack? {
            if (!isFakeSeed(getItem())) return null
            breakFakeSeed()
            return ItemStack.EMPTY
        }

        override fun drawInBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks)
            if (isFakeSeed(getItem())) {
                val pos = position
                graphics.fill(pos.x + 1, pos.y + 1, pos.x + 17, pos.y + 17, FAKE_SEED_TINT)
            }
        }
    }

    private inner class SummarySyncWidget(
        private val summary: TextBoxWidget?,
        private val costPanel: WidgetGroup?,
        private val actionGroup: WidgetGroup?,
        private val actionButton: ButtonWidget?
    ) : Widget(0, 0, 0, 0) {
        private var lastLines: List<String> = emptyList()
        private var lastCostFingerprint = ""
        private var lastEnabled: Boolean? = null
        private val costRows = mutableListOf<Widget>()

        private var actionBaseBackground: IGuiTexture? = null

        override fun updateScreen() {
            super.updateScreen()
            updateSummary()
            updateCostRows()
            updateActionButton()
        }

        private fun updateSummary() {
            val box = summary ?: return
            val lines = summaryLines()
            if (lines != lastLines) {
                lastLines = lines
                box.setContent(lines)
            }
        }

        private fun updateCostRows() {
            val panel = costPanel ?: return
            val entries = costEntries()
            val fingerprint = entries.joinToString("|") { "${it.aspect.name}:${it.have}:${it.need}" }
            if (fingerprint == lastCostFingerprint) return
            lastCostFingerprint = fingerprint

            costRows.forEach { panel.removeWidget(it) }
            costRows.clear()

            var y = COST_ROW_START_Y
            for (entry in entries) {
                val icon = ImageWidget(COST_ICON_X, y, COST_ICON_SIZE, COST_ICON_SIZE, ResourceTexture(aspectIcon(entry.aspect)))
                panel.addWidget(icon)
                costRows.add(icon)

                val text = "${EndlessPouchItem.aspectName(entry.aspect).string}: " +
                    "${EndlessPouchItem.formatAmount(entry.have)} / ${EndlessPouchItem.formatAmount(entry.need)}"
                val label = LabelWidget(COST_LABEL_X, y, text)
                label.setColor(if (entry.enough) GOOD_COLOR else MISS_COLOR)
                panel.addWidget(label)
                costRows.add(label)

                y += COST_ROW_HEIGHT
            }
        }

        private fun updateActionButton() {
            val button = actionButton ?: return
            val busy = isCrafting()
            val enabled = !busy && !insufficientEssence()
            if (enabled == lastEnabled) return
            lastEnabled = enabled
            button.setActive(enabled)
            if (enabled) {
                button.setHoverTooltips(listOf<Component>())
            } else {
                val reason = if (busy) {
                    "gui.hexwright.coalescer.make.tooltip.busy"
                } else {
                    "gui.hexwright.coalescer.make.tooltip.insufficient"
                }
                button.setHoverTooltips(listOf(Component.translatable(reason).withStyle(ChatFormatting.RED)))
            }

            val group = actionGroup ?: return
            if (actionBaseBackground == null) {
                actionBaseBackground = group.backgroundTexture
            }
            val base = actionBaseBackground ?: return
            group.setBackground(if (enabled) base else GuiTextureGroup(base, ColorRectTexture(BUTTON_DISABLED_TINT)))
        }
    }

    private inner class CraftGhostWidget(private val slot: SlotWidget) : Widget(0, 0, 0, 0) {

        override fun drawInBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks)
            val ghost = craftResult
            if (ghost.isEmpty || craftTicks <= 0) return

            val fraction = craftFraction(partialTicks)
            val eased = fraction * fraction * (3f - 2f * fraction)
            val level = (eased * 255f).toInt().coerceIn(0, 255)
            val tint = (level shl 24) or (level shl 16) or (level shl 8) or level
            val pos = slot.position
            DrawerHelper.drawItemStack(graphics, ghost, pos.x + 1, pos.y + 1, tint, "")
        }
    }

    private fun aspectIcon(aspect: IngredientCategory): ResourceLocation =
        Hexwright.id("textures/gui/essence/${aspect.name.lowercase()}.png")


    private fun createFallbackUI(entityPlayer: Player): ModularUI {
        val ui = ModularUI(IMAGE_WIDTH, IMAGE_HEIGHT, this, entityPlayer)
        ui.background(PANEL_BACKGROUND)

        ui.widget(LabelWidget(12, 8, Component.translatable("block.hexwright.coalescer")))

        ui.widget(
            SourceSlotWidget(KEY_SLOT_X - 1, TOP_SLOT_Y - 1)
                .setBackground(ColorRectTexture(TRANSPARENT_COLOR))
                .setHoverTooltips(Component.translatable("gui.hexwright.coalescer.pouch.tooltip"))
        )
        ui.widget(
            SeedSlotWidget(SEED_SLOT_X - 1, TOP_SLOT_Y - 1)
                .setBackground(ColorRectTexture(TRANSPARENT_COLOR))
                .setHoverTooltips(Component.translatable("gui.hexwright.coalescer.seed.tooltip"))
        )
        ui.widget(
            SlotWidget(this, OUTPUT_SLOT, RESULT_SLOT_X - 1, TOP_SLOT_Y - 1, true, false)
                .setBackground(ColorRectTexture(TRANSPARENT_COLOR))
        )

        ui.widget(SummaryPanelWidget(PANEL_X, PANEL_Y, PANEL_WIDTH, PANEL_HEIGHT))

        ui.widget(LabelWidget(AMOUNT_LABEL_X, AMOUNT_ROW_Y + 4, Component.translatable("gui.hexwright.coalescer.amount")))
        ui.widget(
            ButtonWidget(
                AMOUNT_DOWN_X, AMOUNT_ROW_Y, STEPPER_WIDTH, BUTTON_HEIGHT,
                GuiTextureGroup(ColorRectTexture(BUTTON_COLOR), TextTexture("-"))
            ) { _ -> stepAmountOnServer(entityPlayer, -1) }.setHoverBorderTexture(1, 0xB0D8C486.toInt())
        )
        ui.widget(LabelWidget(AMOUNT_VALUE_X, AMOUNT_ROW_Y + 4) { amount().toString() })
        ui.widget(
            ButtonWidget(
                AMOUNT_UP_X, AMOUNT_ROW_Y, STEPPER_WIDTH, BUTTON_HEIGHT,
                GuiTextureGroup(ColorRectTexture(BUTTON_COLOR), TextTexture("+"))
            ) { _ -> stepAmountOnServer(entityPlayer, 1) }.setHoverBorderTexture(1, 0xB0D8C486.toInt())
        )
        ui.widget(
            ButtonWidget(
                ACTION_X, AMOUNT_ROW_Y, ACTION_WIDTH, BUTTON_HEIGHT,
                GuiTextureGroup(ColorRectTexture(BUTTON_COLOR), TextTexture("gui.hexwright.coalescer.make"))
            ) { _ ->
                val serverPlayer = entityPlayer as? ServerPlayer
                if (serverPlayer != null && level?.isClientSide == false) {
                    onCoalesceClicked(serverPlayer)
                }
            }
                .setHoverBorderTexture(1, 0xB0D8C486.toInt())
        )

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

    private fun stepAmountOnServer(entityPlayer: Player, delta: Int) {
        if (entityPlayer !is ServerPlayer || level?.isClientSide != false) return
        setAmount(amount() + delta)
    }

    private inner class SummaryPanelWidget(x: Int, y: Int, width: Int, height: Int) : Widget(x, y, width, height) {

        override fun drawInBackground(
            graphics: GuiGraphics,
            mouseX: Int,
            mouseY: Int,
            partialTicks: Float
        ) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks)
            val pos = position
            val size = this.size
            graphics.fill(pos.x - 2, pos.y - 2, pos.x + size.width + 2, pos.y + size.height + 2, PANEL_BORDER_COLOR)
            graphics.fill(pos.x - 1, pos.y - 1, pos.x + size.width + 1, pos.y + size.height + 1, BAR_BG_COLOR)

            val font = Minecraft.getInstance().font
            var lineY = pos.y + 4
            for (text in summaryLines()) {
                graphics.drawString(font, text.replace("%%", "%"), pos.x + 4, lineY, TEXT_COLOR, false)
                lineY += font.lineHeight + 1
            }
            for (entry in costEntries()) {
                val text = "${EndlessPouchItem.aspectName(entry.aspect).string}: " +
                    "${EndlessPouchItem.formatAmount(entry.have)} / ${EndlessPouchItem.formatAmount(entry.need)}"
                graphics.drawString(font, text, pos.x + 4, lineY, if (entry.enough) GOOD_COLOR else MISS_COLOR, false)
                lineY += font.lineHeight + 1
            }
        }
    }
}
