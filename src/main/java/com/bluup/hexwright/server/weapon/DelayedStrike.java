package com.bluup.hexwright.server.weapon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

public final class DelayedStrike {

    private static final double REACH_SQR = 36.0D;

    @Nullable
    private static Player landing;

    private static float landingStrength;

    private DelayedStrike() {
    }

    public static boolean hold(Player player, Entity target) {
        if (player == landing) {
            return false;
        }
        if (!(player instanceof ServerPlayer server)) {
            return false;
        }
        Item held = player.getMainHandItem().getItem();
        if (!(held instanceof AnimatedWeapon weapon)) {
            return false;
        }
        int delay = AnimatedWeapon.frameToTicks(player, weapon, weapon.strikeClip(player, 0),
            weapon.strikeHitDelayTicks());
        if (delay <= 0) {
            return false;
        }
        if (!target.isAttackable()) {
            return false;
        }

        float strength = player.getAttackStrengthScale(0.5F);
        player.resetAttackStrengthTicker();
        boolean onGround = player.onGround();
        float fallDistance = player.fallDistance;

        SlamWindUp.schedule(server.server, delay,
            () -> land(server, target, held, strength, onGround, fallDistance));
        return true;
    }

    public static boolean isLanding(Player player) {
        return player == landing;
    }

    public static float landingStrength() {
        return landingStrength;
    }

    private static void land(ServerPlayer player, Entity target, Item weapon, float strength,
                             boolean onGround, float fallDistance) {
        if (player.isRemoved() || !player.isAlive() || player.isSpectator()) {
            return;
        }
        if (player.getMainHandItem().getItem() != weapon) {
            return;
        }
        if (target.isRemoved() || target.level() != player.level()) {
            return;
        }
        if (target instanceof LivingEntity living && !living.isAlive()) {
            return;
        }
        if (player.distanceToSqr(target) > REACH_SQR) {
            return;
        }

        Player previousLanding = landing;
        float previousStrength = landingStrength;
        boolean liveOnGround = player.onGround();
        float liveFallDistance = player.fallDistance;
        landing = player;
        landingStrength = strength;
        player.setOnGround(onGround);
        player.fallDistance = fallDistance;
        try {
            player.attack(target);
        } finally {
            landing = previousLanding;
            landingStrength = previousStrength;
            player.setOnGround(liveOnGround);
            player.fallDistance = liveFallDistance;
        }
    }
}
