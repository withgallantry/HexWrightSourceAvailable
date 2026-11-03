package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.dust.DustSupport;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityDustSupportMixin {

    @Inject(method = "move", at = @At("TAIL"))
    private void hexwright$dustSupport(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (type == MoverType.SELF || type == MoverType.PLAYER) {
            DustSupport.apply((Entity) (Object) this);
        }
    }
}
