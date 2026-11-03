package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.PortalViewRenderer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller", remap = false)
public abstract class SodiumOcclusionCullerMixin {

    @Unique
    private SectionPos hexwright$seed;

    @ModifyVariable(method = "findVisible", at = @At("HEAD"), argsOnly = true, remap = false)
    private boolean hexwright$noOcclusionCullingForPortalPass(boolean useOcclusionCulling) {
        Vec3 destination = PortalViewRenderer.activeDestinationCenter();
        if (destination == null) {
            hexwright$seed = null;
            return useOcclusionCulling;
        }
        hexwright$seed = SectionPos.of(destination);
        return false;
    }

    @ModifyVariable(method = "init", at = @At("STORE"), ordinal = 0, remap = false)
    private SectionPos hexwright$seedInit(SectionPos cameraSection) {
        return hexwright$seed != null ? hexwright$seed : cameraSection;
    }

    @ModifyVariable(method = "initWithinWorld", at = @At("STORE"), ordinal = 0, remap = false)
    private SectionPos hexwright$seedInitWithinWorld(SectionPos cameraSection) {
        return hexwright$seed != null ? hexwright$seed : cameraSection;
    }
}
