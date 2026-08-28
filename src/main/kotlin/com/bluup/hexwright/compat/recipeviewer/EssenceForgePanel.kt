package com.bluup.hexwright.compat.recipeviewer

import com.bluup.hexwright.Hexwright
import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory
import com.bluup.hexwright.server.block.WorktableRecipes
import com.bluup.hexwright.server.item.EndlessPouchItem
import com.bluup.hexwright.server.pocketcaster.PocketCasterData
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import java.util.Locale

object EssenceForgePanel {

    const val WIDTH = 160

    private const val NAME_Y = 0

    const val SLOT_Y = 16
    const val POUCH_X = 2
    const val OUTPUT_X = 58
    const val SLOT_BACKGROUND_INSET = 1
    private const val SLOT_SIZE = 18

    const val ARROW_X = 26
    const val ARROW_Y = SLOT_Y

    private const val HINT_X = 84

    private const val COST_LINE = 11
    private const val ICON_SIZE = 8

    private const val SECTION_GAP = 5
    private const val LABEL_GAP = 3
    private const val LOCK_GAP = 6
    private const val BOTTOM_PADDING = 2

    private const val TITLE_COLOR = 0x404040
    private const val LABEL_COLOR = 0x666666
    private const val VALUE_COLOR = 0x404040
    private const val LOCK_COLOR = 0x8B1A1A

    private val COST_LABEL: Component = Component.translatable("gui.hexwright.recipe.essence_required")
    private val GRADE_HINT: Component = Component.translatable("gui.hexwright.recipe.grade_hint")

    private val font: Font get() = Minecraft.getInstance().font

    val height: Int
        get() {
            val top = costY()
            return WorktableRecipes.RECIPES.maxOf { recipe ->
                val costBottom = top + recipe.steps.size * COST_LINE
                val required = recipe.requiredMastery
                    ?: return@maxOf costBottom
                costBottom + LOCK_GAP + font.split(requirementText(required), WIDTH).size * font.lineHeight
            } + BOTTOM_PADDING
        }

    private fun costLabelY(): Int = maxOf(
        SLOT_Y - SLOT_BACKGROUND_INSET + SLOT_SIZE,
        SLOT_Y + font.split(GRADE_HINT, WIDTH - HINT_X).size * font.lineHeight
    ) + SECTION_GAP

    private fun costY(): Int = costLabelY() + font.lineHeight + LABEL_GAP

    fun draw(graphics: GuiGraphics, display: EssenceForgeDisplay) {
        val font = this.font

        graphics.drawString(font, display.name.copy().withStyle(ChatFormatting.BOLD), 0, NAME_Y, TITLE_COLOR, false)

        if (display.graded) {
            var hintY = SLOT_Y
            for (line in font.split(GRADE_HINT, WIDTH - HINT_X)) {
                graphics.drawString(font, line, HINT_X, hintY, LABEL_COLOR, false)
                hintY += font.lineHeight
            }
        }

        graphics.drawString(font, COST_LABEL, 0, costLabelY(), LABEL_COLOR, false)

        var y = costY()
        for (step in display.steps) {
            drawAspectIcon(graphics, step.aspect, 1, y)
            val line = Component.translatable(
                "gui.hexwright.recipe.essence_line",
                EndlessPouchItem.aspectName(step.aspect),
                EndlessPouchItem.formatAmount(step.amount)
            )
            graphics.drawString(font, line, ICON_SIZE + 4, y, VALUE_COLOR, false)
            y += COST_LINE
        }

        val required = display.requiredMastery ?: return
        var lockY = y + LOCK_GAP
        for (line in font.split(requirementText(required), WIDTH)) {
            graphics.drawString(font, line, 0, lockY, LOCK_COLOR, false)
            lockY += font.lineHeight
        }
    }

    private fun requirementText(required: PocketCasterData.Quality): Component {
        val titleKey = required.advancementTitleKey()
            ?: return Component.translatable(
                "gui.hexwright.recipe.requires.grade",
                Component.translatable(required.translationKey())
            )
        return Component.translatable("gui.hexwright.recipe.requires", Component.translatable(titleKey))
    }

    private fun drawAspectIcon(graphics: GuiGraphics, aspect: IngredientCategory, x: Int, y: Int) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate(x.toFloat(), y.toFloat(), 0f)
        val scale = ICON_SIZE / 64f
        pose.scale(scale, scale, 1f)
        graphics.blit(aspectIcon(aspect), 0, 0, 0f, 0f, 64, 64, 64, 64)
        pose.popPose()
    }

    private fun aspectIcon(aspect: IngredientCategory): ResourceLocation =
        Hexwright.id("textures/gui/essence/${aspect.name.lowercase(Locale.ROOT)}.png")
}
