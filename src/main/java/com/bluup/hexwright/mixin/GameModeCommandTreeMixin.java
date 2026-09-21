package com.bluup.hexwright.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class GameModeCommandTreeMixin {

    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "changeGameModeForPlayer", at = @At("RETURN"))
    private void hexwright$resendCommandTree(GameType gameType, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || this.player.connection == null) {
            return;
        }
        this.player.server.getCommands().sendCommands(this.player);
    }
}
