package com.bluup.hexwright.server.staff_assembly;

import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.inits.HexwrightNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class StaffCastAnimationTrigger {
    private static final String CLIP = "staff_projectile_release";

    private StaffCastAnimationTrigger() {
    }

    public static void onSuccessfulCast(ServerPlayer caster) {
        HexwrightNetworking.sendPlayerAnimation(caster, PlayerAnimationLayer.ONE_SHOT, CLIP);
    }
}
