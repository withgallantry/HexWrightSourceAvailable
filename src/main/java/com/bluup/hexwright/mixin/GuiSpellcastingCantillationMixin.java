package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import com.bluup.hexwright.client.cantillation.ClientCantillations;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = GuiSpellcasting.class, remap = false)
public abstract class GuiSpellcastingCantillationMixin {

    private static final String PLAY =
        "Lnet/minecraft/client/sounds/SoundManager;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V";

    @Unique
    private HexPattern hexwright$drawn;

    @ModifyArg(method = "drawMove", index = 2, at = @At(value = "INVOKE",
        target = "Lat/petrak/hexcasting/client/gui/GuiSpellcasting$PatternDrawState$Drawing;<init>(Lat/petrak/hexcasting/api/casting/math/HexCoord;Lat/petrak/hexcasting/api/casting/math/HexCoord;Lat/petrak/hexcasting/api/casting/math/HexPattern;)V"))
    private HexPattern hexwright$keepDrawn(HexPattern pattern) {
        hexwright$drawn = pattern;
        return pattern;
    }

    @ModifyArg(method = "drawStart", at = @At(value = "INVOKE", target = PLAY, remap = true))
    private SoundInstance hexwright$singStart(SoundInstance hexSound) {
        return ClientCantillations.stroke(null, hexSound);
    }

    @ModifyArg(method = "drawMove", at = @At(value = "INVOKE", target = PLAY, remap = true))
    private SoundInstance hexwright$singSegment(SoundInstance hexSound) {
        return ClientCantillations.stroke(hexwright$drawn, hexSound);
    }
}
