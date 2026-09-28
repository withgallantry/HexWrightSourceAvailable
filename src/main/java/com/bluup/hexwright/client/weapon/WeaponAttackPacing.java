package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

public final class WeaponAttackPacing {

    @Nullable
    private static Item bufferedFor;

    private static int bufferedAt;

    private WeaponAttackPacing() {
    }

    public static boolean mayAttack(Minecraft mc) {
        if (!isPaced(mc)) {
            return true;
        }
        LocalPlayer player = mc.player;
        float charge = player.getAttackStrengthScale(0.0F);
        if (charge >= 1.0F) {
            bufferedFor = null;
            return true;
        }
        if (charge >= 0.5F) {
            bufferedFor = player.getMainHandItem().getItem();
            bufferedAt = player.tickCount;
        }
        return false;
    }

    public static boolean takeBufferedClick(Minecraft mc) {
        if (bufferedFor == null) {
            return false;
        }
        LocalPlayer player = mc.player;
        int age = player == null ? -1 : player.tickCount - bufferedAt;
        if (player == null
            || age < 0
            || age > player.getCurrentItemAttackStrengthDelay()
            || player.getMainHandItem().getItem() != bufferedFor
            || player.isUsingItem()
            || !isPaced(mc)) {
            bufferedFor = null;
            return false;
        }
        if (player.getAttackStrengthScale(0.0F) < 1.0F) {
            return false;
        }
        bufferedFor = null;
        return true;
    }

    private static boolean isPaced(Minecraft mc) {
        return mc.player != null
            && (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK)
            && AnimatedWeapon.ownsAttackSwing(mc.player);
    }
}
