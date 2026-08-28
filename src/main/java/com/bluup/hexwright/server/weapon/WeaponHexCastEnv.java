package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.pocketcaster.PocketCasterCastEnv;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public final class WeaponHexCastEnv extends PocketCasterCastEnv {

    public WeaponHexCastEnv(ServerPlayer caster, InteractionHand castingHand) {
        super(caster, castingHand);
    }
}
