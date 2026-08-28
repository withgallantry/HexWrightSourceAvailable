package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(GuiSpellcasting.class)
public interface GuiSpellcastingAccessor {
    @Accessor("handOpenedWith")
    InteractionHand hexwright$getHandOpenedWith();

    @Accessor("usedSpots")
    Set<HexCoord> hexwright$getUsedSpots();
}
