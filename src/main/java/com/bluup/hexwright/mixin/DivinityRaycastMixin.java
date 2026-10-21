package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.common.casting.actions.raycast.OpBlockAxisRaycast;
import at.petrak.hexcasting.common.casting.actions.raycast.OpBlockRaycast;
import at.petrak.hexcasting.common.casting.actions.raycast.OpEntityRaycast;
import com.bluup.hexwright.server.item.DivinityItem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({OpBlockRaycast.class, OpBlockAxisRaycast.class, OpEntityRaycast.class})
public abstract class DivinityRaycastMixin {

    @WrapOperation(
        method = "execute",
        at = @At(
            value = "INVOKE",
            target = "Lat/petrak/hexcasting/api/casting/castables/Action$Companion;raycastEnd(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 hexwright$reachAmbitEdge(Action.Companion companion, Vec3 origin, Vec3 look,
                                          Operation<Vec3> original,
                                          @Local(argsOnly = true) CastingEnvironment env) {
        double reach = DivinityItem.raycastReach(env);
        if (reach <= Action.RAYCAST_DISTANCE) {
            return original.call(companion, origin, look);
        }
        return origin.add(look.normalize().scale(reach));
    }
}
