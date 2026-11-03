package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.portal.PortalInteraction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class PlayerPortalMenuMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean hexwright$portalAwareStillValid(AbstractContainerMenu menu, Player player) {
        boolean vanillaValid = menu.stillValid(player);
        if ((Object) this instanceof ServerPlayer serverPlayer) {
            return PortalInteraction.menuStillValid(serverPlayer, menu, vanillaValid);
        }
        return vanillaValid;
    }
}
