package com.bluup.hexwright.mixin;

import com.bluup.hexwright.common.aspects.RecipeAspects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerRecipeAspectsMixin {

    @Inject(method = "handleUpdateRecipes", at = @At("TAIL"))
    private void hexwright$rebuildRecipeAspects(ClientboundUpdateRecipesPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return;
        }
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        RecipeAspects.rebuild(self.getRecipeManager(), self.registryAccess());
    }
}
