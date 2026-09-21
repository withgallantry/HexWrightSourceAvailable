package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.pentabox.PentaboxEvents;
import com.bluup.hexwright.server.talisman.TalismanCasting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDeathMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void hexwright$rearmReprieve(DamageSource source, CallbackInfo ci) {
        TalismanCasting.onDied((ServerPlayer) (Object) this);
        PentaboxEvents.onDied((ServerPlayer) (Object) this);
    }
}
