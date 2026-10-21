package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.dungeon.DungeonInstances;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListRemoveMixin {

    @Inject(method = "remove", at = @At("HEAD"))
    private void hexwright$settleDungeon(ServerPlayer player, CallbackInfo ci) {
        DungeonInstances.onLeaving(player);
    }
}
