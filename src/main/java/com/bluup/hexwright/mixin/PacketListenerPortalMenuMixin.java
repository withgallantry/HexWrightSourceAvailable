package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.portal.PortalInteraction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PacketListenerPortalMenuMixin {

    @Redirect(method = {"handleContainerClick", "handleContainerButtonClick", "handlePlaceRecipe", "handleSetBeaconPacket"},
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean hexwright$portalAwareStillValid(AbstractContainerMenu menu, Player player) {
        return PortalInteraction.menuStillValid((ServerPlayer) player, menu, menu.stillValid(player));
    }

    @Redirect(method = "handleRenameItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/AnvilMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean hexwright$portalAwareAnvilStillValid(AnvilMenu menu, Player player) {
        return PortalInteraction.menuStillValid((ServerPlayer) player, menu, menu.stillValid(player));
    }

    @Redirect(method = "handleSelectTrade", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/MerchantMenu;stillValid(Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean hexwright$portalAwareMerchantStillValid(MerchantMenu menu, Player player) {
        return PortalInteraction.menuStillValid((ServerPlayer) player, menu, menu.stillValid(player));
    }
}
