package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.mod.HexConfig;
import com.bluup.hexwright.server.block.ResonantAnchorBlockEntity;
import com.bluup.hexwright.server.staff_assembly.StaffCorePatternPower;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "at.petrak.hexcasting.common.casting.actions.spells.great.OpTeleport$Spell")
public abstract class OpTeleportSpellMixin {
    @Shadow
    @Final
    private Entity teleportee;

    private Vec3 hexwright$warpFrom;

    @Inject(method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V", at = @At("HEAD"), remap = false)
    private void hexwright$captureBefore(CastingEnvironment env, CallbackInfo ci) {
        hexwright$warpFrom = teleportee.position();
    }

    @Inject(method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V", at = @At("TAIL"), remap = false)
    private void hexwright$captureAfter(CastingEnvironment env, CallbackInfo ci) {
        StaffCorePatternPower.onTravellerTeleportCast(env, teleportee, hexwright$warpFrom);
    }

    @Redirect(
        method = "cast(Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)V",
        at = @At(
            value = "INVOKE",
            target = "Lat/petrak/hexcasting/api/mod/HexConfig$ServerConfigAccess;doesGreaterTeleportSplatItems()Z"
        ),
        remap = false
    )
    private boolean hexwright$spareProtectedLandings(HexConfig.ServerConfigAccess config, CastingEnvironment env) {
        if (!config.doesGreaterTeleportSplatItems()) {
            return false;
        }
        if (hexwright$landedOnAnchor()) {
            return false;
        }
        return !StaffCorePatternPower.travellerBenefitsApply(env, teleportee);
    }

    private boolean hexwright$landedOnAnchor() {
        BlockPos landing = BlockPos.containing(teleportee.position());
        return teleportee.level().getBlockEntity(landing.below()) instanceof ResonantAnchorBlockEntity
            || teleportee.level().getBlockEntity(landing) instanceof ResonantAnchorBlockEntity;
    }
}
