package com.bluup.hexwright.mixin;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Widget.class)
public interface WidgetHoverTextureAccessor {
    @Accessor("hoverTexture")
    IGuiTexture hexwright$getHoverTexture();
}
