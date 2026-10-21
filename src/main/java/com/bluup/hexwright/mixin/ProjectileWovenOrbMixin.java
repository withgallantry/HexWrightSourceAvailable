package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.hexweave.WovenOrbTriggers;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileWovenOrbMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void hexwright$incomingProjectile(CallbackInfo ci) {
        WovenOrbTriggers.beforeMove((Projectile) (Object) this);
    }
}
