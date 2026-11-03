package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.portal.PortalManager;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityPortalRangeMixin {

    @Shadow
    @Final
    Entity entity;

    @ModifyVariable(method = "updatePlayer", at = @At("STORE"), ordinal = 0)
    private boolean hexwright$trackThroughPortals(boolean inRange, ServerPlayer player,
                                                  @Local(ordinal = 0) double range) {
        if (inRange) {
            return true;
        }
        return PortalManager.withinRangeThroughPair(player.serverLevel(), player.position(),
            entity.position(), range) && entity.broadcastToPlayer(player);
    }
}
