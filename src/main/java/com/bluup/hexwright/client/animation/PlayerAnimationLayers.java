package com.bluup.hexwright.client.animation;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public final class PlayerAnimationLayers {

    private PlayerAnimationLayers() {
    }

    public static <T extends IAnimation> T of(AbstractClientPlayer player, ResourceLocation id, int priority,
                                              Class<T> type, Function<AbstractClientPlayer, T> factory) {
        var data = PlayerAnimationAccess.getPlayerAssociatedData(player);
        IAnimation stored = data.get(id);
        if (type.isInstance(stored)) {
            return type.cast(stored);
        }

        T layer = factory.apply(player);
        data.set(id, layer);
        PlayerAnimationAccess.getPlayerAnimLayer(player).addAnimLayer(priority, layer);
        return layer;
    }
}
