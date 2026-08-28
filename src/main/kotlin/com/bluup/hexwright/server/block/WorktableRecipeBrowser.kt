package com.bluup.hexwright.server.block

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.client.ldlib.widget.RuneTextWidget
import com.bluup.hexwright.client.ldlib.widget.StaffItemDisplayWidget
import com.bluup.hexwright.client.progression.ClientMastery
import com.bluup.hexwright.client.progression.ClientRecipeUnlocks
import com.bluup.hexwright.server.block.WorktableRecipes.RECIPES
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup
import com.lowdragmc.lowdraglib.gui.widget.SelectorWidget
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import kotlin.math.roundToInt

class WorktableRecipeBrowser(
    private val forge: WorktableBlockEntity,
    private val scrollGroup: DraggableScrollableWidgetGroup
) {
    companion object {

        internal val RECIPE_ROW_ACTIVE_TEXTURE: IGuiTexture =
            ResourceBorderTexture("ldlib:textures/menu/list-box-select.png", 195, 136, 4, 4)

        internal val RECIPE_ROW_BACKGROUND_TEXTURE: IGuiTexture =
            ResourceBorderTexture("ldlib:textures/menu/list-box.png", 195, 136, 4, 4)

        private const val RECIPE_NAME_SCALE = 0.8f

        private const val LOCKED_RUNE_COLOR = 0xFF8C87A8.toInt()

        private const val CATEGORY_SELECTOR_ID = "drop-down"

        private const val SELECTOR_TEXT_COLOR = 0xFFC8D7FF.toInt()

        private val DROPDOWN_BACKGROUND_TEXTURE: IGuiTexture = RECIPE_ROW_BACKGROUND_TEXTURE

        private val DROPDOWN_ACTIVE_TEXTURE: IGuiTexture = RECIPE_ROW_ACTIVE_TEXTURE

        private val DROPDOWN_SCROLL_BAR_TEXTURE: IGuiTexture =
            ResourceBorderTexture("ldlib:textures/scrollbar-body.png", 180, 180, 2, 2)
    }

    private data class RecipeRowWidgets(
        val row: WidgetGroup,
        val clickZone: ButtonWidget,
        val nameWidget: TextTextureWidget?,
        val iconWidget: StaffItemDisplayWidget?,
        val runeWidget: RuneTextWidget
    )

    private val entryWidgets = HashMap<Int, RecipeRowWidgets>()

    private var category: EssenceForgeCategories.ForgeCategory? = null

    fun buildRows(root: WidgetGroup, player: Player): Boolean {
        val template = root.getFirstWidgetById("^recipe_entry$") as? WidgetGroup ?: return false
        val templateTag = (template as? IConfigurableWidget)?.serializeWrapper() ?: return false
        scrollGroup.removeWidget(template)

        scrollGroup.clearAllWidgets()
        entryWidgets.clear()
        for (index in RECIPES.indices) {
            val recipe = RECIPES[index]

            val clone = IConfigurableWidget.deserializeWrapper(templateTag)?.widget() as? WidgetGroup ?: continue
            clone.id = "recipe_entry_$index"
            clone.setSelfPosition(clone.selfPositionX, index * (clone.sizeHeight + 2))

            val icon = clone.getFirstWidgetById("^recipe_image$") as? StaffItemDisplayWidget
            val iconStack = recipe.preview()
            icon?.setStackSupplier { iconStack }

            val nameWidget = clone.getFirstWidgetById("^recipe_name$") as? TextTextureWidget
            nameWidget?.setClientSideWidget()
            nameWidget?.let {
                it.textTexture.scale(RECIPE_NAME_SCALE)
                it.textTexture.setWidth((it.size.width / RECIPE_NAME_SCALE).roundToInt())
                it.textTexture.transform(-it.size.width * (1f - RECIPE_NAME_SCALE) / 2f, 0f)
            }
            nameWidget?.setLastComponent(Component.translatable(recipe.nameKey))

            val runeWidget = RuneTextWidget(0, 0, clone.sizeWidth, clone.sizeHeight)
                .setSourceText(Component.translatable(recipe.nameKey))
                .setColor(LOCKED_RUNE_COLOR)
                .setMaxScale(RECIPE_NAME_SCALE)
            runeWidget.setVisible(false)
            clone.addWidget(runeWidget)

            val clickIndex = index
            val clickZone = ButtonWidget(0, 0, clone.sizeWidth, clone.sizeHeight) { _ -> forge.selectRecipe(clickIndex, player) }
            clone.addWidget(clickZone)

            clone.setBackground(RECIPE_ROW_BACKGROUND_TEXTURE)
            clone.setHoverTexture(RECIPE_ROW_ACTIVE_TEXTURE)

            scrollGroup.addWidget(clone)
            entryWidgets[index] = RecipeRowWidgets(clone, clickZone, nameWidget, icon, runeWidget)
        }
        scrollGroup.computeMax()
        return true
    }

    fun bindCategorySelector(root: WidgetGroup) {
        val categories = EssenceForgeCategories.get(RECIPES.mapTo(HashSet()) { it.nameKey })

        val marker = root.getFirstWidgetById("^$CATEGORY_SELECTOR_ID$") as? SelectorWidget
        val host = marker?.parent
        if (marker == null || host == null) {
            Hexwright.LOGGER.warn("${WorktableBlockEntity.UI_PROJECT_NAME} is missing selector widget '$CATEGORY_SELECTOR_ID'")
            show(null)
            return
        }

        val labels = categories.map { it.label() }

        val selector = object : SelectorWidget(
            marker.selfPositionX, marker.selfPositionY,
            marker.sizeWidth, marker.sizeHeight,
            labels, SELECTOR_TEXT_COLOR
        ) {
            override fun computeLayout() {
                super.computeLayout()
                setButtonBackground(DROPDOWN_BACKGROUND_TEXTURE)
                popUp.setBackground(DROPDOWN_BACKGROUND_TEXTURE)
                button.setHoverTexture(DROPDOWN_ACTIVE_TEXTURE)
                popUp.setYBarStyle(null, DROPDOWN_SCROLL_BAR_TEXTURE)
                selectables.forEach { row ->
                    row.setSelectedTexture(DROPDOWN_ACTIVE_TEXTURE)
                    row.setHoverTexture(DROPDOWN_ACTIVE_TEXTURE)
                    row.isOverlayUnderWidgets = true
                }
            }
        }
        selector.setClientSideWidget()
        selector.setOnChanged { picked ->
            show(categories.getOrNull(labels.indexOf(picked)))
        }

        val slot = host.widgets.indexOf(marker)
        host.removeWidget(marker)
        host.addWidget(if (slot >= 0) slot else host.widgets.size, selector)

        val initial = categories.firstOrNull()
        selector.setValue(initial?.label().orEmpty())
        show(initial)
    }

    private fun show(category: EssenceForgeCategories.ForgeCategory?) {
        this.category = category
        refilter()
    }

    private fun isRecipeListed(index: Int): Boolean {
        if (forge.level?.isClientSide != true) return true
        val recipe = RECIPES[index]
        if (!ClientRecipeUnlocks.isDiscovered(recipe.nameKey)) return false
        val required = recipe.requiredMastery
        return required == null || ClientMastery.hasMastery(required)
    }

    fun refilter() {
        val category = this.category
        scrollGroup.setScrollYOffset(0)

        val inCategory = RECIPES.indices.filter { category == null || category.lists(RECIPES[it].nameKey) }
        val order = inCategory.filter { isRecipeListed(it) } + inCategory.filterNot { isRecipeListed(it) }
        val slots = HashMap<Int, Int>(order.size)
        order.forEachIndexed { slot, index -> slots[index] = slot }

        for (index in RECIPES.indices) {
            val widgets = entryWidgets[index] ?: continue
            val entry = widgets.row
            val slot = slots[index]
            setRowLocked(widgets, !isRecipeListed(index))
            entry.setSelfPosition(entry.selfPositionX, slot?.times(entry.sizeHeight + 2) ?: -(entry.sizeHeight + 2))
            entry.setVisible(slot != null)
        }
        scrollGroup.computeMax()
    }

    private fun setRowLocked(widgets: RecipeRowWidgets, locked: Boolean) {
        widgets.iconWidget?.setVisible(!locked)
        widgets.nameWidget?.setVisible(!locked)
        widgets.runeWidget.setVisible(locked)
        widgets.clickZone.setActive(!locked)
        widgets.row.setActive(!locked)
    }

    fun highlightSelection(selected: Int) {
        for ((index, entry) in entryWidgets) {
            val isSelected = index == selected && isRecipeListed(index)
            entry.row.setBackground(if (isSelected) RECIPE_ROW_ACTIVE_TEXTURE else RECIPE_ROW_BACKGROUND_TEXTURE)
        }
    }
}
