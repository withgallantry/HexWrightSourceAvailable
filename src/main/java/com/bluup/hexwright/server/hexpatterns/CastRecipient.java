package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import com.bluup.hexwright.server.constructshand.ConstructsGrasp;
import com.bluup.hexwright.server.constructshand.ConstructsHandBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class CastRecipient {

    private CastRecipient() {
    }

    public static void tell(CastingEnvironment env, Component message) {
        ServerPlayer caster = env.getCaster();
        if (caster != null) {
            caster.displayClientMessage(message, true);
        } else {
            env.printMessage(message);
        }
    }

    public static Vec3 dropPos(CastingEnvironment env) {
        ConstructsHandBlockEntity hand = ConstructsGrasp.graspedHand(env);
        return hand != null
            ? Vec3.atBottomCenterOf(hand.getBlockPos().above())
            : env.mishapSprayPos();
    }
}
