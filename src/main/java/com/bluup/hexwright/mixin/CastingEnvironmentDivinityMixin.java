package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.PatternShapeMatch;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.mishaps.MishapDisallowedSpell;
import com.bluup.hexwright.server.item.DivinityItem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CastingEnvironment.class)
public abstract class CastingEnvironmentDivinityMixin {

    @WrapOperation(
        method = {"canEditBlockAt", "assertPosInRangeForEditing"},
        at = @At(
            value = "INVOKE",
            target = "Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;isVecInRange(Lnet/minecraft/world/phys/Vec3;)Z"))
    private boolean hexwright$buildReach(CastingEnvironment env, Vec3 vec, Operation<Boolean> original) {
        return original.call(env, vec) || DivinityItem.inBuildReach(env, vec);
    }

    @Inject(method = "precheckAction", at = @At("HEAD"), remap = false)
    private void hexwright$refuseGreatSpells(PatternShapeMatch match, CallbackInfo ci) {
        CastingEnvironment self = (CastingEnvironment) (Object) this;
        if (match instanceof PatternShapeMatch.PerWorld perWorld
            && self.getCastingEntity() instanceof Player player && DivinityItem.holdsOwn(player)) {
            throw new MishapDisallowedSpell("hexwright_divinity", perWorld.key.location());
        }
    }
}
