package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.bluup.hexwright.server.media.MediaGrants;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CastingEnvironment.class)
public abstract class CastingEnvironmentMediaGrantMixin {

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void hexwright$installGrant(ServerLevel world, CallbackInfo ci) {
        MediaGrants.install((CastingEnvironment) (Object) this);
    }
}
