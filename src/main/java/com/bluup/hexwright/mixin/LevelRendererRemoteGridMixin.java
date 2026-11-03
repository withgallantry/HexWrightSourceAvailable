package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.RemoteLevelManager;
import com.bluup.hexwright.client.portal.SodiumPortalCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererRemoteGridMixin {

    @ModifyArg(method = "allChanged", index = 2, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ViewArea;<init>(Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;Lnet/minecraft/world/level/Level;ILnet/minecraft/client/renderer/LevelRenderer;)V"))
    private int hexwright$smallGridForStreamedRemote(int viewDistance) {
        int capped = RemoteLevelManager.streamedViewDistance((LevelRenderer) (Object) this);
        if (SodiumPortalCompat.isActive()) {
            return capped < 0 ? viewDistance : Math.min(viewDistance, capped);
        }
        int wanted = Minecraft.getInstance().options.getEffectiveRenderDistance();
        return capped < 0 ? wanted : Math.min(wanted, capped);
    }
}
