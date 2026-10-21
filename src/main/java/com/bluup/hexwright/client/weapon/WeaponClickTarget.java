package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import com.bluup.hexwright.server.weapon.SwingArc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;

public final class WeaponClickTarget {

    private WeaponClickTarget() {
    }

    public static boolean isDig(Minecraft mc) {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        LocalPlayer player = mc.player;
        return player == null
            || !(player.getMainHandItem().getItem() instanceof AnimatedWeapon weapon)
            || !SwingArc.reachesEnemy(player, weapon);
    }
}
