package com.bluup.hexwright.server.menu

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.GuiGraphics

class SliderFillTexture(
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
