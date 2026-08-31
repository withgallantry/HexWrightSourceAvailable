package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import com.bluup.hexwright.client.spellcasting.PatternDrawBatch;
import com.bluup.hexwright.client.spellcasting.PatternGeometryCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec2;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

@Mixin(GuiSpellcasting.class)
public abstract class GuiSpellcastingGridBatchMixin {
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lat/petrak/hexcasting/client/render/RenderLib;drawSpot(Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec2;FFFFF)V"))
    private void hexwright$batchGridDot(Matrix4f mat, Vec2 point, float radius,
                                        float r, float g, float b, float a) {
        PatternDrawBatch.drawSpot(mat, point, radius, r, g, b, a);
    }

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lat/petrak/hexcasting/client/render/RenderLib;drawPatternFromPoints(Lorg/joml/Matrix4f;Ljava/util/List;Ljava/util/Set;ZIIFFFD)V"))
    private void hexwright$batchPattern(Matrix4f mat, List<Vec2> points, Set<Integer> dupIndices, boolean drawLast,
                                        int colorStart, int colorEnd, float flowIrregular, float readabilityOffset,
                                        float lastSegmentLenProportion, double seed) {
        PatternDrawBatch.drawPattern(mat, points, dupIndices, drawLast, colorStart, colorEnd,
            flowIrregular, readabilityOffset, lastSegmentLenProportion, seed);
    }

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lat/petrak/hexcasting/api/casting/math/HexPattern;toLines(FLnet/minecraft/world/phys/Vec2;)Ljava/util/List;"))
    private List<Vec2> hexwright$cachePatternLines(HexPattern pattern, float hexSize, Vec2 origin) {
        return PatternGeometryCache.toLines(pattern, hexSize, origin);
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;enableDepthTest()V"))
    private void hexwright$drawGridBatch(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                         CallbackInfo ci) {
        PatternDrawBatch.flush();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void hexwright$drawStrayGridBatch(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                              CallbackInfo ci) {
        PatternDrawBatch.flush();
    }
}
